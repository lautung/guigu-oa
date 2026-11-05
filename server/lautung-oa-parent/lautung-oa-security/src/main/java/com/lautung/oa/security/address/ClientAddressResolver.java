package com.lautung.oa.security.address;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端来源地址的唯一解析端口（#262）：登录失败计数、注册限流、操作日志与审计都从这里取
 * 同一个结果，新增匿名端点不再各写一套信任规则。
 */
@FunctionalInterface
public interface ClientAddressResolver {

    /**
     * 返回这次请求的来源地址。默认实现只信 socket 对端；只有对端落在配置的受信代理网段内
     * 才读取 {@code X-Real-IP} / {@code X-Forwarded-For}。
     *
     * @param request 当前请求，不能为 {@code null}
     * @return 非空地址串；取不到对端时是 {@code unknown}
     */
    String resolve(HttpServletRequest request);
}
