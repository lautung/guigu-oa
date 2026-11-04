package com.lautung.oa.vo.system;

import com.lautung.oa.model.system.SysDept;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增部门的请求体（ADR 0034/0036）：上级、名称由 HTTP 边界拦空，其余为空即缺省。
 * ancestors 不由客户端提供，由 service 按父级路径重算（#247）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增部门")
public class SysDeptSaveRequest extends SysDeptRequest {

    @NotNull(message = "上级部门不能为空")
    @Schema(description = "上级部门id，0 表示顶层")
    private Long parentId;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称过长")
    @Schema(description = "部门名称")
    private String name;

    public SysDept toEntity() {
        SysDept dept = new SysDept();
        dept.setParentId(parentId);
        dept.setName(name);
        fill(dept);
        return dept;
    }
}
