package com.lautung.oa.monitor.controller;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.monitor.service.MonitorJobService;
import com.lautung.oa.vo.monitor.MonitorJobUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务后台接口（#238，ADR 0060）：页面授予覆盖读（列表与执行记录），
 * 编辑与手动执行各要自己的按钮点。不做新增/删除——任务行由启动登记产生。
 */
@Tag(name = "定时任务接口")
@RestController
@RequestMapping("/admin/monitor/jobs")
public class MonitorJobController {

    private final MonitorJobService monitorJobService;

    public MonitorJobController(MonitorJobService monitorJobService) {
        this.monitorJobService = monitorJobService;
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/job')")
    @Operation(summary = "定时任务列表：可用性 + 最近一次执行摘要")
    @GetMapping
    public Result list() {
        return Result.ok(monitorJobService.list());
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/job')")
    @Operation(summary = "最近 N 条执行记录（缺省 20）")
    @GetMapping("{id}/logs")
    public Result logs(@PathVariable Long id,
                       @RequestParam(required = false) Integer limit) {
        return Result.ok(monitorJobService.logs(id, limit));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/job','monitor:job:edit')")
    @Operation(summary = "编辑 cron / 启停 / 并发 / 备注（null 不覆盖）")
    @OperLog(title = "定时任务·修改", businessType = BusinessType.UPDATE)
    @PutMapping("{id}")
    public Result update(@PathVariable Long id,
                         @Valid @RequestBody MonitorJobUpdateRequest request) {
        monitorJobService.update(id, new MonitorJobService.MonitorJobUpdateCommand(
                request.getCron(), request.getEnabled(), request.getConcurrency(), request.getRemark()));
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/job','monitor:job:run')")
    @Operation(summary = "手动执行：异步触发，立即返回")
    @OperLog(title = "定时任务·手动执行", businessType = BusinessType.OTHER)
    @PostMapping("{id}/run")
    public Result run(@PathVariable Long id) {
        monitorJobService.run(id);
        return Result.ok();
    }
}
