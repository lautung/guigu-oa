package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SysMenu 写请求的公共核心：持有 Save/Update 两半注解完全一致的字段与落库填充（ADR 0047）。
 * 必填性注解（parentId/name/type/path）是 ADR 0034 seam 的两半语义，留在各壳；
 * {@code isPageComponentFilled()} 两半逻辑互补（save 判必填、update 只拦显式空白），不上收。
 */
@Data
public abstract class SysMenuRequest {

    @Size(max = 200, message = "组件路径过长")
    @Schema(description = "组件路径")
    private String component;

    @Size(max = 100, message = "权限标识过长")
    @Schema(description = "权限标识")
    private String perms;

    @Size(max = 100, message = "图标过长")
    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sortValue;

    @Schema(description = "状态(0:禁止,1:正常)")
    private Integer status;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。 */
    protected void fill(SysMenu menu) {
        menu.setComponent(component);
        menu.setPerms(perms);
        menu.setIcon(icon);
        menu.setSortValue(sortValue);
        menu.setStatus(status);
    }
}
