package com.lautung.oa.audit.config;

import com.lautung.oa.audit.support.OperParamSanitizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计侧的对象装配。{@link ObjectMapper} 用 Spring Boot 自动配置的那一份（Jackson 3），
 * 不另建序列化规则，保证日志里的字段名与请求体一致。
 */
@Configuration
public class AuditConfiguration {

    @Bean
    public OperParamSanitizer operParamSanitizer(ObjectMapper objectMapper) {
        return new OperParamSanitizer(objectMapper);
    }
}
