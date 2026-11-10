package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 发放流水查询条件（#280）：按假期类型与发放日期区间筛选。 */
@Data
@Schema(description = "发放流水查询条件")
public class AttendanceLedgerQueryVo {

    @Schema(description = "假期类型 id")
    private Long leaveTypeId;

    @Schema(description = "开始日期（含，按发放时间自然日）")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    @Schema(description = "结束日期（含，按发放时间自然日）")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}
