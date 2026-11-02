package com.atguigu.vo.system;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "分配角色")
@Data
public class AssginRoleVo {

    @NotNull(message = "用户标识不能为空")
    @Schema(description = "用户id")
    private Long userId;

    /** 为空表示清空该用户的角色，所以不是必填（ADR 0034）。 */
    @Schema(description = "角色id列表")
    private List<Long> roleIdList;

}
