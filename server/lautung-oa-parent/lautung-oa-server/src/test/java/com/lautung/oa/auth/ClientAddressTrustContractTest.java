package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #262 的来源信任边界（ADR 0063）：对端 127.0.0.1 不在受信配置里（`10.0.0.0/8`，另混入一个
 * 非法 CIDR 证明启动容错），{@code X-Real-IP} / {@code X-Forwarded-For} 完全无效——换头
 * 不能制造新的注册限流桶，也不能让登录/注册审计记下伪造来源。非法 CIDR 只 WARN 不阻断
 * 启动的细节由 {@code TrustedProxyClientAddressResolverTest} 的 WARN 断言钉住。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:guigu_oa_client_address_contract;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;"
                + "INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP",
        "lautung.oa.security.trusted-proxies=not-a-cidr, 10.0.0.0/8"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class ClientAddressTrustContractTest {

    private static final String SWITCH_KEY = "sys.account.registerUser";
    private static final String SWITCH_CACHE_KEY = "config:" + SWITCH_KEY;
    private static final String REGISTER_PATH = "/admin/system/index/register";
    private static final String LOGIN_PATH = "/admin/system/index/login";
    private static final String TOO_FREQUENT = "注册尝试过于频繁，请稍后再试";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE sys_config SET config_value = 'true' WHERE config_key = ?",
                SWITCH_KEY);
        redisTemplate.delete(SWITCH_CACHE_KEY);
        clearRegisterAttempts();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("UPDATE sys_config SET config_value = 'false' WHERE config_key = ?",
                SWITCH_KEY);
        redisTemplate.delete(SWITCH_CACHE_KEY);
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE username LIKE 'ca-%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'ca-%'");
        clearRegisterAttempts();
    }

    @Test
    void forwardedHeadersCannotMintNewRegisterBucketsWhenThePeerIsNotATrustedProxy() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt += 1) {
            // 每次换一组伪造头：如果头被信任，每一次都会落进新桶，第 6 次也不会被限流。
            register("ca-rate-" + attempt,
                    "203.0.113." + attempt, "198.51.100." + attempt)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("密码长度必须在 6 到 100 位之间"));
        }

        register("ca-sixth", "203.0.113.99", "198.51.100.99")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(TOO_FREQUENT));

        assertEquals(0, userCount("ca-sixth"),
                "a rate-limited attempt must not create the user");
    }

    @Test
    void registerAndLoginAuditRecordTheResolvedPeerNotTheForgedHeaders() throws Exception {
        register("ca-register", "203.0.113.7", "198.51.100.9")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码长度必须在 6 到 100 位之间"));

        mockMvc.perform(post(LOGIN_PATH)
                        .header("X-Real-IP", "203.0.113.8")
                        .header("X-Forwarded-For", "198.51.100.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ca-unknown\",\"password\":\"secret1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));

        assertEquals("127.0.0.1", onlyLoggedIp("ca-register"),
                "register must keep the socket peer, not X-Real-IP/X-Forwarded-For");
        assertEquals("127.0.0.1", onlyLoggedIp("ca-unknown"),
                "login must keep the socket peer, not X-Real-IP/X-Forwarded-For");
    }

    private org.springframework.test.web.servlet.ResultActions register(
            String username, String realIp, String forwardedFor) throws Exception {
        return mockMvc.perform(post(REGISTER_PATH)
                .header("X-Real-IP", realIp)
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"12345\","
                        + "\"confirm\":\"12345\",\"name\":\"来源合同\",\"phone\":\"\"}"));
    }

    private String onlyLoggedIp(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ip FROM sys_login_log WHERE username = ?", username);
        assertEquals(1, rows.size(), "expected exactly one audit row for " + username);
        return (String) rows.get(0).get("ip");
    }

    private int userCount(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, username);
    }

    private void clearRegisterAttempts() {
        Set<String> keys = redisTemplate.keys("register:attempt:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
