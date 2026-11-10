package com.lautung.oa.vo.attendance;

import com.lautung.oa.vo.process.BusinessApprovalSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 请假单行/详情（#282，ADR 0067 读模型）：列表与详情共用单据列；
 * 详情额外拼最近一条审批摘要（单据表不存 process_id）。
 */
@Data
@Schema(description = "请假单")
public class AttendanceLeaveVo {

    @Schema(description = "请假单 id（单号即 id）")
    private Long id;

    @Schema(description = "假期类型 id")
    private Long leaveTypeId;

    @Schema(description = "假期类型名称")
    private String leaveTypeName;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "天数（提交时定稿）")
    private Integer days;

    @Schema(description = "小时数（提交时定稿）")
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
