package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.attendance.service.AttendanceShiftService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceShift;
import com.lautung.oa.vo.attendance.AttendanceShiftSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceShiftUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * 班次 CRUD 实现（#278，ADR 0073）：下班晚于上班（同一天内）、午休成对且有序、
 * 宽限分钟非负；被考勤组绑定的班次不能删除。
 *
 * <p>更新按完整表单覆盖（名称/时刻必填，午休成对为空=清除），不走「空=不覆盖」的
 * 缺省策略——午休是成对展示值，部分更新要么清不掉、要么静默清掉，都不诚实。
 */
@Service
public class AttendanceShiftServiceImpl extends ServiceImpl<AttendanceShiftMapper, AttendanceShift>
        implements AttendanceShiftService {

    @Autowired
    private AttendanceGroupMapper attendanceGroupMapper;

    @Override
    public void saveShift(AttendanceShiftSaveRequest request) {
        validateTimes(request.getStartTime(), request.getEndTime(),
                request.getLunchStart(), request.getLunchEnd());
        this.save(request.toEntity());
    }

    @Override
    public void updateShift(AttendanceShiftUpdateRequest request) {
        AttendanceShift existing = getById(request.getId());
        if (existing == null) {
            throw new BusinessException(201, "班次不存在");
        }
        validateTimes(request.getStartTime(), request.getEndTime(),
                request.getLunchStart(), request.getLunchEnd());
        // 完整表单覆盖：午休用显式 set 写 NULL（updateById 的空值策略会跳过 null，清不掉午休）。
        this.update(new LambdaUpdateWrapper<AttendanceShift>()
                .eq(AttendanceShift::getId, request.getId())
                .set(AttendanceShift::getName, request.getName())
                .set(AttendanceShift::getStartTime, request.getStartTime())
                .set(AttendanceShift::getEndTime, request.getEndTime())
                .set(AttendanceShift::getLunchStart, request.getLunchStart())
                .set(AttendanceShift::getLunchEnd, request.getLunchEnd())
                .set(AttendanceShift::getGraceMinutes, request.getGraceMinutes())
                .set(AttendanceShift::getRemark, request.getRemark()));
    }

    @Override
    public void removeShift(Long id) {
        Long boundGroups = attendanceGroupMapper.selectCount(new LambdaQueryWrapper<AttendanceGroup>()
                .eq(AttendanceGroup::getShiftId, id));
        if (boundGroups != null && boundGroups > 0) {
            throw new BusinessException(201, "仍有考勤组绑定该班次，不能删除");
        }
        removeById(id);
    }

    private void validateTimes(LocalTime start, LocalTime end,
                               LocalTime lunchStart, LocalTime lunchEnd) {
        if (!end.isAfter(start)) {
            throw new BusinessException(201, "下班时刻必须晚于上班时刻");
        }
        if ((lunchStart == null) != (lunchEnd == null)) {
            throw new BusinessException(201, "午休开始与结束必须同时填写");
        }
        if (lunchStart != null && !lunchEnd.isAfter(lunchStart)) {
            throw new BusinessException(201, "午休结束必须晚于午休开始");
        }
    }
}
