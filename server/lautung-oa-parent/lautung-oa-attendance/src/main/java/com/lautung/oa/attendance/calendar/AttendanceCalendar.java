package com.lautung.oa.attendance.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMemberMapper;
import com.lautung.oa.attendance.mapper.AttendanceHolidayMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.model.attendance.AttendanceShift;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 考勤日历（#293，ADR 0074）：按用户与日期区间打开一个日历视图，工作日优先级与
 * 日工时公式的唯一 owner。打开时一次装载成员、考勤组、班次与区间节假日，视图可
 * 反复问日类型、区间工作日数与日工时，不逐日回表。
 *
 * <p>事实与拒绝分离：日历只回答日期事实，不回答「能不能提交请假」，也不抛
 * 「未加入考勤组/无有效班次」的 201。未入组、无班次返回可判定的空形状，拒绝
 * 文案与时机留在消费方——请假定稿按现有 201 命名拒绝，日状态保持
 * {@code hasGroup=false} 的未落状态 VO。
 *
 * <p>工作日公式（ADR 0073）：补班日覆盖休息日与组工作日，休假日覆盖组工作日，
 * 否则按组工作日多选；日工时 = 下班 − 上班 − 午休（无午休不减），非正数兜底
 * 8 小时。请假提交与逐日推导（月度统计经日状态）都从这里取同一份口径。
 */
@Service
public class AttendanceCalendar {

    private final AttendanceGroupMemberMapper memberMapper;

    private final AttendanceGroupMapper groupMapper;

    private final AttendanceShiftMapper shiftMapper;

    private final AttendanceHolidayMapper holidayMapper;

    public AttendanceCalendar(AttendanceGroupMemberMapper memberMapper,
                              AttendanceGroupMapper groupMapper,
                              AttendanceShiftMapper shiftMapper,
                              AttendanceHolidayMapper holidayMapper) {
        this.memberMapper = memberMapper;
        this.groupMapper = groupMapper;
        this.shiftMapper = shiftMapper;
        this.holidayMapper = holidayMapper;
    }

    /**
     * 为 {@code [startDate, endDate]}（含首尾）打开日历视图：成员、考勤组、班次与
     * 区间节假日一次装载，之后视图上的查询都不再回表。区间由调用方保证非空且
     * 首晚于尾不成立（事实与拒绝分离，区间非法的 201 是写路径消费方的事）。
     */
    public AttendanceCalendarView open(Long userId, LocalDate startDate, LocalDate endDate) {
        AttendanceGroupMember member = memberMapper.selectOne(
                new LambdaQueryWrapper<AttendanceGroupMember>()
                        .eq(AttendanceGroupMember::getUserId, userId));
        AttendanceGroup group = member == null ? null : groupMapper.selectById(member.getGroupId());
        AttendanceShift shift = group == null ? null : shiftMapper.selectById(group.getShiftId());
        Map<LocalDate, Integer> holidays = holidaysBetween(startDate, endDate);
        return new AttendanceCalendarView(member, group, shift, holidays, startDate, endDate);
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
}
