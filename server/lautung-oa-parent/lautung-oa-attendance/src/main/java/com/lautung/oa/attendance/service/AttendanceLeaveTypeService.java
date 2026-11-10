package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceLeaveTypeUpdateRequest;

/**
 * 假期类型 CRUD（#280，ADR 0073）：类型被流水与请假单引用后只能停用不删除；
 * 加班入账目标全局唯一（更新时排除自身）。
 */
public interface AttendanceLeaveTypeService extends IService<AttendanceLeaveType> {

    void saveType(AttendanceLeaveTypeSaveRequest request);

    void updateType(AttendanceLeaveTypeUpdateRequest request);

    void removeType(Long id);
}
