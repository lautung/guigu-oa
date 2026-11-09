package com.lautung.oa.process.approval;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.common.result.ResultCodeEnum;
import com.lautung.oa.common.validation.ValidationMessages;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessAddSignTask;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.ProcessApprovalSupport.AddSignStatus;
import com.lautung.oa.process.ProcessApprovalSupport.AddSignType;
import com.lautung.oa.process.ProcessStartSupport;
import com.lautung.oa.process.attachment.AttachmentService;
import com.lautung.oa.process.cc.CarbonCopyWriter;
import com.lautung.oa.process.mapper.OaProcessAddSignTaskMapper;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.membership.ApprovalMembership;
import com.lautung.oa.process.membership.ApprovalMembershipResolver;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.process.service.OaProcessRecordService;
import com.lautung.oa.vo.process.AddSignVo;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.TransferVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批写入的唯一实现：账本、抄送、附件绑定、引擎命令的次序与事务边界都在这里。
 *
 * <p>发起带提交标识时，同一用户同一标识只对应一条审批。冲突兜底靠
 * {@code uk_oa_process_idempotency_key}；命中唯一索引的调用方必须在本类事务之外
 * 调用 {@link #startedOutcome}，否则新事务看不到对手已提交的行。
 */
@Service
public class ApprovalWriter {

    private static final Logger log = LoggerFactory.getLogger(ApprovalWriter.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    /** 催办频控（ADR 0068）：同一审批 24 小时内最多一次（窗口常量与待处理催办标记同源）。 */
    private static final Duration URGE_COOLDOWN = ProcessApprovalSupport.URGE_MARK_WINDOW;

    private final ApprovalTemplate approvalTemplate;
    private final ApprovalEngine approvalEngine;
    private final AttachmentService attachmentService;
    private final CarbonCopyWriter carbonCopyWriter;
    private final ApprovalMembershipResolver membershipResolver;
    private final OaProcessMapper processMapper;
    private final OaProcessAddSignTaskMapper addSignTaskMapper;
    private final OaProcessRecordService processRecordService;
    private final SysUserService sysUserService;

    public ApprovalWriter(
            ApprovalTemplate approvalTemplate,
            ApprovalEngine approvalEngine,
            AttachmentService attachmentService,
            CarbonCopyWriter carbonCopyWriter,
            ApprovalMembershipResolver membershipResolver,
            OaProcessMapper processMapper,
            OaProcessAddSignTaskMapper addSignTaskMapper,
            OaProcessRecordService processRecordService,
            SysUserService sysUserService) {
        this.approvalTemplate = approvalTemplate;
        this.approvalEngine = approvalEngine;
        this.attachmentService = attachmentService;
        this.carbonCopyWriter = carbonCopyWriter;
        this.membershipResolver = membershipResolver;
        this.processMapper = processMapper;
        this.addSignTaskMapper = addSignTaskMapper;
        this.processRecordService = processRecordService;
        this.sysUserService = sysUserService;
    }

    @Transactional
    public Approval.StartOutcome startUp(SysUser user, ProcessFormVo processFormVo) {
        String idempotencyKey = normalizeIdempotencyKey(processFormVo.getIdempotencyKey());
        Process started = startedProcess(user, idempotencyKey);
        if (started != null) {
            return replayedOutcome(started);
        }

        // 只在真正新发起时检查表单结构：重复送达按 ADR 0032 返回第一条，不比对内容。
        requireWellFormedFormValues(processFormVo.getFormValues());

        ApprovalTemplate.PublishedApproval publication =
                approvalTemplate.resolveForInitiation(processFormVo.getProcessTemplateId());
        ProcessTemplate processTemplate = publication.template();

        // 已发布模板的动态必填（ADR 0035）：结构校验只保证 JSON 形状，这里才拿得到模板约束。
        // 仍在任何写入之前，201 的含义是「什么都没发生」。
        requireRequiredFieldsPresent(processTemplate.getFormProps(), processFormVo.getFormValues());

        Process process = new Process();
        BeanUtils.copyProperties(processFormVo, process);
        process.setStatus(1);
        process.setProcessCode(System.currentTimeMillis() + "");
        process.setUserId(user.getId());
        process.setFormValues(processFormVo.getFormValues());
        process.setTitle(ProcessStartSupport.title(user.getName(), processTemplate.getName()));
        process.setIdempotencyKey(idempotencyKey);
        processMapper.insert(process);

        carbonCopyWriter.copyAtStart(user, process.getId(), processFormVo.getCcUserIds());

        attachmentService.bindDraft(
                user, processFormVo.getAttachmentDraftId(), processFormVo.getAttachmentIds(), process.getId());

        String processInstanceId = approvalEngine.start(
                publication.processDefinitionId(),
                String.valueOf(process.getId()),
                ProcessStartSupport.engineVariables(processFormVo.getFormValues()));

        List<ApprovalEngine.CurrentTask> currentTasks = approvalEngine.currentTasks(processInstanceId);
        List<String> nameList = approverNames(currentTasks);
        process.setProcessInstanceId(processInstanceId);
        process.setDescription(ProcessStartSupport.waitingDescription(nameList));
        processMapper.updateById(process);

        processRecordService.record(process.getId(), ProcessApprovalSupport.ApprovalAction.initiate(), user);

        return new Approval.StartOutcome(
                process.getId(),
                processInstanceId,
                currentTasks.stream().map(ApprovalEngine.CurrentTask::taskId).toList());
    }

    /**
     * 幂等命中后的读取：调用方必须已经脱离 {@link #startUp} 的事务（通常由
     * {@code DuplicateKeyException} 触发），否则读不到对手已提交的那一行。
     * 找不到返回 {@code null}，让调用方把原来的唯一约束异常抛出去。
     */
    @Transactional(readOnly = true)
    public Approval.StartOutcome startedOutcome(SysUser user, String rawIdempotencyKey) {
        if (user == null || user.getId() == null || rawIdempotencyKey == null) {
            return null;
        }
        Process started = startedProcess(user, normalizeIdempotencyKey(rawIdempotencyKey));
        return started == null ? null : replayedOutcome(started);
    }

    private Process startedProcess(SysUser user, String idempotencyKey) {
        if (idempotencyKey == null || user == null || user.getId() == null) {
            return null;
        }
        LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Process::getUserId, user.getId())
                .eq(Process::getIdempotencyKey, idempotencyKey);
        return processMapper.selectOne(wrapper);
    }

    private Approval.StartOutcome replayedOutcome(Process process) {
        log.info("Start replay hit idempotency_key={} processId={}",
                process.getIdempotencyKey(), process.getId());
        List<ApprovalEngine.CurrentTask> currentTasks = StringUtils.hasText(process.getProcessInstanceId())
                ? approvalEngine.currentTasks(process.getProcessInstanceId())
                : List.of();
        return new Approval.StartOutcome(
                process.getId(),
                process.getProcessInstanceId(),
                currentTasks.stream().map(ApprovalEngine.CurrentTask::taskId).toList());
    }

    private static String normalizeIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null) {
            return null;
        }
        String idempotencyKey = rawIdempotencyKey.trim();
        if (idempotencyKey.isEmpty()) {
            throw new BusinessException(201, "提交标识不能为空");
        }
        if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(201, "提交标识过长");
        }
        return idempotencyKey;
    }

    private static void requireWellFormedFormValues(String formValues) {
        if (!ProcessStartSupport.isValidFormValues(formValues)) {
            throw new BusinessException(201, "表单内容格式无效");
        }
    }

    private static void requireRequiredFieldsPresent(String formProps, String formValues) {
        List<String> messages = ProcessStartSupport.missingRequiredMessages(formProps, formValues);
        if (!messages.isEmpty()) {
            throw new BusinessException(201, ValidationMessages.join(messages));
        }
    }

    /**
     * 处理动作（#288 起 id 双关）：taskId 命中加签任务行走加签路径（计数/批次完成/整条拒绝），
     * 否则走引擎任务路径（认领 + complete/rejectToEnd）。两条路径共用同一端口形状。
     */
    @Transactional
    public Approval.DecisionOutcome approve(SysUser user, ApprovalVo approvalVo) {
        requireStatusInDomain(approvalVo);
        Process process = requireApprovableProcess(user, approvalVo);
        ProcessAddSignTask signTask = resolveSignTask(approvalVo.getTaskId(), process.getId());
        if (signTask != null) {
            return approveSignTask(user, process, signTask, approvalVo.getStatus());
        }
        return approveEngineTask(user, process, approvalVo);
    }

    private Approval.DecisionOutcome approveEngineTask(SysUser user, Process process, ApprovalVo approvalVo) {
        requireCurrentApprover(user, process, approvalVo.getTaskId());
        try {
            // 处理动作本身认领（ADR 0069）：候选用户处理时先写 assignee，历史证据随之完整；
            // 并发后到者（认领冲突/乐观锁/任务已被处理掉）按「已被其他人处理」命名拒绝。
            approvalEngine.claim(approvalVo.getTaskId(), user.getUsername());
            if (approvalVo.getStatus() != null && approvalVo.getStatus() == 1) {
                approvalEngine.complete(approvalVo.getTaskId());
            } else {
                approvalEngine.rejectToEnd(approvalVo.getTaskId());
            }
        } catch (FlowableTaskAlreadyClaimedException
                | FlowableOptimisticLockingException
                | FlowableObjectNotFoundException e) {
            throw new BusinessException(201, "该审批已被其他人处理");
        }

        processRecordService.record(
                approvalVo.getProcessId(),
                ProcessApprovalSupport.ApprovalAction.decision(approvalVo.getStatus()),
                user);

        List<ApprovalEngine.CurrentTask> nextTasks =
                approvalEngine.currentTasks(process.getProcessInstanceId());
        ProcessApprovalSupport.applyOutcome(
                process,
                approvalVo.getStatus(),
                approverNames(nextTasks),
                !nextTasks.isEmpty());
        processMapper.updateById(process);

        return new Approval.DecisionOutcome(
                process.getId(),
                process.getStatus(),
                nextTasks.stream().map(ApprovalEngine.CurrentTask::taskId).toList());
    }

    /**
     * 加签任务的处理路径（#288，ADR 0068）：加签人对自己的未决加签任务通过或拒绝。
     * 通过=计数（全部通过才触发批次收口）；拒绝=对配对引擎任务 rejectToEnd，整条审批拒绝，
     * 剩余未决加签任务销毁，后加签原处理人已记账的通过保留。
     */
    private Approval.DecisionOutcome approveSignTask(
            SysUser user, Process process, ProcessAddSignTask signTask, Integer status) {
        if (!user.getId().equals(signTask.getAssigneeId())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        // 条件更新收敛并发：重复提交或批次已被对手收口的后来者拿到 0 行，命名拒绝。
        int updated = addSignTaskMapper.update(null, new LambdaUpdateWrapper<ProcessAddSignTask>()
                .eq(ProcessAddSignTask::getId, signTask.getId())
                .eq(ProcessAddSignTask::getStatus, AddSignStatus.PENDING.name())
                .set(ProcessAddSignTask::getStatus,
                        status != null && status == 1
                                ? AddSignStatus.APPROVED.name()
                                : AddSignStatus.REJECTED.name()));
        if (updated == 0) {
            throw new BusinessException(201, "该审批已被其他人处理");
        }
        processRecordService.record(process.getId(), ProcessApprovalSupport.ApprovalAction.decision(status), user);

        if (status != null && status == 1) {
            return settleBatchIfComplete(process, signTask);
        }

        // 任一加签人拒绝：整条审批拒绝（原处理人的通过记录保留，不删不改）。
        try {
            approvalEngine.rejectToEnd(signTask.getEngineTaskId());
        } catch (FlowableObjectNotFoundException e) {
            throw new BusinessException(201, "该审批已被其他人处理");
        }
        destroyPendingSignTasks(process.getId());
        ProcessApprovalSupport.applyOutcome(process, status, List.of(), false);
        processMapper.updateById(process);
        return new Approval.DecisionOutcome(process.getId(), process.getStatus(), List.of());
    }

    /**
     * 批次收口（#288）：本批次还有未决行则只计数返回；全部通过后——前加签解挂回原处理人
     * （引擎任务原样活动，等待文案回原处理人），后加签用已记账的通过完成当前节点进入下一节点。
     * 收口判定用锁定读（FOR UPDATE）：并发最后两人不再互相看不见对方的通过而双双搁置。
     */
    private Approval.DecisionOutcome settleBatchIfComplete(Process process, ProcessAddSignTask signTask) {
        List<ProcessAddSignTask> stillPending = addSignTaskMapper.selectList(
                new LambdaQueryWrapper<ProcessAddSignTask>()
                        .eq(ProcessAddSignTask::getEngineTaskId, signTask.getEngineTaskId())
                        .eq(ProcessAddSignTask::getBatchNo, signTask.getBatchNo())
                        .eq(ProcessAddSignTask::getStatus, AddSignStatus.PENDING.name())
                        .last("FOR UPDATE"));
        if (!stillPending.isEmpty()) {
            return new Approval.DecisionOutcome(process.getId(), process.getStatus(), List.of());
        }
        if (AddSignType.BEFORE.name().equals(signTask.getSignType())) {
            refreshWaitingDescription(process);
            processMapper.updateById(process);
            return new Approval.DecisionOutcome(
                    process.getId(), process.getStatus(), List.of(signTask.getEngineTaskId()));
        }
        try {
            approvalEngine.complete(signTask.getEngineTaskId());
        } catch (FlowableObjectNotFoundException e) {
            throw new BusinessException(201, "该审批已被其他人处理");
        }
        List<ApprovalEngine.CurrentTask> nextTasks =
                approvalEngine.currentTasks(process.getProcessInstanceId());
        ProcessApprovalSupport.applyOutcome(
                process, 1, approverNames(nextTasks), !nextTasks.isEmpty());
        processMapper.updateById(process);
        return new Approval.DecisionOutcome(
                process.getId(),
                process.getStatus(),
                nextTasks.stream().map(ApprovalEngine.CurrentTask::taskId).toList());
    }

    /** 加签任务的行级识别：taskId 是数字且命中本审批的加签任务才走加签路径，其余归引擎路径。 */
    private ProcessAddSignTask resolveSignTask(String taskId, Long processId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        long signTaskId;
        try {
            signTaskId = Long.parseLong(taskId.trim());
        } catch (NumberFormatException notAnEngineTaskId) {
            return null;
        }
        ProcessAddSignTask signTask = addSignTaskMapper.selectById(signTaskId);
        return signTask != null && processId.equals(signTask.getProcessId()) ? signTask : null;
    }

    /** 审批终态时未决加签任务的收场（拒绝/撤回共用）：全部销毁，不进任何人的待处理。 */
    private void destroyPendingSignTasks(Long processId) {
        addSignTaskMapper.update(null, new LambdaUpdateWrapper<ProcessAddSignTask>()
                .eq(ProcessAddSignTask::getProcessId, processId)
                .eq(ProcessAddSignTask::getStatus, AddSignStatus.PENDING.name())
                .set(ProcessAddSignTask::getStatus, AddSignStatus.DESTROYED.name()));
    }

    private static void requireStatusInDomain(ApprovalVo approvalVo) {
        Integer status = approvalVo == null ? null : approvalVo.getStatus();
        if (status == null || (status != 1 && status != -1)) {
            throw new BusinessException(201, "审批状态只能为 1（通过）或 -1（拒绝）");
        }
    }

    /**
     * 撤回（ADR 0068，#287）：仅申请人、仅审批中；结束运行时实例，状态落 -2 已撤回，
     * 写 WITHDRAW 账本。校验先于写：所有 201 的含义都是「确定未提交」。
     * 已处理行、附件与抄送一律不动（保留面完整），撤回不可恢复，重新提交是另一次审批。
     */
    @Transactional
    public Approval.WithdrawOutcome withdraw(SysUser user, Long processId) {
        Process process = requireApplicantActiveProcess(user, processId, "撤回审批", "撤回");
        try {
            approvalEngine.cancel(process.getProcessInstanceId(), "申请人撤回");
        } catch (FlowableObjectNotFoundException e) {
            // 校验与取消之间，并发对手（通过/拒绝）已经结束实例：本侧确定未提交。
            throw new BusinessException(201, "该审批已被其他人处理");
        }
        // 未决加签任务随终态销毁（#288）：加签等待面不越过撤回存活。
        destroyPendingSignTasks(processId);
        processRecordService.record(processId, ProcessApprovalSupport.ApprovalAction.withdraw(), user);
        ProcessApprovalSupport.applyWithdrawn(process);
        processMapper.updateById(process);
        return new Approval.WithdrawOutcome(processId, process.getStatus());
    }

    /**
     * 转办（ADR 0068，#288）：当前处理人把审批永久交给一个启用用户（不能本人/申请人）。
     * 写 TRANSFER 账本（target=目标）；原处理人成为转办经手人（只读，经账本推导）；
     * 不设转回与接受态；引擎侧经 ApprovalEngine.reassign 改派（转办人候选身份一并移除）。
     * 校验先于写：所有 201 的含义都是「确定未提交」。
     */
    @Transactional
    public Approval.TransferOutcome transfer(SysUser user, Long processId, TransferVo transferVo) {
        Process process = requireActiveHandlerProcess(user, processId, "转办");
        String engineTaskId = requireActionableEngineTaskId(user, process, "转办");
        SysUser target = requireActionTarget(
                user, process, transferVo == null ? null : transferVo.getTargetUserId(), "转办", "转办目标");
        try {
            approvalEngine.reassign(engineTaskId, user.getUsername(), target.getUsername());
        } catch (FlowableObjectNotFoundException e) {
            // 校验与改派之间，并发对手已经处理掉任务：本侧确定未提交。
            throw new BusinessException(201, "该审批已被其他人处理");
        }
        processRecordService.record(
                processId,
                ProcessApprovalSupport.ApprovalAction.transfer(target.getId(), target.getName()),
                user);
        refreshWaitingDescription(process);
        processMapper.updateById(process);
        return new Approval.TransferOutcome(processId, process.getStatus());
    }

    /**
     * 加签（ADR 0068，#288）：当前处理人指定 1..N 个加签人（不能本人/申请人，会签口径全部通过）。
     * 前加签：原任务保持活动但挂起（成员资格挂起标记），加签人全部通过后解挂回原处理人；
     * 后加签：原处理人的通过先记账（APPROVE 账本行）但引擎任务不完成，批次全部通过后才完成当前节点。
     * 加签不注入 BPMN、不改已发布产物；加签人不能再加签、同一批次进行中不能再加签（可动作引擎任务
     * 门槛统一拒绝）。
     */
    @Transactional
    public Approval.AddSignOutcome addSign(SysUser user, Long processId, AddSignVo addSignVo) {
        Process process = requireActiveHandlerProcess(user, processId, "加签");
        String engineTaskId = requireActionableEngineTaskId(user, process, "加签");
        AddSignType signType = requireAddSignType(addSignVo == null ? null : addSignVo.getSignType());
        List<SysUser> targets =
                requireAddSignTargets(user, process, addSignVo == null ? null : addSignVo.getAssigneeIds());
        int batchNo = nextBatchNo(engineTaskId);

        List<Long> signTaskIds = new ArrayList<>();
        for (SysUser target : targets) {
            ProcessAddSignTask signTask = new ProcessAddSignTask();
            signTask.setProcessId(processId);
            signTask.setEngineTaskId(engineTaskId);
            signTask.setBatchNo(batchNo);
            signTask.setSignType(signType.name());
            signTask.setAssigneeId(target.getId());
            signTask.setStatus(AddSignStatus.PENDING.name());
            addSignTaskMapper.insert(signTask);
            signTaskIds.add(signTask.getId());
        }

        // 后加签：原处理人的通过先记账（ADR 0068）——引擎任务不完成，批次全部通过后才用它收口。
        if (signType == AddSignType.AFTER) {
            processRecordService.record(processId, ProcessApprovalSupport.ApprovalAction.decision(1), user);
        }
        String targetNames = targets.stream().map(SysUser::getName).collect(Collectors.joining("、"));
        processRecordService.record(processId, ProcessApprovalSupport.ApprovalAction.addSign(targetNames), user);

        process.setDescription(ProcessApprovalSupport.addSignWaitingDescription(
                targets.stream().map(SysUser::getName).toList()));
        processMapper.updateById(process);
        return new Approval.AddSignOutcome(processId, batchNo, signTaskIds);
    }

    /**
     * 催办（ADR 0068，#287）：仅申请人、仅审批中；同一审批 24 小时内最多一次。
     * 对象是当前全部处理人（含未认领候选人），写 URGE 账本带对象姓名；
     * 不改状态、不发通知，白名单保证它不进任何人的已处理。
     */
    @Transactional
    public Approval.UrgeOutcome urge(SysUser user, Long processId) {
        Process process = requireApplicantActiveProcess(user, processId, "催办审批", "催办");
        requireUrgeNotInCooldown(processId);
        List<String> targetNames = approverNames(approvalEngine.currentTasks(process.getProcessInstanceId()));
        String joined = String.join("、", targetNames);
        processRecordService.record(
                processId,
                ProcessApprovalSupport.ApprovalAction.urge(joined.isEmpty() ? null : joined),
                user);
        return new Approval.UrgeOutcome(processId);
    }

    /** 申请人动作的共有前置（撤回/催办同族）：非申请人、非审批中都是 201 命名拒绝。 */
    private Process requireApplicantActiveProcess(
            SysUser user, Long processId, String applicantLabel, String activeLabel) {
        if (user == null || user.getId() == null || processId == null) {
            throw new BusinessException(201, "只有申请人可以" + applicantLabel);
        }
        Process process = processMapper.selectById(processId);
        if (process == null) {
            throw new BusinessException(201, "审批不存在");
        }
        if (!user.getId().equals(process.getUserId())) {
            throw new BusinessException(201, "只有申请人可以" + applicantLabel);
        }
        if (!Integer.valueOf(1).equals(process.getStatus())
                || !StringUtils.hasText(process.getProcessInstanceId())) {
            throw new BusinessException(201, "只有审批中的审批可以" + activeLabel);
        }
        return process;
    }

    /** 催办频控（ADR 0068）：查账本最近一次 URGE，24 小时内已有则命名拒绝。 */
    private void requireUrgeNotInCooldown(Long processId) {
        LambdaQueryWrapper<ProcessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessRecord::getProcessId, processId)
                .eq(ProcessRecord::getActionType,
                        ProcessApprovalSupport.ApprovalActionType.URGE.name())
                .orderByDesc(ProcessRecord::getCreateTime);
        List<ProcessRecord> urges = processRecordService.list(wrapper);
        if (urges.isEmpty()) {
            return;
        }
        Date latest = urges.get(0).getCreateTime();
        if (latest != null && latest.toInstant().isAfter(Instant.now().minus(URGE_COOLDOWN))) {
            throw new BusinessException(201, "24 小时内已催办过，请稍后再试");
        }
    }

    private Process requireApprovableProcess(SysUser user, ApprovalVo approvalVo) {
        if (user == null || !StringUtils.hasText(user.getUsername())
                || approvalVo == null || approvalVo.getProcessId() == null
                || !StringUtils.hasText(approvalVo.getTaskId())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = processMapper.selectById(approvalVo.getProcessId());
        if (process == null || !StringUtils.hasText(process.getProcessInstanceId())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        return process;
    }

    /** 处理动作的成员资格门槛（引擎与加签两条路径各自的 id 语义都由 canAct 判定）。 */
    private void requireCurrentApprover(SysUser user, Process process, String taskId) {
        if (!membershipResolver.resolve(user, process).canAct(taskId)) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
    }

    /**
     * 转办/加签的共有前置（#288）：审批存在且审批中，操作人是当前处理人。
     * 与 approve 的 403 族不同，这类办事动作沿用撤回/催办的 201 命名拒绝（校验先于写）。
     */
    private Process requireActiveHandlerProcess(SysUser user, Long processId, String actionLabel) {
        if (user == null || user.getId() == null || processId == null) {
            throw new BusinessException(201, "只有当前处理人可以" + actionLabel);
        }
        Process process = processMapper.selectById(processId);
        if (process == null) {
            throw new BusinessException(201, "审批不存在");
        }
        if (!Integer.valueOf(1).equals(process.getStatus())
                || !StringUtils.hasText(process.getProcessInstanceId())) {
            throw new BusinessException(201, "只有审批中的审批可以" + actionLabel);
        }
        ApprovalMembership membership = membershipResolver.resolve(user, process);
        if (!StringUtils.hasText(user.getUsername()) || membership.engineTaskIds().isEmpty()) {
            throw new BusinessException(201, "只有当前处理人可以" + actionLabel);
        }
        return process;
    }

    /**
     * 可动作的引擎任务 id（#288）：挂起中的任务（有未决加签批次压着）不能发起转办/加签——
     * 前加签挂起的原处理人不能动作，同一批次进行中不能再加签，加签人也够不到这个门槛
     * （他们只有加签任务 id，没有引擎任务）。并行流程多任务可动作时取解析序的第一个。
     */
    private String requireActionableEngineTaskId(SysUser user, Process process, String actionLabel) {
        ApprovalMembership membership = membershipResolver.resolve(user, process);
        for (String engineTaskId : membership.engineTaskIds()) {
            if (!membership.suspendedTaskIds().contains(engineTaskId)) {
                return engineTaskId;
            }
        }
        if (!membership.engineTaskIds().isEmpty()) {
            throw new BusinessException(201, "加签处理中，暂不能" + actionLabel);
        }
        throw new BusinessException(201, "只有当前处理人可以" + actionLabel);
    }

    /** 转办/加签目标的合法性（#288）：启用用户、不能本人、不能申请人；201 命名拒绝。 */
    private SysUser requireActionTarget(
            SysUser actor, Process process, Long targetUserId, String actionLabel, String targetLabel) {
        if (targetUserId == null) {
            throw new BusinessException(201, targetLabel + "不能为空");
        }
        if (targetUserId.equals(actor.getId())) {
            throw new BusinessException(201, "不能" + actionLabel + "给自己");
        }
        SysUser target = sysUserService.getById(targetUserId);
        if (target == null || !Integer.valueOf(1).equals(target.getStatus())) {
            throw new BusinessException(201, targetLabel + "不存在或已停用");
        }
        if (targetUserId.equals(process.getUserId())) {
            throw new BusinessException(201, "不能" + actionLabel + "给申请人");
        }
        return target;
    }

    private AddSignType requireAddSignType(Integer signType) {
        if (signType == null || (signType != 1 && signType != 2)) {
            throw new BusinessException(201, "加签类型只能为 1（前加签）或 2（后加签）");
        }
        return signType == 1 ? AddSignType.BEFORE : AddSignType.AFTER;
    }

    private List<SysUser> requireAddSignTargets(SysUser actor, Process process, List<Long> assigneeIds) {
        Set<Long> requested = assigneeIds == null ? Set.of() : new LinkedHashSet<>(assigneeIds);
        if (requested.isEmpty()) {
            throw new BusinessException(201, "加签人不能为空");
        }
        List<SysUser> targets = new ArrayList<>();
        for (Long assigneeId : requested) {
            targets.add(requireActionTarget(actor, process, assigneeId, "加签", "加签人"));
        }
        return targets;
    }

    /** 同一引擎任务内的批次序号：历史最大批次 +1（前加签回到原处理人后可再来一批）。 */
    private int nextBatchNo(String engineTaskId) {
        List<ProcessAddSignTask> existing = addSignTaskMapper.selectList(
                new LambdaQueryWrapper<ProcessAddSignTask>()
                        .eq(ProcessAddSignTask::getEngineTaskId, engineTaskId)
                        .select(ProcessAddSignTask::getBatchNo));
        return existing.stream()
                .map(ProcessAddSignTask::getBatchNo)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0) + 1;
    }

    /** 等待文案回写（等待文案由 description 唯一拥有，ADR 0043）：按当前任务的处理人名单重算。 */
    private void refreshWaitingDescription(Process process) {
        List<ApprovalEngine.CurrentTask> nextTasks =
                approvalEngine.currentTasks(process.getProcessInstanceId());
        process.setDescription(ProcessStartSupport.waitingDescription(approverNames(nextTasks)));
    }

    private List<String> approverNames(List<ApprovalEngine.CurrentTask> taskList) {
        List<String> nameList = new ArrayList<>();
        for (ApprovalEngine.CurrentTask task : taskList) {
            // 候选任务尚无 assignee：按候选用户集给等待名单（order 由解析器排序）。
            LinkedHashSet<String> usernames = new LinkedHashSet<>();
            if (StringUtils.hasText(task.assignee())) {
                usernames.add(task.assignee());
            }
            usernames.addAll(task.candidates());
            for (String username : usernames) {
                SysUser assignee = sysUserService.getUserByUserName(username);
                nameList.add(assignee != null && StringUtils.hasText(assignee.getName())
                        ? assignee.getName()
                        : username);
            }
        }
        return nameList;
    }
}
