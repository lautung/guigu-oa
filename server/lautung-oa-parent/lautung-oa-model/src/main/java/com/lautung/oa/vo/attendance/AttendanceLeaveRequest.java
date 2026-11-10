package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 请假单写请求的公共字段（#282，ADR 0073）：整天粒度，天数/小时数不在请求里，
 * 由服务端提交时按班次与节假日日历定稿。
 */
@Data
@Schema(description = "请假单请求")
public class AttendanceLeaveRequest {

    @NotNull(message = "假期类型不能为空")
    @Schema(description = "假期类型 id（启用类型）")
    private Long leaveTypeId;

    @NotNull(message = "开始日期不能为空")
    @Schema(description = "开始日期")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Size(max = 255, message = "事由过长")
    @Schema(description = "事由（提交时必填）")
    private String reason;
}
