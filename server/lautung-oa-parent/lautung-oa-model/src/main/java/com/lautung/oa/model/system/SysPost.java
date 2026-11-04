package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 岗位（职务标签）：只描述一个人的职务，不产生授权，不参与审批任务分配，
 * 也不套数据范围（#248）。用户与岗位是多对多关系，关联行在 {@link SysUserPost}。
 */
@Data
@Schema(description = "岗位")
@TableName("sys_post")
public class SysPost extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "岗位编码")
    @TableField("post_code")
    private String postCode;

    @Schema(description = "岗位名称")
    @TableField("name")
    private String name;

    @Schema(description = "排序")
    @TableField("sort_value")
    private Integer sortValue;

    @Schema(description = "状态（1：正常 0：停用）")
    @TableField("status")
    private Integer status;
}
