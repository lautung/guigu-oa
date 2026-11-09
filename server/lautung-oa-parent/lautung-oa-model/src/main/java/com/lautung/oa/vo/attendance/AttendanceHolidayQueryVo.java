package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 节假日列表查询条件（#278）。 */
@Data
@Schema(description = "节假日查询条件")
public class AttendanceHolidayQueryVo {

    @Schema(description = "名称，包含匹配")
    private String name;

    @Schema(description = "类型（1 休假日 / 2 补班日）")
    private Integer dayType;
}
