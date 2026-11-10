package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 月度统计的请假分列（#283，ADR 0073 §统计口径）：一个假期类型一行，天数按
 * 逐日推导的请假状态汇总（整天粒度，休假日/休息日不计）。
 */
@Data
@Schema(description = "月度统计的请假分列")
public class AttendanceLeaveStatVo {

    @Schema(description = "假期类型名称")
    private String leaveTypeName;

    @Schema(description = "当月请假天数")
    private Integer days;
}
