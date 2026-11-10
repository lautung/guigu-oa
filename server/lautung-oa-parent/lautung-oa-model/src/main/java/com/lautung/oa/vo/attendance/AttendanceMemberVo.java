package com.lautung.oa.vo.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 考勤组成员行（#278，ADR 0073）：join sys_user 的姓名展示，形状对齐
 * {@code SysUserOptionVo}（username/name）。不携带其他资料列。
 */
@Data
@Schema(description = "考勤组成员行")
public class AttendanceMemberVo {

    @Schema(description = "关系行 id")
    private Long id;

    @Schema(description = "成员用户 id")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;
}
