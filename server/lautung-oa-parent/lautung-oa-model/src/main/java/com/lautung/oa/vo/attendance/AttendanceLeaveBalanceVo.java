package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 本人某假期类型的余额（#280，ADR 0073）：可用 = 发放 + 入账 − 扣减 − 在途。
 * 在途只算审批中请假单（status=1），通过后已落扣减流水，不重复扣。
 */
@Data
@Schema(description = "假期余额")
public class AttendanceLeaveBalanceVo {

    @Schema(description = "假期类型 id")
    private Long leaveTypeId;

    @Schema(description = "假期类型名称")
    private String leaveTypeName;

    @Schema(description = "状态（1 启用 / 0 停用）")
    private Integer status;

    @Schema(description = "发放合计（含负数调整，签好数值）")
    private BigDecimal grantedHours;

    @Schema(description = "加班入账合计")
    private BigDecimal creditedHours;

    @Schema(description = "审批扣减合计（正数展示）")
    private BigDecimal deductedHours;

    @Schema(description = "在途合计（审批中请假单小时数）")
    private BigDecimal pendingHours;

    @Schema(description = "可用余额 = 发放 + 入账 − 扣减 − 在途")
    private BigDecimal availableHours;
}
