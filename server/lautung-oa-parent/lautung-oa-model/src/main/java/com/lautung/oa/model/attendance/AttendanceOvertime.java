package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/**
 * 加班单（#282，ADR 0073）：日期 + 起止时刻 + 事由，小时数由服务端计算。
 *
 * <p>结束必须晚于开始且同一天内（跨天拆两天提交）；允许休息日加班，
 * 不与打卡记录交叉校验、不做重叠校验。审批通过回调向调休类型写加班入账流水。
 * 状态档与请假单相同（{@link AttendanceApplyStatus}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "加班单")
@TableName("attendance_overtime")
public class AttendanceOvertime extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请人 id（sys_user.id）")
    @TableField("applicant_id")
    private Long applicantId;

    @Schema(description = "加班日期")
    @TableField("overtime_date")
    private LocalDate overtimeDate;

    @Schema(description = "开始时刻")
    @TableField("start_time")
    private LocalTime startTime;

    @Schema(description = "结束时刻（晚于开始，同一天内）")
    @TableField("end_time")
    private LocalTime endTime;

    @Schema(description = "小时数（服务端计算）")
    @TableField("hours")
    private BigDecimal hours;

    @Schema(description = "事由（提交时必填）")
    @TableField("reason")
    private String reason;

    @Schema(description = "状态（0 草稿 / 1 审批中 / 2 通过 / -1 拒绝 / -2 撤回）")
    @TableField("status")
    private Integer status;

    @Schema(description = "提交时间")
    @TableField("submit_time")
    private Date submitTime;

    @Schema(description = "终态时间")
    @TableField("finish_time")
    private Date finishTime;
}
