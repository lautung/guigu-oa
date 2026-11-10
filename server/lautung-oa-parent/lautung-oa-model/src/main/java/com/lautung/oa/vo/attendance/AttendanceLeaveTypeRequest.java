package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceLeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 假期类型写请求的公共核心（#280，ADR 0073/0034）：Save/Update 两半共享字段与落库填充。
 * 名称必填与 id 必填分别在壳上声明；状态缺省启用、两个开关缺省 0、排序缺省 0。
 */
@Data
public abstract class AttendanceLeaveTypeRequest {

    @Size(max = 50, message = "假期类型名称过长")
    @Schema(description = "类型名称")
    private String name;

    @Schema(description = "是否需要余额（1 是 / 0 否）")
    private Integer balanceRequired;

    @Schema(description = "是否加班入账目标（1 是 / 0 否）")
    private Integer overtimeCredit;

    @Schema(description = "状态（1 启用 / 0 停用）")
    private Integer status;

    @PositiveOrZero(message = "排序不能为负数")
    @Schema(description = "排序")
    private Integer sortNo;

    @Size(max = 255, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    /** 完整表单覆盖：名称去首尾空白，空值落到约定缺省。 */
    protected AttendanceLeaveType fill(AttendanceLeaveType type) {
        type.setName(name == null ? null : name.trim());
        type.setBalanceRequired(balanceRequired == null ? 0 : balanceRequired);
        type.setOvertimeCredit(overtimeCredit == null ? 0 : overtimeCredit);
        type.setStatus(status == null ? AttendanceLeaveType.STATUS_ENABLED : status);
        type.setSortNo(sortNo == null ? 0 : sortNo);
        type.setRemark(remark == null ? "" : remark.trim());
        return type;
    }
}
