package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 考勤组列表查询条件（#278）。 */
@Data
@Schema(description = "考勤组查询条件")
public class AttendanceGroupQueryVo {

    @Schema(description = "考勤组名称，包含匹配")
    private String name;
}
