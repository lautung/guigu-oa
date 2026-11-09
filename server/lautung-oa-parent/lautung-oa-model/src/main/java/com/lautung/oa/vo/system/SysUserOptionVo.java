package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 启用用户选项（#286，ADR 0069）：部门负责人选择器等引用用户的下拉用。
 * 只带 id/用户名/姓名三个字段，不携带凭据与其他资料列。
 */
@Data
@Schema(description = "启用用户选项")
public class SysUserOptionVo {

    @Schema(description = "用户id")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    public SysUserOptionVo(Long id, String username, String name) {
        this.id = id;
        this.username = username;
        this.name = name;
    }
}
