package com.atguigu.process;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class WechatCapabilityAbsentTest {

    @Test
    public void wechatOauthControllerMustStayAbsent() {
        assertNull(load("com.atguigu.process.controller.api.WechatController"));
    }

    @Test
    public void officialAccountMenuControllerMustStayAbsent() {
        assertNull(load("com.atguigu.wechat.controller.MenuController"));
    }

    @Test
    public void templateMessageServiceMustStayAbsent() {
        assertNull(load("com.atguigu.process.service.MessageService"));
        assertNull(load("com.atguigu.process.service.impl.MessageServiceImpl"));
    }

    @Test
    public void wechatMpConfigMustStayAbsent() {
        assertNull(load("com.atguigu.wechat.config.WeChatMpConfig"));
        assertNull(load("com.atguigu.wechat.service.MenuService"));
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }
}
