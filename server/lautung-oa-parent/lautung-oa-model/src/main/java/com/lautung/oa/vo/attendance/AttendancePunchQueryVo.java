package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 后台打卡记录查询条件（#279）：按人（姓名/用户名包含）、日期区间与来源筛选。 */
@Data
@Schema(description = "打卡记录查询条件")
public class AttendancePunchQueryVo {

    @Schema(description = "姓名或用户名，包含匹配")
    private String name;

    @Schema(description = "开始日期（含）")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate beginDate;

    @Schema(description = "结束日期（含）")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Schema(description = "来源（1 网页打卡 / 2 补卡）")
    private Integer source;
}
