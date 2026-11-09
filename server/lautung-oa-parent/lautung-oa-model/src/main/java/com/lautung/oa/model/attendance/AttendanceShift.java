package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

/**
 * 班次（#278，ADR 0073）：独立的上下班时刻定义，可建多个。
 *
 * <p>时刻是同一天内的 {@link LocalTime}，不支持跨天班；下班必须晚于上班（service 校验）。
 * 午休只用于展示与日工时折算，不参与迟到/早退判定；宽限分钟默认 0。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "考勤班次")
@TableName("attendance_shift")
public class AttendanceShift extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "班次名称")
    @TableField("name")
    private String name;

    @Schema(description = "上班时刻")
    @TableField("start_time")
    private LocalTime startTime;

    @Schema(description = "下班时刻，晚于上班时刻，同一天内")
    @TableField("end_time")
    private LocalTime endTime;

    @Schema(description = "午休开始时刻，可选")
    @TableField("lunch_start")
    private LocalTime lunchStart;

    @Schema(description = "午休结束时刻，可选")
    @TableField("lunch_end")
    private LocalTime lunchEnd;

    @Schema(description = "迟到/早退宽限分钟")
    @TableField("grace_minutes")
    private Integer graceMinutes;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
