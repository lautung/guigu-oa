package com.lautung.oa.auth.service;

import com.lautung.oa.auth.grant.ConfigurationGrant;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import org.springframework.stereotype.Component;

/**
 * 配置路由的唯一 gate：把当前用户与菜单 path 交给配置授予判定。
 * 注解绑定这个 facade，而不是 SysMenuServiceImpl 的 bean 名。
 */
@Component("configurationMenuGate")
public class ConfigurationMenuGate {

    private final ConfigurationGrant configurationGrant;

    public ConfigurationMenuGate(ConfigurationGrant configurationGrant) {
        this.configurationGrant = configurationGrant;
    }

    public boolean isGranted(String menuPath) {
        Long userId = LoginUserInfoHelper.getUserId();
        return userId != null && configurationGrant.snapshot(userId).covers(menuPath);
    }
}
