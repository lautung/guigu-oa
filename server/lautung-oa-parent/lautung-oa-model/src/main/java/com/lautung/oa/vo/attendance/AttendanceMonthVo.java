package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 办事「考勤记录」页的当月逐日状态（#281，ADR 0073）。余额区归 #280。 */
@Data
@Schema(description = "当月逐日考勤状态")
public class AttendanceMonthVo {

    @Schema(description = "月份（yyyy-MM）")
    private String month;

    @Schema(description = "逐日状态（按日期升序）")
    private List<AttendanceDayStatusVo> days;
}
