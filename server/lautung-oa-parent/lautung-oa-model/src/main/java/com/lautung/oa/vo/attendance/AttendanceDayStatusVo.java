package com.lautung.oa.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 逐日推导结果（#281，ADR 0073）：后台下钻与办事考勤记录页共用。
 *
 * <p>{@code status} 取 {@link com.lautung.oa.model.attendance.AttendanceDayStatus}
 * 的九枚举之一；未到下班时刻且缺槽位时为空且 {@code pending=true}（「未打卡」）；
 * 未来日期与未加入考勤组也为空。{@code clockInTime}/{@code clockOutTime} 是槽位
 * 有效时刻：有补卡行取最新一条补卡行，否则上班最早网页行/下班最晚网页行。
 */
@Data
@Schema(description = "逐日考勤状态")
public class AttendanceDayStatusVo {

    @Schema(description = "日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Schema(description = "星期（1 周一 … 7 周日）")
    private Integer weekday;

    @Schema(description = "日类型（0 未分组 / 1 工作日 / 2 休息日 / 3 休假日 / 4 补班日）")
    private Integer dayType;

    @Schema(description = "是否工作日（工作日公式：组工作日且非休假日，或补班日）")
    private Boolean workday;

    @Schema(description = "是否已加入考勤组")
    private Boolean hasGroup;

    @Schema(description = "考勤组 id")
    private Long groupId;

    @Schema(description = "考勤组名称")
    private String groupName;

    @Schema(description = "班次摘要；未分组或班次缺失时为空")
    private AttendanceShiftVo shift;

    @Schema(description = "九枚举日状态；未打卡/未来/未分组时为空")
    private Integer status;

    @Schema(description = "请假类型名称；status=请假时有值")
    private String leaveTypeName;

    @Schema(description = "是否「未打卡」（工作日未到下班时刻且缺槽位）")
    private Boolean pending;

    @Schema(description = "上班卡有效时刻；未打卡为空")
    private LocalTime clockInTime;

    @Schema(description = "下班卡有效时刻；未打卡为空")
    private LocalTime clockOutTime;
}
