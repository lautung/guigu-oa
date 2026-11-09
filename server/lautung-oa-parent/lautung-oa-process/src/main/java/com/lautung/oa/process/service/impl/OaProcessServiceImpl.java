package com.lautung.oa.process.service.impl;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.common.result.ResultCodeEnum;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.approval.ApprovalWriter;
import com.lautung.oa.process.cc.CarbonCopyRecipient;
import com.lautung.oa.process.cc.CarbonCopyService;
import com.lautung.oa.process.mapper.OaProcessMapper;
import com.lautung.oa.process.membership.ApprovalMembership;
import com.lautung.oa.process.membership.ApprovalMembershipResolver;
import com.lautung.oa.process.read.ApprovalListReader;
import com.lautung.oa.process.service.OaProcessRecordService;
import com.lautung.oa.process.service.OaProcessService;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.vo.process.AddSignVo;
import com.lautung.oa.vo.process.ApprovalVo;
import com.lautung.oa.vo.process.ProcessFormVo;
import com.lautung.oa.vo.process.ProcessVo;
import com.lautung.oa.vo.process.TransferVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OaProcessServiceImpl extends ServiceImpl<OaProcessMapper, Process> implements OaProcessService {

    @Autowired
    private ApprovalTemplate processTemplateService;

    @Autowired
    private OaProcessRecordService processRecordService;

    @Autowired
    private ApprovalMembershipResolver membershipResolver;

    @Autowired
    private ApprovalWriter approvalWriter;

    @Autowired
    private ApprovalListReader approvalListReader;

    @Autowired
    private CarbonCopyService carbonCopyService;

    @Override
    public Approval.StartOutcome startUp(SysUser user, ProcessFormVo processFormVo) {
        try {
            return approvalWriter.startUp(user, processFormVo);
        } catch (DuplicateKeyException duplicate) {
            // 这一层没有事务：重读落在新事务里，才能看见唯一索引挡住的那次提交。
            Approval.StartOutcome started =
                    approvalWriter.startedOutcome(user, processFormVo.getIdempotencyKey());
            if (started == null) {
                throw duplicate;
            }
            return started;
        }
    }

    @Override
    public Map<String, Object> show(SysUser user, Long id) {
        ReadableApproval readable = requireReadableApproval(id, user);
        Process process = readable.process();
        LambdaQueryWrapper<ProcessRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessRecord::getProcessId, id);
        List<ProcessRecord> processRecordList = processRecordService.list(wrapper);
        ProcessTemplate processTemplate = processTemplateService.read(process.getProcessTemplateId());
        // isApprove 是「有可动作的任务面」（#288）：挂起中的原处理人与后加签已记账的原处理人为 false，
        // 加签任务 assignee 为 true。行标记（actionKind/挂起/催办）供详情头部呈现。
        ProcessVo viewerRow = approvalListReader.pendingRow(user, process);
        return ProcessApprovalSupport.showPayload(
                process, processRecordList, processTemplate,
                readable.membership().hasActionableTask(), viewerRow);
    }

    @Override
    public Process requireApprovalMember(Long processId, SysUser user) {
        return requireReadableApproval(processId, user).process();
    }

    private ReadableApproval requireReadableApproval(Long processId, SysUser user) {
        if (processId == null || user == null || user.getId() == null) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = baseMapper.selectById(processId);
        if (process == null) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        ApprovalMembership membership = membershipResolver.resolve(user, process);
        if (!membership.canRead()) {
            throw new BusinessException(ResultCodeEnum.FORBIDDEN);
        }
        return new ReadableApproval(process, membership);
    }

    private record ReadableApproval(Process process, ApprovalMembership membership) {
    }

    @Override
    public Approval.DecisionOutcome approve(SysUser user, ApprovalVo approvalVo) {
        return approvalWriter.approve(user, approvalVo);
    }

    @Override
    public Approval.WithdrawOutcome withdraw(SysUser user, Long processId) {
        return approvalWriter.withdraw(user, processId);
    }

    @Override
    public Approval.UrgeOutcome urge(SysUser user, Long processId) {
        return approvalWriter.urge(user, processId);
    }

    @Override
    public Approval.TransferOutcome transfer(SysUser user, Long processId, TransferVo transferVo) {
        return approvalWriter.transfer(user, processId, transferVo);
    }

    @Override
    public Approval.AddSignOutcome addSign(SysUser user, Long processId, AddSignVo addSignVo) {
        return approvalWriter.addSign(user, processId, addSignVo);
    }

    /**
     * 转办/加签候选目标（#288）：登录即可读、可见性由写前校验兜底（抄送 carbonCopyRecipients
     * 同约定）；投影复用 CarbonCopyRecipient，排除申请人，排除本人由客户端本地过滤。
     */
    @Override
    public List<CarbonCopyRecipient> actionTargets(Long processId) {
        if (processId == null) {
            throw new BusinessException(201, "审批不存在");
        }
        Process process = baseMapper.selectById(processId);
        if (process == null) {
            throw new BusinessException(201, "审批不存在");
        }
        return carbonCopyService.findRecipients(process.getUserId());
    }
}
