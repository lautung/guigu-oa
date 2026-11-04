package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.security.cache.PermissionCacheKey;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色侧成员维护的 HTTP 合同（#256，ADR 0006/0054/0062）：成员分页与关键字、替换语义
 * （增/删/清空、重复 id、非法 id 先校验后删除）、全局配置不套数据范围、缓存失效、
 * 页面/按钮 205、操作日志恰好一行，以及被移出最后一条角色（含自己移除自己）后下一次请求 205。
 *
 * <p>正向路径依赖真实 Flyway 迁移与 V129 种子授予（{@code super_admin}）；夹具统一
 * {@code t256} 前缀，结束时按前缀清理。205 用例不要求固定消息，但码 + HTTP 200 足以指认分支。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_role_users_256;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysRoleAssignUsersContractTest {

    private static final String PASSWORD = "111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 't256%') "
                + "OR role_id IN (SELECT id FROM sys_role WHERE role_code LIKE 't256%')");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code LIKE 't256%')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 't256%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 't256%'");
        Set<String> keys = redisTemplate.keys(PermissionCacheKey.PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void pageMembersByKeywordAndPaginationWithoutApplyingDataScope() throws Exception {
        long roleId = insertRole("t256_page", 1);
        long first = insertUser("t256-m1", "T256成员甲", "13800002561");
        long second = insertUser("t256-m2", "T256成员乙", "13800002562");
        long third = insertUser("t256-m3", "T256成员丙", "13800002563");
        long outsider = insertUser("t256-out", "T256非成员", "13800002564");
        joinRole(roleId, first);
        joinRole(roleId, second);
        joinRole(roleId, third);

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "2").param("assigned", "true")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].username").value(contains("t256-m1", "t256-m2")))
                .andExpect(jsonPath("$.data.records[*].assigned").value(contains(true, true)));

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "2").param("limit", "2").param("assigned", "true")
                        .header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].username").value(contains("t256-m3")));

        // 关键字口径与用户页一致：用户名 / 姓名 / 手机号任一命中。
        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .param("keyword", "t256-m2").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) second));
        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .param("keyword", "T256成员丙").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) third));
        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .param("keyword", "13800002561").header("token", adminToken()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) first));

        // 候选侧：只返回尚未挂在该角色上的用户。
        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "false")
                        .param("keyword", "t256-").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) outsider))
                .andExpect(jsonPath("$.data.records[0].assigned").value(false));

        // 角色是全局配置：查看者自身的「仅本人」数据范围不能过滤成员列表。
        long viewerRole = insertRole("t256_viewer", 5);
        long viewer = insertUser("t256-viewer", "T256查看者", "13800002565");
        joinRole(viewerRole, viewer);
        grantMenu(viewerRole, menuIdByPath("/system/sysRole"));
        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .header("token", loginToken("t256-viewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].username")
                        .value(contains("t256-m1", "t256-m2", "t256-m3")));
    }

    @Test
    void replacesTheWholeMemberSetAndValidatesBeforeDeleting() throws Exception {
        long roleId = insertRole("t256_replace", 1);
        long first = insertUser("t256-r1", "T256替换甲", "13800002571");
        long second = insertUser("t256-r2", "T256替换乙", "13800002572");
        long third = insertUser("t256-r3", "T256替换丙", "13800002573");
        assignUsers(adminToken(), roleId, List.of(first, second))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(List.of(first, second), activeMembers(roleId));

        // 增 + 删同一提交：整体替换，不是增量追加。
        assignUsers(adminToken(), roleId, List.of(second, third))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(List.of(second, third), activeMembers(roleId));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_role WHERE role_id = " + roleId
                + " AND user_id = " + first + " AND is_deleted = 0"));

        // 重复 id 去重，不产生重复关系行。
        assignUsers(adminToken(), roleId, List.of(third, third, second))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, count("SELECT COUNT(*) FROM sys_user_role WHERE role_id = " + roleId
                + " AND is_deleted = 0"));

        // 空列表是合法的清空。
        assignUsers(adminToken(), roleId, List.of())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(List.of(), activeMembers(roleId));

        // 非法用户先校验后删除：拒绝整个请求，既有成员保持不动。
        assignUsers(adminToken(), roleId, List.of(first))
                .andExpect(jsonPath("$.code").value(200));
        assignUsers(adminToken(), roleId, List.of(999_999L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只能分配已存在的用户"));
        assertEquals(List.of(first), activeMembers(roleId));

        assignUsers(adminToken(), 999_999L, List.of(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("角色不存在"));

        // 用户侧读端点看到的是同一张关系表。
        mockMvc.perform(get("/admin/system/sysRole/toAssign/{userId}", first)
                        .header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assginRoleList[*].id").value(contains((int) roleId)));
    }

    @Test
    void invalidatesThePermissionCacheOfAddedAndRemovedMembersOnly() throws Exception {
        long roleId = insertRole("t256_cache", 1);
        long removed = insertUser("t256-cache-removed", "T256失效移出", "13800002581");
        long added = insertUser("t256-cache-added", "T256失效加入", "13800002582");
        long untouched = insertUser("t256-cache-untouched", "T256失效无关", "13800002583");
        joinRole(roleId, removed);

        String removedKey = PermissionCacheKey.of("t256-cache-removed");
        String addedKey = PermissionCacheKey.of("t256-cache-added");
        String untouchedKey = PermissionCacheKey.of("t256-cache-untouched");
        redisTemplate.opsForValue().set(removedKey, "[{\"authority\":\"system:role:assignUser\"}]");
        redisTemplate.opsForValue().set(addedKey, "[]");
        redisTemplate.opsForValue().set(untouchedKey, "[]");

        assignUsers(adminToken(), roleId, List.of(added))
                .andExpect(jsonPath("$.code").value(200));

        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(removedKey)), "被移出的成员必须失效权限缓存");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(addedKey)), "新加入的成员必须失效权限缓存");
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(untouchedKey)), "无关用户缓存应保留");
    }

    @Test
    void aMemberRemovedFromTheirLastRoleLosesThePageOnTheNextRequest() throws Exception {
        long roleId = insertRole("t256_revoke", 1);
        long member = insertUser("t256-revoke", "T256被移出", "13800002591");
        joinRole(roleId, member);
        grantMenu(roleId, menuIdByPath("/system/sysRole"));
        String token = loginToken("t256-revoke");

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .header("token", token))
                .andExpect(jsonPath("$.code").value(200));

        assignUsers(adminToken(), roleId, List.of())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
    }

    @Test
    void aMemberWhoRemovesThemselvesLosesThePageOnTheNextRequest() throws Exception {
        long roleId = insertRole("t256_self", 1);
        long member = insertUser("t256-self", "T256自己移出", "13800002601");
        joinRole(roleId, member);
        grantMenu(roleId, menuIdByPath("/system/sysRole"));
        grantMenu(roleId, menuIdByPerms("system:role:assignUser"));
        String token = loginToken("t256-self");

        // 先走一次 /info 把权限 JSON 写进 perm:cache:<username>，再验证失效。
        mockMvc.perform(get("/admin/system/index/info").header("token", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.permissions",
                        org.hamcrest.Matchers.hasItem("system:role:assignUser")));
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(PermissionCacheKey.of("t256-self"))));

        assignUsers(token, roleId, List.of())
                .andExpect(jsonPath("$.code").value(200));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(PermissionCacheKey.of("t256-self"))),
                "自己移除自己后必须失效自己的权限缓存");

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        assignUsers(token, roleId, List.of(member))
                .andExpect(jsonPath("$.code").value(205));
    }

    @Test
    void pagesAndButtonsGateTheRoleUserRoutes() throws Exception {
        long roleId = insertRole("t256_gate", 1);
        long member = insertUser("t256-gate-target", "T256网关目标", "13800002611");
        String plainUserToken = loginToken("user");

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .header("token", plainUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        assignUsers(plainUserToken, roleId, List.of(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        long pageRole = insertRole("t256_pageonly", 1);
        long pageUser = insertUser("t256-pageonly", "T256仅页面", "13800002612");
        joinRole(pageRole, pageUser);
        grantMenu(pageRole, menuIdByPath("/system/sysRole"));
        String pageToken = loginToken("t256-pageonly");

        mockMvc.perform(get("/admin/system/sysRole/{id}/users", roleId)
                        .param("page", "1").param("limit", "10").param("assigned", "true")
                        .header("token", pageToken))
                .andExpect(jsonPath("$.code").value(200));
        assignUsers(pageToken, roleId, List.of(member))
                .andExpect(jsonPath("$.code").value(205));

        grantMenu(pageRole, menuIdByPerms("system:role:assignUser"));
        assignUsers(pageToken, roleId, List.of(member))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(List.of(member), activeMembers(roleId));
    }

    @Test
    void recordsExactlyOneAuditRowForARoleMemberAssignment() throws Exception {
        long roleId = insertRole("t256_audit", 1);
        long first = insertUser("t256-a1", "T256审计甲", "13800002621");
        long second = insertUser("t256-a2", "T256审计乙", "13800002622");
        jdbcTemplate.update("DELETE FROM sys_oper_log");

        // 205 不得产生操作日志行（切面在方法安全之内）。
        assignUsers(loginToken("user"), roleId, List.of(first));

        assignUsers(adminToken(), roleId, List.of(first, second))
                .andExpect(jsonPath("$.code").value(200));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM sys_oper_log");
        assertEquals(1, rows.size(), "一次角色分配用户应恰好产生一行操作日志");
        Map<String, Object> row = normalized(rows.get(0));
        assertEquals("角色·分配用户", row.get("title"));
        assertEquals(4, intValue(row, "business_type"));
        assertEquals("com.lautung.oa.auth.controller.SysRoleController#doAssignUsers", row.get("method"));
        assertEquals("POST", row.get("request_method"));
        assertEquals(200, intValue(row, "result_code"));
        assertEquals(0, intValue(row, "status"));
        assertTrue(((String) row.get("oper_param")).contains("\"userIds\""), (String) row.get("oper_param"));
    }

    private ResultActions assignUsers(String token, long roleId, List<Long> userIds) throws Exception {
        return mockMvc.perform(post("/admin/system/sysRole/doAssignUsers")
                .header("token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleId\":" + roleId + ",\"userIds\":" + userIds + "}"));
    }

    private List<Long> activeMembers(long roleId) {
        return jdbcTemplate.queryForList("SELECT user_id FROM sys_user_role WHERE role_id = ? "
                + "AND is_deleted = 0 ORDER BY id", Long.class, roleId);
    }

    private long insertUser(String username, String name, String phone) {
        // 复用种子 admin 的 BCrypt 哈希，插入的用户可以直接用 PASSWORD 登录。
        String hash = jdbcTemplate.queryForObject(
                "SELECT password FROM sys_user WHERE username = 'admin'", String.class);
        jdbcTemplate.update("INSERT INTO sys_user (username, password, name, phone, status, is_deleted) "
                        + "VALUES (?, ?, ?, ?, 1, 0)", username, hash, name, phone);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ? AND is_deleted = 0", Long.class, username);
    }

    private long insertRole(String roleCode, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, 0)", "T256角色", roleCode, dataScope);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ? AND is_deleted = 0", Long.class, roleCode);
    }

    private void joinRole(long roleId, long userId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (role_id, user_id, is_deleted) VALUES (?, ?, 0)",
                roleId, userId);
    }

    private void grantMenu(long roleId, long menuId) {
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, menuId);
    }

    private long menuIdByPath(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND type = 1 AND is_deleted = 0 LIMIT 1",
                Long.class, path);
    }

    private long menuIdByPerms(String perms) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND perms IS NOT NULL AND is_deleted = 0",
                Long.class, perms);
    }

    private String adminToken() throws Exception {
        return loginToken("admin");
    }

    private String loginToken(String username) throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private static Map<String, Object> normalized(Map<String, Object> row) {
        Map<String, Object> normalized = new java.util.HashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private static int intValue(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }
}
