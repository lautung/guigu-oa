package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceLeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改假期类型的请求体（#280）：按完整表单覆盖（含状态，停用即 status=0）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "修改假期类型")
public class AttendanceLeaveTypeUpdateRequest extends AttendanceLeaveTypeRequest {

    @NotNull(message = "假期类型不能为空")
    @Schema(description = "类型 id")
    private Long id;

    @Override
    @NotBlank(message = "假期类型名称不能为空")
    public String getName() {
        return super.getName();
    }

    public AttendanceLeaveType toEntity() {
        AttendanceLeaveType type = fill(new AttendanceLeaveType());
        type.setId(id);
        return type;
    }
}
