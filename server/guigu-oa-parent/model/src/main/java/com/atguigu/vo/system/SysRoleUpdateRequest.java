package com.atguigu.vo.system;

import com.atguigu.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新角色的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）。
 */
@Data
@Schema(description = "更新角色")
public class SysRoleUpdateRequest {

    @NotNull(message = "角色标识不能为空")
    @Schema(description = "角色id")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "描述")
    private String description;

    public SysRole toEntity() {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setDescription(description);
        return role;
    }
}
