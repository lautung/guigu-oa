package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.support.InMemoryRedisTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 锁屏校验（#255）：已认证端点只校验当前用户密码。错误口令是业务失败（200 + 201），
 * 不入登录失败计数、不签发令牌、不改会话；未认证仍是认证家族（401 + 208）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_verify_password_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
public class VerifyPasswordContractTest {

    private static final String SOURCE = "203.0.113.61";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private MutableLoginClock loginClock;

    @BeforeEach
    public void resetLoginClock() {
        loginClock.useSystemTime();
    }

    @AfterEach
    public void removeLoginLogs() {
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE username IN (?, ?)", "admin", "verify-contract-user");
    }

    @Test
    public void verifyPasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/admin/system/index/verifyPassword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"111111\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208))
                .andExpect(jsonPath("$.message").value("认证失败"));
    }

    @Test
    public void correctPasswordReturnsSuccessWithoutIssuingATokenOrEndingTheSession() throws Exception {
        String token = loginToken("admin", "111111", SOURCE);

        verifyPassword(token, "111111")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        // 原会话仍可用：校验口令不是登录，也不是续期。
        mockMvc.perform(get("/admin/system/index/info").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void wrongPasswordIsANamedBusinessFailureAndKeepsTheSession() throws Exception {
        String token = loginToken("admin", "111111", SOURCE);

        verifyPassword(token, "wrong-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码错误"))
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());

        mockMvc.perform(get("/admin/system/index/info").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void blankPasswordIsRejectedAsValidationFailure() throws Exception {
        String token = loginToken("admin", "111111", SOURCE);

        verifyPassword(token, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    public void wrongVerificationsDoNotCountTowardsTheLoginLimitOrAudit() throws Exception {
        loginClock.useFixed(Instant.parse("2026-11-04T00:00:00Z"));
        String token = loginToken("admin", "111111", SOURCE);
        int loginRowsBefore = loginLogCount("admin");

        for (int attempt = 1; attempt <= 6; attempt += 1) {
            verifyPassword(token, "wrong-" + attempt)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("密码错误"));
        }

        // 锁屏校验不写登录审计；成功登录仍写一行，所以账在最后一次登录前结。
        assertEquals(loginRowsBefore, loginLogCount("admin"), "lock-screen checks write no login audit row");

        // Six recorded failures would already trigger the account backoff at a frozen clock;
        // the correct password must still arrive without waiting.
        mockMvc.perform(post("/admin/system/index/login")
                        .header("X-Forwarded-For", SOURCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(loginRowsBefore + 1, loginLogCount("admin"));
    }

    @Test
    public void legacyMd5HashesAreAcceptedForVerificationToo() throws Exception {
        String token = loginToken("admin", "111111", SOURCE);
        jdbcTemplate.update("UPDATE sys_user SET password = ? WHERE id = 1",
                "96e79218965eb72c92a549dd5a330112");

        verifyPassword(token, "111111")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private ResultActions verifyPassword(String token, String password) throws Exception {
        return mockMvc.perform(post("/admin/system/index/verifyPassword")
                .header("token", token)
                .header("X-Forwarded-For", SOURCE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private String loginToken(String username, String password, String source) throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .header("X-Forwarded-For", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private int loginLogCount(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_login_log WHERE username = ?", Integer.class, username);
    }

    @TestConfiguration
    public static class InMemoryRedisConfig {

        @Bean
        @Primary
        public RedisTemplate<String, String> redisTemplate() {
            return new InMemoryRedisTemplate();
        }

        @Bean
        @Primary
        public MutableLoginClock loginAttemptTestClock() {
            return new MutableLoginClock();
        }
    }

    static final class MutableLoginClock extends Clock {

        private volatile Clock delegate = Clock.systemUTC();

        void useFixed(Instant instant) {
            delegate = Clock.fixed(instant, ZoneOffset.UTC);
        }

        void useSystemTime() {
            delegate = Clock.systemUTC();
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return delegate.withZone(zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }
    }
}
