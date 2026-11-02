package com.lautung.oa.process.controller;


import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.model.process.ProcessType;
import com.lautung.oa.process.service.OaProcessTypeService;
import com.lautung.oa.vo.process.ProcessTypeSaveRequest;
import com.lautung.oa.vo.process.ProcessTypeUpdateRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 审批类型 前端控制器
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
@RestController
@RequestMapping(value = "/admin/process/processType")
public class OaProcessTypeController {

    @Autowired
    private OaProcessTypeService processTypeService;

    //查询所有审批分类
    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processType')")
    @GetMapping("findAll")
    public Result findAll() {
        List<ProcessType> list = processTypeService.list();
        return Result.ok(list);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processType')")
    @Operation(summary = "获取分页列表")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit) {
        Page<ProcessType> pageParam = PageWindow.of(page, limit);
        IPage<ProcessType> pageModel = processTypeService.page(pageParam);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processType')")
    @Operation(summary = "获取")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id) {
        ProcessType processType = processTypeService.getById(id);
        return Result.ok(processType);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processType','process:type:add')")
    @Operation(summary = "新增")
    @PostMapping("save")
    public Result save(@Valid @RequestBody ProcessTypeSaveRequest request) {
        processTypeService.save(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processType','process:type:edit')")
    @Operation(summary = "修改")
    @PutMapping("update")
    public Result updateById(@Valid @RequestBody ProcessTypeUpdateRequest request) {
        processTypeService.updateById(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/processSet/processType','process:type:remove')")
    @Operation(summary = "删除")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        processTypeService.removeById(id);
        return Result.ok();
    }
}

