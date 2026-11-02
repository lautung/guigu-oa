package com.lautung.oa.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.audit.entity.SysOperLog;
import com.lautung.oa.audit.service.SysOperLogService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.datascope.DataScopeProvider;
import com.lautung.oa.vo.system.SysOperLogQueryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志后台接口（ADR 0054，#214）：分页、详情、按 id 批量删除、清空。
 *
 * <p>页面授予 {@code /system/log/operLog} 覆盖读；写动作再要按钮点。数据范围由
 * {@link DataScopeProvider} 解析（实现由 auth 的解析器供给），本模块不读角色与部门表。
 * 删除/清空本身是后台写动作，也要记一行操作日志；清空行在方法返回后由切面写入，所以
 * 它不会被同一次清空删掉。
 */
@Tag(name = "操作日志接口")
@RestController
@RequestMapping("/admin/system/sysOperLog")
public class SysOperLogController {

    @Autowired
    private SysOperLogService service;

    @Autowired
    private DataScopeProvider dataScopeProvider;

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/operLog')")
    @Operation(summary = "操作日志分页查询")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit,
                        SysOperLogQueryVo queryVo) {
        Page<SysOperLog> pageParam = PageWindow.of(page, limit);
        DataScope scope = dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
        return Result.ok(service.page(pageParam, queryVo, scope));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/operLog')")
    @Operation(summary = "操作日志详情")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id) {
        DataScope scope = dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
        return Result.ok(service.get(id, scope));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/operLog','system:operLog:remove')")
    @Operation(summary = "删除操作日志")
    @OperLog(title = "操作日志·删除", businessType = BusinessType.DELETE)
    @DeleteMapping("remove")
    public Result remove(@RequestBody List<Long> idList) {
        boolean removed = service.removeByIds(idList);
        return removed ? Result.ok() : Result.fail();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/operLog','system:operLog:clean')")
    @Operation(summary = "清空操作日志")
    @OperLog(title = "操作日志·清空", businessType = BusinessType.CLEAR)
    @DeleteMapping("clean")
    public Result clean() {
        service.clean();
        return Result.ok();
    }
}
