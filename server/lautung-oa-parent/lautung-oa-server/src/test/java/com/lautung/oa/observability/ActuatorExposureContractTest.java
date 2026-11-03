package com.lautung.oa.observability;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.support.InMemoryRedisTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /actuator 暴露面合同（ADR 0060）：只暴露 health，服务监控自研直读、不代理 actuator。
 * 钉三件事——链接页只含 self + health、其它端点不可达（404）、/actuator 基路径不属于
 * 匿名白名单（匿名 401+208；白名单只有 /actuator/health）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_actuator_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class ActuatorExposureContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        ((InMemoryRedisTemplate) redisTemplate).clear();
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 0, token_version = 0 "
                + "WHERE id IN (1, 2)");
    }

    @Test
    void actuatorLinksPageIsHealthOnly() throws Exception {
        String body = mockMvc.perform(get("/actuator")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode links = OBJECT_MAPPER.readTree(body).path("_links");
        // health-path 是探针启用时 health 端点自己的子路径链接（readiness/liveness），不是新端点。
        assertEquals(Set.of("self", "health", "health-path"), Set.copyOf(links.propertyNames()),
                "暴露面只允许 health 家族：出现新端点即红（ADR 0060）");
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        for (String endpoint : new String[]{"metrics", "env", "threaddump", "loggers", "configprops", "mappings"}) {
            mockMvc.perform(get("/actuator/" + endpoint).header("token", token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void actuatorBasePathIsNotAnonymous() throws Exception {
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    void healthStaysAnonymous() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
