package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceHoliday;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 新增节假日的请求体（#278，ADR 0034）：save 只创建、不带主键。
 * 公共字段与日期格式在 {@link AttendanceHolidayRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增节假日")
public class AttendanceHolidaySaveRequest extends AttendanceHolidayRequest {

    @Override
    @NotNull(message = "节假日日期不能为空")
    public LocalDate getHolidayDate() {
        return super.getHolidayDate();
    }

    @Override
    @NotBlank(message = "节假日名称不能为空")
    public String getName() {
        return super.getName();
    }

    @Override
    @NotNull(message = "节假日类型不能为空")
    public Integer getDayType() {
        return super.getDayType();
    }

    public AttendanceHoliday toEntity() {
        AttendanceHoliday holiday = new AttendanceHoliday();
        fill(holiday);
        return holiday;
    }
}
