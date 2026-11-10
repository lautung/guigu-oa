package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceOvertime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改加班单（#282）：草稿/拒绝/撤回可改，审批中与已通过拒绝。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "修改加班单")
public class AttendanceOvertimeUpdateRequest extends AttendanceOvertimeRequest {

    @NotNull(message = "加班单标识不能为空")
    @Schema(description = "加班单 id")
    private Long id;

    public AttendanceOvertime toEntity() {
        AttendanceOvertime overtime = new AttendanceOvertime();
        overtime.setId(getId());
        overtime.setOvertimeDate(getOvertimeDate());
        overtime.setStartTime(getStartTime());
        overtime.setEndTime(getEndTime());
        overtime.setReason(getReason());
        return overtime;
    }
}
