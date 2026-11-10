package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发放/调整请求（#280，ADR 0073）：一次选多人，每人一行 {@code change_hours}；
 * 正数发放、负数调整冲抵，零是空操作（service 201 拒绝）。
 */
@Data
@Schema(description = "假期发放/调整")
public class AttendanceLedgerGrantRequest {

    @NotNull(message = "假期类型不能为空")
    @Schema(description = "假期类型 id（必须需要余额且启用）")
    private Long leaveTypeId;

    @NotEmpty(message = "发放成员不能为空")
    @Schema(description = "发放成员 id 列表")
    private List<Long> userIds;

    @NotNull(message = "调整小时数不能为空")
    @Digits(integer = 8, fraction = 2, message = "调整小时数最多两位小数")
    @Schema(description = "每人变动小时数（正发放 / 负调整）")
    private BigDecimal changeHours;

    @Size(max = 255, message = "备注过长")
    @Schema(description = "备注")
    private String remark;
}
