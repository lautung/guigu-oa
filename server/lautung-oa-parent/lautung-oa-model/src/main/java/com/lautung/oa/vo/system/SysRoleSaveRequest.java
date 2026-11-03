package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增角色的请求体（ADR 0034）。长度上限对齐 sys_role 列宽（ADR 0036）。
 * dataScope 为空按新角色默认值 5（仅本人）落库；自定部门由 service 同步。
 * 公共字段与 fill 在 {@link SysRoleRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增角色")
public class SysRoleSaveRequest extends SysRoleRequest {

    private static final int DEFAULT_DATA_SCOPE = 5;

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
        if (role.getDataScope() == null) {
            role.setDataScope(DEFAULT_DATA_SCOPE);
        }
        return role;
    }
}
