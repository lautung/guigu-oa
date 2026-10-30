package com.atguigu.vo.system;

import com.atguigu.model.system.SysMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Objects;

/**
 * 新增菜单的请求体。上级、名称、类型、路由地址由 HTTP 边界拦空；其余沿用配置 gate 的判定（ADR 0034）。
 * 类型只收 0/1（不再借道「只能维护配置菜单」），菜单页必须自带组件路径——保存时无从回读旧行，
 * 这条在请求体上判死；目录转菜单的旧行判定在 updateMenu 的 service 里（ADR 0036）。
 */
@Data
@Schema(description = "新增菜单")
public class SysMenuSaveRequest {

    @NotNull(message = "上级菜单不能为空")
    @Schema(description = "所属上级，0 表示顶层")
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称过长")
    @Schema(description = "名称")
    private String name;

    @NotNull(message = "菜单类型不能为空")
    @Min(value = 0, message = "菜单类型只能为 0 或 1")
    @Max(value = 1, message = "菜单类型只能为 0 或 1")
    @Schema(description = "类型(0:目录,1:菜单)")
    private Integer type;

    @NotBlank(message = "路由地址不能为空")
    @Size(max = 200, message = "路由地址过长")
    @Schema(description = "路由地址")
    private String path;

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

    @AssertTrue(message = "类型为菜单时组件路径不能为空")
    @Schema(hidden = true)
    public boolean isPageComponentFilled() {
        return !Objects.equals(type, 1) || (component != null && !component.isBlank());
    }

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
