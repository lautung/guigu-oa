package com.lautung.oa.process.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.model.process.ProcessRecord;
import com.lautung.oa.process.ProcessApprovalSupport.ApprovalActionType;
import com.lautung.oa.process.service.OaProcessRecordService;
import org.springframework.stereotype.Service;

/**
 * {@link MembershipLedgerEvidence} 的账本实现：动作记录查 oa_process_record，动作类型
 * 白名单在此收口。加签任务事实（未决批次、加签人）由待处理 module 的
 * {@code PendingSignTaskEvidence} 供给，本类不再读 oa_process_add_sign_task。
 */
@Service
public class LedgerMembershipEvidence implements MembershipLedgerEvidence {

    private final OaProcessRecordService processRecordService;

    public LedgerMembershipEvidence(OaProcessRecordService processRecordService) {
        this.processRecordService = processRecordService;
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
}
