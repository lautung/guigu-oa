package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceShift;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

/**
 * 新增班次的请求体（#278，ADR 0034）：save 只创建、不带主键；宽限分钟缺省 0。
 * 公共字段与时刻格式在 {@link AttendanceShiftRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增班次")
public class AttendanceShiftSaveRequest extends AttendanceShiftRequest {

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

    public AttendanceShift toEntity() {
        AttendanceShift shift = new AttendanceShift();
        fill(shift);
        if (shift.getGraceMinutes() == null) {
            shift.setGraceMinutes(0);
        }
        return shift;
    }
}
