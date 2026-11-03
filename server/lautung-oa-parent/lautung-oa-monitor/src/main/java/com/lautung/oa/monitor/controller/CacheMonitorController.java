package com.lautung.oa.monitor.controller;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.monitor.service.CacheMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 缓存监控与缓存列表后台接口（#237，ADR 0060）。
 *
 * <p>缓存监控页只读（页面授予覆盖读，无按钮点）；缓存列表页的读走页面授予，
 * 两个清理动作（单键删除 / 清空权限缓存）共用 {@code monitor:cache:clean} 按钮点。
 */
@Tag(name = "缓存监控接口")
@RestController
@RequestMapping("/admin/monitor/cache")
public class CacheMonitorController {

    private final CacheMonitorService cacheMonitorService;

    public CacheMonitorController(CacheMonitorService cacheMonitorService) {
        this.cacheMonitorService = cacheMonitorService;
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/cache')")
    @Operation(summary = "Redis INFO 五段快照")
    @GetMapping
    public Result info() {
        return Result.ok(cacheMonitorService.info());
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/cacheList')")
    @Operation(summary = "按白名单前缀分页浏览缓存键")
    @GetMapping("/keys")
    public Result keys(@RequestParam(required = false) String prefix,
                       @RequestParam(defaultValue = "0") long cursor,
                       @RequestParam(defaultValue = "100") long count) {
        return Result.ok(cacheMonitorService.keys(prefix, cursor, count));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/cacheList','monitor:cache:clean')")
    @Operation(summary = "删除一个 perm: 前缀的缓存键")
    @OperLog(title = "缓存列表·删除键", businessType = BusinessType.DELETE)
    @DeleteMapping("/keys")
    public Result deleteKey(@RequestParam(required = false) String key) {
        cacheMonitorService.deleteKey(key);
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/cacheList','monitor:cache:clean')")
    @Operation(summary = "清空全部权限缓存键")
    @OperLog(title = "缓存列表·清空权限缓存", businessType = BusinessType.CLEAR)
    @DeleteMapping("/perm")
    public Result clearPerm() {
        return Result.ok(cacheMonitorService.clearPermissionCache());
    }
}
