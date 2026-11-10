package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceLeave;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 新建请假草稿（#282，ADR 0034）：只创建、不带主键；天数/小时数先置 0，
 * 提交时定稿。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新建请假单")
public class AttendanceLeaveSaveRequest extends AttendanceLeaveRequest {

    public AttendanceLeave toEntity(Long applicantId) {
        AttendanceLeave leave = new AttendanceLeave();
        leave.setApplicantId(applicantId);
        leave.setLeaveTypeId(getLeaveTypeId());
        leave.setStartDate(getStartDate());
        leave.setEndDate(getEndDate());
        leave.setReason(getReason());
        leave.setDays(0);
        leave.setHours(BigDecimal.ZERO);
        leave.setStatus(AttendanceApplyStatus.DRAFT);
        return leave;
    }
}
