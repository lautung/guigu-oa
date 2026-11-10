package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 后台补卡请求（#281，ADR 0073）：指定成员 + 日期，补上班/下班时间，可只补一槽。
 *
 * <p>补卡 append-only：每个非空槽位追加一条 source=2 流水行，原网页行保留。
 */
@Data
@Schema(description = "补卡请求")
public class AttendanceRepairRequest {

    @Schema(description = "成员用户 id")
    private Long userId;

    @Schema(description = "补卡日期")
    private LocalDate repairDate;

    @Schema(description = "上班时间；只补下班时留空")
    private LocalTime clockInTime;

    @Schema(description = "下班时间；只补上班时留空")
    private LocalTime clockOutTime;
}
