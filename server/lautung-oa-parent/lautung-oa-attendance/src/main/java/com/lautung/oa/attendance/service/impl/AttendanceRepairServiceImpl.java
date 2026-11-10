package com.lautung.oa.attendance.service.impl;

import com.lautung.oa.attendance.mapper.AttendancePunchMapper;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceDayStatusService;
import com.lautung.oa.attendance.service.AttendanceRepairService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendancePunch;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.vo.attendance.AttendanceDayStatusVo;
import com.lautung.oa.vo.attendance.AttendanceRepairRequest;
import com.lautung.oa.vo.attendance.AttendanceRepairUserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 补卡实现（#281，ADR 0073）：补齐/覆盖槽位靠追加 source=2 流水行，不改原行。
 *
 * <p>校验先于写：成员/日期必填、至少一个槽位、双槽位时上班早于下班、成员在数据
 * 范围内、成员已加入考勤组。数据范围判定与查询共用一条 1 = 0 兜底（空集零行）。
 */
@Service
public class AttendanceRepairServiceImpl implements AttendanceRepairService {

    private final AttendanceUserMapper userMapper;

    private final AttendancePunchMapper punchMapper;

    private final AttendanceDayStatusService dayStatusService;

    @Autowired
    public AttendanceRepairServiceImpl(AttendanceUserMapper userMapper,
                                       AttendancePunchMapper punchMapper,
                                       AttendanceDayStatusService dayStatusService) {
        this.userMapper = userMapper;
        this.punchMapper = punchMapper;
        this.dayStatusService = dayStatusService;
    }

    @Override
    public List<AttendanceRepairUserVo> users(String name, DataScope scope) {
        return userMapper.selectScopedUsers(trimToNull(name), null, scope);
    }

    @Override
    public AttendanceDayStatusVo day(Long userId, LocalDate date, DataScope scope) {
        if (userId == null) {
            throw new BusinessException(201, "成员不能为空");
        }
        if (date == null) {
            throw new BusinessException(201, "补卡日期不能为空");
        }
        requireVisibleUser(userId, scope);
        return dayStatusService.day(userId, date);
    }

    @Override
    @Transactional
    public AttendanceDayStatusVo repair(AttendanceRepairRequest request, Long operatorUserId,
                                        DataScope scope) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException(201, "成员不能为空");
        }
        if (request.getRepairDate() == null) {
            throw new BusinessException(201, "补卡日期不能为空");
        }
        if (request.getClockInTime() == null && request.getClockOutTime() == null) {
            throw new BusinessException(201, "至少补一个槽位");
        }
        if (request.getClockInTime() != null && request.getClockOutTime() != null
                && !request.getClockInTime().isBefore(request.getClockOutTime())) {
            throw new BusinessException(201, "上班时间必须早于下班时间");
        }

        requireVisibleUser(request.getUserId(), scope);
        AttendanceDayStatusVo before = dayStatusService.day(request.getUserId(), request.getRepairDate());
        if (!Boolean.TRUE.equals(before.getHasGroup())) {
            throw new BusinessException(201, "成员未加入考勤组");
        }

        LocalDate date = request.getRepairDate();
        if (request.getClockInTime() != null) {
            append(request.getUserId(), date, AttendancePunch.SLOT_IN,
                    request.getClockInTime(), operatorUserId);
        }
        if (request.getClockOutTime() != null) {
            append(request.getUserId(), date, AttendancePunch.SLOT_OUT,
                    request.getClockOutTime(), operatorUserId);
        }
        return dayStatusService.day(request.getUserId(), date);
    }

    private void append(Long userId, LocalDate date, int slot,
                        LocalTime time, Long operatorUserId) {
        AttendancePunch punch = new AttendancePunch();
        punch.setUserId(userId);
        punch.setPunchDate(date);
        punch.setSlot(slot);
        punch.setPunchTime(date.atTime(time));
        punch.setSource(AttendancePunch.SOURCE_REPAIR);
        punch.setOperatorUserId(operatorUserId);
        punchMapper.insert(punch);
    }

    private void requireVisibleUser(Long userId, DataScope scope) {
        List<AttendanceRepairUserVo> visible = userMapper.selectScopedUsers(null, userId, scope);
        if (visible.isEmpty()) {
            // 不存在与范围外同一句：不泄露用户是否存在。
            throw new BusinessException(201, "成员不在数据范围内");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
