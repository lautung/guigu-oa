package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 加班单写请求的公共字段（#282，ADR 0073）：日期 + 起止时刻 + 事由，
 * 小时数由服务端计算；结束必须晚于开始且同一天内（跨天拆两天提交）。
 */
@Data
@Schema(description = "加班单请求")
public class AttendanceOvertimeRequest {

    @NotNull(message = "加班日期不能为空")
    @Schema(description = "加班日期")
    private LocalDate overtimeDate;

    @NotNull(message = "开始时刻不能为空")
    @Schema(description = "开始时刻")
    private LocalTime startTime;

    @NotNull(message = "结束时刻不能为空")
    @Schema(description = "结束时刻（晚于开始，同一天内）")
    private LocalTime endTime;

    @Size(max = 255, message = "事由过长")
    @Schema(description = "事由（提交时必填）")
    private String reason;
}
