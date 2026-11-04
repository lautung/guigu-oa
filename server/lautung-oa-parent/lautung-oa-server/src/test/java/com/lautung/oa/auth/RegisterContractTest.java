package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.security.login.LoginAttemptLimiter;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注册的 HTTP 合同（#253，ADR 0062）：参数开关两态、字段校验与确认密码、ADR 0042 的用户名
 * 唯一（含并发）、按 IP 的 5 次/分钟限流、{@code sys_login_log.event} 的 register/login 形状，
 * 以及零角色新用户登录后只有工作台/办事（后台页面 205）。
 *
 * <p>用最小 H2 schema + 内存 Redis，开关直接改 {@code sys_config} 并清 {@code config:} 缓存，
 * 不经过参数管理页；限流与登录失败限流互不相干，分别用 {@code register:attempt:} 与
 * {@code login:attempt:} 前缀证明。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_register_contract;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;"
        + "INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class RegisterContractTest {

    private static final String SWITCH_KEY = "sys.account.registerUser";
    private static final String SWITCH_CACHE_KEY = "config:" + SWITCH_KEY;
    private static final String REGISTER_PATH = "/admin/system/index/register";
    private static final String SWITCH_PATH = REGISTER_PATH + "/switch";
    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String TOO_FREQUENT = "注册尝试过于频繁，请稍后再试";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAttemptLimiter loginAttemptLimiter;

    @BeforeEach
    void resetRegisterState() {
        deleteRegisterUsers();
        switchOff();
        clearRegisterAttempts();
    }

    @AfterEach
    void cleanRegisterState() {
        deleteRegisterUsers();
        clearRegisterAttempts();
    }

    @Test
    void theSwitchEndpointReportsBothStatesWithoutAuthentication() throws Exception {
        mockMvc.perform(get(SWITCH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(false));

        switchOn();

        mockMvc.perform(get(SWITCH_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    void aClosedSwitchRefusesTheAttemptAndStillWritesARegisterAuditRow() throws Exception {
        register(payload("reg-closed-user", "secret1", "secret1", "爱丽丝", null),
                "203.0.113.70", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("注册未开放"));

        assertEquals(0, userCount("reg-closed-user"), "a closed switch must not create the user");

        Map<String, Object> row = onlyLogRow("reg-closed-user");
        assertEquals("register", row.get("event"));
        assertEquals(1, intValue(row, "status"));
        assertEquals("注册未开放", row.get("msg"));
        assertEquals("203.0.113.70", row.get("ip"));
    }

    @Test
    void aRegisteredUserHasTheNormalStatusNoDeptNoRolesAndNoBackofficePage() throws Exception {
        switchOn();

        register(payload("reg-success-user", "secret1", "secret1", "爱丽丝", "13800001234"),
                "203.0.113.71", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_user WHERE username = ?", "reg-success-user");
        assertEquals("爱丽丝", user.get("name"));
        assertEquals("13800001234", user.get("phone"));
        assertEquals(1, intValue(user, "status"), "a registered user is enabled immediately");
        assertNull(user.get("dept_id"), "a registered user has no dept");
        assertNotEquals("secret1", user.get("password"), "the password must be hashed, never stored raw");
        assertTrue(passwordEncoder.matches("secret1", (String) user.get("password")),
                "the stored hash must verify the submitted password");
        assertEquals(0, roleCount(((Number) user.get("id")).longValue()),
                "registration must never write sys_user_role");

        String token = login("reg-success-user", "secret1");

        // 零角色 → /info 只有空授予，客户端因此只有内置的工作台/办事可用。
        mockMvc.perform(get("/admin/system/index/info").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("爱丽丝"))
                .andExpect(jsonPath("$.data.roles", hasSize(0)))
                .andExpect(jsonPath("$.data.permissions", hasSize(0)))
                .andExpect(jsonPath("$.data.menus", hasSize(0)));

        mockMvc.perform(get("/admin/system/sysUser/1/10").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        // 审计两行：注册成功 event=register/status=0，随后登录 event=login/status=0。
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sys_login_log WHERE username = ? ORDER BY id", "reg-success-user");
        assertEquals(2, rows.size());
        assertEquals("register", rows.get(0).get("event"));
        assertEquals(0, intValue(rows.get(0), "status"));
        assertEquals("注册成功", rows.get(0).get("msg"));
        assertEquals("login", rows.get(1).get("event"));
        assertEquals(0, intValue(rows.get(1), "status"));
    }

    @Test
    void phoneIsOptional() throws Exception {
        switchOn();

        register(payload("reg-no-phone", "secret1", "secret1", "无手机", null),
                "203.0.113.72", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNull(jdbcTemplate.queryForObject(
                "SELECT phone FROM sys_user WHERE username = ?", String.class, "reg-no-phone"));
    }

    @Test
    void everyFieldIsNamedInTheRejection() throws Exception {
        switchOn();

        assertFieldRejected(payload("", "secret1", "secret1", "爱丽丝", null), "用户名不能为空");
        assertFieldRejected(payload("u".repeat(21), "secret1", "secret1", "爱丽丝", null), "用户名过长");
        assertFieldRejected(payload("reg-null-password", null, null, "爱丽丝", null),
                "确认密码不能为空；密码不能为空");
        assertFieldRejected(payload("reg-short-password", "12345", "12345", "爱丽丝", null),
                "密码长度必须在 6 到 100 位之间");
        assertFieldRejected(payload("reg-blank-name", "secret1", "secret1", "  ", null), "姓名不能为空");
        assertFieldRejected(payload("reg-long-name", "secret1", "secret1", "名".repeat(21), null), "姓名过长");
        assertFieldRejected(payload("reg-long-phone", "secret1", "secret1", "爱丽丝", "1".repeat(21)),
                "手机号过长");
        assertFieldRejected(payload("reg-mismatch", "secret1", "secret2", "爱丽丝", null),
                "两次输入的密码不一致");

        // 全空体按字段名排序连接（ADR 0033）：confirm/name/password/username。
        mockMvc.perform(post(REGISTER_PATH)
                        .header("X-Forwarded-For", "203.0.113.73")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(
                        "确认密码不能为空；姓名不能为空；密码不能为空；用户名不能为空"));

        assertEquals(0, registerUserRows(), "a rejected payload must never create a user");
    }

    @Test
    void aDuplicateUsernameIsANamed201AndKeepsOneRegisterSuccessAndOneFailureRow() throws Exception {
        switchOn();

        register(payload("reg-duplicate", "secret1", "secret1", "爱丽丝", null),
                "203.0.113.74", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        register(payload("reg-duplicate", "other-secret", "other-secret", "另一个人", null),
                "203.0.113.74", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("用户名已存在"));

        assertEquals(1, userCount("reg-duplicate"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sys_login_log WHERE username = ? ORDER BY id", "reg-duplicate");
        assertEquals(2, rows.size());
        assertEquals("register", rows.get(0).get("event"));
        assertEquals(0, intValue(rows.get(0), "status"));
        assertEquals("register", rows.get(1).get("event"));
        assertEquals(1, intValue(rows.get(1), "status"));
        assertEquals("用户名已存在", rows.get(1).get("msg"));
    }

    @Test
    void concurrentRegistrationsWithTheSameNameLeaveExactlyOneUser() throws Exception {
        switchOn();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<MvcResult> attempt = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post(REGISTER_PATH)
                                .header("X-Forwarded-For", "203.0.113.75")
                                .header("User-Agent", CHROME_WINDOWS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload("reg-race-user", "secret1", "secret1", "并发", null)))
                        .andReturn();
            };
            Future<MvcResult> first = pool.submit(attempt);
            Future<MvcResult> second = pool.submit(attempt);
            assertTrue(ready.await(5, TimeUnit.SECONDS), "both requests must reach the start line");
            start.countDown();

            List<MvcResult> results = List.of(first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));
            List<Integer> codes = new ArrayList<>();
            for (MvcResult result : results) {
                codes.add(JsonPath.read(result.getResponse().getContentAsString(), "$.code"));
            }
            assertEquals(Set.of(200, 201), new HashSet<>(codes),
                    "exactly one concurrent write wins; the other is the named conflict");
            MvcResult loser = results.get(codes.indexOf(201));
            assertEquals("用户名已存在",
                    JsonPath.read(loser.getResponse().getContentAsString(), "$.message"));
            assertEquals(1, userCount("reg-race-user"),
                    "the unique index must leave exactly one row behind");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theSixthAttemptFromOneAddressIsRateLimitedWithoutTouchingTheLoginLimiter() throws Exception {
        switchOn();
        String limitedSource = "203.0.113.76";

        for (int attempt = 1; attempt <= 5; attempt += 1) {
            // 短密码让这五次都失败，同时证明失败尝试也计入 IP 次数。
            register(payload("reg-rate-" + attempt, "12345", "12345", "限流", null),
                    limitedSource, CHROME_WINDOWS)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.message").value("密码长度必须在 6 到 100 位之间"));
        }

        assertFalse(loginAttemptLimiter.isBlocked("reg-rate-5", limitedSource),
                "register counters must never feed the login failure limiter");
        Set<String> loginKeys = redisTemplate.keys("login:attempt:*");
        assertTrue(loginKeys == null || loginKeys.isEmpty(),
                "registration must not write any login:attempt: key, saw " + loginKeys);

        register(payload("reg-rate-sixth", "secret1", "secret1", "限流", null),
                limitedSource, CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(TOO_FREQUENT));

        assertEquals(0, userCount("reg-rate-sixth"), "a rate-limited attempt must not create the user");
        assertEquals(0, logCount("reg-rate-sixth"), "a rate-limited attempt stays out of the audit table");

        // 另一个地址不受这个计数影响。
        register(payload("reg-rate-other", "secret1", "secret1", "其他地址", null),
                "203.0.113.77", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private ResultActions register(String body, String source, String userAgent) throws Exception {
        return mockMvc.perform(post(REGISTER_PATH)
                .header("X-Forwarded-For", source)
                .header("User-Agent", userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
        assertTrue(token != null && !token.isBlank(), "login must issue an access token");
        return token;
    }

    private int fieldAttemptSource = 100;

    private void assertFieldRejected(String body, String message) throws Exception {
        // 每次字段拒绝换一个来源，避免 5 次/分钟的注册限流抢先拒绝。
        register(body, "203.0.113." + fieldAttemptSource++, CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(message));
    }

    private void switchOn() {
        setSwitch("true");
    }

    private void switchOff() {
        setSwitch("false");
    }

    private void setSwitch(String value) {
        jdbcTemplate.update("UPDATE sys_config SET config_value = ? WHERE config_key = ?",
                value, SWITCH_KEY);
        redisTemplate.delete(SWITCH_CACHE_KEY);
    }

    private void clearRegisterAttempts() {
        Set<String> keys = redisTemplate.keys("register:attempt:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void deleteRegisterUsers() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'reg-%')");
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE username LIKE 'reg-%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'reg-%'");
    }

    private int userCount(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = ?", Integer.class, username);
    }

    private int registerUserRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username LIKE 'reg-%'", Integer.class);
    }

    private int logCount(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_login_log WHERE username = ?", Integer.class, username);
    }

    private int roleCount(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ?", Integer.class, userId);
    }

    private Map<String, Object> onlyLogRow(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sys_login_log WHERE username = ?", username);
        assertEquals(1, rows.size(), "expected exactly one register audit row for " + username);
        return lowerCaseKeys(rows.get(0));
    }

    private static Map<String, Object> lowerCaseKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new java.util.HashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static int intValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private static String payload(String username, String password, String confirm, String name, String phone) {
        return "{" + field("username", username)
                + "," + field("password", password)
                + "," + field("confirm", confirm)
                + "," + field("name", name)
                + "," + field("phone", phone)
                + "}";
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":" + (value == null ? "null" : "\"" + value + "\"");
    }
}
