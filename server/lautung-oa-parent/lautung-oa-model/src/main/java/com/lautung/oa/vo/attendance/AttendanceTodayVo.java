package com.lautung.oa.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 办事打卡页的今日状态（#279，ADR 0073）：今日班次、工作日判定与两张卡的有效时刻。
 *
 * <p>时刻一律服务器时间；上班卡取最早、下班卡取最晚（有补卡行时取最新补卡行）。
 * 未打卡的槽位为 {@code null}。{@code dayType} 取值见
 * {@link com.lautung.oa.model.attendance.AttendanceDayType}。
 */
@Data
@Schema(description = "今日打卡状态")
public class AttendanceTodayVo {

    @Schema(description = "今日日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Schema(description = "星期（1 周一 … 7 周日）")
    private Integer weekday;

    @Schema(description = "日类型（0 未分组 / 1 工作日 / 2 休息日 / 3 休假日 / 4 补班日）")
    private Integer dayType;

    @Schema(description = "今日是否可打卡")
    private Boolean workday;

    @Schema(description = "是否已加入考勤组")
    private Boolean hasGroup;

    @Schema(description = "考勤组 id")
    private Long groupId;

    @Schema(description = "考勤组名称")
    private String groupName;

    @Schema(description = "今日班次；未分组时为空")
    private AttendanceShiftVo shift;

    @Schema(description = "上班卡有效时刻；未打卡为空")
    private LocalTime clockInTime;

    @Schema(description = "下班卡有效时刻；未打卡为空")
    private LocalTime clockOutTime;
}
