package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendancePunchMapper;
import com.lautung.oa.attendance.service.AttendanceClock;
import com.lautung.oa.attendance.service.AttendanceDayStatusService;
import com.lautung.oa.attendance.service.AttendancePunchService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceDayStatusVo;
import com.lautung.oa.vo.attendance.AttendancePunchQueryVo;
import com.lautung.oa.vo.attendance.AttendancePunchVo;
import com.lautung.oa.vo.attendance.AttendanceTodayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 打卡实现（#279，ADR 0073）：只落事件流水，日状态读时推导。
 *
 * <p>今日状态的班次、工作日判定与槽位有效时刻统一由
 * {@link AttendanceDayStatusService} 推导（#281 收成唯一 service），本类只负责
 * 写字与把推导结果投影成办事今日视图。无组账号不能打卡；非工作日提交打卡
 * 201「今日无排班」。重复打卡每次追加一行。
 */
@Service
public class AttendancePunchServiceImpl extends ServiceImpl<AttendancePunchMapper, AttendancePunch>
        implements AttendancePunchService {

    @Autowired
    private AttendanceDayStatusService attendanceDayStatusService;

    @Autowired
    private AttendanceClock attendanceClock;

    @Override
    public AttendanceTodayVo today(Long userId) {
        return buildToday(userId, attendanceClock.now().toLocalDate());
    }

    @Override
    @Transactional
    public AttendanceTodayVo punch(Long userId, int slot) {
        if (slot != AttendancePunch.SLOT_IN && slot != AttendancePunch.SLOT_OUT) {
            throw new BusinessException(201, "打卡槽位不合法");
        }
        LocalDateTime now = attendanceClock.now();
        LocalDate date = now.toLocalDate();
        AttendanceDayStatusVo day = attendanceDayStatusService.day(userId, date);
        if (!Boolean.TRUE.equals(day.getHasGroup())) {
            throw new BusinessException(201, "未加入考勤组，不能打卡");
        }
        if (day.getShift() == null) {
            throw new BusinessException(201, "考勤组未绑定有效班次");
        }
        if (!Boolean.TRUE.equals(day.getWorkday())) {
            throw new BusinessException(201, "今日无排班");
        }

        AttendancePunch punch = new AttendancePunch();
        punch.setUserId(userId);
        punch.setPunchDate(date);
        punch.setSlot(slot);
        punch.setPunchTime(now);
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
        AttendanceDayStatusVo day = attendanceDayStatusService.day(userId, date);
        AttendanceTodayVo vo = new AttendanceTodayVo();
        vo.setDate(day.getDate());
        vo.setWeekday(day.getWeekday());
        vo.setDayType(day.getDayType());
        vo.setWorkday(day.getWorkday());
        vo.setHasGroup(day.getHasGroup());
        vo.setGroupId(day.getGroupId());
        vo.setGroupName(day.getGroupName());
        vo.setShift(day.getShift());
        vo.setClockInTime(day.getClockInTime());
        vo.setClockOutTime(day.getClockOutTime());
        return vo;
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
}
