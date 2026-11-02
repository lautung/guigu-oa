package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

/**
 * 新增菜单的请求体。上级、名称、类型由 HTTP 边界拦空；其余沿用配置 gate 的判定（ADR 0034）。
 * 类型收 0/1/2：目录与页面必须有路由地址，菜单页必须自带组件路径（保存时无从回读旧行）；
 * 按钮点必须带权限标识，path/component 为空。按钮的父级是否为页面在 saveMenu 的 service 里判
 * （请求体看不到父行）。公共字段与 fill 在 {@link SysMenuRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增菜单")
public class SysMenuSaveRequest extends SysMenuRequest {

    @NotNull(message = "上级菜单不能为空")
    @Schema(description = "所属上级，0 表示顶层")
    private Long parentId;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称过长")
    @Schema(description = "名称")
    private String name;

    @NotNull(message = "菜单类型不能为空")
    @Min(value = 0, message = "菜单类型只能为 0、1 或 2")
    @Max(value = 2, message = "菜单类型只能为 0、1 或 2")
    @Schema(description = "类型(0:目录,1:菜单,2:按钮)")
    private Integer type;

    @Size(max = 200, message = "路由地址过长")
    @Schema(description = "路由地址")
    private String path;

    @AssertTrue(message = "路由地址不能为空")
    @Schema(hidden = true)
    public boolean isPagePathFilled() {
        return Objects.equals(type, 2) || hasText(path);
    }

    @AssertTrue(message = "按钮的路由地址必须为空")
    @Schema(hidden = true)
    public boolean isButtonPathEmpty() {
        return !Objects.equals(type, 2) || !hasText(path);
    }

    @AssertTrue(message = "按钮的组件路径必须为空")
    @Schema(hidden = true)
    public boolean isButtonComponentEmpty() {
        return !Objects.equals(type, 2) || !hasText(getComponent());
    }

    @AssertTrue(message = "按钮权限标识不能为空")
    @Schema(hidden = true)
    public boolean isButtonPermsFilled() {
        return !Objects.equals(type, 2) || hasText(getPerms());
    }

    @AssertTrue(message = "类型为菜单时组件路径不能为空")
    @Schema(hidden = true)
    public boolean isPageComponentFilled() {
        return !Objects.equals(type, 1) || hasText(getComponent());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public SysMenu toEntity() {
        SysMenu menu = new SysMenu();
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPath(path);
        fill(menu);
        return menu;
    }
}
