package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceHoliday;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新节假日的请求体（#278，ADR 0034）：只要求主键，其余字段为空即不覆盖。
 * 公共字段与日期格式在 {@link AttendanceHolidayRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新节假日")
public class AttendanceHolidayUpdateRequest extends AttendanceHolidayRequest {

    @NotNull(message = "节假日标识不能为空")
    @Schema(description = "节假日id")
    private Long id;

    public AttendanceHoliday toEntity() {
        AttendanceHoliday holiday = new AttendanceHoliday();
        holiday.setId(id);
        fill(holiday);
        return holiday;
    }
}
