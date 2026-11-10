package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 补卡页的成员候选（#281）：启用账号，按数据范围过滤。 */
@Data
@Schema(description = "补卡成员候选")
public class AttendanceRepairUserVo {

    @Schema(description = "用户 id")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "当前部门 id")
    private Long deptId;

    @Schema(description = "当前部门名称")
    private String deptName;
}
