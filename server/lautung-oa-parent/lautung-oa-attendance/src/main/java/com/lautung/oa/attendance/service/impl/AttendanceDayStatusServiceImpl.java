package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMemberMapper;
import com.lautung.oa.attendance.mapper.AttendanceHolidayMapper;
import com.lautung.oa.attendance.mapper.AttendancePunchMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.attendance.service.AttendanceClock;
import com.lautung.oa.attendance.service.AttendanceDayStatusService;
import com.lautung.oa.attendance.service.AttendanceWorkCalendar;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceDayStatus;
import com.lautung.oa.model.attendance.AttendanceDayType;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.attendance.AttendanceShift;
import com.lautung.oa.vo.attendance.AttendanceDayStatusVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveSpanVo;
import com.lautung.oa.vo.attendance.AttendanceMonthVo;
import com.lautung.oa.vo.attendance.AttendanceShiftVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 逐日推导实现（#281，ADR 0073）：所有状态都从事件流水/请假行读时算出。
 *
 * <p>槽位有效时刻：有补卡行取<b>最新一条补卡行</b>（id 最大），否则上班取最早网页行、
 * 下班取最晚网页行；补卡 append-only，原网页行保留。
 *
 * <p>缺卡口径：当日为工作日且已过下班时刻（{@code now >= shift.endTime}）仍缺槽位
 * 才算缺卡；未到下班时刻且缺槽位只置 {@code pending=true}（「未打卡」），不落九枚举。
 * 未来日期不落状态，避免把还没到的日子记成缺卡。补班日是工作日：过了下班时刻仍缺
 * 槽位照常落缺卡；只有未到下班时刻且无任何打卡时保留「补班日」日历提示。
 *
 * <p>时钟从容器注入（{@link AttendanceClock}），测试可换固定/可推进实现，
 * 推导不依赖真实时钟。
 */
@Service
public class AttendanceDayStatusServiceImpl implements AttendanceDayStatusService {

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private final AttendanceGroupMemberMapper memberMapper;

    private final AttendanceGroupMapper groupMapper;

    private final AttendanceShiftMapper shiftMapper;

    private final AttendanceHolidayMapper holidayMapper;

    private final AttendancePunchMapper punchMapper;

    private final AttendanceClock clock;

    @Autowired
    public AttendanceDayStatusServiceImpl(AttendanceGroupMemberMapper memberMapper,
                                          AttendanceGroupMapper groupMapper,
                                          AttendanceShiftMapper shiftMapper,
                                          AttendanceHolidayMapper holidayMapper,
                                          AttendancePunchMapper punchMapper,
                                          AttendanceClock clock) {
        this.memberMapper = memberMapper;
        this.groupMapper = groupMapper;
        this.shiftMapper = shiftMapper;
        this.holidayMapper = holidayMapper;
        this.punchMapper = punchMapper;
        this.clock = clock;
    }

    @Override
    public AttendanceDayStatusVo day(Long userId, LocalDate date) {
        List<AttendanceDayStatusVo> days = days(userId, date, date);
        return days.get(0);
    }

    @Override
    public List<AttendanceDayStatusVo> days(Long userId, LocalDate beginDate, LocalDate endDate) {
        if (userId == null || beginDate == null || endDate == null) {
            throw new BusinessException(201, "考勤推导参数不完整");
        }
        if (beginDate.isAfter(endDate)) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
        LocalDateTime now = now();
        LocalDate today = now.toLocalDate();

        AttendanceGroupMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<AttendanceGroupMember>()
                        .eq(AttendanceGroupMember::getUserId, userId));
        AttendanceGroup group = member == null ? null : groupMapper.selectById(member.getGroupId());
        AttendanceShift shift = group == null ? null : shiftMapper.selectById(group.getShiftId());

        Map<LocalDate, AttendanceHoliday> holidays = holidaysBetween(beginDate, endDate);
        Map<LocalDate, List<AttendancePunch>> punches = punchesBetween(userId, beginDate, endDate);
        List<AttendanceLeaveSpanVo> leaves = punchMapper.selectApprovedLeaves(userId, beginDate, endDate);

        List<AttendanceDayStatusVo> result = new ArrayList<>();
        for (LocalDate date = beginDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            result.add(derive(date, today, now, group, shift, holidays.get(date),
                    punches.getOrDefault(date, List.of()), leaves));
        }
        return result;
    }

    @Override
    public AttendanceMonthVo month(Long userId, String month) {
        YearMonth yearMonth = resolveMonth(month);
        AttendanceMonthVo vo = new AttendanceMonthVo();
        vo.setMonth(yearMonth.toString());
        vo.setDays(days(userId, yearMonth.atDay(1), yearMonth.atEndOfMonth()));
        return vo;
    }

    @Override
    public YearMonth resolveMonth(String month) {
        if (month == null || month.isBlank()) {
            return currentMonth();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FORMAT);
        } catch (DateTimeParseException ignored) {
            throw new BusinessException(201, "月份格式不正确");
        }
    }

    @Override
    public YearMonth currentMonth() {
        return YearMonth.from(now());
    }

    private AttendanceDayStatusVo derive(LocalDate date, LocalDate today,
                                         LocalDateTime now, AttendanceGroup group,
                                         AttendanceShift shift,
                                         AttendanceHoliday holiday,
                                         List<AttendancePunch> punches,
                                         List<AttendanceLeaveSpanVo> leaves) {
        AttendanceDayStatusVo vo = new AttendanceDayStatusVo();
        vo.setDate(date);
        vo.setWeekday(date.getDayOfWeek().getValue());
        vo.setPending(false);
        vo.setLate(false);
        vo.setEarly(false);
        if (group == null) {
            vo.setHasGroup(false);
            vo.setWorkday(false);
            vo.setDayType(AttendanceDayType.NONE);
            return vo;
        }
        vo.setHasGroup(true);
        vo.setGroupId(group.getId());
        vo.setGroupName(group.getName());
        if (shift != null) {
            vo.setShift(AttendanceShiftVo.of(shift));
        }
        int dayType = dayType(group, holiday, date);
        vo.setDayType(dayType);
        boolean workday = AttendanceDayType.isWorkday(dayType);
        vo.setWorkday(workday);

        LocalTime clockIn = effectiveTime(punches, AttendancePunch.SLOT_IN);
        LocalTime clockOut = effectiveTime(punches, AttendancePunch.SLOT_OUT);
        vo.setClockInTime(clockIn);
        vo.setClockOutTime(clockOut);

        if (dayType == AttendanceDayType.REST) {
            vo.setStatus(AttendanceDayStatus.REST);
            return vo;
        }
        if (dayType == AttendanceDayType.HOLIDAY) {
            vo.setStatus(AttendanceDayStatus.HOLIDAY);
            return vo;
        }
        if (!workday || shift == null) {
            // 未分组/班次缺失没有可判定的义务，不臆造状态。
            return vo;
        }

        AttendanceLeaveSpanVo leave = leaveOn(leaves, date);
        if (leave != null) {
            vo.setStatus(AttendanceDayStatus.LEAVE);
            vo.setLeaveTypeName(leave.getLeaveTypeName());
            return vo;
        }

        boolean future = date.isAfter(today);
        if (future) {
            if (dayType == AttendanceDayType.MAKEUP && clockIn == null && clockOut == null) {
                vo.setStatus(AttendanceDayStatus.MAKEUP);
            }
            return vo;
        }

        // 迟到/早退次数按卡计：缺卡日只要该卡存在且超线仍计一次（ADR 0073），
        // 因此先于缺卡分支标记；请假/非工作日/未来日期不产生迟到早退。
        markLateAndEarly(vo, shift, clockIn, clockOut);

        boolean pastEnd = date.isBefore(today)
                || !now.toLocalTime().isBefore(shift.getEndTime());
        if (!pastEnd) {
            if (clockIn != null && clockOut != null) {
                vo.setStatus(attendanceStatus(shift, clockIn, clockOut));
            } else {
                // 补班日也是工作日：未到下班时刻缺槽位同样只是「未打卡」（#281 AC）。
                vo.setPending(true);
            }
            return vo;
        }

        if (clockIn == null || clockOut == null) {
            vo.setStatus(AttendanceDayStatus.MISSING);
            return vo;
        }
        vo.setStatus(attendanceStatus(shift, clockIn, clockOut));
        return vo;
    }

    /** 迟到 = 上班卡晚于上班时刻 + 宽限；早退 = 下班卡早于下班时刻 − 宽限。 */
    private int attendanceStatus(AttendanceShift shift, LocalTime clockIn, LocalTime clockOut) {
        boolean late = isLate(shift, clockIn);
        boolean early = isEarly(shift, clockOut);
        if (late && early) {
            return AttendanceDayStatus.LATE_AND_EARLY;
        }
        if (late) {
            return AttendanceDayStatus.LATE;
        }
        if (early) {
            return AttendanceDayStatus.EARLY_LEAVE;
        }
        return AttendanceDayStatus.NORMAL;
    }

    /** 按卡标记迟到/早退；缺卡日仍按存在的卡计，月统计直接消费这两个布尔。 */
    private void markLateAndEarly(AttendanceDayStatusVo vo, AttendanceShift shift,
                                  LocalTime clockIn, LocalTime clockOut) {
        vo.setLate(isLate(shift, clockIn));
        vo.setEarly(isEarly(shift, clockOut));
    }

    private boolean isLate(AttendanceShift shift, LocalTime clockIn) {
        if (clockIn == null || shift.getStartTime() == null) {
            return false;
        }
        int grace = shift.getGraceMinutes() == null ? 0 : shift.getGraceMinutes();
        return clockIn.isAfter(shift.getStartTime().plusMinutes(grace));
    }

    private boolean isEarly(AttendanceShift shift, LocalTime clockOut) {
        if (clockOut == null || shift.getEndTime() == null) {
            return false;
        }
        int grace = shift.getGraceMinutes() == null ? 0 : shift.getGraceMinutes();
        return clockOut.isBefore(shift.getEndTime().minusMinutes(grace));
    }

    private AttendanceLeaveSpanVo leaveOn(List<AttendanceLeaveSpanVo> leaves, LocalDate date) {
        for (AttendanceLeaveSpanVo leave : leaves) {
            if (leave.getStartDate() != null && leave.getEndDate() != null
                    && !date.isBefore(leave.getStartDate()) && !date.isAfter(leave.getEndDate())) {
                return leave;
            }
        }
        return null;
    }

    /**
     * 槽位有效时刻（ADR 0073）：有补卡行取最新一条补卡行（id 最大），
     * 没有补卡行时上班取最早网页行、下班取最晚网页行。
     */
    private LocalTime effectiveTime(List<AttendancePunch> punches, int slot) {
        LocalTime repairTime = null;
        long repairId = -1L;
        LocalTime webTime = null;
        for (AttendancePunch punch : punches) {
            if (punch.getSlot() == null || punch.getSlot() != slot || punch.getPunchTime() == null) {
                continue;
            }
            LocalTime time = punch.getPunchTime().toLocalTime();
            if (punch.getSource() != null && punch.getSource() == AttendancePunch.SOURCE_REPAIR) {
                long id = punch.getId() == null ? Long.MIN_VALUE : punch.getId();
                if (id > repairId) {
                    repairId = id;
                    repairTime = time;
                }
            } else if (webTime == null
                    || (slot == AttendancePunch.SLOT_IN ? time.isBefore(webTime) : time.isAfter(webTime))) {
                webTime = time;
            }
        }
        return repairTime != null ? repairTime : webTime;
    }

    private Map<LocalDate, AttendanceHoliday> holidaysBetween(LocalDate beginDate, LocalDate endDate) {
        Map<LocalDate, AttendanceHoliday> holidays = new HashMap<>();
        List<AttendanceHoliday> rows = holidayMapper.selectList(
                new LambdaQueryWrapper<AttendanceHoliday>()
                        .ge(AttendanceHoliday::getHolidayDate, beginDate)
                        .le(AttendanceHoliday::getHolidayDate, endDate));
        for (AttendanceHoliday row : rows) {
            holidays.put(row.getHolidayDate(), row);
        }
        return holidays;
    }

    private Map<LocalDate, List<AttendancePunch>> punchesBetween(Long userId,
                                                                 LocalDate beginDate,
                                                                 LocalDate endDate) {
        Map<LocalDate, List<AttendancePunch>> punches = new HashMap<>();
        List<AttendancePunch> rows = punchMapper.selectList(new LambdaQueryWrapper<AttendancePunch>()
                .eq(AttendancePunch::getUserId, userId)
                .ge(AttendancePunch::getPunchDate, beginDate)
                .le(AttendancePunch::getPunchDate, endDate)
                .orderByAsc(AttendancePunch::getPunchDate)
                .orderByAsc(AttendancePunch::getPunchTime)
                .orderByAsc(AttendancePunch::getId));
        for (AttendancePunch row : rows) {
            punches.computeIfAbsent(row.getPunchDate(), key -> new ArrayList<>()).add(row);
        }
        return punches;
    }

    private int dayType(AttendanceGroup group, AttendanceHoliday holiday, LocalDate date) {
        if (holiday != null && holiday.getDayType() != null) {
            if (holiday.getDayType() == AttendanceHoliday.DAY_TYPE_HOLIDAY) {
                return AttendanceDayType.HOLIDAY;
            }
            if (holiday.getDayType() == AttendanceHoliday.DAY_TYPE_WORKDAY) {
                return AttendanceDayType.MAKEUP;
            }
        }
        return AttendanceWorkCalendar.containsWorkday(group.getWorkdays(), date.getDayOfWeek().getValue())
                ? AttendanceDayType.WORKDAY
                : AttendanceDayType.REST;
    }

    private LocalDateTime now() {
        return clock.now();
    }
}
