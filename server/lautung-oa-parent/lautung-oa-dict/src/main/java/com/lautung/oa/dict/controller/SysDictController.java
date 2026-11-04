package com.lautung.oa.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.dict.service.SysDictService;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.SysDictData;
import com.lautung.oa.model.system.SysDictType;
import com.lautung.oa.vo.system.SysDictDataQueryVo;
import com.lautung.oa.vo.system.SysDictDataSaveRequest;
import com.lautung.oa.vo.system.SysDictDataUpdateRequest;
import com.lautung.oa.vo.system.SysDictTypeQueryVo;
import com.lautung.oa.vo.system.SysDictTypeSaveRequest;
import com.lautung.oa.vo.system.SysDictTypeUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典管理（#251，ADR 0062）：左类型、右数据两级。
 *
 * <p>管理读走 {@code /system/dict} 页面授予；写动作各要 {@code system:dict:add|edit|remove}
 * 按钮点。标签读（单类型与批量）登录即有、不设页面授予——字典值不是授权来源，
 * 公告/日志等页面按值渲染标签不需要字典管理权限。
 */
@Tag(name = "字典管理接口")
@RestController
@RequestMapping("/admin/system/dict")
public class SysDictController {

    @Autowired
    private SysDictService sysDictService;

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict')")
    @Operation(summary = "字典类型分页查询")
    @GetMapping("type/{page}/{limit}")
    public Result pageQueryType(@PathVariable Long page,
                                @PathVariable Long limit,
                                SysDictTypeQueryVo queryVo) {
        Page<SysDictType> pageParam = PageWindow.of(page, limit);
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryVo.getDictName())) {
            wrapper.like(SysDictType::getDictName, queryVo.getDictName());
        }
        if (StringUtils.hasText(queryVo.getDictType())) {
            wrapper.like(SysDictType::getDictType, queryVo.getDictType());
        }
        if (queryVo.getStatus() != null) {
            wrapper.eq(SysDictType::getStatus, queryVo.getStatus());
        }
        wrapper.orderByAsc(SysDictType::getId);
        IPage<SysDictType> pageModel = sysDictService.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:add')")
    @Operation(summary = "新增字典类型")
    @OperLog(title = "字典·新增类型", businessType = BusinessType.CREATE)
    @PostMapping("type/save")
    public Result saveType(@Valid @RequestBody SysDictTypeSaveRequest request) {
        sysDictService.saveType(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:edit')")
    @Operation(summary = "修改字典类型")
    @OperLog(title = "字典·修改类型", businessType = BusinessType.UPDATE)
    @PutMapping("type/update")
    public Result updateType(@Valid @RequestBody SysDictTypeUpdateRequest request) {
        sysDictService.updateType(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:remove')")
    @Operation(summary = "删除字典类型")
    @OperLog(title = "字典·删除类型", businessType = BusinessType.DELETE)
    @DeleteMapping("type/remove/{id}")
    public Result removeType(@PathVariable Long id) {
        sysDictService.removeType(id);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict')")
    @Operation(summary = "字典数据分页查询")
    @GetMapping("data/{page}/{limit}")
    public Result pageQueryData(@PathVariable Long page,
                                @PathVariable Long limit,
                                SysDictDataQueryVo queryVo) {
        Page<SysDictData> pageParam = PageWindow.of(page, limit);
        if (!StringUtils.hasText(queryVo.getDictType())) {
            // 未选类型时返回空页，不把全表倒给页面。
            return Result.ok(pageParam);
        }
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, queryVo.getDictType().trim());
        if (StringUtils.hasText(queryVo.getDictLabel())) {
            wrapper.like(SysDictData::getDictLabel, queryVo.getDictLabel());
        }
        if (queryVo.getStatus() != null) {
            wrapper.eq(SysDictData::getStatus, queryVo.getStatus());
        }
        wrapper.orderByAsc(SysDictData::getSortValue).orderByAsc(SysDictData::getId);
        IPage<SysDictData> pageModel = sysDictService.pageData(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:add')")
    @Operation(summary = "新增字典数据")
    @OperLog(title = "字典·新增数据", businessType = BusinessType.CREATE)
    @PostMapping("data/save")
    public Result saveData(@Valid @RequestBody SysDictDataSaveRequest request) {
        sysDictService.saveData(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:edit')")
    @Operation(summary = "修改字典数据")
    @OperLog(title = "字典·修改数据", businessType = BusinessType.UPDATE)
    @PutMapping("data/update")
    public Result updateData(@Valid @RequestBody SysDictDataUpdateRequest request) {
        sysDictService.updateData(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:remove')")
    @Operation(summary = "删除字典数据")
    @OperLog(title = "字典·删除数据", businessType = BusinessType.DELETE)
    @DeleteMapping("data/remove/{id}")
    public Result removeData(@PathVariable Long id) {
        sysDictService.removeData(id);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/dict','system:dict:edit')")
    @Operation(summary = "手动刷新字典缓存")
    @OperLog(title = "字典·刷新缓存", businessType = BusinessType.CLEAR)
    @PostMapping("refresh")
    public Result refresh() {
        return Result.ok(sysDictService.refreshCache());
    }
}
