package com.lautung.oa.attendance.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveLedgerMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.mapper.AttendanceOvertimeMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.model.attendance.AttendanceLeaveLedger;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import com.lautung.oa.process.business.BusinessApprovalCallback;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 加班单审批终态回调（#282，ADR 0067/0073）：APPROVED → 2 通过 + 同事务向
 * 加班入账目标（{@code overtime_credit=1} 的调休类型）写入账流水（entry_type=3，恒正）；
 * REJECTED → -1；WITHDRAWN → -2。同值 no-op、异值抛错回滚，CAS 与请假回调同构。
 *
 * <p>入账后不可逆（没有反审批动作，ADR 0073）；错发用负数发放行冲抵。
 */
@Component
public class AttendanceOvertimeApprovalCallback implements BusinessApprovalCallback {

    private final AttendanceOvertimeMapper overtimeMapper;

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceLeaveLedgerMapper ledgerMapper;

    public AttendanceOvertimeApprovalCallback(AttendanceOvertimeMapper overtimeMapper,
                                              AttendanceLeaveTypeMapper leaveTypeMapper,
                                              AttendanceLeaveLedgerMapper ledgerMapper) {
        this.overtimeMapper = overtimeMapper;
        this.leaveTypeMapper = leaveTypeMapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Override
    public String businessType() {
        return AttendanceBusinessTypes.OVERTIME;
    }

    @Override
    public void onTerminal(Long businessId, BusinessApprovalOutcome outcome) {
        if (businessId == null || outcome == null) {
            throw new BusinessException(201, "加班单终态回写参数不完整");
        }
        AttendanceOvertime overtime = overtimeMapper.selectById(businessId);
        if (overtime == null) {
            throw new BusinessException(201, "加班单不存在");
        }
        int target = targetStatus(outcome);
        if (Integer.valueOf(target).equals(overtime.getStatus())) {
            return;
        }
        if (!Integer.valueOf(AttendanceApplyStatus.SUBMITTED).equals(overtime.getStatus())) {
            throw new BusinessException(201, "加班单终态冲突");
        }

        Date now = new Date();
        int updated = overtimeMapper.update(null, new LambdaUpdateWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, businessId)
                .eq(AttendanceOvertime::getStatus, AttendanceApplyStatus.SUBMITTED)
                .set(AttendanceOvertime::getStatus, target)
                .set(AttendanceOvertime::getFinishTime, now)
                .set(AttendanceOvertime::getUpdateTime, now));
        if (updated == 0) {
            AttendanceOvertime current = overtimeMapper.selectById(businessId);
            if (current != null && Integer.valueOf(target).equals(current.getStatus())) {
                return;
            }
            throw new BusinessException(201, "加班单终态冲突");
        }

        if (outcome == BusinessApprovalOutcome.APPROVED) {
            writeCredit(overtime);
        }
    }

    private int targetStatus(BusinessApprovalOutcome outcome) {
        return switch (outcome) {
            case APPROVED -> AttendanceApplyStatus.APPROVED;
            case REJECTED -> AttendanceApplyStatus.REJECTED;
            case WITHDRAWN -> AttendanceApplyStatus.WITHDRAWN;
        };
    }

    /**
     * 加班入账：全局唯一的调休类型一行 entry_type=3、恒正流水。回调不按启用状态过滤——
     * 目标类型在审批途中被停用时，在途审批仍要能落账（否则「通过」永远回滚）；新提交
     * 的启用校验在 service 里。目标被彻底取消（overtime_credit 全为 0）才抛错回滚。
     */
    private void writeCredit(AttendanceOvertime overtime) {
        if (overtime.getHours() == null) {
            throw new BusinessException(201, "加班单小时数缺失，不能写入账流水");
        }
        List<AttendanceLeaveType> targets = leaveTypeMapper.selectList(
                new LambdaQueryWrapper<AttendanceLeaveType>()
                        .eq(AttendanceLeaveType::getOvertimeCredit, 1)
                        .orderByAsc(AttendanceLeaveType::getId));
        if (targets.isEmpty()) {
            throw new BusinessException(201, "未配置加班入账的调休类型");
        }
        AttendanceLeaveLedger ledger = new AttendanceLeaveLedger();
        ledger.setUserId(overtime.getApplicantId());
        ledger.setLeaveTypeId(targets.get(0).getId());
        ledger.setEntryType(AttendanceLeaveLedger.ENTRY_OVERTIME_CREDIT);
        ledger.setChangeHours(overtime.getHours());
        ledger.setBusinessType(AttendanceBusinessTypes.OVERTIME);
        ledger.setBusinessId(overtime.getId());
        ledger.setRemark("加班审批通过入账");
        ledgerMapper.insert(ledger);
    }
}
