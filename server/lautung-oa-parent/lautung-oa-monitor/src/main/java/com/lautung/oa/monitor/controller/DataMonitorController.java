package com.lautung.oa.monitor.controller;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.monitor.service.DataMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据监控后台接口（ADR 0060）：连接池实时指标 + 慢 SQL 内存环。
 * 采集跟随 {@code lautung.oa.sql.slow-sql.enabled}，页面授予覆盖读，无按钮点（ADR 0056）。
 */
@Tag(name = "数据监控接口")
@RestController
@RequestMapping("/admin/monitor/data")
public class DataMonitorController {

    private final DataMonitorService dataMonitorService;

    public DataMonitorController(DataMonitorService dataMonitorService) {
        this.dataMonitorService = dataMonitorService;
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/data')")
    @Operation(summary = "数据监控快照")
    @GetMapping
    public Result index() {
        return Result.ok(dataMonitorService.read());
    }
}
