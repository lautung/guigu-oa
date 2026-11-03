package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色数据范围的自定部门选项（#225）：只读，不含 CRUD 语义。
 * 只返回启用且未删除的部门；{@code parentId} 供客户端展示层级。
 */
@Data
@Schema(description = "部门选项")
public class SysDeptOptionVo {

    @Schema(description = "部门id")
    private Long id;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "上级部门id")
    private Long parentId;
}
