package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 请假单（#282，ADR 0073）：整天粒度，天数与小时数由服务端在提交时按
 * 当时班次与节假日日历定稿（休假日剔除、补班日照算）。
 *
 * <p>单据表不存 process_id：审批按业务键 {@code attendance.leave} + 本单 id
 * 回查（ADR 0067）。状态档见 {@link AttendanceApplyStatus}；审批中不可删改，
 * 通过后锁定，拒绝/撤回可改后重提（单据号不变）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "请假单")
@TableName("attendance_leave")
public class AttendanceLeave extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请人 id（sys_user.id）")
    @TableField("applicant_id")
    private Long applicantId;

    @Schema(description = "假期类型 id")
    @TableField("leave_type_id")
    private Long leaveTypeId;

    @Schema(description = "开始日期")
    @TableField("start_date")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    @TableField("end_date")
    private LocalDate endDate;

    @Schema(description = "天数（提交时按日历折算定稿）")
    @TableField("days")
    private Integer days;

    @Schema(description = "小时数（提交时按班次日工时定稿）")
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
