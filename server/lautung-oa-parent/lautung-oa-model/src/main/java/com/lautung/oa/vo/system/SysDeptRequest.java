package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDept;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * SysDept 写请求的公共核心（ADR 0047）：Save/Update 两半注解一致的字段与落库填充。
 * 必填性注解（parentId/name）是 ADR 0034 两半语义，留在各壳。
 */
@Data
public abstract class SysDeptRequest {

    @Schema(description = "负责人用户id（ADR 0065：部门表单选启用用户，不写姓名文本）")
    private Long leaderUserId;

    @Schema(description = "排序")
    private Integer sortValue;

    @Schema(description = "状态(0:停用,1:正常)")
    private Integer status;

    /** 填充公共字段；壳的 toEntity 设完自己的字段后调用。ancestors 由 service 按父级路径算。 */
    protected void fill(SysDept dept) {
        dept.setLeaderUserId(leaderUserId);
        dept.setSortValue(sortValue);
        dept.setStatus(status);
    }
}
