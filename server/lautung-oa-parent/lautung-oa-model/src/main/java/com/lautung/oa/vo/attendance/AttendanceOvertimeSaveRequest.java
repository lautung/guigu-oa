package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 新建加班草稿（#282，ADR 0034）：只创建、不带主键；小时数先置 0，提交时定稿。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新建加班单")
public class AttendanceOvertimeSaveRequest extends AttendanceOvertimeRequest {

    public AttendanceOvertime toEntity(Long applicantId) {
        AttendanceOvertime overtime = new AttendanceOvertime();
        overtime.setApplicantId(applicantId);
        overtime.setOvertimeDate(getOvertimeDate());
        overtime.setStartTime(getStartTime());
        overtime.setEndTime(getEndTime());
        overtime.setReason(getReason());
        overtime.setHours(BigDecimal.ZERO);
        overtime.setStatus(AttendanceApplyStatus.DRAFT);
        return overtime;
    }
}
