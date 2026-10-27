package com.atguigu.process.approval;

import com.atguigu.auth.service.SysUserService;
import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.ProcessApprovalSupport;
import com.atguigu.process.ProcessStartSupport;
import com.atguigu.process.attachment.AttachmentService;
import com.atguigu.process.cc.CarbonCopyWriter;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.membership.ApprovalMembershipResolver;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 审批写入的唯一实现：账本、抄送、附件绑定、引擎命令的次序与事务边界都在这里。
 */
@Service
public class ApprovalWriter {

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
        ApprovalTemplate.PublishedApproval publication =
                approvalTemplate.resolveForInitiation(processFormVo.getProcessTemplateId());
        ProcessTemplate processTemplate = publication.template();

        Process process = new Process();
        BeanUtils.copyProperties(processFormVo, process);
        process.setStatus(1);
        process.setProcessCode(System.currentTimeMillis() + "");
        process.setUserId(user.getId());
        process.setFormValues(processFormVo.getFormValues());
        process.setTitle(ProcessStartSupport.title(user.getName(), processTemplate.getName()));
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

        processRecordService.record(process.getId(), 1, "发起申请", user);

        return new Approval.StartOutcome(
                process.getId(),
                processInstanceId,
                currentTasks.stream().map(ApprovalEngine.CurrentTask::taskId).toList());
    }

    @Transactional
    public Approval.DecisionOutcome approve(SysUser user, ApprovalVo approvalVo) {
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

    private Process requireCurrentApprover(SysUser user, ApprovalVo approvalVo) {
        if (user == null || !StringUtils.hasText(user.getUsername())
                || approvalVo == null || approvalVo.getProcessId() == null
                || !StringUtils.hasText(approvalVo.getTaskId())) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = processMapper.selectById(approvalVo.getProcessId());
        if (process == null || !StringUtils.hasText(process.getProcessInstanceId())) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        if (!membershipResolver.resolve(user, process).canAct(approvalVo.getTaskId())) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
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
