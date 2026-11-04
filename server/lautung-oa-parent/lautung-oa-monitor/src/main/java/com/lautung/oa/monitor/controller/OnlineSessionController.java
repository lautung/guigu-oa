package com.lautung.oa.monitor.controller;

import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.monitor.service.OnlineSessionService;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.security.session.OnlineSessionRegistry;
import com.lautung.oa.vo.monitor.OnlineSessionQueryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线用户后台接口（ADR 0060）：页面授予覆盖读，强退另要按钮点。
 *
 * <p>一行 = 一次登录；强退只作废那一个 sessionId（吊销 + 注销），不改用户状态。
 * 强退自己那台时下一次请求按 208 处理，客户端沿用清凭证回登录页。
 */
@Tag(name = "在线用户接口")
@RestController
@RequestMapping("/admin/monitor/online")
public class OnlineSessionController {

    private final OnlineSessionService onlineSessionService;

    private final OnlineSessionRegistry onlineSessionRegistry;

    public OnlineSessionController(
            OnlineSessionService onlineSessionService,
            OnlineSessionRegistry onlineSessionRegistry) {
        this.onlineSessionService = onlineSessionService;
        this.onlineSessionRegistry = onlineSessionRegistry;
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/online')")
    @Operation(summary = "在线用户分页查询")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                        @PathVariable Long limit,
                        OnlineSessionQueryVo queryVo) {
        return Result.ok(onlineSessionService.page(
                page, limit, queryVo, LoginUserInfoHelper.getSessionId()));
    }

    @PreAuthorize("@backofficeGate.isAllowed('/monitor/online','monitor:online:forceLogout')")
    @Operation(summary = "强制一个会话下线")
    @OperLog(title = "在线用户·强退", businessType = BusinessType.OTHER)
    @PostMapping("{sessionId}/forceLogout")
    public Result forceLogout(@PathVariable String sessionId) {
        if (!onlineSessionRegistry.forceLogout(sessionId)) {
            throw new BusinessException(201, "会话不存在");
        }
        return Result.ok();
    }
}
