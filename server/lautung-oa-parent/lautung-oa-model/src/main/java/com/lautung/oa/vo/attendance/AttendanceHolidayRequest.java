package com.lautung.oa.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lautung.oa.model.attendance.AttendanceHoliday;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 节假日写请求的公共核心（#278，ADR 0034/0073）：Save/Update 两半一致的字段与落库填充。
 * 日期/名称/类型的必填性由各壳在自己的 getter 上声明；类型值域（1 休假日 / 2 补班日）
 * 与日期唯一在 service 校验。
 */
@Data
public abstract class AttendanceHolidayRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "节假日日期")
    private LocalDate holidayDate;

    @Size(max = 50, message = "节假日名称过长")
    @Schema(description = "名称")
    private String name;

    @Schema(description = "类型（1 休假日 / 2 补班日）")
    private Integer dayType;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(AttendanceHoliday holiday) {
        holiday.setHolidayDate(holidayDate);
        holiday.setName(name);
        holiday.setDayType(dayType);
    }
}
