package com.atguigu.vo.system;

import com.atguigu.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增角色的请求体（ADR 0034）。长度上限对齐 sys_role 列宽（ADR 0036）。
 * 公共字段与 fill 在 {@link SysRoleRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增角色")
public class SysRoleSaveRequest extends SysRoleRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 20, message = "角色名称过长")
    @Schema(description = "角色名称")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 20, message = "角色编码过长")
    @Schema(description = "角色编码")
    private String roleCode;

    public SysRole toEntity() {
        SysRole role = new SysRole();
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        fill(role);
        return role;
    }
}
