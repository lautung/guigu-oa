package com.lautung.oa.model.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 角色自定数据范围（ADR 0057）：{@code sys_role.data_scope=2} 时可见的部门集合。
 * 被软删的部门不进入可见集——按当前树解析，不做历史快照。
 */
@Data
@Schema(description = "角色部门")
@TableName("sys_role_dept")
public class SysRoleDept extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "角色id")
    @TableField("role_id")
    private Long roleId;

    @Schema(description = "部门id")
    @TableField("dept_id")
    private Long deptId;
}
