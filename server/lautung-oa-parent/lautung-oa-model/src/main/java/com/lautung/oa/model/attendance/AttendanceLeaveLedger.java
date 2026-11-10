package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 假期余额流水（#280，ADR 0073）：一行一流水，{@code change_hours} 签好数值，
 * 余额 = Σ(change_hours)。
 *
 * <p>发放与调整行 append-only：不提供更新/删除入口，发错用负数行冲抵。
 * 扣减行（审批通过回调）恒负，加班入账行恒正；回调写入时业务键指向单据。
 * 数据范围「本人」列是 {@code user_id}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "假期余额流水")
@TableName("attendance_leave_ledger")
public class AttendanceLeaveLedger extends BaseEntity {

    public static final int ENTRY_GRANT = 1;
    public static final int ENTRY_DEDUCT = 2;
    public static final int ENTRY_OVERTIME_CREDIT = 3;

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户 id（sys_user.id）")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "假期类型 id")
    @TableField("leave_type_id")
    private Long leaveTypeId;

    @Schema(description = "条目类型（1 发放 / 2 扣减 / 3 加班入账）")
    @TableField("entry_type")
    private Integer entryType;

    @Schema(description = "变动小时数（签好数值；扣减恒负、入账恒正）")
    @TableField("change_hours")
    private BigDecimal changeHours;

    @Schema(description = "业务类型（attendance.leave / attendance.overtime）")
    @TableField("business_type")
    private String businessType;

    @Schema(description = "业务单据 id")
    @TableField("business_id")
    private Long businessId;

    @Schema(description = "发放操作人；回调写入为空")
    @TableField("operator_user_id")
    private Long operatorUserId;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
