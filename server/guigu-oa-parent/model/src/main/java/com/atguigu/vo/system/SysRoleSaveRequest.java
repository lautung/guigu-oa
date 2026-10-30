package com.atguigu.vo.system;

import com.atguigu.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增角色的请求体（ADR 0034）。
 */
@Data
@Schema(description = "新增角色")
public class SysRoleSaveRequest {

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "描述")
    private String description;

    public SysRole toEntity() {
        SysRole role = new SysRole();
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setDescription(description);
        return role;
    }
}
