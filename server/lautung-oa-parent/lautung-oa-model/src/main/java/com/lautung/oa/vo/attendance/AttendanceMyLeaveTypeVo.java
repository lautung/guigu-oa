package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 办事请假类型选项（#282）：只列启用类型；需要余额的类型带本人可用余额
 * （可用 = 发放 + 入账 − 扣减 − 在途，与「考勤记录」页同口径）。
 */
@Data
@Schema(description = "我的请假类型")
public class AttendanceMyLeaveTypeVo {

    @Schema(description = "假期类型 id")
    private Long id;

    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "是否需要余额（1 是 / 0 否）")
    private Integer balanceRequired;

    @Schema(description = "本人可用余额（小时；不需要余额的类型为 null）")
    private BigDecimal availableHours;
}
