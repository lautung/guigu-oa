package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * SysRole 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * roleName/roleCode 的必填性是 ADR 0034 seam 的两半语义（save 必填 / update 空=不覆盖），留在各壳。
 * dataScope/deptIds 同样沿用空值语义：save 的 dataScope 为空按新角色默认 5；update 为空即不覆盖。
 * 自定部门行只由 service 按最终落库形状同步，不进入角色实体（ADR 0057）。
 */
@Data
public abstract class SysRoleRequest {

    @Size(max = 255, message = "角色描述过长")
    @Schema(description = "描述")
    private String description;

    @Min(value = 1, message = "数据范围只能为 1、2、3、4 或 5")
    @Max(value = 5, message = "数据范围只能为 1、2、3、4 或 5")
    @Schema(description = "数据范围：1 全部 / 2 自定部门 / 3 本部门 / 4 本部门及以下 / 5 仅本人")
    private Integer dataScope;

    @Schema(description = "自定部门id列表（dataScope=2 时生效）")
    private List<Long> deptIds;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysRole role) {
        role.setDescription(description);
        role.setDataScope(dataScope);
    }
}
