package com.lautung.oa.vo.attendance;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/** 发放流水行（#280）：join 姓名/部门/类型/操作人，数据范围落在 sys_user 当前部门。 */
@Data
@Schema(description = "发放流水行")
public class AttendanceLedgerVo {

    @Schema(description = "流水行 id")
    private Long id;

    @Schema(description = "用户 id")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "用户当前部门 id")
    private Long deptId;

    @Schema(description = "用户当前部门名称")
    private String deptName;

    @Schema(description = "假期类型 id")
    private Long leaveTypeId;

    @Schema(description = "假期类型名称（停用后历史流水照读）")
    private String leaveTypeName;

    @Schema(description = "条目类型（1 发放 / 2 扣减 / 3 加班入账）")
    private Integer entryType;

    @Schema(description = "变动小时数（签好数值，负数冲抵）")
    private BigDecimal changeHours;

    @Schema(description = "业务类型（attendance.leave / attendance.overtime）")
    private String businessType;

    @Schema(description = "业务单据 id")
    private Long businessId;

    @Schema(description = "发放操作人 id")
    private Long operatorUserId;

    @Schema(description = "发放操作人姓名；回调写入为空")
    private String operatorName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "发放时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
