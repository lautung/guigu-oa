package com.lautung.oa.process.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.model.process.ProcessAddSignTask;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.process.ProcessApprovalSupport;
import com.lautung.oa.process.ProcessApprovalSupport.ApprovalActionType;
import com.lautung.oa.process.mapper.OaProcessAddSignTaskMapper;
import com.lautung.oa.process.service.OaProcessRecordService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link MembershipLedgerEvidence} 的账本实现：动作记录查 oa_process_record，
 * 未决加签任务查 oa_process_add_sign_task（V137）。软删与动作类型白名单在此收口。
 */
@Service
public class LedgerMembershipEvidence implements MembershipLedgerEvidence {

    private final OaProcessRecordService processRecordService;
    private final OaProcessAddSignTaskMapper addSignTaskMapper;

    public LedgerMembershipEvidence(
            OaProcessRecordService processRecordService,
            OaProcessAddSignTaskMapper addSignTaskMapper) {
        this.processRecordService = processRecordService;
        this.addSignTaskMapper = addSignTaskMapper;
    }

    @Override
    public boolean hasDecisionBy(Long processId, Long userId) {
        if (processId == null || userId == null) {
            return false;
        }
        return processRecordService.count(new LambdaQueryWrapper<ProcessRecord>()
                .eq(ProcessRecord::getProcessId, processId)
                .eq(ProcessRecord::getOperateUserId, userId)
                .in(ProcessRecord::getActionType,
                        ApprovalActionType.APPROVE.name(), ApprovalActionType.REJECT.name())) > 0;
    }

    @Override
    public boolean hasTransferBy(Long processId, Long userId) {
        if (processId == null || userId == null) {
            return false;
        }
        return processRecordService.count(new LambdaQueryWrapper<ProcessRecord>()
                .eq(ProcessRecord::getProcessId, processId)
                .eq(ProcessRecord::getOperateUserId, userId)
                .eq(ProcessRecord::getActionType, ApprovalActionType.TRANSFER.name())) > 0;
    }

    @Override
    public boolean hasAddSignBy(Long processId, Long userId) {
        if (processId == null || userId == null) {
            return false;
        }
        return processRecordService.count(new LambdaQueryWrapper<ProcessRecord>()
                .eq(ProcessRecord::getProcessId, processId)
                .eq(ProcessRecord::getOperateUserId, userId)
                .eq(ProcessRecord::getActionType, ApprovalActionType.ADD_SIGN.name())) > 0;
    }

    @Override
    public List<SignTaskSnapshot> pendingSignTasks(Long processId) {
        if (processId == null) {
            return List.of();
        }
        List<ProcessAddSignTask> tasks = addSignTaskMapper.selectList(new LambdaQueryWrapper<ProcessAddSignTask>()
                .eq(ProcessAddSignTask::getProcessId, processId)
                .eq(ProcessAddSignTask::getStatus, ProcessApprovalSupport.AddSignStatus.PENDING.name()));
        return tasks.stream()
                .filter(task -> StringUtils.hasText(task.getEngineTaskId()))
                .map(task -> new SignTaskSnapshot(
                        String.valueOf(task.getId()),
                        task.getEngineTaskId(),
                        task.getSignType(),
                        task.getAssigneeId()))
                .toList();
    }
}
