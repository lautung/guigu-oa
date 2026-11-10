package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 假期类型（#280，ADR 0073）：独立配置表，不是字典——字典装不下「是否需要余额」
 * 与「是否加班入账目标」这类行为配置。
 *
 * <p>被流水与单据引用后只能停用不删除；加班入账目标全局唯一（service 校验，
 * 更新时排除自身）。预置五项由 V139 种入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "假期类型")
@TableName("attendance_leave_type")
public class AttendanceLeaveType extends BaseEntity {

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ENABLED = 1;

    private static final long serialVersionUID = 1L;

    @Schema(description = "类型名称")
    @TableField("name")
    private String name;

    @Schema(description = "是否需要余额（1 是 / 0 否）")
    @TableField("balance_required")
    private Integer balanceRequired;

    @Schema(description = "是否加班入账目标（1 是 / 0 否，全局唯一）")
    @TableField("overtime_credit")
    private Integer overtimeCredit;

    @Schema(description = "状态（1 启用 / 0 停用）")
    @TableField("status")
    private Integer status;

    @Schema(description = "排序")
    @TableField("sort_no")
    private Integer sortNo;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
