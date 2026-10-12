package com.atguigu.common.result;

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
        ObjectMapper mapper = new ObjectMapper();
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            mapper.writeValue(response.getWriter(), r);
        } catch (IOException e) {
            log.warn("Could not write authentication response", e);
        }
    }
}
