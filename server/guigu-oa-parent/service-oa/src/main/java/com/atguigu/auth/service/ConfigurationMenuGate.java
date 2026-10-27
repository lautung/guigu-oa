package com.atguigu.auth.service;

import com.atguigu.security.custom.LoginUserInfoHelper;
import org.springframework.stereotype.Component;

/**
 * 配置路由的唯一 gate：把当前用户与菜单 path 交给菜单授予判定。
 * 注解绑定这个 facade，而不是 SysMenuServiceImpl 的 bean 名。
 */
@Component("configurationMenuGate")
public class ConfigurationMenuGate {

    private final SysMenuService sysMenuService;

    public ConfigurationMenuGate(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    public boolean isGranted(String menuPath) {
        Long userId = LoginUserInfoHelper.getUserId();
        return userId != null && sysMenuService.hasMenuAccess(userId, menuPath);
    }
}
