package com.lautung.oa.auth.controller;

import com.lautung.oa.auth.service.SysDeptService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.vo.system.SysDeptSaveRequest;
import com.lautung.oa.vo.system.SysDeptUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门维护（#247）：树列表 + 新增/修改/删除。读只判页面授予，写再加按钮点（ADR 0056）；
 * 部门不参与授权，登记者与用户归属仍只认 {@code sys_user.dept_id}。
 */
@Tag(name = "部门管理接口")
@RestController
@RequestMapping("/admin/system/sysDept")
public class SysDeptController {

    @Autowired
    private SysDeptService sysDeptService;

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysDept')")
    @Operation(summary = "部门树")
    @GetMapping("findTree")
    public Result findTree() {
        List<SysDept> tree = sysDeptService.findTree();
        return Result.ok(tree);
    }

    /**
     * 部门负责人选择器的启用用户选项（#286，ADR 0069）：只读，只判部门页授予——
     * 部门管理员未必有用户管理页，不能再借用 {@code /admin/system/sysUser} 的读端点。
     */
    @PreAuthorize("@backofficeGate.isAllowed('/system/sysDept')")
    @Operation(summary = "部门负责人选择器用户选项（只读）")
    @GetMapping("userOptions")
    public Result userOptions() {
        return Result.ok(sysDeptService.findEnabledUserOptions());
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysDept','system:dept:add')")
    @Operation(summary = "新增部门")
    @OperLog(title = "部门·新增", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody SysDeptSaveRequest request) {
        sysDeptService.saveDept(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysDept','system:dept:edit')")
    @Operation(summary = "修改部门")
    @OperLog(title = "部门·修改", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody SysDeptUpdateRequest request) {
        sysDeptService.updateDept(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysDept','system:dept:remove')")
    @Operation(summary = "删除部门")
    @OperLog(title = "部门·删除", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        sysDeptService.removeDept(id);
        return Result.ok();
    }
}
