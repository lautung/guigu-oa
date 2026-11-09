package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import com.lautung.oa.vo.attendance.AttendanceHolidaySaveRequest;
import com.lautung.oa.vo.attendance.AttendanceHolidayUpdateRequest;

/**
 * 全局节假日维护（#278，ADR 0073）：日期唯一、类型 1 休假日 / 2 补班日；
 * 行删除为硬删（配置修正，操作日志留痕）。
 */
public interface AttendanceHolidayService extends IService<AttendanceHoliday> {

    void saveHoliday(AttendanceHolidaySaveRequest request);

    void updateHoliday(AttendanceHolidayUpdateRequest request);

    void removeHoliday(Long id);
}
