package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月度考勤统计行（#283，ADR 0073 §统计口径）：按人汇总当月。
 *
 * <p>应出勤按工作日判定公式逐日计（含补班、剔休假日）；实出勤 =
 * 应出勤 − 请假天数 − 缺卡天数；迟到/早退次数按卡计（缺卡日仍按存在的卡计）；
 * 请假按类型分列；加班时长只认审批通过的加班单（hours 定稿值）。
 * 全部读时推导/聚合，不落统计表。
 */
@Data
@Schema(description = "月度考勤统计行")
public class AttendanceStatVo {

    @Schema(description = "用户 id（sys_user.id）")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "当前部门 id")
    private Long deptId;

    @Schema(description = "当前部门名称")
    private String deptName;

    @Schema(description = "应出勤天数（工作日公式逐日计）")
    private Integer requiredDays;

    @Schema(description = "实出勤天数（= 应出勤 − 请假天数 − 缺卡天数）")
    private Integer actualDays;

    @Schema(description = "迟到次数（按卡计）")
    private Integer lateCount;

    @Schema(description = "早退次数（按卡计）")
    private Integer earlyCount;

    @Schema(description = "缺卡天数")
    private Integer missingDays;

    @Schema(description = "请假按类型分列（天）")
    private List<AttendanceLeaveStatVo> leaveDays;

    @Schema(description = "加班时长（小时，只认审批通过的单据）")
    private BigDecimal overtimeHours;
}
