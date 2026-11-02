package com.lautung.oa.process;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class WechatCapabilityAbsentTest {

    @Test
    public void wechatOauthControllerMustStayAbsent() {
        assertNull(load("com.lautung.oa.process.controller.api.WechatController"));
    }

    @Test
    public void officialAccountMenuControllerMustStayAbsent() {
        assertNull(load("com.lautung.oa.wechat.controller.MenuController"));
    }

    @Test
    public void templateMessageServiceMustStayAbsent() {
        assertNull(load("com.lautung.oa.process.service.MessageService"));
        assertNull(load("com.lautung.oa.process.service.impl.MessageServiceImpl"));
    }

    @Test
    public void wechatMpConfigMustStayAbsent() {
        assertNull(load("com.lautung.oa.wechat.config.WeChatMpConfig"));
        assertNull(load("com.lautung.oa.wechat.service.MenuService"));
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
