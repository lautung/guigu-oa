package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceShift;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

/**
 * 更新班次的请求体（#278）：按完整表单覆盖——名称与上下班时刻必填、午休成对
 * （成对为空=清除午休、成对给出=覆盖），避免「部分更新静默清掉午休」或
 * 「清不掉午休」两个方向的反直觉（ADR 0034 的空=不覆盖按域收窄，见 VO 注释）。
 * 公共字段与时刻格式在 {@link AttendanceShiftRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新班次")
public class AttendanceShiftUpdateRequest extends AttendanceShiftRequest {

    @NotNull(message = "班次标识不能为空")
    @Schema(description = "班次id")
    private Long id;

    @Override
    @NotBlank(message = "班次名称不能为空")
    public String getName() {
        return super.getName();
    }

    @Override
    @NotNull(message = "上班时刻不能为空")
    public LocalTime getStartTime() {
        return super.getStartTime();
    }

    @Override
    @NotNull(message = "下班时刻不能为空")
    public LocalTime getEndTime() {
        return super.getEndTime();
    }

    @Override
    @NotNull(message = "宽限分钟不能为空")
    public Integer getGraceMinutes() {
        return super.getGraceMinutes();
    }

    public AttendanceShift toEntity() {
        AttendanceShift shift = new AttendanceShift();
        shift.setId(id);
        fill(shift);
        return shift;
    }
}
