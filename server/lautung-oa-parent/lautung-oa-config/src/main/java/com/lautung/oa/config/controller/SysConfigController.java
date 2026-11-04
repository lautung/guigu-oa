package com.lautung.oa.config.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.config.service.SysConfigService;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.SysConfig;
import com.lautung.oa.vo.system.SysConfigQueryVo;
import com.lautung.oa.vo.system.SysConfigSaveRequest;
import com.lautung.oa.vo.system.SysConfigUpdateRequest;
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
 * 参数设置（#252，ADR 0062）：读走页面授予；新增/修改/删除各要按钮点，
 * 手动刷新缓存复用修改点。内置项保护在 service，不信任请求体。
 */
@Tag(name = "参数设置接口")
@RestController
@RequestMapping("/admin/system/config")
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    @PreAuthorize("@backofficeGate.isAllowed('/system/config')")
    @Operation(summary = "参数分页查询")
    @GetMapping("{page}/{limit}")
    public Result pageQueryConfig(@PathVariable Long page,
                                  @PathVariable Long limit,
                                  SysConfigQueryVo queryVo) {
        Page<SysConfig> pageParam = PageWindow.of(page, limit);
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryVo.getConfigKey())) {
            wrapper.like(SysConfig::getConfigKey, queryVo.getConfigKey());
        }
        if (StringUtils.hasText(queryVo.getConfigName())) {
            wrapper.like(SysConfig::getConfigName, queryVo.getConfigName());
        }
        wrapper.orderByAsc(SysConfig::getId);
        IPage<SysConfig> pageModel = sysConfigService.page(pageParam, wrapper);
        return Result.ok(pageModel);
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/config','system:config:add')")
    @Operation(summary = "新增参数")
    @OperLog(title = "参数·新增", businessType = BusinessType.CREATE)
    @PostMapping("save")
    public Result save(@Valid @RequestBody SysConfigSaveRequest request) {
        sysConfigService.saveConfig(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/config','system:config:edit')")
    @Operation(summary = "修改参数")
    @OperLog(title = "参数·修改", businessType = BusinessType.UPDATE)
    @PutMapping("update")
    public Result update(@Valid @RequestBody SysConfigUpdateRequest request) {
        sysConfigService.updateConfig(request.toEntity());
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/config','system:config:remove')")
    @Operation(summary = "删除参数")
    @OperLog(title = "参数·删除", businessType = BusinessType.DELETE)
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id) {
        sysConfigService.removeConfig(id);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/config','system:config:edit')")
    @Operation(summary = "手动刷新参数缓存")
    @OperLog(title = "参数·刷新缓存", businessType = BusinessType.CLEAR)
    @PostMapping("refresh")
    public Result refresh() {
        return Result.ok(sysConfigService.refreshCache());
    }
}
