package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceLeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增假期类型的请求体（#280，ADR 0034）：save 只创建、不带主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增假期类型")
public class AttendanceLeaveTypeSaveRequest extends AttendanceLeaveTypeRequest {

    @Override
    @NotBlank(message = "假期类型名称不能为空")
    public String getName() {
        return super.getName();
    }

    public AttendanceLeaveType toEntity() {
        return fill(new AttendanceLeaveType());
    }
}
