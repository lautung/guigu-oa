package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendanceShift;
import com.lautung.oa.vo.attendance.AttendanceShiftSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceShiftUpdateRequest;

/**
 * 班次 CRUD（#278，ADR 0073）：可建多个班次定义；时刻必须同一天内
 * （下班晚于上班），不支持跨天班。午休只用于展示与日工时折算。
 */
public interface AttendanceShiftService extends IService<AttendanceShift> {

    void saveShift(AttendanceShiftSaveRequest request);

    void updateShift(AttendanceShiftUpdateRequest request);

    void removeShift(Long id);
}
