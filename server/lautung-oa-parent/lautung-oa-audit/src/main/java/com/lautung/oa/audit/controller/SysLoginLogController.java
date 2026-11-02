package com.lautung.oa.audit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.audit.entity.SysLoginLog;
import com.lautung.oa.audit.service.SysLoginLogService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.model.system.DataScope;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.datascope.DataScopeProvider;
import com.lautung.oa.vo.system.SysLoginLogQueryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录日志后台接口（ADR 0054，#216）：分页、按 id 批量删除、清空与手动解锁。
 *
 * <p>登录日志一行即全貌，没有详情端点。页面授予 {@code /system/log/loginLog} 覆盖读；
 * 删除/清空/解锁再要各自的按钮点。数据范围由 {@link DataScopeProvider} 解析（实现由 auth
 * 的解析器供给），只作用于列表；删除/清空/解锁是 gate-only 的写动作。解锁同时写两张表：
 * 登录日志记「该账号被解」（限流端口写），操作日志记「谁点的」（本方法上的注解写）。
 */
@Tag(name = "登录日志接口")
@RestController
@RequestMapping("/admin/system/sysLoginLog")
public class SysLoginLogController {

    @Autowired
    private SysLoginLogService service;

    @Autowired
    private DataScopeProvider dataScopeProvider;

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/loginLog')")
    @Operation(summary = "登录日志分页查询")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit,
                        SysLoginLogQueryVo queryVo) {
        Page<SysLoginLog> pageParam = PageWindow.of(page, limit);
        DataScope scope = dataScopeProvider.resolve(LoginUserInfoHelper.getUserId());
        return Result.ok(service.page(pageParam, queryVo, scope));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/loginLog','system:loginLog:remove')")
    @Operation(summary = "删除登录日志")
    @OperLog(title = "登录日志·删除", businessType = BusinessType.DELETE)
    @DeleteMapping("remove")
    public Result remove(@RequestBody List<Long> idList) {
        boolean removed = service.removeByIds(idList);
        return removed ? Result.ok() : Result.fail();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/loginLog','system:loginLog:clean')")
    @Operation(summary = "清空登录日志")
    @OperLog(title = "登录日志·清空", businessType = BusinessType.CLEAR)
    @DeleteMapping("clean")
    public Result clean() {
        service.clean();
        return Result.ok();
    }

    @PreAuthorize("@backofficeGate.isAllowed('/system/log/loginLog','system:loginLog:unlock')")
    @Operation(summary = "解锁账号")
    @OperLog(title = "登录日志·解锁", businessType = BusinessType.UNLOCK)
    @PostMapping("unlock/{username}")
    public Result unlock(@PathVariable String username) {
        service.unlock(username);
        return Result.ok();
    }
}
