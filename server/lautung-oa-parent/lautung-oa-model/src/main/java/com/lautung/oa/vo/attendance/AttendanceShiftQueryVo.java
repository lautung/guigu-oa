package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 班次列表查询条件（#278）。 */
@Data
@Schema(description = "班次查询条件")
public class AttendanceShiftQueryVo {

    @Schema(description = "班次名称，包含匹配")
    private String name;
}
