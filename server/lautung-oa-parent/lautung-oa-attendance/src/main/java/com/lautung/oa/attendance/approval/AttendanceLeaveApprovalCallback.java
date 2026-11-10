package com.lautung.oa.attendance.approval;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveLedgerMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.model.attendance.AttendanceLeave;
import com.lautung.oa.model.attendance.AttendanceLeaveLedger;
import com.lautung.oa.process.business.BusinessApprovalCallback;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 请假单审批终态回调（#282，ADR 0067/0073）：由 process 在审批动作同一事务内、
 * 账本更新之后调用。
 *
 * <p>APPROVED → 2 通过 + 同事务写扣减流水（entry_type=2，恒负）；REJECTED → -1；
 * WITHDRAWN → -2。单据状态机是唯一仲裁：同值终态 no-op 成功，异值抛错回滚，
 * 并发终态用 {@code UPDATE ... WHERE status=1} 的 CAS 收口（后到者重读判定）。
 */
@Component
public class AttendanceLeaveApprovalCallback implements BusinessApprovalCallback {

    private final AttendanceLeaveMapper leaveMapper;

    private final AttendanceLeaveLedgerMapper ledgerMapper;

    public AttendanceLeaveApprovalCallback(AttendanceLeaveMapper leaveMapper,
                                           AttendanceLeaveLedgerMapper ledgerMapper) {
        this.leaveMapper = leaveMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public String businessType() {
        return AttendanceBusinessTypes.LEAVE;
    }

    @Override
    public void onTerminal(Long businessId, BusinessApprovalOutcome outcome) {
        if (businessId == null || outcome == null) {
            throw new BusinessException(201, "请假单终态回写参数不完整");
        }
        AttendanceLeave leave = leaveMapper.selectById(businessId);
        if (leave == null) {
            throw new BusinessException(201, "请假单不存在");
        }
        int target = targetStatus(outcome);
        if (Integer.valueOf(target).equals(leave.getStatus())) {
            return;
        }
        if (!Integer.valueOf(AttendanceApplyStatus.SUBMITTED).equals(leave.getStatus())) {
            throw new BusinessException(201, "请假单终态冲突");
        }

        Date now = new Date();
        int updated = leaveMapper.update(null, new LambdaUpdateWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, businessId)
                .eq(AttendanceLeave::getStatus, AttendanceApplyStatus.SUBMITTED)
                .set(AttendanceLeave::getStatus, target)
                .set(AttendanceLeave::getFinishTime, now)
                .set(AttendanceLeave::getUpdateTime, now));
        if (updated == 0) {
            AttendanceLeave current = leaveMapper.selectById(businessId);
            if (current != null && Integer.valueOf(target).equals(current.getStatus())) {
                return;
            }
            throw new BusinessException(201, "请假单终态冲突");
        }

        if (outcome == BusinessApprovalOutcome.APPROVED) {
            writeDeduction(leave);
        }
    }

    private int targetStatus(BusinessApprovalOutcome outcome) {
        return switch (outcome) {
            case APPROVED -> AttendanceApplyStatus.APPROVED;
            case REJECTED -> AttendanceApplyStatus.REJECTED;
            case WITHDRAWN -> AttendanceApplyStatus.WITHDRAWN;
        };
    }

    /** 通过扣减：一行入口类型 2、恒负的流水，业务键指回请假单。 */
    private void writeDeduction(AttendanceLeave leave) {
        if (leave.getHours() == null) {
            throw new BusinessException(201, "请假单小时数缺失，不能写扣减流水");
        }
        AttendanceLeaveLedger ledger = new AttendanceLeaveLedger();
        ledger.setUserId(leave.getApplicantId());
        ledger.setLeaveTypeId(leave.getLeaveTypeId());
        ledger.setEntryType(AttendanceLeaveLedger.ENTRY_DEDUCT);
        ledger.setChangeHours(leave.getHours().abs().negate());
        ledger.setBusinessType(AttendanceBusinessTypes.LEAVE);
        ledger.setBusinessId(leave.getId());
        ledger.setRemark("请假审批通过扣减");
        ledgerMapper.insert(ledger);
    }
}
