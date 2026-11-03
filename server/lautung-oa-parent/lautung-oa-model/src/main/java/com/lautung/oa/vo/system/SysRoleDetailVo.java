package com.lautung.oa.vo.system;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 角色编辑回读（#225）：实体字段 + {@code dataScope=2} 时的自定部门集合。
 * 自定部门行在 {@code sys_role_dept}，不是角色实体列，单独用 VO 返回，避免把
 * {@code deptIds} 带进写请求的实体落库路径。
 */
@Data
@Schema(description = "角色详情")
public class SysRoleDetailVo {

    @Schema(description = "角色id")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "数据范围：1 全部 / 2 自定部门 / 3 本部门 / 4 本部门及以下 / 5 仅本人")
    private Integer dataScope;

    @Schema(description = "自定部门id列表")
    private List<Long> deptIds;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
