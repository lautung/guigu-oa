package com.lautung.oa.auth;

import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.security.login.LoginAttemptPolicy;
import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.RedisLoginAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录日志落库的 HTTP 合同（ADR 0054）：成功/失败/锁定/解锁/停用各写一条 {@code sys_login_log}，
 * 被锁定挡下的尝试只留应用日志，DB 故障不影响登录。
 *
 * <p>用 {@code auth-contract-schema.sql} 的最小库（sys_user + sys_login_log）与内存 Redis，
 * 时钟可控，所以锁定与惰性解锁不用真等 15 分钟。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_login_log_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class LoginLogContractTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String SAFARI_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Safari/605.1.15";
    private static final Instant START = Instant.parse("2026-10-29T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LoginAttemptLimiter loginAttemptLimiter;

    @Autowired
    private LoginAudit loginAudit;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private LoginAuthContractTest.MutableLoginClock loginClock;

    @BeforeEach
    void resetLoginLogs() {
        loginClock.useSystemTime();
        jdbcTemplate.update("DELETE FROM sys_login_log");
    }

    @Test
    void successfulLoginWritesStatusZeroWithTheUserSnapshotAndParsedClient() throws Exception {
        jdbcTemplate.update("UPDATE sys_user SET dept_id = 77 WHERE id = 1");

        login("admin", "111111", "203.0.113.40", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        Map<String, Object> row = onlyRow();
        assertEquals("admin", row.get("username"));
        assertEquals(1L, longValue(row, "user_id"));
        assertEquals(77L, longValue(row, "dept_id"));
        assertEquals("203.0.113.40", row.get("ip"));
        assertEquals("Chrome", row.get("browser"));
        assertEquals("Windows", row.get("os"));
        assertEquals(0, intValue(row, "status"));
        assertEquals("login", row.get("event"), "the login path defaults to the login dimension");
        assertNull(row.get("msg"));
        assertNotNull(row.get("create_time"), "create_time must be filled by the common-field handler");
        assertFalse(String.valueOf(row).contains("AppleWebKit"), "the raw user-agent must never be stored");
    }

    @Test
    void aWrongPasswordWritesStatusOneAndAnUnknownUserKeepsTheSnapshotEmpty() throws Exception {
        login("no-such-login-user", "wrong-password", "203.0.113.41", SAFARI_MAC)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));

        Map<String, Object> row = onlyRow();
        assertEquals("no-such-login-user", row.get("username"));
        assertNull(row.get("user_id"));
        assertNull(row.get("dept_id"));
        assertEquals("203.0.113.41", row.get("ip"));
        assertEquals("Safari", row.get("browser"));
        assertEquals("macOS", row.get("os"));
        assertEquals(1, intValue(row, "status"));
        assertEquals("login", row.get("event"));
        assertEquals("登录失败", row.get("msg"));
    }

    @Test
    void theTenthAccountFailureWritesOneLockedRowWithTheScopeInTheMessage() {
        loginClock.useFixed(START);

        for (int failure = 1; failure <= 10; failure += 1) {
            loginAttemptLimiter.recordFailure("lock-log-user", "198.51.100.50", CHROME_WINDOWS);
        }

        assertEquals(10, rowCount("status = 1"));
        Map<String, Object> locked = row("status = 2");
        assertEquals("lock-log-user", locked.get("username"));
        assertEquals("198.51.100.50", locked.get("ip"));
        assertEquals("Chrome", locked.get("browser"));
        assertEquals("账号锁定", locked.get("msg"));
    }

    @Test
    void aSourceLockWritesItsOwnLockedRowWithTheScopeInTheMessage() {
        RedisLoginAttemptLimiter limiter = new RedisLoginAttemptLimiter(
                redisTemplate,
                LoginAttemptPolicy.account(),
                new LoginAttemptPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                        2, Duration.ofMinutes(5), Duration.ofMinutes(15)),
                loginAudit,
                loginClock);

        limiter.recordFailure("source-lock-a", "198.51.100.51", CHROME_WINDOWS);
        limiter.recordFailure("source-lock-b", "198.51.100.51", CHROME_WINDOWS);

        Map<String, Object> locked = row("status = 2");
        assertEquals("source-lock-b", locked.get("username"));
        assertEquals("来源锁定", locked.get("msg"));
    }

    @Test
    void anExpiredLockWritesUnlockedRowsForBothScopesWhenTheNextAttemptClearsThem() {
        loginClock.useFixed(START);
        RedisLoginAttemptLimiter limiter = new RedisLoginAttemptLimiter(
                redisTemplate,
                new LoginAttemptPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                        1, Duration.ofMinutes(1), Duration.ofMinutes(15)),
                new LoginAttemptPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                        1, Duration.ofMinutes(1), Duration.ofMinutes(15)),
                loginAudit,
                loginClock);

        limiter.recordFailure("lazy-unlock-user", "198.51.100.52", CHROME_WINDOWS);
        assertTrue(limiter.isBlocked("lazy-unlock-user", "198.51.100.52"));
        assertEquals(2, rowCount("status = 2"));

        loginClock.advance(Duration.ofMinutes(2));
        assertFalse(limiter.isBlocked("lazy-unlock-user", "198.51.100.52"));

        assertEquals(1, rowCount("status = 3 AND msg = '账号解锁'"));
        assertEquals(1, rowCount("status = 3 AND msg = '来源解锁'"));
    }

    @Test
    void manualUnlockClearsTheAccountDimensionAndWritesStatusThree() {
        for (int failure = 1; failure <= 10; failure += 1) {
            loginAttemptLimiter.recordFailure("manual-unlock-user", "198.51.100.53", CHROME_WINDOWS);
        }
        assertTrue(loginAttemptLimiter.isBlocked("manual-unlock-user", "198.51.100.53"));

        loginAttemptLimiter.unlockAccount("manual-unlock-user");

        Map<String, Object> unlocked = row("status = 3");
        assertEquals("manual-unlock-user", unlocked.get("username"));
        assertEquals("手动解锁", unlocked.get("msg"));
        assertFalse(loginAttemptLimiter.isBlocked("manual-unlock-user", "203.0.113.54"),
                "the account dimension must be clear after a manual unlock");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aBlockedAttemptLeavesNoDatabaseRowButDoesLeaveAnApplicationLogLine(CapturedOutput output)
            throws Exception {
        for (int failure = 1; failure <= 10; failure += 1) {
            loginAttemptLimiter.recordFailure("blocked-attempt-user", "198.51.100.55", CHROME_WINDOWS);
        }
        int rowsBefore = rowCount("1 = 1");

        // The correct password never reaches the encoder: the account lock refuses the attempt first.
        mockMvc.perform(post("/admin/system/index/login")
                        .header("X-Forwarded-For", "198.51.100.55")
                        .header("User-Agent", CHROME_WINDOWS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked-attempt-user\",\"password\":\"111111\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));

        assertEquals(rowsBefore, rowCount("1 = 1"), "a blocked attempt must not add a database row");
        assertTrue(output.getOut().contains("login audit event=locked"),
                "the blocked attempt must still leave an application log line");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void aDatabaseOutageStillLetsTheLoginSucceedAndFallsBackToTheApplicationLog(CapturedOutput output)
            throws Exception {
        jdbcTemplate.execute("DROP TABLE sys_login_log");

        login("admin", "111111", "203.0.113.56", CHROME_WINDOWS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        assertTrue(output.getOut().contains("login audit event=success"),
                "a failed write must fall back to the application log");
    }

    @Test
    void theUsernameColumnKeepsAtMostSixtyFourCharacters() throws Exception {
        String username = "u".repeat(80);

        login(username, "wrong-password", "203.0.113.57", CHROME_WINDOWS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));

        Map<String, Object> row = onlyRow();
        assertEquals(64, ((String) row.get("username")).length());
        assertEquals(username.substring(0, 64), row.get("username"));
    }

    @Test
    void aDisabledAccountWritesStatusOneWithTheAccountDisabledMessage() throws Exception {
        login("disabled_user", "111111", "203.0.113.58", CHROME_WINDOWS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(209));

        Map<String, Object> row = onlyRow();
        assertEquals("disabled_user", row.get("username"));
        assertEquals(99L, longValue(row, "user_id"));
        assertEquals(1, intValue(row, "status"));
        assertEquals("账号已停用", row.get("msg"));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String username, String password, String source, String userAgent) throws Exception {
        return mockMvc.perform(post("/admin/system/index/login")
                .header("X-Forwarded-For", source)
                .header("User-Agent", userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private int rowCount(String condition) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_login_log WHERE " + condition, Integer.class);
    }

    private Map<String, Object> row(String condition) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM sys_login_log WHERE " + condition);
        assertEquals(1, rows.size(), "expected exactly one row for " + condition);
        return lowerCaseKeys(rows.get(0));
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sys_login_log");
        assertEquals(1, rows.size(), "exactly one login attempt should produce exactly one row");
        return lowerCaseKeys(rows.get(0));
    }

    private static Map<String, Object> lowerCaseKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new HashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static int intValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private static long longValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }
}
