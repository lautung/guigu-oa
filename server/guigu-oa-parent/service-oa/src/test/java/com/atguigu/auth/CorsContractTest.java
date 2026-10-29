package com.atguigu.auth;

import com.atguigu.common.jwt.JwtHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 服务端不接跨源（#141，见 ADR 0027）：统一客户端经 nginx 的 {@code /dev-api} 同源代理访问 API，
 * 控制器上的 {@code @CrossOrigin} 或全局跨源配置都会重新打开任意来源与 {@code token} 请求头。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_cors_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class CorsContractTest {

    private static final String HOSTILE_ORIGIN = "http://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void approvalReadDoesNotAnswerAHostileOriginWithCorsHeaders() throws Exception {
        mockMvc.perform(get("/admin/process/findPending/1/10")
                        .header("token", adminToken())
                        .header(HttpHeaders.ORIGIN, HOSTILE_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void crossOriginPreflightDoesNotGrantTheTokenHeader() throws Exception {
        // 带上 token，预检才走到 MVC；否则匿名请求会先被鉴权过滤器挡下，测不到跨源层。
        mockMvc.perform(options("/admin/process/approve")
                        .header("token", adminToken())
                        .header(HttpHeaders.ORIGIN, HOSTILE_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "token,content-type"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void noControllerOrGlobalConfigurationEnablesCrossOrigin() {
        List<String> controllers = handlerMapping.getHandlerMethods().values().stream()
                .filter(this::carriesCrossOrigin)
                .map(handler -> handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName())
                .distinct()
                .toList();
        assertTrue(controllers.isEmpty(), "控制器不得自行放行跨源：" + controllers);

        // 框架自带的 HandlerMappingIntrospector 只反映 @CrossOrigin，上面的断言已覆盖；
        // 这里挡住应用自己注册的跨源来源。
        List<String> corsSources = applicationContext.getBeansOfType(CorsConfigurationSource.class)
                .values().stream()
                .filter(source -> !(source instanceof HandlerMappingIntrospector))
                .map(source -> source.getClass().getName())
                .toList();
        assertTrue(corsSources.isEmpty(), "服务端不应注册 CorsConfigurationSource：" + corsSources);
        CorsRegistry registry = new ObservableCorsRegistry();
        applicationContext.getBeansOfType(WebMvcConfigurer.class).values()
                .forEach(configurer -> configurer.addCorsMappings(registry));
        assertTrue(((ObservableCorsRegistry) registry).configurations().isEmpty(),
                "服务端不应注册全局跨源映射");
    }

    /** {@code CorsRegistry#getCorsConfigurations} 是 protected，测试用子类读出注册结果。 */
    private static final class ObservableCorsRegistry extends CorsRegistry {

        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin").token();
    }

    private boolean carriesCrossOrigin(HandlerMethod handler) {
        return AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), CrossOrigin.class)
                || handler.hasMethodAnnotation(CrossOrigin.class);
    }
}
