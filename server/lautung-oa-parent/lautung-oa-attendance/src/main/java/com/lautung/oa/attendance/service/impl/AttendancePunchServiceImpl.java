package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMemberMapper;
import com.lautung.oa.attendance.mapper.AttendanceHolidayMapper;
import com.lautung.oa.attendance.mapper.AttendancePunchMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.attendance.service.AttendancePunchService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceDayType;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.attendance.AttendanceShift;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import com.lautung.oa.vo.attendance.AttendanceShiftVo;
import com.lautung.oa.vo.attendance.AttendanceTodayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 打卡实现（#279，ADR 0073）：只落事件流水，日状态读时推导。
 *
 * <p>工作日公式：{@code (星期 ∈ 组工作日 且 该日非休假日) 或 该日是补班日}。
 * 无组账号没有考勤义务（不能打卡，今日状态 hasGroup=false）；非工作日提交打卡
 * 201「今日无排班」。重复打卡每次追加一行，槽位状态取极值：有补卡行取最新一条
 * 补卡行，否则上班取最早网页行、下班取最晚网页行。
 */
@Service
public class AttendancePunchServiceImpl extends ServiceImpl<AttendancePunchMapper, AttendancePunch>
        implements AttendancePunchService {

    @Autowired
    private AttendanceGroupMemberMapper memberMapper;

    @Autowired
    private AttendanceGroupMapper groupMapper;

    @Autowired
    private AttendanceShiftMapper shiftMapper;

    @Autowired
    private AttendanceHolidayMapper holidayMapper;

    @Override
    public AttendanceTodayVo today(Long userId) {
        return buildToday(userId, LocalDate.now());
    }

    @Override
    @Transactional
    public AttendanceTodayVo punch(Long userId, int slot) {
        if (slot != AttendancePunch.SLOT_IN && slot != AttendancePunch.SLOT_OUT) {
            throw new BusinessException(201, "打卡槽位不合法");
        }
        LocalDate date = LocalDate.now();
        DayContext context = resolveDay(userId, date);
        if (!context.hasGroup()) {
            throw new BusinessException(201, "未加入考勤组，不能打卡");
        }
        if (context.shift() == null) {
            throw new BusinessException(201, "考勤组未绑定有效班次");
        }
        if (!AttendanceDayType.isWorkday(context.dayType())) {
            throw new BusinessException(201, "今日无排班");
        }

        AttendancePunch punch = new AttendancePunch();
        punch.setUserId(userId);
        punch.setPunchDate(date);
        punch.setSlot(slot);
        punch.setPunchTime(LocalDateTime.now());
        punch.setSource(AttendancePunch.SOURCE_WEB);
        this.save(punch);
        return buildToday(userId, date);
    }

    @Override
    public IPage<AttendancePunchVo> page(Page<AttendancePunchVo> page,
                                         AttendancePunchQueryVo query,
                                         DataScope scope) {
        validateQuery(query);
        return baseMapper.selectPunchPage(page, query, scope);
    }

    private AttendanceTodayVo buildToday(Long userId, LocalDate date) {
        DayContext context = resolveDay(userId, date);
        AttendanceTodayVo vo = new AttendanceTodayVo();
        vo.setDate(date);
        vo.setWeekday(date.getDayOfWeek().getValue());
        vo.setDayType(context.dayType());
        vo.setWorkday(AttendanceDayType.isWorkday(context.dayType()));
        vo.setHasGroup(context.hasGroup());
        if (context.group() != null) {
            vo.setGroupId(context.group().getId());
            vo.setGroupName(context.group().getName());
        }
        if (context.shift() != null) {
            vo.setShift(AttendanceShiftVo.of(context.shift()));
        }
        List<AttendancePunch> punches = list(new LambdaQueryWrapper<AttendancePunch>()
                .eq(AttendancePunch::getUserId, userId)
                .eq(AttendancePunch::getPunchDate, date)
                .orderByAsc(AttendancePunch::getPunchTime)
                .orderByAsc(AttendancePunch::getId));
        vo.setClockInTime(effectiveTime(punches, AttendancePunch.SLOT_IN));
        vo.setClockOutTime(effectiveTime(punches, AttendancePunch.SLOT_OUT));
        return vo;
    }

    /**
     * 槽位有效时刻（ADR 0073）：补卡行优先且取最新一条（id 最大），
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
                if (punch.getId() != null && punch.getId() > repairId) {
                    repairId = punch.getId();
                    repairTime = time;
                }
            } else if (webTime == null
                    || (slot == AttendancePunch.SLOT_IN ? time.isBefore(webTime) : time.isAfter(webTime))) {
                webTime = time;
            }
        }
        return repairTime != null ? repairTime : webTime;
    }

    private DayContext resolveDay(Long userId, LocalDate date) {
        AttendanceGroupMember member = memberMapper.selectOne(new LambdaQueryWrapper<AttendanceGroupMember>()
                .eq(AttendanceGroupMember::getUserId, userId));
        if (member == null) {
            return DayContext.noGroup();
        }
        AttendanceGroup group = groupMapper.selectById(member.getGroupId());
        if (group == null) {
            return DayContext.noGroup();
        }
        AttendanceShift shift = shiftMapper.selectById(group.getShiftId());
        AttendanceHoliday holiday = holidayMapper.selectOne(new LambdaQueryWrapper<AttendanceHoliday>()
                .eq(AttendanceHoliday::getHolidayDate, date));
        return new DayContext(true, group, shift, dayType(group, holiday, date));
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
        return containsWorkday(group.getWorkdays(), date.getDayOfWeek().getValue())
                ? AttendanceDayType.WORKDAY
                : AttendanceDayType.REST;
    }

    private boolean containsWorkday(String workdays, int weekday) {
        if (workdays == null || workdays.isEmpty()) {
            return false;
        }
        for (String part : workdays.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == weekday) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // 配置行由 service 规范化；脏值当作不包含，不让一条坏配置拦住打卡。
            }
        }
        return false;
    }

    private void validateQuery(AttendancePunchQueryVo query) {
        if (query == null) {
            return;
        }
        if (query.getSource() != null
                && query.getSource() != AttendancePunch.SOURCE_WEB
                && query.getSource() != AttendancePunch.SOURCE_REPAIR) {
            throw new BusinessException(201, "打卡来源不合法");
        }
        if (query.getBeginDate() != null && query.getEndDate() != null
                && query.getBeginDate().isAfter(query.getEndDate())) {
            throw new BusinessException(201, "开始日期不能晚于结束日期");
        }
    }

    /** 一次解析的日上下文；{@code hasGroup=false} 时其余字段为空。 */
    private record DayContext(boolean hasGroup, AttendanceGroup group,
                              AttendanceShift shift, int dayType) {

        static DayContext noGroup() {
            return new DayContext(false, null, null, AttendanceDayType.NONE);
        }
    }
}
