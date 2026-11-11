package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.attendance.mapper.AttendanceOvertimeMapper;
import com.lautung.oa.attendance.mapper.AttendanceStatMapper;
import com.lautung.oa.attendance.service.AttendanceDayStatusService;
import com.lautung.oa.attendance.service.AttendanceStatService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceDayStatus;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceDayStatusVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveStatVo;
import com.lautung.oa.vo.attendance.AttendanceMonthVo;
import com.lautung.oa.vo.attendance.AttendanceStatVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 月度统计实现（#283，ADR 0073 §统计口径）。
 *
 * <p>口径与 #281 的逐日推导逐项对齐：应出勤 = 工作日公式逐日计；缺卡 = 状态 5；
 * 请假 = 状态 6 并按类型名分列；迟到/早退直接消费推导 VO 的按卡布尔
 * （缺卡日只要该卡存在且超线仍计一次）；实出勤 = 应出勤 − 请假 − 缺卡。
 * 加班时长只认 status=2 的加班单，按 overtime_date 落在当月汇总 hours。
 */
@Service
public class AttendanceStatServiceImpl implements AttendanceStatService {

    private final AttendanceStatMapper statMapper;

    private final AttendanceOvertimeMapper overtimeMapper;

    private final AttendanceDayStatusService dayStatusService;

    @Autowired
    public AttendanceStatServiceImpl(AttendanceStatMapper statMapper,
                                     AttendanceOvertimeMapper overtimeMapper,
                                     AttendanceDayStatusService dayStatusService) {
        this.statMapper = statMapper;
        this.overtimeMapper = overtimeMapper;
        this.dayStatusService = dayStatusService;
    }

    @Override
    public IPage<AttendanceStatVo> page(Page<AttendanceStatVo> page, String month,
                                        DataScope scope) {
        YearMonth yearMonth = dayStatusService.resolveMonth(month);
        LocalDate beginDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        IPage<AttendanceStatVo> result = statMapper.selectStatPage(page, beginDate, endDate, scope);
        Map<Long, BigDecimal> overtime = overtimeHours(result.getRecords(), beginDate, endDate);
        for (AttendanceStatVo row : result.getRecords()) {
            summarize(row, beginDate, endDate,
                    overtime.getOrDefault(row.getUserId(), BigDecimal.ZERO));
        }
        return result;
    }

    @Override
    public AttendanceMonthVo month(Long userId, String month, DataScope scope) {
        if (userId == null) {
            throw new BusinessException(201, "成员不能为空");
        }
        YearMonth yearMonth = dayStatusService.resolveMonth(month);
        AttendanceStatVo visible = statMapper.selectScopedStatUser(
                yearMonth.atDay(1), yearMonth.atEndOfMonth(), userId, scope);
        if (visible == null) {
            // 不存在与范围外同一句：不泄露用户是否存在。
            throw new BusinessException(201, "成员不在数据范围内");
        }
        return dayStatusService.month(userId, yearMonth.toString());
    }

    private void summarize(AttendanceStatVo row, LocalDate beginDate, LocalDate endDate,
                           BigDecimal overtimeHours) {
        int requiredDays = 0;
        int missingDays = 0;
        int lateCount = 0;
        int earlyCount = 0;
        Map<String, Integer> leaveDays = new TreeMap<>();
        for (AttendanceDayStatusVo day : dayStatusService.days(row.getUserId(), beginDate, endDate)) {
            if (Boolean.TRUE.equals(day.getWorkday())) {
                requiredDays++;
            }
            if (Boolean.TRUE.equals(day.getLate())) {
                lateCount++;
            }
            if (Boolean.TRUE.equals(day.getEarly())) {
                earlyCount++;
            }
            Integer status = day.getStatus();
            if (status == null) {
                continue;
            }
            if (status == AttendanceDayStatus.MISSING) {
                missingDays++;
            } else if (status == AttendanceDayStatus.LEAVE) {
                String typeName = day.getLeaveTypeName() == null ? "请假" : day.getLeaveTypeName();
                leaveDays.merge(typeName, 1, Integer::sum);
            }
        }
        int leaveTotal = leaveDays.values().stream().mapToInt(Integer::intValue).sum();

        row.setRequiredDays(requiredDays);
        row.setActualDays(requiredDays - leaveTotal - missingDays);
        row.setLateCount(lateCount);
        row.setEarlyCount(earlyCount);
        row.setMissingDays(missingDays);
        row.setLeaveDays(leaveStats(leaveDays));
        row.setOvertimeHours(overtimeHours.setScale(2, RoundingMode.HALF_UP));
    }

    private Map<Long, BigDecimal> overtimeHours(List<AttendanceStatVo> rows,
                                                LocalDate beginDate, LocalDate endDate) {
        List<Long> userIds = new ArrayList<>();
        for (AttendanceStatVo row : rows) {
            if (row.getUserId() != null) {
                userIds.add(row.getUserId());
            }
        }
        Map<Long, BigDecimal> hours = new HashMap<>();
        if (userIds.isEmpty()) {
            return hours;
        }
        List<AttendanceOvertime> approved = overtimeMapper.selectList(
                new LambdaQueryWrapper<AttendanceOvertime>()
                        .in(AttendanceOvertime::getApplicantId, userIds)
                        .eq(AttendanceOvertime::getStatus, AttendanceApplyStatus.APPROVED.code())
                        .ge(AttendanceOvertime::getOvertimeDate, beginDate)
                        .le(AttendanceOvertime::getOvertimeDate, endDate));
        for (AttendanceOvertime overtime : approved) {
            if (overtime.getApplicantId() != null && overtime.getHours() != null) {
                hours.merge(overtime.getApplicantId(), overtime.getHours(), BigDecimal::add);
            }
        }
        return hours;
    }

    private List<AttendanceLeaveStatVo> leaveStats(Map<String, Integer> leaveDays) {
        List<AttendanceLeaveStatVo> stats = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : leaveDays.entrySet()) {
            AttendanceLeaveStatVo stat = new AttendanceLeaveStatVo();
            stat.setLeaveTypeName(entry.getKey());
            stat.setDays(entry.getValue());
            stats.add(stat);
        }
        return stats;
    }
}
