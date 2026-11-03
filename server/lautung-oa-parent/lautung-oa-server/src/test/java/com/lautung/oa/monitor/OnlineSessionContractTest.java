package com.lautung.oa.monitor;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在线用户与会话强退的 HTTP 合同（ADR 0060）：登录登记、最后活跃节流、失效口径、
 * 单会话强退、当前会话标记、/info sessionId、按钮点与种子授予。
 *
 * <p>用真迁移 + 真种子起库；Redis 是测试内存实现，能观察键形状、hash 字段与 TTL。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_online_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class OnlineSessionContractTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String SAFARI_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Safari/605.1.15";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long READ_ONLY_ROLE_ID = 801L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void resetSessionsAndUsers() {
        redis().clear();
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 0, token_version = 0 "
                + "WHERE id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", READ_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", READ_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", READ_ONLY_ROLE_ID);
    }

    @AfterEach
    void restoreSeededUsers() {
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 0, token_version = 0 "
                + "WHERE id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", READ_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", READ_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", READ_ONLY_ROLE_ID);
    }

    @Test
    void aSuccessfulLoginRegistersOneHashWithSevenDayTtlAndTheParsedClient() throws Exception {
        LoginTokens tokens = login("admin", "111111", "203.0.113.40", CHROME_WINDOWS);
        String sessionId = tokens.sessionId();
        String key = "login:session:" + sessionId;

        assertTrue(redis().hasKey(key), "login must register the session hash");
        Map<String, String> fields = redis().<String, String>opsForHash().entries(key);
        assertEquals("1", fields.get("userId"));
        assertEquals("admin", fields.get("username"));
        assertEquals("管理员", fields.get("name"));
        assertEquals("203.0.113.40", fields.get("ip"));
        assertEquals("Chrome", fields.get("browser"));
        assertEquals("Windows", fields.get("os"));
        assertEquals("0", fields.get("tokenVersion"));
        assertTrue(fields.get("loginTime").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals(fields.get("loginTime"), fields.get("lastActive"));

        Duration ttl = redis().ttl(key);
        assertTrue(ttl != null && ttl.toMillis() > Duration.ofDays(6).toMillis(),
                "TTL must be the refresh lifetime (7 days), was " + ttl);
        assertTrue(ttl.toMillis() <= Duration.ofDays(7).toMillis());
        // 登录本身不写最后活跃的节流键；第一次认证请求才会写。
        assertFalse(redis().hasKey("login:session:active:" + sessionId));
    }

    @Test
    void lastActiveIsThrottledAndTouchDoesNotRenewTheRegistrationTtl() throws Exception {
        LoginTokens tokens = login("admin", "111111", "203.0.113.41", CHROME_WINDOWS);
        String sessionId = tokens.sessionId();
        String key = "login:session:" + sessionId;
        String activeKey = "login:session:active:" + sessionId;
        redis().opsForHash().put(key, "lastActive", "2000-01-01 00:00:00");
        Duration ttlBefore = redis().ttl(key);

        mockMvc.perform(get("/admin/system/index/info").header("token", tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        String afterFirstRequest = (String) redis().opsForHash().get(key, "lastActive");
        assertNotEquals("2000-01-01 00:00:00", afterFirstRequest,
                "the first request inside the window must refresh lastActive");
        assertTrue(redis().hasKey(activeKey), "the 60-second throttle key must exist");
        Duration ttlAfter = redis().ttl(key);
        assertTrue(ttlAfter != null && ttlAfter.toMillis() <= ttlBefore.toMillis(),
                "touch must not renew the session hash TTL");

        // 同一个 60 秒窗口内的第二次请求命中节流，不覆盖 lastActive。
        redis().opsForHash().put(key, "lastActive", "2000-01-01 00:00:00");
        mockMvc.perform(get("/admin/system/index/info").header("token", tokens.accessToken()))
                .andExpect(status().isOk());
        assertEquals("2000-01-01 00:00:00", redis().opsForHash().get(key, "lastActive"));
    }

    @Test
    void passwordChangeDisableAndDeleteHideOldSessionsWithoutDeletingTheKey() throws Exception {
        String adminToken = login("admin", "111111", "203.0.113.10", CHROME_WINDOWS).accessToken();
        String userSession = login("user", "111111", "203.0.113.20", SAFARI_MAC).sessionId();
        String userKey = "login:session:" + userSession;

        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].sessionId",
                        org.hamcrest.Matchers.hasItem(userSession)))
                .andExpect(jsonPath("$.data.records[*].username",
                        org.hamcrest.Matchers.hasItem("user")));

        // 改密：token_version 与登记值不一致的会话不展示，但键留给 TTL 收尾。
        jdbcTemplate.update("UPDATE sys_user SET token_version = 1 WHERE id = 2");
        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", adminToken))
                .andExpect(jsonPath("$.data.total").value(1));
        assertTrue(redis().hasKey(userKey), "the stale session must not be deleted eagerly");

        // 停用：状态过滤同样只影响展示。
        jdbcTemplate.update("UPDATE sys_user SET token_version = 0, status = 0 WHERE id = 2");
        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", adminToken))
                .andExpect(jsonPath("$.data.total").value(1));

        // 已删：软删行不再出现。
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 1 WHERE id = 2");
        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", adminToken))
                .andExpect(jsonPath("$.data.total").value(1));
        assertTrue(redis().hasKey(userKey));
    }

    @Test
    void theListShapeSortsLoginTimeDescAndMarksTheCurrentSession() throws Exception {
        LoginTokens admin = login("admin", "111111", "203.0.113.11", CHROME_WINDOWS);
        LoginTokens user = login("user", "111111", "203.0.113.21", SAFARI_MAC);
        redis().opsForHash().put("login:session:" + user.sessionId(), "loginTime", "2000-01-01 00:00:00");

        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.records[0].sessionId").value(admin.sessionId()))
                .andExpect(jsonPath("$.data.records[0].current").value(true))
                .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                .andExpect(jsonPath("$.data.records[0].name").value("管理员"))
                .andExpect(jsonPath("$.data.records[0].ip").value("203.0.113.11"))
                .andExpect(jsonPath("$.data.records[0].browser").value("Chrome"))
                .andExpect(jsonPath("$.data.records[0].os").value("Windows"))
                .andExpect(jsonPath("$.data.records[0].loginTime").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].lastActive").isNotEmpty())
                .andExpect(jsonPath("$.data.records[1].sessionId").value(user.sessionId()))
                .andExpect(jsonPath("$.data.records[1].current").value(false))
                .andExpect(jsonPath("$.data.records[1].browser").value("Safari"))
                .andExpect(jsonPath("$.data.records[1].os").value("macOS"));

        // username 包含匹配、时间范围与内存分页。
        mockMvc.perform(get("/admin/monitor/online/1/10")
                        .param("username", "us")
                        .header("token", admin.accessToken()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("user"));
        mockMvc.perform(get("/admin/monitor/online/1/10")
                        .param("beginTime", "2999-01-01 00:00:00")
                        .header("token", admin.accessToken()))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/admin/monitor/online/1/10")
                        .param("endTime", "2001-01-01 00:00:00")
                        .header("token", admin.accessToken()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("user"));
        mockMvc.perform(get("/admin/monitor/online/1/1").header("token", admin.accessToken()))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    void forceLogoutRevokesOnlyTheChosenSessionAndTheOtherLoginStillWorks() throws Exception {
        LoginTokens first = login("admin", "111111", "203.0.113.12", CHROME_WINDOWS);
        LoginTokens second = login("admin", "111111", "203.0.113.13", CHROME_WINDOWS);

        mockMvc.perform(post("/admin/monitor/online/{sessionId}/forceLogout", first.sessionId())
                        .header("token", second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertFalse(redis().hasKey("login:session:" + first.sessionId()));
        assertFalse(redis().hasKey("login:session:active:" + first.sessionId()));
        assertTrue(redis().hasKey("token:revoked:" + first.sessionId()),
                "force logout must revoke the one session id");
        assertTrue(redis().hasKey("login:session:" + second.sessionId()),
                "the other login must keep its registration");

        mockMvc.perform(get("/admin/system/index/info").header("token", first.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(get("/admin/system/index/info").header("token", second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/monitor/online/{sessionId}/forceLogout", "no-such-session")
                        .header("token", second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("会话不存在"));
    }

    @Test
    void forcingOutYourOwnSessionMakesTheNextRequest208() throws Exception {
        LoginTokens own = login("admin", "111111", "203.0.113.14", CHROME_WINDOWS);

        mockMvc.perform(post("/admin/monitor/online/{sessionId}/forceLogout", own.sessionId())
                        .header("token", own.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", own.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    void infoCarriesTheCurrentSessionId() throws Exception {
        LoginTokens tokens = login("admin", "111111", "203.0.113.15", CHROME_WINDOWS);

        mockMvc.perform(get("/admin/system/index/info").header("token", tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(tokens.sessionId()))
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.menus").isArray());
    }

    @Test
    void logoutAlsoRevokesAndRemovesTheRegistration() throws Exception {
        LoginTokens tokens = login("admin", "111111", "203.0.113.16", CHROME_WINDOWS);

        mockMvc.perform(post("/admin/system/index/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("token", tokens.accessToken())
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertFalse(redis().hasKey("login:session:" + tokens.sessionId()));
        assertTrue(redis().hasKey("token:revoked:" + tokens.sessionId()));
        mockMvc.perform(get("/admin/system/index/info").header("token", tokens.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    void theOnlineEndpointsRequireThePageAndTheForceLogoutButtonPoint() throws Exception {
        LoginTokens roleless = login("user", "111111", "203.0.113.17", CHROME_WINDOWS);

        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", roleless.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/monitor/online/{sessionId}/forceLogout", "anything")
                        .header("token", roleless.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        // 只授页面、不授按钮点：读放行，强退仍是 205。
        Integer pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/monitor/online' AND type = 1 AND is_deleted = 0",
                Integer.class);
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, is_deleted) "
                + "VALUES (?, '在线监控只读', 'online_readonly', 0)", READ_ONLY_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                READ_ONLY_ROLE_ID, pageId);
        jdbcTemplate.update("INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, 2, 0)",
                READ_ONLY_ROLE_ID);
        redis().clear();
        LoginTokens pageOnly = login("user", "111111", "203.0.113.18", CHROME_WINDOWS);

        mockMvc.perform(get("/admin/monitor/online/1/10").header("token", pageOnly.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(post("/admin/monitor/online/{sessionId}/forceLogout", "anything")
                        .header("token", pageOnly.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    private LoginTokens login(String username, String password, String ip, String userAgent) throws Exception {
        String body = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", ip)
                        .header("User-Agent", userAgent)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = OBJECT_MAPPER.readTree(body).path("data");
        String accessToken = data.path("token").asString("");
        String refreshToken = data.path("refreshToken").asString("");
        String sessionId = jwtHelper.readAccessToken(accessToken).sessionId();
        return new LoginTokens(accessToken, refreshToken, sessionId);
    }

    private InMemoryRedisTemplate redis() {
        return (InMemoryRedisTemplate) redisTemplate;
    }

    private record LoginTokens(String accessToken, String refreshToken, String sessionId) {
    }
}
