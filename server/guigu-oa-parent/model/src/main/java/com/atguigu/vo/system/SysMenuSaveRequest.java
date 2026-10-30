package com.atguigu.vo.system;

import com.atguigu.model.system.SysMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新增菜单的请求体。上级、名称、类型、路由地址由 HTTP 边界拦空；其余沿用配置 gate 的判定（ADR 0034）。
 */
@Data
@Schema(description = "新增菜单")
public class SysMenuSaveRequest {

    @NotNull(message = "上级菜单不能为空")
    @Schema(description = "所属上级，0 表示顶层")
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Schema(description = "名称")
    private String name;

    @NotNull(message = "菜单类型不能为空")
    @Schema(description = "类型(0:目录,1:菜单)")
    private Integer type;

    @NotBlank(message = "路由地址不能为空")
    @Schema(description = "路由地址")
    private String path;

    @Schema(description = "组件路径")
    private String component;

    @Schema(description = "权限标识")
    private String perms;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序")
    private Integer sortValue;

    @Schema(description = "状态(0:禁止,1:正常)")
    private Integer status;

    public SysMenu toEntity() {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPath(path);
        menu.setComponent(component);
        menu.setPerms(perms);
        menu.setIcon(icon);
        menu.setSortValue(sortValue);
        menu.setStatus(status);
        return menu;
    }
}
