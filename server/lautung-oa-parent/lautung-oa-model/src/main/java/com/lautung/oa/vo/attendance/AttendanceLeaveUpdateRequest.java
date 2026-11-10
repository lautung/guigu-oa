package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceLeave;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改请假单（#282）：草稿/拒绝/撤回可改，审批中与已通过拒绝。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "修改请假单")
public class AttendanceLeaveUpdateRequest extends AttendanceLeaveRequest {

    @NotNull(message = "请假单标识不能为空")
    @Schema(description = "请假单 id")
    private Long id;

    public AttendanceLeave toEntity() {
        AttendanceLeave leave = new AttendanceLeave();
        leave.setId(getId());
        leave.setLeaveTypeId(getLeaveTypeId());
        leave.setStartDate(getStartDate());
        leave.setEndDate(getEndDate());
        leave.setReason(getReason());
        return leave;
    }
}
