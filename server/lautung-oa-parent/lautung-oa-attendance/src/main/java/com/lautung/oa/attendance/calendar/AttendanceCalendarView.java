package com.lautung.oa.attendance.calendar;

import com.lautung.oa.model.attendance.AttendanceDayType;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.model.attendance.AttendanceShift;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

/**
 * 考勤日历视图（#293，ADR 0074）：{@link AttendanceCalendar#open} 打开后可反复问的
 * 只读日期事实，区间内的回答不回表。
 *
 * <p>日类型沿用 {@link AttendanceDayType} 编号：休假日覆盖补班日与组工作日，补班日
 * 覆盖组工作日，否则按组工作日多选；未加入考勤组时是 {@link AttendanceDayType#NONE}
 * 的可判定空形状。日工时 = 下班 − 上班 − 午休（无午休不减），非正数兜底 8 小时；
 * 无班次时返回 0（没有可判定的义务），拒绝文案由消费方给。
 */
public class AttendanceCalendarView {

    private static final BigDecimal FALLBACK_DAILY_HOURS = new BigDecimal("8");

    private final AttendanceGroupMember member;

    private final AttendanceGroup group;

    private final AttendanceShift shift;

    private final Map<LocalDate, Integer> holidays;

    private final LocalDate startDate;

    private final LocalDate endDate;

    AttendanceCalendarView(AttendanceGroupMember member, AttendanceGroup group,
                           AttendanceShift shift, Map<LocalDate, Integer> holidays,
                           LocalDate startDate, LocalDate endDate) {
        this.member = member;
        this.group = group;
        this.shift = shift;
        this.holidays = holidays;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** 成员关系行存在（一人最多一组；组行可以悬空，见 {@link #hasGroup()}）。 */
    public boolean hasMember() {
        return member != null;
    }

    /** 成员行与考勤组行都存在；组行悬空等同未入组（与日状态 hasGroup 同一口径）。 */
    public boolean hasGroup() {
        return member != null && group != null;
    }

    /** 考勤组行，未入组或组行悬空时为 null。 */
    public AttendanceGroup group() {
        return group;
    }

    /** 绑定的班次行，未绑定有效班次时为 null（可判定的空形状，拒绝留给消费方）。 */
    public AttendanceShift shift() {
        return shift;
    }

    /**
     * 该日日类型：未入组 {@link AttendanceDayType#NONE}；休假日
     * {@link AttendanceDayType#HOLIDAY}、补班日 {@link AttendanceDayType#MAKEUP}、
     * 否则按组工作日多选 {@link AttendanceDayType#WORKDAY}/{@link AttendanceDayType#REST}。
     * 节假日行只装载了打开区间内的日期，区间外按星期回落。
     */
    public int dayType(LocalDate date) {
        if (!hasGroup()) {
            return AttendanceDayType.NONE;
        }
        Integer holidayDayType = holidays.get(date);
        if (holidayDayType != null && holidayDayType == AttendanceHoliday.DAY_TYPE_HOLIDAY) {
            return AttendanceDayType.HOLIDAY;
        }
        if (holidayDayType != null && holidayDayType == AttendanceHoliday.DAY_TYPE_WORKDAY) {
            return AttendanceDayType.MAKEUP;
        }
        return containsWorkday(group.getWorkdays(), date.getDayOfWeek().getValue())
                ? AttendanceDayType.WORKDAY
                : AttendanceDayType.REST;
    }

    /** 该日是否可排义务（工作日或补班日）。 */
    public boolean isWorkday(LocalDate date) {
        return AttendanceDayType.isWorkday(dayType(date));
    }

    /** 组工作日多选（如 "1,2,3,4,5"）是否包含该星期；脏值当作不包含。 */
    private static boolean containsWorkday(String workdays, int weekday) {
        if (workdays == null || workdays.isEmpty()) {
            return false;
        }
        for (String part : workdays.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == weekday) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 配置行由 service 规范化；脏值当作不包含，不让一条坏配置拦住折算。
            }
        }
        return false;
    }

    /** 打开区间（含首尾）内的工作日数；未入组时为 0。 */
    public int workdayCount() {
        int count = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isWorkday(date)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 日工时（小时，两位小数）：下班 − 上班 − 午休；无午休不减，非正数兜底 8 小时。
     * 无班次时返回 0 的空形状。
     */
    public BigDecimal dailyHours() {
        if (shift == null) {
            return BigDecimal.ZERO;
        }
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return FALLBACK_DAILY_HOURS;
        }
        long minutes = Duration.between(shift.getStartTime(), shift.getEndTime()).toMinutes();
        if (shift.getLunchStart() != null && shift.getLunchEnd() != null
                && shift.getLunchEnd().isAfter(shift.getLunchStart())
                && !shift.getLunchStart().isBefore(shift.getStartTime())
                && !shift.getLunchEnd().isAfter(shift.getEndTime())) {
            minutes -= Duration.between(shift.getLunchStart(), shift.getLunchEnd()).toMinutes();
        }
        if (minutes <= 0) {
            return FALLBACK_DAILY_HOURS;
        }
        return BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
