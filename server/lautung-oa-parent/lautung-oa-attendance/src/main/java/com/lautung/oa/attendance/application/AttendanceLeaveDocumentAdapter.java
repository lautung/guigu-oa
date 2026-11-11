package com.lautung.oa.attendance.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lautung.oa.attendance.calendar.AttendanceCalendar;
import com.lautung.oa.attendance.calendar.AttendanceCalendarView;
import com.lautung.oa.attendance.mapper.AttendanceLeaveLedgerMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveMapper;
import com.lautung.oa.attendance.mapper.AttendanceLeaveTypeMapper;
import com.lautung.oa.attendance.service.AttendanceLedgerService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceBusinessTypes;
import com.lautung.oa.model.attendance.AttendanceLeave;
import com.lautung.oa.model.attendance.AttendanceLeaveLedger;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.vo.attendance.AttendanceLeaveBalanceVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveVo;
import com.lautung.oa.vo.process.BusinessApprovalSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Date;

/**
 * 请假单适配器（#294，ADR 0075）：生命周期 module 的请假领域差异唯一落点。
 *
 * <p>提交前校验（类型/事由/日历定稿/区间重叠/余额）、提交快照（天数与小时数）、
 * 审批摘要文案、终态扣减流水（entry_type=2、恒负）与 VO 投影都在这里；草稿 CAS、
 * 提交序列、同键重放与终态仲裁在 {@link DocumentLifecycle}。
 */
@Component
public class AttendanceLeaveDocumentAdapter
        implements ApplyDocumentAdapter<AttendanceLeave, AttendanceLeaveDocumentAdapter.FinalizedSpan,
        AttendanceLeaveVo> {

    private final AttendanceLeaveMapper leaveMapper;

    private final AttendanceLeaveTypeMapper leaveTypeMapper;

    private final AttendanceLeaveLedgerMapper ledgerMapper;

    private final AttendanceLedgerService ledgerService;

    private final AttendanceCalendar calendar;

    public AttendanceLeaveDocumentAdapter(AttendanceLeaveMapper leaveMapper,
                                          AttendanceLeaveTypeMapper leaveTypeMapper,
                                          AttendanceLeaveLedgerMapper ledgerMapper,
                                          AttendanceLedgerService ledgerService,
                                          AttendanceCalendar calendar) {
        this.leaveMapper = leaveMapper;
        this.leaveTypeMapper = leaveTypeMapper;
        this.ledgerMapper = ledgerMapper;
        this.ledgerService = ledgerService;
        this.calendar = calendar;
    }

    @Override
    public String businessType() {
        return AttendanceBusinessTypes.LEAVE;
    }

    @Override
    public String label() {
        return "请假单";
    }

    @Override
    public DocumentSnapshot<AttendanceLeave> load(Long id) {
        AttendanceLeave leave = id == null ? null : leaveMapper.selectById(id);
        if (leave == null) {
            return null;
        }
        return new DocumentSnapshot<>(leave, leave.getId(), leave.getApplicantId(),
                AttendanceApplyStatus.fromCode(leave.getStatus()));
    }

    @Override
    public Long insertDraft(AttendanceLeave draft) {
        leaveMapper.insert(draft);
        return draft.getId();
    }

    @Override
    public int casUpdateDraft(Long applicantId, Long id, AttendanceLeave draft) {
        return leaveMapper.update(null, new LambdaUpdateWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, id)
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.submittableCodes())
                .set(AttendanceLeave::getLeaveTypeId, draft.getLeaveTypeId())
                .set(AttendanceLeave::getStartDate, draft.getStartDate())
                .set(AttendanceLeave::getEndDate, draft.getEndDate())
                .set(AttendanceLeave::getReason, draft.getReason())
                .set(AttendanceLeave::getUpdateTime, new Date()));
    }

    @Override
    public int casDeleteDraft(Long applicantId, Long id) {
        return leaveMapper.delete(new LambdaQueryWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, id)
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.submittableCodes()));
    }

    /**
     * 提交前定稿：类型 → 事由 → 日历定稿天数小时（未入组/无班次/区间无工作日 201）→
     * 区间重叠 → 余额（含在途）。
     */
    @Override
    public SubmitPlan<FinalizedSpan> prepareSubmit(DocumentSnapshot<AttendanceLeave> document) {
        AttendanceLeave leave = document.document();
        AttendanceLeaveType type = requireEnabledType(leave.getLeaveTypeId());
        if (ApplyTexts.trimToNull(leave.getReason()) == null) {
            throw new BusinessException(201, "请假事由不能为空");
        }
        FinalizedSpan span = finalizeDaysAndHours(
                document.applicantId(), leave.getStartDate(), leave.getEndDate());
        requireNoOverlap(document.applicantId(), document.id(), leave.getStartDate(), leave.getEndDate());
        if (Integer.valueOf(1).equals(type.getBalanceRequired())) {
            requireBalance(document.applicantId(), type, span.hours());
        }
        return new SubmitPlan<>(span, leaveSummary(type, leave, span));
    }

    @Override
    public int casMarkSubmitted(Long applicantId, Long id, FinalizedSpan span, Date submitTime) {
        return leaveMapper.update(null, new LambdaUpdateWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, id)
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.submittableCodes())
                .set(AttendanceLeave::getStatus, AttendanceApplyStatus.SUBMITTED.code())
                .set(AttendanceLeave::getDays, span.days())
                .set(AttendanceLeave::getHours, span.hours())
                .set(AttendanceLeave::getSubmitTime, submitTime)
                .set(AttendanceLeave::getFinishTime, null)
                .set(AttendanceLeave::getUpdateTime, submitTime));
    }

    @Override
    public int casMarkTerminal(Long id, AttendanceApplyStatus target, Date finishTime) {
        return leaveMapper.update(null, new LambdaUpdateWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getId, id)
                .eq(AttendanceLeave::getStatus, AttendanceApplyStatus.SUBMITTED.code())
                .set(AttendanceLeave::getStatus, target.code())
                .set(AttendanceLeave::getFinishTime, finishTime)
                .set(AttendanceLeave::getUpdateTime, finishTime));
    }

    /** 通过扣减：一行入口类型 2、恒负的流水，业务键指回请假单。 */
    @Override
    public void settleApproved(Long id) {
        AttendanceLeave leave = leaveMapper.selectById(id);
        if (leave == null) {
            throw new BusinessException(201, "请假单不存在");
        }
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

    @Override
    public AttendanceLeaveVo detail(Long applicantId, Long id) {
        AttendanceLeaveVo vo = leaveMapper.selectMyLeaveDetail(id, applicantId);
        if (vo == null) {
            throw new BusinessException(201, "请假单不存在");
        }
        return vo;
    }

    @Override
    public void bindApproval(AttendanceLeaveVo projection, BusinessApprovalSummary summary) {
        projection.setApproval(summary);
    }

    /**
     * 提交时定稿的天数与小时数：打开考勤日历视图取区间事实（休假日剔除、补班日照算），
     * 未入组/无班次/区间无工作日按现有 201 命名拒绝——日历只给可判定的空形状，
     * 拒绝时机与文案留在请假 adapter（#293，ADR 0074）。
     */
    private FinalizedSpan finalizeDaysAndHours(Long applicantId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(201, "请假日期不能为空");
        }
        requireDateOrder(startDate, endDate);
        AttendanceCalendarView view = calendar.open(applicantId, startDate, endDate);
        if (!view.hasMember()) {
            throw new BusinessException(201, "未加入考勤组，不能提交请假");
        }
        if (view.shift() == null) {
            throw new BusinessException(201, "考勤组未绑定有效班次");
        }
        int days = view.workdayCount();
        if (days == 0) {
            throw new BusinessException(201, "请假区间没有工作日");
        }
        BigDecimal hours = view.dailyHours().multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
        return new FinalizedSpan(days, hours);
    }

    /** 提交时定稿的天数与小时数。 */
    public record FinalizedSpan(int days, BigDecimal hours) {
    }

    private AttendanceLeaveType requireEnabledType(Long leaveTypeId) {
        AttendanceLeaveType type = leaveTypeId == null ? null : leaveTypeMapper.selectById(leaveTypeId);
        if (type == null) {
            throw new BusinessException(201, "假期类型不存在");
        }
        if (!Integer.valueOf(AttendanceLeaveType.STATUS_ENABLED).equals(type.getStatus())) {
            throw new BusinessException(201, "假期类型已停用");
        }
        return type;
    }

    private static void requireDateOrder(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
    }

    private void requireNoOverlap(Long applicantId, Long id, LocalDate startDate, LocalDate endDate) {
        Long overlaps = leaveMapper.selectCount(new LambdaQueryWrapper<AttendanceLeave>()
                .eq(AttendanceLeave::getApplicantId, applicantId)
                .in(AttendanceLeave::getStatus, AttendanceApplyStatus.SUBMITTED.code(),
                        AttendanceApplyStatus.APPROVED.code())
                .ne(AttendanceLeave::getId, id)
                .le(AttendanceLeave::getStartDate, endDate)
                .ge(AttendanceLeave::getEndDate, startDate));
        if (overlaps != null && overlaps > 0) {
            throw new BusinessException(201, "与本人已通过或在途的请假日期重叠");
        }
    }

    private void requireBalance(Long applicantId, AttendanceLeaveType type, BigDecimal hours) {
        BigDecimal available = BigDecimal.ZERO;
        for (AttendanceLeaveBalanceVo balance : ledgerService.balances(applicantId)) {
            if (type.getId().equals(balance.getLeaveTypeId())) {
                available = balance.getAvailableHours() == null
                        ? BigDecimal.ZERO : balance.getAvailableHours();
                break;
            }
        }
        if (available.compareTo(hours) < 0) {
            throw new BusinessException(201, "可用余额不足");
        }
    }

    private static String leaveSummary(AttendanceLeaveType type, AttendanceLeave leave,
                                       FinalizedSpan span) {
        return type.getName() + " " + span.days() + " 天（" + span.hours().toPlainString()
                + " 小时），" + leave.getStartDate() + " 至 " + leave.getEndDate();
    }
}
