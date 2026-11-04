package com.lautung.oa.monitor.controller;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.monitor.service.ServerMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务监控后台接口（ADR 0060）：控制器直读 JMX 与 Hikari，返回一份稳定快照。
 * 不代理 actuator、不新增端点暴露；页面授予覆盖读，无按钮点（ADR 0056）。
 */
@Tag(name = "服务监控接口")
@RestController
@RequestMapping("/admin/monitor/server")
public class ServerMonitorController {

    private final ServerMonitorService serverMonitorService;

    public ServerMonitorController(ServerMonitorService serverMonitorService) {
        this.serverMonitorService = serverMonitorService;
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/server')")
    @Operation(summary = "服务监控快照")
    @GetMapping
    public Result index() {
        return Result.ok(serverMonitorService.read());
    }
}
