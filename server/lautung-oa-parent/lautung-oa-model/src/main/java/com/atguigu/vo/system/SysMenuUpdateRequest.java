package com.atguigu.vo.system;

import com.atguigu.model.system.SysMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

/**
 * 更新菜单的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）。
 * 类型只收 0/1；类型为菜单时不允许把组件路径清空，未发送（null，保留旧行）的判定在
 * updateMenu 的 service 里——边界看不到旧行，不补默认值也不猜测（ADR 0036）。
 * 公共字段与 fill 在 {@link SysMenuRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新菜单")
public class SysMenuUpdateRequest extends SysMenuRequest {

    @NotNull(message = "菜单标识不能为空")
    @Schema(description = "菜单id")
    private Long id;

    @Schema(description = "所属上级，0 表示顶层")
    private Long parentId;

    @Size(max = 50, message = "菜单名称过长")
    @Schema(description = "名称")
    private String name;

    @Min(value = 0, message = "菜单类型只能为 0 或 1")
    @Max(value = 1, message = "菜单类型只能为 0 或 1")
    @Schema(description = "类型(0:目录,1:菜单)")
    private Integer type;

    @Size(max = 200, message = "路由地址过长")
    @Schema(description = "路由地址")
    private String path;

    /** 显式送来的组件路径不能是空白：空白会按 MyBatis-Plus 的非空策略覆盖旧行。 */
    @AssertTrue(message = "类型为菜单时组件路径不能为空")
    @Schema(hidden = true)
    public boolean isPageComponentFilled() {
        return !Objects.equals(type, 1) || getComponent() == null || !getComponent().isBlank();
    }

    public SysMenu toEntity() {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        menu.setType(type);
        menu.setPath(path);
        fill(menu);
        return menu;
    }
}
