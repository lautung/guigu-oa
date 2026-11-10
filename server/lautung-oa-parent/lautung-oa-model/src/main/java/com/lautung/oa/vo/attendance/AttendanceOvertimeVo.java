package com.lautung.oa.vo.attendance;

import com.lautung.oa.vo.process.BusinessApprovalSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/**
 * 加班单行/详情（#282，ADR 0067 读模型）：列表与详情共用单据列；
 * 详情额外拼最近一条审批摘要（单据表不存 process_id）。
 */
@Data
@Schema(description = "加班单")
public class AttendanceOvertimeVo {

    @Schema(description = "加班单 id（单号即 id）")
    private Long id;

    @Schema(description = "加班日期")
    private LocalDate overtimeDate;

    @Schema(description = "开始时刻")
    private LocalTime startTime;

    @Schema(description = "结束时刻")
    private LocalTime endTime;

    @Schema(description = "小时数（服务端计算）")
    private BigDecimal hours;

    @Schema(description = "事由")
    private String reason;

    @Schema(description = "状态（0 草稿 / 1 审批中 / 2 通过 / -1 拒绝 / -2 撤回）")
    private Integer status;

    @Schema(description = "提交时间")
    private Date submitTime;

    @Schema(description = "终态时间")
    private Date finishTime;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "最近一条审批摘要（草稿无审批时为 null）")
    private BusinessApprovalSummary approval;
}
