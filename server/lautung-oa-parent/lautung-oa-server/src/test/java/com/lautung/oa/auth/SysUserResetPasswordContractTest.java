package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员重置密码的 HTTP 合同（#250，ADR 0029/0054/0062）：按钮点 system:user:resetPwd 缺失即 205；
 * 良构体写新哈希并推进 token_version，目标用户改密前的 access/refresh 立即 208，操作者不受影响；
 * 操作日志恰好一行、参数里的 newPassword 已打码、响应与日志都不回显明文；
 * 目标用户不存在时按现有 update 语义静默成功，停用用户可重置但仍不能登录。
 *
 * <p>正向路径依赖真实 Flyway 迁移与 V125 种子授予（super_admin），不是最小 schema 的恒拒绝场景。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_reset_pwd_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysUserResetPasswordContractTest {

    private static final String OLD_PASSWORD = "111111";

    private static final String NEW_PASSWORD = "654321";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'reset_pwd_%'");
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 0 WHERE id IN (1, 2)");
    }

    @Test
    void rejectsAResetFromAUserWithoutTheButtonPointBeforeAnyChange() throws Exception {
        String adminToken = loginToken("admin");
        String targetUsername = "reset_pwd_denied";
        long targetId = saveUser(adminToken, targetUsername);
        String targetHash = passwordHash(targetId);
        jdbcTemplate.update("DELETE FROM sys_oper_log");

        mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", targetId)
                        .header("token", loginToken("user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        assertEquals(0L, tokenVersion(targetId), "205 不得推进目标用户的令牌版本");
        assertEquals(targetHash, passwordHash(targetId), "205 不得写新哈希");
        assertEquals(0, operLogCount(), "被 gate 挡下的请求不得产生操作日志行");
    }

    @Test
    void resetsTheTargetPasswordRetiresEveryOldSessionAndKeepsTheOperatorIn() throws Exception {
        String adminToken = loginToken("admin");
        String targetUsername = "reset_pwd_success";
        long targetId = saveUser(adminToken, targetUsername);
        String oldLogin = loginBody(targetUsername, OLD_PASSWORD);
        String oldAccessToken = JsonPath.read(oldLogin, "$.data.token");
        String oldRefreshToken = JsonPath.read(oldLogin, "$.data.refreshToken");

        String response = mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", targetId)
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertFalse(response.contains(NEW_PASSWORD), "响应不得回显明文密码: " + response);

        assertEquals(1L, tokenVersion(targetId), "重置密码必须推进目标用户的令牌版本");
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, passwordHash(targetId)),
                "新密码必须以 BCrypt 哈希落库");

        // 改密前签发的 access/refresh 都立即作废（ADR 0029 的同一机制）。
        mockMvc.perform(get("/admin/system/index/info").header("token", oldAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208))
                .andExpect(jsonPath("$.message").value("认证失败"));

        // 只影响目标用户：发起重置的管理员仍可继续操作。
        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 新口令签发的新令牌可用；旧口令不再登录。
        String freshToken = loginToken(targetUsername, NEW_PASSWORD);
        mockMvc.perform(get("/admin/system/index/info").header("token", freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"" + OLD_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    void recordsExactlyOneAuditRowWithThePasswordMasked() throws Exception {
        String adminToken = loginToken("admin");
        long targetId = saveUser(adminToken, "reset_pwd_audit");
        jdbcTemplate.update("DELETE FROM sys_oper_log");

        mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", targetId)
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Map<String, Object> row = onlyOperRow();
        assertEquals("用户·重置密码", row.get("title"));
        assertEquals(2, intValue(row, "business_type"));
        assertEquals("com.lautung.oa.auth.controller.SysUserController#resetPassword", row.get("method"));
        assertEquals("POST", row.get("request_method"));
        assertEquals(200, intValue(row, "result_code"));
        assertEquals(0, intValue(row, "status"));

        String param = (String) row.get("oper_param");
        assertTrue(param.contains("\"newPassword\":\"***\""), param);
        assertFalse(param.contains(NEW_PASSWORD), "操作日志不得回显明文密码: " + param);
    }

    @Test
    void treatsAMissingTargetAsANoOpLikeTheExistingUserUpdate() throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", 999_999L)
                        .header("token", loginToken("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id = 999999", Integer.class).intValue());
    }

    @Test
    void resetsADisabledUsersPasswordWithoutEnablingThem() throws Exception {
        String adminToken = loginToken("admin");
        String targetUsername = "reset_pwd_disabled";
        long targetId = saveUser(adminToken, targetUsername);

        mockMvc.perform(put("/admin/system/sysUser/updateStatus/{id}/{status}", targetId, 0)
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", targetId)
                        .header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1L, tokenVersion(targetId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE id = ?", Integer.class, targetId).intValue(),
                "重置密码不是停用/启用动作，用户状态保持不变");
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(209));
    }

    @Test
    void rejectsBlankAndShortPasswordsWithFieldMessages() throws Exception {
        // 校验先于 gate（ADR 0034）：用没有后台授予的 user 令牌也得到 201，而不是 205。
        String plainUserToken = loginToken("user");
        long targetId = userIdOf("admin");

        mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", targetId)
                        .header("token", plainUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码长度必须在 6 到 100 位之间"));

        // 六个空格：@NotBlank 拒绝，长度恰好不触发 @Size，消息唯一。
        mockMvc.perform(post("/admin/system/sysUser/{id}/resetPassword", targetId)
                        .header("token", plainUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"      \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    private String loginToken(String username) throws Exception {
        return loginToken(username, OLD_PASSWORD);
    }

    private String loginToken(String username, String password) throws Exception {
        return JsonPath.read(loginBody(username, password), "$.data.token");
    }

    private String loginBody(String username, String password) throws Exception {
        return mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** 建号并返回新 id；用户名唯一与密码必填由既有 save 合同覆盖，这里只服务重置路径。 */
    private long saveUser(String token, String username) throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"重置目标\",\"password\":\""
                                + OLD_PASSWORD + "\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return userIdOf(username);
    }

    private long userIdOf(String username) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ? AND is_deleted = 0", Long.class, username);
        assertTrue(id != null);
        return id;
    }

    private long tokenVersion(long userId) {
        Long version = jdbcTemplate.queryForObject(
                "SELECT token_version FROM sys_user WHERE id = ?", Long.class, userId);
        assertTrue(version != null);
        return version;
    }

    private String passwordHash(long userId) {
        return jdbcTemplate.queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, userId);
    }

    private int operLogCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_oper_log", Integer.class);
    }

    private Map<String, Object> onlyOperRow() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sys_oper_log");
        assertEquals(1, rows.size(), "一次重置密码应恰好产生一行操作日志");
        Map<String, Object> normalized = new java.util.HashMap<>();
        rows.get(0).forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static int intValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }
}
