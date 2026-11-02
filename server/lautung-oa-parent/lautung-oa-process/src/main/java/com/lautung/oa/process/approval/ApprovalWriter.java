package com.lautung.oa.process.approval;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.common.result.ResultCodeEnum;
import com.lautung.oa.common.validation.ValidationMessages;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.ProcessStartSupport;
import com.lautung.oa.process.attachment.AttachmentService;
import com.lautung.oa.process.cc.CarbonCopyWriter;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.membership.ApprovalMembershipResolver;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.process.service.OaProcessRecordService;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

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

    private final ApprovalTemplate approvalTemplate;
    private final ApprovalEngine approvalEngine;
    private final AttachmentService attachmentService;
    private final CarbonCopyWriter carbonCopyWriter;
    private final ApprovalMembershipResolver membershipResolver;
    private final OaProcessMapper processMapper;
    private final OaProcessRecordService processRecordService;
    private final SysUserService sysUserService;

    public ApprovalWriter(
            ApprovalTemplate approvalTemplate,
            ApprovalEngine approvalEngine,
            AttachmentService attachmentService,
            CarbonCopyWriter carbonCopyWriter,
            ApprovalMembershipResolver membershipResolver,
            OaProcessMapper processMapper,
            OaProcessRecordService processRecordService,
            SysUserService sysUserService) {
        this.approvalTemplate = approvalTemplate;
        this.approvalEngine = approvalEngine;
        this.attachmentService = attachmentService;
        this.carbonCopyWriter = carbonCopyWriter;
        this.membershipResolver = membershipResolver;
        this.processMapper = processMapper;
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

        processRecordService.record(process.getId(), 1, ProcessApprovalSupport.INITIATION_DESCRIPTION, user);

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

    @Transactional
    public Approval.DecisionOutcome approve(SysUser user, ApprovalVo approvalVo) {
        requireStatusInDomain(approvalVo);
        Process process = requireCurrentApprover(user, approvalVo);
        if (approvalVo.getStatus() != null && approvalVo.getStatus() == 1) {
            approvalEngine.complete(approvalVo.getTaskId());
        } else {
            approvalEngine.rejectToEnd(approvalVo.getTaskId());
        }

        processRecordService.record(
                approvalVo.getProcessId(),
                approvalVo.getStatus(),
                ProcessApprovalSupport.recordDescription(approvalVo.getStatus()),
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

    private static void requireStatusInDomain(ApprovalVo approvalVo) {
        Integer status = approvalVo == null ? null : approvalVo.getStatus();
        if (status == null || (status != 1 && status != -1)) {
            throw new BusinessException(201, "审批状态只能为 1（通过）或 -1（拒绝）");
        }
    }

    private Process requireCurrentApprover(SysUser user, ApprovalVo approvalVo) {
        if (user == null || !StringUtils.hasText(user.getUsername())
                || approvalVo == null || approvalVo.getProcessId() == null
                || !StringUtils.hasText(approvalVo.getTaskId())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = processMapper.selectById(approvalVo.getProcessId());
        if (process == null || !StringUtils.hasText(process.getProcessInstanceId())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        if (!membershipResolver.resolve(user, process).canAct(approvalVo.getTaskId())) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        return process;
    }

    private List<String> approverNames(List<ApprovalEngine.CurrentTask> taskList) {
        List<String> nameList = new ArrayList<>();
        for (ApprovalEngine.CurrentTask task : taskList) {
            String assigneeName = task.assignee();
            if (!StringUtils.hasText(assigneeName)) {
                continue;
            }
            SysUser assignee = sysUserService.getUserByUserName(assigneeName);
            nameList.add(assignee != null && StringUtils.hasText(assignee.getName())
                    ? assignee.getName()
                    : assigneeName);
        }
        return nameList;
    }
}
