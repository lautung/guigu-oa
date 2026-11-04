package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 组织部门（ADR 0056）：{@code ancestors} 存含自身的包裹逗号路径，如部门 5 在 3 下为
 * {@code ,0,3,5,}，根为 {@code ,0,1,}。数据范围「本部门及以下」按该路径解析（ADR 0057）。
 */
@Data
@Schema(description = "部门")
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "上级部门id")
    @TableField("parent_id")
    private Long parentId;

    @Schema(description = "祖级路径（含自身，包裹逗号）")
    @TableField("ancestors")
    private String ancestors;

    @Schema(description = "部门名称")
    @TableField("name")
    private String name;

    @Schema(description = "负责人")
    @TableField("leader")
    private String leader;

    @Schema(description = "排序")
    @TableField("sort_value")
    private Integer sortValue;

    @Schema(description = "状态(0:停用,1:正常)")
    @TableField("status")
    private Integer status;

    /** 树列表的下级节点（#247）；只在读侧装配，不落库。 */
    @TableField(exist = false)
    private List<SysDept> children;
}
