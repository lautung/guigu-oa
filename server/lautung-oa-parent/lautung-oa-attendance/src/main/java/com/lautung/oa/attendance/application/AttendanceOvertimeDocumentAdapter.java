package com.lautung.oa.attendance.application;

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
import com.lautung.oa.vo.attendance.AttendanceOvertimeVo;
import com.lautung.oa.vo.process.BusinessApprovalSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

/**
 * 加班单适配器（#294，ADR 0075）：生命周期 module 的加班领域差异唯一落点。
 *
 * <p>提交前校验（事由/起止时刻/入账目标）、提交快照（服务端算出的小时数）、摘要文案、
 * 终态入账流水（entry_type=3、恒正）与 VO 投影都在这里；允许休息日加班，不校验考勤组、
 * 不与打卡记录交叉校验、不做重叠校验（ADR 0073）。
 */
@Component
public class AttendanceOvertimeDocumentAdapter
        implements ApplyDocumentAdapter<AttendanceOvertime, BigDecimal, AttendanceOvertimeVo> {

    private final AttendanceOvertimeMapper overtimeMapper;

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceLeaveLedgerMapper ledgerMapper;

    public AttendanceOvertimeDocumentAdapter(AttendanceOvertimeMapper overtimeMapper,
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
    public String label() {
        return "加班单";
    }

    @Override
    public DocumentSnapshot<AttendanceOvertime> load(Long id) {
        AttendanceOvertime overtime = id == null ? null : overtimeMapper.selectById(id);
        if (overtime == null) {
            return null;
        }
        return new DocumentSnapshot<>(overtime, overtime.getId(), overtime.getApplicantId(),
                AttendanceApplyStatus.fromCode(overtime.getStatus()));
    }

    @Override
    public Long insertDraft(AttendanceOvertime draft) {
        overtimeMapper.insert(draft);
        return draft.getId();
    }

    @Override
    public int casUpdateDraft(Long applicantId, Long id, AttendanceOvertime draft) {
        return overtimeMapper.update(null, new LambdaUpdateWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, id)
                .eq(AttendanceOvertime::getApplicantId, applicantId)
                .in(AttendanceOvertime::getStatus, AttendanceApplyStatus.submittableCodes())
                .set(AttendanceOvertime::getOvertimeDate, draft.getOvertimeDate())
                .set(AttendanceOvertime::getStartTime, draft.getStartTime())
                .set(AttendanceOvertime::getEndTime, draft.getEndTime())
                .set(AttendanceOvertime::getReason, draft.getReason())
                .set(AttendanceOvertime::getUpdateTime, new Date()));
    }

    @Override
    public int casDeleteDraft(Long applicantId, Long id) {
        return overtimeMapper.delete(new LambdaQueryWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, id)
                .eq(AttendanceOvertime::getApplicantId, applicantId)
                .in(AttendanceOvertime::getStatus, AttendanceApplyStatus.submittableCodes()));
    }

    /** 提交前定稿：事由 → 起止时刻 → 入账目标存在 → 服务端算小时数。 */
    @Override
    public SubmitPlan<BigDecimal> prepareSubmit(DocumentSnapshot<AttendanceOvertime> document) {
        AttendanceOvertime overtime = document.document();
        if (ApplyTexts.trimToNull(overtime.getReason()) == null) {
            throw new BusinessException(201, "加班事由不能为空");
        }
        requireTimeOrder(overtime.getStartTime(), overtime.getEndTime());
        requireOvertimeCreditType();
        BigDecimal hours = hoursOf(overtime.getStartTime(), overtime.getEndTime());
        return new SubmitPlan<>(hours, overtimeSummary(overtime, hours));
    }

    @Override
    public int casMarkSubmitted(Long applicantId, Long id, BigDecimal hours, Date submitTime) {
        return overtimeMapper.update(null, new LambdaUpdateWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, id)
                .eq(AttendanceOvertime::getApplicantId, applicantId)
                .in(AttendanceOvertime::getStatus, AttendanceApplyStatus.submittableCodes())
                .set(AttendanceOvertime::getStatus, AttendanceApplyStatus.SUBMITTED.code())
                .set(AttendanceOvertime::getHours, hours)
                .set(AttendanceOvertime::getSubmitTime, submitTime)
                .set(AttendanceOvertime::getFinishTime, null)
                .set(AttendanceOvertime::getUpdateTime, submitTime));
    }

    @Override
    public int casMarkTerminal(Long id, AttendanceApplyStatus target, Date finishTime) {
        return overtimeMapper.update(null, new LambdaUpdateWrapper<AttendanceOvertime>()
                .eq(AttendanceOvertime::getId, id)
                .eq(AttendanceOvertime::getStatus, AttendanceApplyStatus.SUBMITTED.code())
                .set(AttendanceOvertime::getStatus, target.code())
                .set(AttendanceOvertime::getFinishTime, finishTime)
                .set(AttendanceOvertime::getUpdateTime, finishTime));
    }

    /**
     * 加班入账：全局唯一的调休类型一行 entry_type=3、恒正流水。回调不按启用状态过滤——
     * 目标类型在审批途中被停用时，在途审批仍要能落账（否则「通过」永远回滚）；新提交
     * 的启用校验在 {@link #prepareSubmit}。目标被彻底取消（overtime_credit 全为 0）才抛错回滚。
     */
    @Override
    public void settleApproved(Long id) {
        AttendanceOvertime overtime = overtimeMapper.selectById(id);
        if (overtime == null) {
            throw new BusinessException(201, "加班单不存在");
        }
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

    @Override
    public AttendanceOvertimeVo detail(Long applicantId, Long id) {
        AttendanceOvertimeVo vo = overtimeMapper.selectMyOvertimeDetail(id, applicantId);
        if (vo == null) {
            throw new BusinessException(201, "加班单不存在");
        }
        return vo;
    }

    @Override
    public void bindApproval(AttendanceOvertimeVo projection, BusinessApprovalSummary summary) {
        projection.setApproval(summary);
    }

    /**
     * 加班入账目标（ADR 0073：仅调休开启，全局唯一）。提交时校验是为了避免
     * 「审批通过却写不了入账」——回调失败会把通过动作整体回滚。
     */
    private AttendanceLeaveType requireOvertimeCreditType() {
        List<AttendanceLeaveType> types = leaveTypeMapper.selectList(
                new LambdaQueryWrapper<AttendanceLeaveType>()
                        .eq(AttendanceLeaveType::getOvertimeCredit, 1)
                        .eq(AttendanceLeaveType::getStatus, AttendanceLeaveType.STATUS_ENABLED)
                        .orderByAsc(AttendanceLeaveType::getId));
        if (types.isEmpty()) {
            throw new BusinessException(201, "未配置加班入账的调休类型");
        }
        return types.get(0);
    }

    private static void requireTimeOrder(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessException(201, "加班起止时刻不能为空");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(201, "结束时刻必须晚于开始时刻");
        }
    }

    private static BigDecimal hoursOf(LocalTime startTime, LocalTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static String overtimeSummary(AttendanceOvertime overtime, BigDecimal hours) {
        return "加班 " + hours.toPlainString() + " 小时：" + overtime.getOvertimeDate()
                + " " + overtime.getStartTime() + "-" + overtime.getEndTime();
    }
}
