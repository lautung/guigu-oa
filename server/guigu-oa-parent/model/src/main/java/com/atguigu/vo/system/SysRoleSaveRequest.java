package com.atguigu.vo.system;

import com.atguigu.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增角色的请求体（ADR 0034）。长度上限对齐 sys_role 列宽（ADR 0036）。
 */
@Data
@Schema(description = "新增角色")
public class SysRoleSaveRequest {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 20, message = "角色名称过长")
    @Schema(description = "角色名称")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 20, message = "角色编码过长")
    @Schema(description = "角色编码")
    private String roleCode;

    @Size(max = 255, message = "角色描述过长")
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
