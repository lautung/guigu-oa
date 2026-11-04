package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDept;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新部门的请求体。只要求主键：其余字段为空即不覆盖（ADR 0034）。
 * 显式送 {@code parentId}（含 0）才移动；父级不存在、指向自己或自己的下级由 service 拒绝，
 * 移动后整棵子树的 ancestors 一并重算（#247）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新部门")
public class SysDeptUpdateRequest extends SysDeptRequest {

    @NotNull(message = "部门标识不能为空")
    @Schema(description = "部门id")
    private Long id;

    @Schema(description = "上级部门id，0 表示顶层")
    private Long parentId;

    @Size(max = 50, message = "部门名称过长")
    @Schema(description = "部门名称")
    private String name;

    public SysDept toEntity() {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setName(name);
        fill(dept);
        return dept;
    }
}
