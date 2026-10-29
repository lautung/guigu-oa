package com.atguigu.security.custom;

import com.atguigu.common.result.ResponseUtil;
import com.atguigu.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 已认证但被安全层拒绝的请求统一 403 + code 205（#145）：
 * 与方法级 @PreAuthorize 经全局异常处理返回的业务码保持一致，不再出现空响应体。
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        ResponseUtil.out(response, HttpServletResponse.SC_FORBIDDEN,
                Result.fail().code(205).message("没有操作权限"));
    }
}
