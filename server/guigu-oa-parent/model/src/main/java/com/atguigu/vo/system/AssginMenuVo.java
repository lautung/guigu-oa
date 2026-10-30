package com.atguigu.vo.system;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "分配菜单")
@Data
public class AssginMenuVo {

    @NotNull(message = "角色标识不能为空")
    @Schema(description = "角色id")
    private Long roleId;

    /** 为空表示清空该角色的菜单授予，所以不是必填（ADR 0034）。 */
    @Schema(description = "菜单id列表")
    private List<Long> menuIdList;

}
