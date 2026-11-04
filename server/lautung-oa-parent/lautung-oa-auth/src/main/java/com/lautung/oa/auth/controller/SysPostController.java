package com.lautung.oa.auth.controller;

import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.SysPost;
import com.lautung.oa.vo.system.SysPostQueryVo;
import com.lautung.oa.vo.system.SysPostSaveRequest;
import com.lautung.oa.vo.system.SysPostUpdateRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 岗位管理（#248）：读走页面授予，三个写动作各要按钮点；删除保护在 service。
 * 岗位只是职务标签，不产生授权、不参与审批分配、不套数据范围。
 */
@Tag(name = "岗位管理接口")
@RestController
@RequestMapping("/admin/system/sysPost")
public class SysPostController {

    @Autowired
    private SysPostService sysPostService;

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysPost')")
    @Operation(summary = "岗位分页查询")
    @GetMapping("{page}/{limit}")
    public Result pageQueryPost(@PathVariable Long page,
                                @PathVariable Long limit,
                                SysPostQueryVo sysPostQueryVo) {
        Page<SysPost> pageParam = PageWindow.of(page, limit);

        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(sysPostQueryVo.getPostCode())) {
            wrapper.like(SysPost::getPostCode, sysPostQueryVo.getPostCode());
        }
        if (StringUtils.hasText(sysPostQueryVo.getName())) {
            wrapper.like(SysPost::getName, sysPostQueryVo.getName());
        }
        if (sysPostQueryVo.getStatus() != null) {
            wrapper.eq(SysPost::getStatus, sysPostQueryVo.getStatus());
        }
        wrapper.orderByAsc(SysPost::getSortValue).orderByAsc(SysPost::getId);

        IPage<SysPost> pageModel = sysPostService.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysPost','system:post:add')")
    @Operation(summary = "新增岗位")
    @OperLog(title = "岗位·新增", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody SysPostSaveRequest request) {
        sysPostService.save(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysPost','system:post:edit')")
    @Operation(summary = "修改岗位")
    @OperLog(title = "岗位·修改", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody SysPostUpdateRequest request) {
        sysPostService.updatePost(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/sysPost','system:post:remove')")
    @Operation(summary = "删除岗位")
    @OperLog(title = "岗位·删除", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        sysPostService.removePost(id);
        return Result.ok();
    }
}
