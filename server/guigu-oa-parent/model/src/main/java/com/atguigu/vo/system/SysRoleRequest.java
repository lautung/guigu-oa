package com.atguigu.vo.system;

import com.atguigu.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SysRole 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * roleName/roleCode 的必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），留在各壳。
 */
@Data
public abstract class SysRoleRequest {

    @Size(max = 255, message = "角色描述过长")
    @Schema(description = "描述")
    private String description;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysRole role) {
        role.setDescription(description);
    }
}
