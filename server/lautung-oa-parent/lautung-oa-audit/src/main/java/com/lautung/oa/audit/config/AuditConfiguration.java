package com.lautung.oa.audit.config;

import com.lautung.oa.audit.login.CompositeLoginAudit;
import com.lautung.oa.audit.login.DatabaseLoginAudit;
import com.lautung.oa.audit.service.LoginLogWriter;
import com.lautung.oa.audit.support.OperParamSanitizer;
import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.Slf4jLoginAudit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * 审计侧的对象装配。{@link ObjectMapper} 用 Spring Boot 自动配置的那一份（Jackson 3），
 * 不另建序列化规则，保证日志里的字段名与请求体一致。
 *
 * <p>登录审计的写端口在 security，DB 实现在本模块（ADR 0054）：这里把
 * {@link DatabaseLoginAudit} 与 {@link Slf4jLoginAudit} 组合成唯一的 {@link LoginAudit} bean，
 * security 的 {@code WebSecurityConfig} 按可选 bean 消费——没有 audit 模块时它自己退回日志实现。
 */
@Configuration
public class AuditConfiguration {

    @Bean
    public OperParamSanitizer operParamSanitizer(ObjectMapper objectMapper) {
        return new OperParamSanitizer(objectMapper);
    }

    @Bean
    public LoginAudit loginAudit(LoginLogWriter loginLogWriter) {
        return new CompositeLoginAudit(
                new DatabaseLoginAudit(loginLogWriter),
                new Slf4jLoginAudit());
    }
}
