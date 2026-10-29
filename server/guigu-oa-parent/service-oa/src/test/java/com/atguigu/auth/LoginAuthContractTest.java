package com.atguigu.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.atguigu.auth.service.SysUserService;
import com.atguigu.model.system.SysUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import com.atguigu.common.jwt.JwtHelper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import com.jayway.jsonpath.JsonPath;

import javax.crypto.SecretKey;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_login_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
public class LoginAuthContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private MutableLoginClock loginClock;

    @BeforeEach
    public void resetLoginClock() {
        loginClock.useSystemTime();
    }

    @AfterEach
    public void removeContractUser() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN (?, ?, ?)",
                "new-contract-user", "lock-contract-user", "reset-contract-user");
    }

    @Test
    public void correctUsernameAndPasswordReturnToken() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.configMenus").doesNotExist())
                .andExpect(jsonPath("$.data.buttons").doesNotExist());
    }

    @Test
    public void loginReturnsAccessAndRefreshTokens() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    public void successfulLoginUpgradesLegacyMd5PasswordToBcrypt() throws Exception {
        SysUser user = sysUserService.getById(1L);
        user.setPassword("96e79218965eb72c92a549dd5a330112");
        sysUserService.updateById(user);

        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String upgradedPassword = sysUserService.getById(1L).getPassword();
        org.junit.jupiter.api.Assertions.assertTrue(upgradedPassword.startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("111111", upgradedPassword));
    }

    @Test
    public void failedLoginDoesNotUpgradeLegacyMd5Password() throws Exception {
        String legacyHash = "96e79218965eb72c92a549dd5a330112";
        SysUser user = sysUserService.getById(1L);
        user.setPassword(legacyHash);
        sysUserService.updateById(user);

        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));

        assertEquals(legacyHash, sysUserService.getById(1L).getPassword());
    }

    @Test
    public void newlySavedAndChangedPasswordsAreStoredAsBcrypt() throws Exception {
        String token = loginToken("admin", "111111");

        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-contract-user\",\"password\":\"first-secret\","
                                + "\"name\":\"新用户\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        SysUser createdUser = sysUserService.getUserByUserName("new-contract-user");
        String createdHash = createdUser.getPassword();
        org.junit.jupiter.api.Assertions.assertTrue(createdHash.startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("first-secret", createdHash));

        mockMvc.perform(put("/admin/system/sysUser/update")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + createdUser.getId() + ",\"password\":\"changed-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String changedHash = sysUserService.getById(createdUser.getId()).getPassword();
        org.junit.jupiter.api.Assertions.assertTrue(changedHash.startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("changed-secret", changedHash));
    }

    @Test
    public void changingThePasswordRetiresEverySessionIssuedBeforeIt() throws Exception {
        String adminToken = loginToken("admin", "111111");
        saveContractUser(adminToken, "reset-contract-user", "111111");
        Long userId = sysUserService.getUserByUserName("reset-contract-user").getId();
        String oldLogin = loginBody("reset-contract-user", "111111", "203.0.113.21");
        String oldAccessToken = JsonPath.read(oldLogin, "$.data.token");
        String oldRefreshToken = JsonPath.read(oldLogin, "$.data.refreshToken");

        mockMvc.perform(put("/admin/system/sysUser/update")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + userId + ",\"username\":\"reset-contract-user\","
                                + "\"name\":\"改密测试用户\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // An edit that leaves the password alone is not a credential change.
        mockMvc.perform(get("/admin/system/index/info").header("token", oldAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(put("/admin/system/sysUser/update")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + userId + ",\"username\":\"reset-contract-user\","
                                + "\"name\":\"改密测试用户\",\"status\":1,\"password\":\"222222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", oldAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
        assertRefreshRejected(oldRefreshToken);

        // Only the changed user's sessions retire; the admin that made the change stays in.
        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String freshLogin = loginBody("reset-contract-user", "222222", "203.0.113.21");
        String freshAccessToken = JsonPath.read(freshLogin, "$.data.token");
        mockMvc.perform(get("/admin/system/index/info").header("token", freshAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void refreshRenewsAccessWithoutExtendingRefreshLifetimeAndAccessTokenCannotRefresh() throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.data.token");
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");

        String refreshedBody = mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextRefreshToken = JsonPath.read(refreshedBody, "$.data.refreshToken");
        String refreshedAccessToken = JsonPath.read(refreshedBody, "$.data.token");
        assertEquals(refreshToken, nextRefreshToken);

        mockMvc.perform(get("/admin/system/index/info").header("token", refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("管理员"));

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));

        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void logoutRevokesTheAccessAndRefreshTokensIssuedByThatLogin() throws Exception {
        String loginBody = loginAndReturnBody();
        String accessToken = JsonPath.read(loginBody, "$.data.token");
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");

        mockMvc.perform(post("/admin/system/index/logout").header("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));

        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void logoutOnlyRevokesTheLoginThatTurnedItsCredentialsIn() throws Exception {
        String firstLogin = loginAndReturnBody();
        String secondLogin = loginAndReturnBody();
        String firstAccessToken = JsonPath.read(firstLogin, "$.data.token");
        String secondAccessToken = JsonPath.read(secondLogin, "$.data.token");
        String secondRefreshToken = JsonPath.read(secondLogin, "$.data.refreshToken");

        mockMvc.perform(post("/admin/system/index/logout").header("token", firstAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", secondAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    public void refreshedAccessTokenStillBelongsToTheLogoutSession() throws Exception {
        String loginBody = loginAndReturnBody();
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");
        String refreshedBody = mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refreshedAccessToken = JsonPath.read(refreshedBody, "$.data.token");

        mockMvc.perform(post("/admin/system/index/logout").header("token", refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", refreshedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));

        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void logoutStillRevokesTheSessionWhenTheAccessTokenHasExpired() throws Exception {
        String loginBody = loginAndReturnBody();
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");
        String sessionId = jwtHelper.readRefreshToken(refreshToken).sessionId();

        mockMvc.perform(post("/admin/system/index/logout")
                        .header("token", expiredToken("access", sessionId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void malformedExpiredWrongTypeAndIdentityInconsistentRefreshTokensAreRejected() throws Exception {
        String accessToken = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        String identityInconsistent = jwtHelper.createTokenPair(1L, "disabled_user", 0L).refreshToken();
        String identityInconsistentAccess = jwtHelper.createTokenPair(1L, "disabled_user", 0L).token();

        assertRefreshRejected("not-a-jwt");
        assertRefreshRejected(expiredRefreshToken());
        assertRefreshRejected(accessToken);
        assertRefreshRejected(identityInconsistent);

        mockMvc.perform(get("/admin/system/index/info").header("token", identityInconsistentAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void disabledUserCannotRecognizeAnAlreadyIssuedAccessToken() throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = JsonPath.read(loginBody, "$.data.token");

        sysUserService.updateStatus(1L, 0);

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(209));
    }

    @Test
    public void disabledUserCannotRefreshAnAlreadyIssuedRefreshToken() throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String refreshToken = JsonPath.read(loginBody, "$.data.refreshToken");

        sysUserService.updateStatus(1L, 0);

        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(209));
    }

    @Test
    public void disabledUserCannotEnter() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disabled_user\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(209))
                .andExpect(jsonPath("$.message").value("账号已停用"));
    }

    @Test
    public void wrongPasswordCannotEnter() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208))
                .andExpect(jsonPath("$.message").value("认证失败"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    public void unknownUsernameCannotEnterTheSameWay() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-such-user\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208))
                .andExpect(jsonPath("$.message").value("认证失败"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    public void disabledUserWithWrongPasswordCannotEnterTheSameWay() throws Exception {
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"disabled_user\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208))
                .andExpect(jsonPath("$.message").value("认证失败"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    public void repeatedFailuresLockTheAccountEvenWhenTheCorrectPasswordArrivesLater(CapturedOutput output)
            throws Exception {
        String adminToken = loginToken("admin", "111111");
        saveContractUser(adminToken, "lock-contract-user", "111111");
        String source = "203.0.113.10";

        loginClock.useFixed(Instant.parse("2026-10-29T00:00:00Z"));
        for (int failure = 1; failure <= 5; failure += 1) {
            assertLoginCode("lock-contract-user", "wrong-" + failure, source, 208);
        }
        for (long waitSeconds : new long[]{1, 2, 4, 8, 16}) {
            loginClock.advance(Duration.ofSeconds(waitSeconds));
            assertLoginCode("lock-contract-user", "wrong-again", source, 208);
        }

        // Backoff alone would be over after thirty seconds; the fifteen minute lock must outlast it.
        loginClock.advance(Duration.ofSeconds(31));
        String locked = loginBody("lock-contract-user", "111111", source);
        assertEquals(208, JsonPath.<Integer>read(locked, "$.code").intValue());
        assertTrue(JsonPath.<String>read(locked, "$.message").contains("频繁"));

        loginClock.advance(Duration.ofMinutes(15).minusSeconds(31));
        assertLoginCode("lock-contract-user", "111111", source, 200);

        assertTrue(output.getOut().contains("login audit event=locked"));
        assertTrue(output.getOut().contains("login audit event=unlocked"));
    }

    @Test
    public void aSuccessfulLoginClearsTheFailuresRecordedBeforeIt() throws Exception {
        String adminToken = loginToken("admin", "111111");
        saveContractUser(adminToken, "reset-contract-user", "111111");
        String source = "203.0.113.11";

        for (int failure = 1; failure <= 4; failure += 1) {
            assertLoginCode("reset-contract-user", "wrong-" + failure, source, 208);
        }
        assertLoginCode("reset-contract-user", "111111", source, 200);

        // Without the reset these four would cross the backoff threshold and refuse the password.
        for (int failure = 1; failure <= 4; failure += 1) {
            assertLoginCode("reset-contract-user", "wrong-again-" + failure, source, 208);
        }
        assertLoginCode("reset-contract-user", "111111", source, 200);
    }

    @Test
    public void anUnknownUsernameIsThrottledWithTheSameThresholds() throws Exception {
        String source = "203.0.113.12";

        loginClock.useFixed(Instant.parse("2026-10-29T00:00:00Z"));
        for (int failure = 1; failure <= 5; failure += 1) {
            assertLoginCode("no-such-lock-user", "wrong-" + failure, source, 208);
        }
        for (long waitSeconds : new long[]{1, 2, 4, 8, 16}) {
            loginClock.advance(Duration.ofSeconds(waitSeconds));
            assertLoginCode("no-such-lock-user", "wrong-again", source, 208);
        }

        loginClock.advance(Duration.ofSeconds(31));
        String locked = loginBody("no-such-lock-user", "111111", source);
        assertEquals(208, JsonPath.<Integer>read(locked, "$.code").intValue());
        assertTrue(JsonPath.<String>read(locked, "$.message").contains("频繁"));
    }

    @Test
    public void protectedEndpointWithoutTokenReturns208() throws Exception {
        mockMvc.perform(get("/admin/system/index/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void loginTokenCanAccessCurrentUserInfo() throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(loginBody, "$.data.token");

        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.configMenus").exists());
    }

    private String loginToken(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private void saveContractUser(String adminToken, String username, String password) throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\","
                                + "\"name\":\"锁定测试用户\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void assertLoginCode(String username, String password, String source, int expectedCode)
            throws Exception {
        String body = loginBody(username, password, source);
        assertEquals(expectedCode, JsonPath.<Integer>read(body, "$.code").intValue(), body);
    }

    private String loginBody(String username, String password, String source) throws Exception {
        return mockMvc.perform(post("/admin/system/index/login")
                        .header("X-Forwarded-For", source)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String loginAndReturnBody() throws Exception {
        return mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void assertRefreshRejected(String token) throws Exception {
        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(208));
    }

    private String expiredRefreshToken() {
        return expiredToken("refresh", "expired-session");
    }

    private String expiredToken(String tokenType, String sessionId) {
        byte[] secret = "test-jwt-secret-for-contract-tests-0000000000000000000000000000000000000000"
                .getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(secret);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("AUTH-USER")
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .claim("userId", 1L)
                .claim("username", "admin")
                .claim("tokenType", tokenType)
                .claim("sessionId", sessionId)
                .signWith(key)
                .compact();
    }

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        @Primary
        public RedisTemplate<String, String> redisTemplate() {
            ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
            RedisTemplate<String, String> template = new RedisTemplate<String, String>() {
                @Override
                @SuppressWarnings("unchecked")
                public ValueOperations<String, String> opsForValue() {
                    return (ValueOperations<String, String>) Proxy.newProxyInstance(
                            ValueOperations.class.getClassLoader(),
                            new Class<?>[]{ValueOperations.class},
                            (proxy, method, args) -> {
                                if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                                    store.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                    return null;
                                }
                                if ("get".equals(method.getName()) && args != null && args.length >= 1) {
                                    return store.get(args[0]);
                                }
                                if ("increment".equals(method.getName()) && args != null && args.length >= 1) {
                                    String key = String.valueOf(args[0]);
                                    return Long.valueOf(store.merge(
                                            key, "1",
                                            (current, ignored) -> String.valueOf(Long.parseLong(current) + 1)));
                                }
                                if ("toString".equals(method.getName())) {
                                    return "InMemoryValueOperations";
                                }
                                throw new UnsupportedOperationException(method.getName());
                            });
                }

                @Override
                public Boolean delete(String key) {
                    return store.remove(key) != null;
                }

                @Override
                public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
                    return store.containsKey(key);
                }

                @Override
                public void afterPropertiesSet() {
                }
            };
            return template;
        }

        @Bean
        @Primary
        public MutableLoginClock loginAttemptTestClock() {
            return new MutableLoginClock();
        }
    }

    /** The limiter reads the clock, so contract tests can walk through backoff and lock windows. */
    static final class MutableLoginClock extends Clock {

        private volatile Clock delegate = Clock.systemUTC();

        void useFixed(Instant instant) {
            delegate = Clock.fixed(instant, ZoneOffset.UTC);
        }

        void advance(Duration duration) {
            delegate = Clock.fixed(delegate.instant().plus(duration), ZoneOffset.UTC);
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
