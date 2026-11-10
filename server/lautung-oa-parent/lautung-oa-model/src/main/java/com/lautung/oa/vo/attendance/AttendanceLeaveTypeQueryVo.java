package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 假期类型查询条件（#280）：名称包含匹配与状态。 */
@Data
@Schema(description = "假期类型查询条件")
public class AttendanceLeaveTypeQueryVo {

    @Schema(description = "类型名称，包含匹配")
    private String name;

    @Schema(description = "状态（1 启用 / 0 停用）")
    private Integer status;
}
