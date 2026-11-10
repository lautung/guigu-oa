package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMemberMapper;
import com.lautung.oa.attendance.mapper.AttendanceHolidayMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.model.attendance.AttendanceShift;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假区间折算（#282，ADR 0073）：提交时按「当时班次 + 节假日日历」定稿天数与小时数。
 *
 * <p>工作日判定：(星期 ∈ 组工作日 且 该日非休假日) 或 该日是补班日；休息日与休假日
 * 都不计天数。日工时 = 下班 − 上班 − 午休（无午休不减），兜底 8 小时；小时数 =
 * 天数 × 日工时（两位小数）。区间没有工作日（全为休息日/休假日）按 201 拒绝。
 */
@Service
public class AttendanceWorkCalendar {

    private static final BigDecimal FALLBACK_DAILY_HOURS = new BigDecimal("8");

    private final AttendanceGroupMemberMapper memberMapper;

    private final AttendanceGroupMapper groupMapper;

    private final AttendanceShiftMapper shiftMapper;

    private final AttendanceHolidayMapper holidayMapper;

    public AttendanceWorkCalendar(AttendanceGroupMemberMapper memberMapper,
                                  AttendanceGroupMapper groupMapper,
                                  AttendanceShiftMapper shiftMapper,
                                  AttendanceHolidayMapper holidayMapper) {
        this.memberMapper = memberMapper;
        this.groupMapper = groupMapper;
        this.shiftMapper = shiftMapper;
        this.holidayMapper = holidayMapper;
    }

    /** 天数 + 小时数（提交时快照）；无组/无班次/区间非法都按 201 命名拒绝。 */
    public WorkdaySpan calculate(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(201, "请假日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
        AttendanceGroupMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<AttendanceGroupMember>()
                        .eq(AttendanceGroupMember::getUserId, userId));
        if (member == null) {
            throw new BusinessException(201, "未加入考勤组，不能提交请假");
        }
        AttendanceGroup group = groupMapper.selectById(member.getGroupId());
        AttendanceShift shift = group == null ? null : shiftMapper.selectById(group.getShiftId());
        if (shift == null) {
            throw new BusinessException(201, "考勤组未绑定有效班次");
        }

        Map<LocalDate, Integer> holidays = holidaysBetween(startDate, endDate);
        int days = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (isWorkday(group.getWorkdays(), holidays.get(date), date)) {
                days++;
            }
        }
        if (days == 0) {
            throw new BusinessException(201, "请假区间没有工作日");
        }
        BigDecimal dailyHours = dailyHours(shift);
        return new WorkdaySpan(days, dailyHours.multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP), dailyHours);
    }

    private boolean isWorkday(String workdays, Integer holidayDayType, LocalDate date) {
        if (holidayDayType != null
                && holidayDayType == AttendanceHoliday.DAY_TYPE_WORKDAY) {
            return true;
        }
        if (holidayDayType != null
                && holidayDayType == AttendanceHoliday.DAY_TYPE_HOLIDAY) {
            return false;
        }
        return containsWorkday(workdays, date.getDayOfWeek().getValue());
    }

    /** 组工作日多选（如 "1,2,3,4,5"）是否包含该星期；请假折算与日状态推导共用这一份公式。 */
    public static boolean containsWorkday(String workdays, int weekday) {
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

    /** 日工时（小时，两位小数）：下班 − 上班 − 午休；无午休不减，非正数兜底 8 小时。 */
    private BigDecimal dailyHours(AttendanceShift shift) {
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

    private Map<LocalDate, Integer> holidaysBetween(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Integer> holidays = new HashMap<>();
        List<AttendanceHoliday> rows = holidayMapper.selectList(
                new LambdaQueryWrapper<AttendanceHoliday>()
                        .ge(AttendanceHoliday::getHolidayDate, startDate)
                        .le(AttendanceHoliday::getHolidayDate, endDate));
        for (AttendanceHoliday row : rows) {
            holidays.put(row.getHolidayDate(), row.getDayType());
        }
        return holidays;
    }

    /** 提交时定稿的天数与小时数。 */
    public record WorkdaySpan(int days, BigDecimal hours, BigDecimal dailyHours) {
    }
}
