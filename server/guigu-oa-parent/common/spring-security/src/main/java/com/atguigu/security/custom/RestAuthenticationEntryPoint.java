package com.atguigu.security.custom;

import com.atguigu.common.result.ResponseUtil;
import com.atguigu.common.result.Result;
import com.atguigu.common.result.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 未认证请求的统一出口（#145）：不再依赖 Spring Security 默认的 403 空体，
 * 与 TokenAuthenticationFilter 的认证失败共用 401 + code 208 的响应体。
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        ResponseUtil.out(response, HttpServletResponse.SC_UNAUTHORIZED,
                Result.build(null, ResultCodeEnum.LOGIN_ERROR));
    }
}
