package com.atguigu.security.response;

import com.atguigu.common.result.ResponseUtil;
import com.atguigu.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 认证家族 401 写出的唯一出口（#173）：HTTP 401 与 body.code（208/209）只在这里拼写，
 * 过滤器、登录过滤器、入口点与续期控制器共用。消息覆写承载登录限流与字段校验文案，
 * 失败分类决策仍归各调用方（登录侧 ADR 0023）。
 */
public final class AuthenticationRejections {

    private AuthenticationRejections() {
    }

    /** 过滤器层直接写响应（无 MVC 返回值可用）。 */
    public static void write(HttpServletResponse response, AuthenticationFailure failure) {
        write(response, failure, null);
    }

    public static void write(
            HttpServletResponse response, AuthenticationFailure failure, String messageOverride) {
        ResponseUtil.out(response, HttpServletResponse.SC_UNAUTHORIZED, result(failure, messageOverride));
    }

    /** MVC 控制器按结果返回不同状态时用（ADR 0037 的续期端点形状）。 */
    public static ResponseEntity<Result> entity(AuthenticationFailure failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result(failure, null));
    }

    private static Result<Object> result(AuthenticationFailure failure, String messageOverride) {
        Result<Object> result = Result.build(null, failure.code());
        return messageOverride == null ? result : result.message(messageOverride);
    }
}
