package com.lautung.oa.auth.service;

import com.lautung.oa.auth.grant.BackofficeGrant;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 后台的唯一方法级 gate（ADR 0056）：读端点只判页面授予，写端点再判按钮点。
 * 判定顺序页面先、按钮后；拒绝统一由 {@code AccessDeniedException} 收成 HTTP 200 + code 205。
 */
@Component("backofficeGate")
public class BackofficeGate {

    private final BackofficeGrant backofficeGrant;

    public BackofficeGate(BackofficeGrant backofficeGrant) {
        this.backofficeGrant = backofficeGrant;
    }

    /** 读端点：页面授予覆盖读。 */
    public boolean isAllowed(String pagePath) {
        return isAllowed(pagePath, null);
    }

    /** 写端点：页面授予 + 按钮点。 */
    public boolean isAllowed(String pagePath, String permission) {
        Long userId = LoginUserInfoHelper.getUserId();
        if (userId == null) {
            return false;
        }
        BackofficeGrant.Snapshot snapshot = backofficeGrant.snapshot(userId);
        if (!snapshot.covers(pagePath)) {
            return false;
        }
        return !StringUtils.hasText(permission) || snapshot.permissions().contains(permission);
    }
}
