package com.lautung.oa.common.result;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ResponseUtil {

    private static final Logger log = LoggerFactory.getLogger(ResponseUtil.class);

    public static void out(HttpServletResponse response, Result r) {
        out(response, HttpStatus.OK.value(), r);
    }

    /**
     * 认证失败写 401（#145）：安全层拒绝的请求带真实状态码，body.code 仍是业务码
     * （208 认证失败 / 209 账号停用），客户端按 body.code 走原刷新与登出分支。
     */
    public static void out(HttpServletResponse response, int status, Result r) {
        ObjectMapper mapper = new ObjectMapper();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            mapper.writeValue(response.getWriter(), r);
        } catch (IOException e) {
            log.warn("Could not write authentication response", e);
        }
    }
}
