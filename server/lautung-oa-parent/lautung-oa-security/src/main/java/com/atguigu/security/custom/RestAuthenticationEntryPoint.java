package com.atguigu.security.custom;

import com.atguigu.security.response.AuthenticationFailure;
import com.atguigu.security.response.AuthenticationRejections;
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
        AuthenticationRejections.write(response, AuthenticationFailure.INVALID_TOKEN);
    }
}
