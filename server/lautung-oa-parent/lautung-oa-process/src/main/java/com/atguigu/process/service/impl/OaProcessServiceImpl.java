package com.atguigu.process.service.impl;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.common.result.ResultCodeEnum;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessRecord;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.ProcessApprovalSupport;
import com.atguigu.process.approval.ApprovalWriter;
import com.atguigu.process.mapper.OaProcessMapper;
import com.atguigu.process.membership.ApprovalMembership;
import com.atguigu.process.membership.ApprovalMembershipResolver;
import com.atguigu.process.service.OaProcessRecordService;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.vo.process.ApprovalVo;
import com.atguigu.vo.process.ProcessFormVo;
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
        return ProcessApprovalSupport.showPayload(
                process, processRecordList, processTemplate, readable.membership().isCurrentApprover());
    }

    @Override
    public Process requireApprovalMember(Long processId, SysUser user) {
        return requireReadableApproval(processId, user).process();
    }

    private ReadableApproval requireReadableApproval(Long processId, SysUser user) {
        if (processId == null || user == null || user.getId() == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        Process process = baseMapper.selectById(processId);
        if (process == null) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        ApprovalMembership membership = membershipResolver.resolve(user, process);
        if (!membership.canRead()) {
            throw new GuiguException(ResultCodeEnum.FORBIDDEN);
        }
        return new ReadableApproval(process, membership);
    }

    private record ReadableApproval(Process process, ApprovalMembership membership) {
    }

    @Override
    public Approval.DecisionOutcome approve(SysUser user, ApprovalVo approvalVo) {
        return approvalWriter.approve(user, approvalVo);
    }
}
