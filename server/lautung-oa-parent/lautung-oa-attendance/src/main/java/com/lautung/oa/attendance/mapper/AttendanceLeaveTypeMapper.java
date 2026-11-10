package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.attendance.AttendanceLeaveType;
import org.apache.ibatis.annotations.Param;

/**
 * 假期类型 Mapper（#280，ADR 0073）：类型只停用不删，删除前查引用历史
 * （流水与请假单；跨表小读留在本域，ADR 0050 同族）。
 */
public interface AttendanceLeaveTypeMapper extends BaseMapper<AttendanceLeaveType> {

    long countLedgerReferences(@Param("leaveTypeId") Long leaveTypeId);

    long countLeaveReferences(@Param("leaveTypeId") Long leaveTypeId);
}
