package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色侧分配用户（#256）：{@code userIds} 整体替换该角色的成员集合。
 *
 * <p>为空表示清空成员，所以不是必填（与用户侧 {@code AssginRoleVo.roleIdList} 同一语义，ADR 0034）。
 */
@Schema(description = "角色分配用户")
@Data
public class AssignRoleUsersVo {

    @NotNull(message = "角色标识不能为空")
    @Schema(description = "角色id")
    private Long roleId;

    /** 为空表示清空该角色的成员。 */
    @Schema(description = "用户id列表")
    private List<Long> userIds;
}
