package com.lautung.oa.attendance.calendar;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMemberMapper;
import com.lautung.oa.attendance.mapper.AttendanceHolidayMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.model.attendance.AttendanceDayType;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.model.attendance.AttendanceShift;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考勤日历 module 单测（#293，ADR 0074）：工作日优先级（休假日覆盖 &gt; 补班日覆盖 &gt;
 * 组工作日）、组工作日多选解析、未入组/无班次的可判定空形状、跨月区间装载与日工时
 * 公式（下班 − 上班 − 午休，非正数兜底 8 小时）在这里唯一断言。mapper 用 Mockito
 * mock，不起 Spring 上下文。
 */
class AttendanceCalendarTest {

    private static final long USER_ID = 29301L;
    private static final long GROUP_ID = 293L;
    private static final long SHIFT_ID = 2930L;

    /** 2026-03-02 周一 至 2026-03-08 周日，与考勤合同测试同一固定周。 */
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 3);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 3, 4);
    private static final LocalDate THURSDAY = LocalDate.of(2026, 3, 5);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 3, 6);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 3, 7);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 3, 8);

    private final AttendanceGroupMemberMapper memberMapper = mock(AttendanceGroupMemberMapper.class);
    private final AttendanceGroupMapper groupMapper = mock(AttendanceGroupMapper.class);
    private final AttendanceShiftMapper shiftMapper = mock(AttendanceShiftMapper.class);
    private final AttendanceHolidayMapper holidayMapper = mock(AttendanceHolidayMapper.class);

    private final AttendanceCalendar calendar =
            new AttendanceCalendar(memberMapper, groupMapper, shiftMapper, holidayMapper);

    @BeforeAll
    static void initTableInfo() {
        // 纯单测没有 MyBatis 上下文：手工初始化实体列缓存，让生产代码里的 LambdaQueryWrapper 可构建。
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "attendanceCalendarTest");
        assistant.setCurrentNamespace("com.lautung.oa.attendance.calendar.AttendanceCalendarTest");
        TableInfoHelper.initTableInfo(assistant, AttendanceGroupMember.class);
        TableInfoHelper.initTableInfo(assistant, AttendanceGroup.class);
        TableInfoHelper.initTableInfo(assistant, AttendanceShift.class);
        TableInfoHelper.initTableInfo(assistant, AttendanceHoliday.class);
    }

    @BeforeEach
    void stubMemberGroupShift() {
        when(memberMapper.selectOne(any())).thenReturn(memberOf());
        when(groupMapper.selectById(GROUP_ID)).thenReturn(groupOf("1,2,3,4,5"));
        when(shiftMapper.selectById(SHIFT_ID)).thenReturn(standardShift());
    }

    private static AttendanceGroupMember memberOf() {
        AttendanceGroupMember member = new AttendanceGroupMember();
        member.setUserId(USER_ID);
        member.setGroupId(GROUP_ID);
        return member;
    }

    private static AttendanceGroup groupOf(String workdays) {
        AttendanceGroup group = new AttendanceGroup();
        group.setId(GROUP_ID);
        group.setShiftId(SHIFT_ID);
        group.setWorkdays(workdays);
        return group;
    }

    private static AttendanceShift standardShift() {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(SHIFT_ID);
        shift.setStartTime(LocalTime.of(9, 0));
        shift.setEndTime(LocalTime.of(18, 0));
        shift.setLunchStart(LocalTime.of(12, 0));
        shift.setLunchEnd(LocalTime.of(13, 0));
        return shift;
    }

    private static AttendanceHoliday holiday(LocalDate date, int dayType) {
        AttendanceHoliday row = new AttendanceHoliday();
        row.setHolidayDate(date);
        row.setDayType(dayType);
        return row;
    }

    private void stubHolidays(AttendanceHoliday... rows) {
        when(holidayMapper.selectList(any())).thenReturn(List.of(rows));
    }

    @Test
    void holidayOverridesGroupWorkday() {
        stubHolidays(holiday(WEDNESDAY, AttendanceHoliday.DAY_TYPE_HOLIDAY));
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        // 组工作日（周三）被休假日覆盖。
        assertEquals(AttendanceDayType.HOLIDAY, view.dayType(WEDNESDAY));
        assertFalse(view.isWorkday(WEDNESDAY));
        assertEquals(4, view.workdayCount());
        // 其余组工作日照常。
        assertEquals(AttendanceDayType.WORKDAY, view.dayType(MONDAY));
        assertTrue(view.isWorkday(MONDAY));
    }

    @Test
    void makeupOverridesWeekendRest() {
        stubHolidays(holiday(SATURDAY, AttendanceHoliday.DAY_TYPE_WORKDAY));
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        // 休息日（周六）被补班日覆盖。
        assertEquals(AttendanceDayType.MAKEUP, view.dayType(SATURDAY));
        assertTrue(view.isWorkday(SATURDAY));
        assertEquals(6, view.workdayCount());
        // 周日没有补班配置，仍是休息日。
        assertEquals(AttendanceDayType.REST, view.dayType(SUNDAY));
    }

    @Test
    void groupWorkdaysParsingFollowsMultiSelectFormula() {
        // 带空格与脏值的多选串：脏值当作不包含，不让一条坏配置拦住解析。
        when(groupMapper.selectById(GROUP_ID)).thenReturn(groupOf("1, 2 ,x,3"));
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        assertEquals(AttendanceDayType.WORKDAY, view.dayType(MONDAY));
        assertEquals(AttendanceDayType.WORKDAY, view.dayType(TUESDAY));
        assertEquals(AttendanceDayType.WORKDAY, view.dayType(WEDNESDAY));
        assertEquals(AttendanceDayType.REST, view.dayType(THURSDAY));
        assertEquals(3, view.workdayCount());

        // workdays 缺失时全部按休息日，不臆造工作日。
        when(groupMapper.selectById(GROUP_ID)).thenReturn(groupOf(null));
        AttendanceCalendarView blankView = calendar.open(USER_ID, MONDAY, FRIDAY);
        assertEquals(AttendanceDayType.REST, blankView.dayType(MONDAY));
        assertEquals(0, blankView.workdayCount());
    }

    @Test
    void notInGroupAnswersUndecidableEmptyShape() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        assertFalse(view.hasMember());
        assertFalse(view.hasGroup());
        assertNull(view.group());
        assertNull(view.shift());
        assertEquals(AttendanceDayType.NONE, view.dayType(MONDAY));
        assertFalse(view.isWorkday(MONDAY));
        assertEquals(0, view.workdayCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(view.dailyHours()));
    }

    @Test
    void danglingGroupKeepsMemberAnswerableButDayUndecidable() {
        // 成员行在、组行没了：hasMember 与 hasGroup 分开回答，请假定稿靠它区分两种 201。
        when(groupMapper.selectById(GROUP_ID)).thenReturn(null);
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        assertTrue(view.hasMember());
        assertFalse(view.hasGroup());
        assertNull(view.group());
        assertEquals(AttendanceDayType.NONE, view.dayType(MONDAY));
        assertEquals(0, view.workdayCount());
    }

    @Test
    void missingShiftKeepsCalendarFactsAnswerable() {
        // 组在、班次没了：日类型仍可判定，日工时给空形状（0），拒绝文案留给消费方。
        when(shiftMapper.selectById(SHIFT_ID)).thenReturn(null);
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        assertTrue(view.hasMember());
        assertTrue(view.hasGroup());
        assertNull(view.shift());
        assertEquals(AttendanceDayType.WORKDAY, view.dayType(MONDAY));
        assertEquals(5, view.workdayCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(view.dailyHours()));
    }

    @Test
    void crossMonthRangeLoadsHolidaysOnceAndCountsAcrossMonths() {
        // 2026-02-27 周五 至 2026-03-03 周二：2/28 休假日、3/2 休假日、3/1 周日休息。
        stubHolidays(holiday(LocalDate.of(2026, 2, 28), AttendanceHoliday.DAY_TYPE_HOLIDAY),
                holiday(LocalDate.of(2026, 3, 2), AttendanceHoliday.DAY_TYPE_HOLIDAY));
        AttendanceCalendarView view = calendar.open(USER_ID,
                LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 3));
        assertEquals(AttendanceDayType.HOLIDAY, view.dayType(LocalDate.of(2026, 2, 28)));
        assertEquals(AttendanceDayType.HOLIDAY, view.dayType(LocalDate.of(2026, 3, 2)));
        assertEquals(AttendanceDayType.REST, view.dayType(LocalDate.of(2026, 3, 1)));
        assertEquals(2, view.workdayCount());
        // 区间节假日一次装载：反复问日类型不逐日回表。
        verify(holidayMapper, times(1)).selectList(any());
    }

    @Test
    void dailyHoursDeductsLunchInsideShift() {
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, MONDAY);
        // 09:00–18:00 扣 12:00–13:00 午休 = 8 小时。
        assertEquals(0, new BigDecimal("8.00").compareTo(view.dailyHours()));

        AttendanceShift noLunch = standardShift();
        noLunch.setLunchStart(null);
        noLunch.setLunchEnd(null);
        when(shiftMapper.selectById(SHIFT_ID)).thenReturn(noLunch);
        AttendanceCalendarView noLunchView = calendar.open(USER_ID, MONDAY, MONDAY);
        // 无午休不减：09:00–18:00 = 9 小时。
        assertEquals(0, new BigDecimal("9.00").compareTo(noLunchView.dailyHours()));
    }

    @Test
    void nonPositiveOrMissingDailyHoursFallBackToEight() {
        // 下班不晚于上班：兜底 8 小时。
        AttendanceShift degenerate = standardShift();
        degenerate.setEndTime(LocalTime.of(9, 0));
        when(shiftMapper.selectById(SHIFT_ID)).thenReturn(degenerate);
        assertEquals(0, new BigDecimal("8").compareTo(
                calendar.open(USER_ID, MONDAY, MONDAY).dailyHours()));

        // 午休吞掉整段班：非正工时兜底 8 小时。
        AttendanceShift swallowed = standardShift();
        swallowed.setLunchStart(LocalTime.of(9, 0));
        swallowed.setLunchEnd(LocalTime.of(18, 0));
        when(shiftMapper.selectById(SHIFT_ID)).thenReturn(swallowed);
        assertEquals(0, new BigDecimal("8").compareTo(
                calendar.open(USER_ID, MONDAY, MONDAY).dailyHours()));

        // 班次时刻缺失：兜底 8 小时。
        AttendanceShift noTimes = standardShift();
        noTimes.setStartTime(null);
        noTimes.setEndTime(null);
        when(shiftMapper.selectById(SHIFT_ID)).thenReturn(noTimes);
        assertEquals(0, new BigDecimal("8").compareTo(
                calendar.open(USER_ID, MONDAY, MONDAY).dailyHours()));
    }

    @Test
    void opensContextOnceAndAnswersRepeatedlyWithoutRequerying() {
        stubHolidays(holiday(WEDNESDAY, AttendanceHoliday.DAY_TYPE_HOLIDAY));
        AttendanceCalendarView view = calendar.open(USER_ID, MONDAY, SUNDAY);
        for (LocalDate date = MONDAY; !date.isAfter(SUNDAY); date = date.plusDays(1)) {
            view.dayType(date);
            view.isWorkday(date);
        }
        view.workdayCount();
        view.dailyHours();
        // 打开视图只装载一次成员/考勤组/班次/节假日，反复问不再回表。
        verify(memberMapper, times(1)).selectOne(any());
        verify(groupMapper, times(1)).selectById(any());
        verify(shiftMapper, times(1)).selectById(any());
        verify(holidayMapper, times(1)).selectList(any());
    }
}
