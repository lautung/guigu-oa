package com.lautung.oa.security.login;

import org.springframework.security.core.AuthenticationException;

/**
 * 登录请求本身不成立（缺字段、空体、畸形 JSON）。走失败认证的统一响应形状：
 * 401 + code 208，文案说明缺了什么；不计入登录失败计数。
 */
public class InvalidLoginInputException extends AuthenticationException {

    public InvalidLoginInputException(String message) {
        super(message);
    }
}
