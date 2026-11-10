package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 推导用的请假区间行（#281）：只读审批通过的请假单（status=2）。
 *
 * <p>请假单本体归 #282；本票先用夹具行与这张读模型验证优先级。
 */
@Data
@Schema(description = "请假区间")
public class AttendanceLeaveSpanVo {

    @Schema(description = "请假单 id")
    private Long id;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "假期类型名称")
    private String leaveTypeName;
}
