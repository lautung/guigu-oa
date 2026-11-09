package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 考勤组列表行（#278，ADR 0073）：join 班次名称与成员数（展示走 join，不依赖 auth）。
 * 班次被删时 shiftName 为空，由页面回退展示。
 */
@Data
@Schema(description = "考勤组列表行")
public class AttendanceGroupVo {

    @Schema(description = "考勤组id")
    private Long id;

    @Schema(description = "考勤组名称")
    private String name;

    @Schema(description = "绑定的班次 id")
    private Long shiftId;

    @Schema(description = "班次名称（join，班次已删时为空）")
    private String shiftName;

    @Schema(description = "每周工作日，如 1,2,3,4,5")
    private String workdays;

    @Schema(description = "成员数")
    private Long memberCount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;
}
