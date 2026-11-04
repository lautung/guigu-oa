package com.lautung.oa.audit;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录日志查询面的 HTTP 合同（ADR 0054/0057，#216）：真实 Flyway（含 V114/V115）与种子授予，
 * 钉住分页形状与 {@code create_time DESC, id DESC} 排序、每个过滤条件、批量删除（含空列表
 * 命名拒绝）、清空、三种数据范围形状（含未知用户行只在 unrestricted 下可见）、解锁清账号维度
 * 的 Redis 键并双写两张表，以及页面授予开读、缺按钮点 205。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_login_log_query_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class LoginLogQueryContractTest {

    private static final long PAGE_ID = 181L;

    private static final long SELF_ONLY_ID = 182L;

    private static final long NO_DEPT_ID = 183L;

    private static final long LOCKED_ID = 184L;

    private static final long ROLE_PAGE_ID = 281L;

    private static final long ROLE_SELF_ID = 282L;

    private static final long ROLE_NO_DEPT_ID = 283L;

    private static final long PARENT_DEPT_ID = 81L;

    private static final long CHILD_DEPT_ID = 82L;

    private static final String LOCKED_USERNAME = "locked-query-user";

    private static final String ACCOUNT_PREFIX = "login:attempt:account:" + LOCKED_USERNAME;

    private static final String SOURCE_KEY = "login:attempt:source:198.51.100.99:failures";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void seedFixtures() {
        cleanFixtures();
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, 0, ',0,81,', '总部', 1, 0)", PARENT_DEPT_ID);
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, 81, ',0,81,82,', '研发部', 1, 0)", CHILD_DEPT_ID);

        // 档位 3（本部门）、档位 5（仅本人）、档位 3 但本人无部门（空集）。
        insertRole(ROLE_PAGE_ID, 3);
        insertRole(ROLE_SELF_ID, 5);
        insertRole(ROLE_NO_DEPT_ID, 3);
        insertUser(PAGE_ID, "login-query-page", PARENT_DEPT_ID);
        insertUser(SELF_ONLY_ID, "login-query-self", CHILD_DEPT_ID);
        insertUser(NO_DEPT_ID, "login-query-nodept", null);
        insertUser(LOCKED_ID, LOCKED_USERNAME, PARENT_DEPT_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ID, ROLE_PAGE_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                SELF_ONLY_ID, ROLE_SELF_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                NO_DEPT_ID, ROLE_NO_DEPT_ID);
        grantPage(ROLE_PAGE_ID);
        grantPage(ROLE_SELF_ID);
        grantPage(ROLE_NO_DEPT_ID);

        // 601/603 在部门 81，602 在部门 82；604 与 603 同一 create_time，用 id 做稳定次序；
        // 604/605 的 user_id/dept_id 为空，模拟查不到用户的历史失败行。
        insertLog(601L, "login-alpha", 181L, PARENT_DEPT_ID, "203.0.113.10", 0,
                "2026-06-01 10:00:00");
        insertLog(602L, "login-beta", 182L, CHILD_DEPT_ID, "203.0.113.11", 1,
                "2026-06-02 10:00:00");
        insertLog(603L, "login-gamma", 181L, PARENT_DEPT_ID, "203.0.113.12", 2,
                "2026-06-03 10:00:00");
        insertLog(604L, "login-alpha", null, null, "203.0.113.10", 3,
                "2026-06-03 10:00:00");
        insertLog(605L, "ghost-user", null, null, "203.0.113.13", 1,
                "2026-06-04 10:00:00");
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
        resetRedis();
    }

    @Test
    void paginationCarriesTheWindowShapeAndStableDescendingOrder() throws Exception {
        mockMvc.perform(list(1L, 1, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.current").value(1))
                // 同一 create_time 时 id DESC 决定次序；只按时间排会在这两行上不稳定。
                .andExpect(jsonPath("$.data.records[0].id").value(605))
                .andExpect(jsonPath("$.data.records[1].id").value(604));

        mockMvc.perform(list(1L, 2, 2))
                .andExpect(jsonPath("$.data.records[0].id").value(603))
                .andExpect(jsonPath("$.data.records[1].id").value(602));

        mockMvc.perform(list(1L, 3, 2))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].id").value(601));

        mockMvc.perform(get("/admin/system/sysLoginLog/1/1000").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("每页记录数必须在 1 到 500 之间"));
    }

    @Test
    void everyListFilterNarrowsTheSameRead() throws Exception {
        mockMvc.perform(list(1L, 1, 10).param("username", "login-alpha"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(601, 604)));

        mockMvc.perform(list(1L, 1, 10).param("ip", "203.0.113.11"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(602));

        mockMvc.perform(list(1L, 1, 10).param("status", "1"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(602, 605)));

        mockMvc.perform(list(1L, 1, 10).param("status", "3"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(604));

        mockMvc.perform(list(1L, 1, 10)
                        .param("createTimeBegin", "2026-06-02 00:00:00")
                        .param("createTimeEnd", "2026-06-03 23:59:59"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(602, 603, 604)));
    }

    @Test
    void theEventFilterSeparatesRegisterRowsFromLoginRows() throws Exception {
        jdbcTemplate.update("UPDATE sys_login_log SET event = 'register' WHERE id = 605");

        mockMvc.perform(list(1L, 1, 10).param("event", "register"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(605))
                .andExpect(jsonPath("$.data.records[0].event").value("register"));

        mockMvc.perform(list(1L, 1, 10).param("event", "login"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItems(605))));
    }

    @Test
    void aRowCarriesTheWholeRecordAndAnUnknownUserKeepsTheSnapshotEmpty() throws Exception {
        mockMvc.perform(list(1L, 1, 10).param("status", "3"))
                .andExpect(jsonPath("$.data.records[0].username").value("login-alpha"))
                .andExpect(jsonPath("$.data.records[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].deptId").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].ip").value("203.0.113.10"))
                .andExpect(jsonPath("$.data.records[0].event").value("login"))
                .andExpect(jsonPath("$.data.records[0].msg").value("手动解锁"));
    }

    @Test
    void threeDataScopeShapesKeepUnrestrictedVisibleDeptAndSelfOnlyDistinct() throws Exception {
        // unrestricted（admin 的 super_admin，data_scope=1）：全部，含 user_id 为空的历史行。
        mockMvc.perform(list(1L, 1, 10))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(604, 605)));

        // visibleDeptIds（档位 3，部门 81）：只含部门 81 的 601/603。
        mockMvc.perform(list(PAGE_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(601, 603)))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItems(602, 604, 605))));

        // selfOnly（档位 5，本人 182）：本人列是 user_id。
        mockMvc.perform(list(SELF_ONLY_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(602));

        // 档位 3 但无部门：空集是零行，不是全部。
        mockMvc.perform(list(NO_DEPT_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void unknownUserRowsAreVisibleOnlyUnderUnrestricted() throws Exception {
        // 604/605 的 user_id 与 dept_id 都为空：部门集与本人行都不匹配，只有「全部」能看到。
        mockMvc.perform(list(PAGE_ID, 1, 10))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItems("ghost-user"))));

        mockMvc.perform(list(SELF_ONLY_ID, 1, 10))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItems("ghost-user"))));

        mockMvc.perform(list(1L, 1, 10))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(604, 605)));
    }

    @Test
    void deleteAndCleanAreNotScopeFiltered() throws Exception {
        // 删除/清空是按 id 与全表的写动作，不接数据范围（ADR 0054：日志模块不自己解析范围）。
        grantButton(ROLE_PAGE_ID, "system:loginLog:remove");
        mockMvc.perform(delete("/admin/system/sysLoginLog/remove")
                        .header("token", tokenOf(PAGE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[605]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, rowCount("id = 605"), "out-of-scope delete must still work by id");

        grantButton(ROLE_PAGE_ID, "system:loginLog:clean");
        mockMvc.perform(delete("/admin/system/sysLoginLog/clean").header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 601/603 在本人部门内、602 与 604/605 在部门外，都随全表清空一起删掉。
        assertEquals(0, rowCount(), "clean is a full-table physical delete, not scope filtered");
        assertEquals(2, operLogCount(), "remove and clean each keep their own audit row");
        assertEquals("登录日志·清空", jdbcTemplate.queryForObject(
                "SELECT title FROM sys_oper_log WHERE title = '登录日志·清空'", String.class));
    }

    @Test
    void pageGrantOpensReadsWhileMissingButtonsStay205() throws Exception {
        // 无页面授予：读也拒绝。
        mockMvc.perform(list(2L, 1, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        // 有页面授予、没有按钮点：读 200，删除/清空/解锁 205；被 gate 挡下的调用不写审计行。
        mockMvc.perform(list(PAGE_ID, 1, 10))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/admin/system/sysLoginLog/remove")
                        .header("token", tokenOf(PAGE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[601]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        mockMvc.perform(delete("/admin/system/sysLoginLog/clean").header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        mockMvc.perform(post("/admin/system/sysLoginLog/unlock/" + LOCKED_USERNAME)
                        .header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        assertEquals(5, rowCount(), "a 205 rejection must not delete login-log rows");
        assertEquals(0, operLogCount(), "a 205 rejection must not leave an audit row");
        assertEquals(0, rowCount("status = 3 AND username = '" + LOCKED_USERNAME + "'"));
    }

    @Test
    void batchRemoveDeletesTheRowsAndWritesItsOwnOperLogRow() throws Exception {
        mockMvc.perform(delete("/admin/system/sysLoginLog/remove")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[601,602]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(0, rowCount("id IN (601,602)"), "both requested rows must be physically deleted");
        Map<String, Object> audit = singleRowByTitle("登录日志·删除");
        assertEquals(3, ((Number) audit.get("business_type")).intValue());
        assertEquals(0, ((Number) audit.get("status")).intValue());
        assertEquals(200, ((Number) audit.get("result_code")).intValue());
        assertTrue(((String) audit.get("oper_param")).contains("601"), String.valueOf(audit.get("oper_param")));
    }

    @Test
    void anEmptyRemoveListIsANamed201WithoutDeletingAnything() throws Exception {
        mockMvc.perform(delete("/admin/system/sysLoginLog/remove")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("删除列表不能为空"));

        assertEquals(5, rowCount(), "empty list must not delete rows");
        // 失败尝试同样留下审计行（ADR 0054：失败行记尝试）。
        Map<String, Object> failure = singleRowByTitle("登录日志·删除");
        assertEquals(1, ((Number) failure.get("status")).intValue());
    }

    @Test
    void removingOnlyMissingIdsKeepsTheExisting201Family() throws Exception {
        mockMvc.perform(delete("/admin/system/sysLoginLog/remove")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[999998,999999]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("失败"));

        assertEquals(5, rowCount());
    }

    @Test
    void cleanPhysicallyEmptiesTheTableAndRecordsTheClearAction() throws Exception {
        mockMvc.perform(delete("/admin/system/sysLoginLog/clean").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(0, rowCount(), "clean must physically empty the login log table");
        Map<String, Object> clearRow = singleRowByTitle("登录日志·清空");
        assertEquals(6, ((Number) clearRow.get("business_type")).intValue());
        assertEquals(0, ((Number) clearRow.get("status")).intValue());
        assertNotNull(clearRow.get("create_time"));
        assertNull(clearRow.get("error_msg"));
    }

    @Test
    void unlockClearsTheAccountDimensionAndWritesBothTables() throws Exception {
        seedLockKeys();

        mockMvc.perform(post("/admin/system/sysLoginLog/unlock/" + LOCKED_USERNAME)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 账号维度的三个键都被清掉；来源维度是抗撞库阈值，手动解锁不碰它。
        assertNull(redisTemplate.opsForValue().get(ACCOUNT_PREFIX + ":failures"), "failures must be cleared");
        assertNull(redisTemplate.opsForValue().get(ACCOUNT_PREFIX + ":last-failure"), "last-failure must be cleared");
        assertNull(redisTemplate.opsForValue().get(ACCOUNT_PREFIX + ":lock"), "lock marker must be cleared");
        assertEquals("99", redisTemplate.opsForValue().get(SOURCE_KEY), "the source dimension must survive");

        // 登录日志那行记「该账号被解」，user_id 按 username 快照。
        Map<String, Object> unlocked = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_login_log WHERE status = 3 AND username = ?", LOCKED_USERNAME);
        assertEquals(LOCKED_ID, ((Number) unlocked.get("user_id")).longValue());
        assertEquals(PARENT_DEPT_ID, ((Number) unlocked.get("dept_id")).longValue());
        assertEquals("手动解锁", unlocked.get("msg"));

        // 操作日志另记一行「谁点的」。
        Map<String, Object> audit = singleRowByTitle("登录日志·解锁");
        assertEquals(7, ((Number) audit.get("business_type")).intValue());
        assertEquals(0, ((Number) audit.get("status")).intValue());
        assertEquals(1L, ((Number) audit.get("oper_id")).longValue());
    }

    @Test
    void unlockIsGateOnlyAndNotScopeFiltered() throws Exception {
        grantButton(ROLE_PAGE_ID, "system:loginLog:unlock");
        seedLockKeys();

        mockMvc.perform(post("/admin/system/sysLoginLog/unlock/" + LOCKED_USERNAME)
                        .header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertNull(redisTemplate.opsForValue().get(ACCOUNT_PREFIX + ":lock"));
        assertEquals(1, rowCount("status = 3 AND username = '" + LOCKED_USERNAME + "'"));
        assertEquals(1, operLogCount());
    }

    private MockHttpServletRequestBuilder list(long userId, long page, long limit) {
        return get("/admin/system/sysLoginLog/" + page + "/" + limit).header("token", tokenOf(userId));
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin", 0L).token();
    }

    private String tokenOf(long userId) {
        return jwtHelper.createTokenPair(userId, usernameOf(userId), 0L).token();
    }

    private String usernameOf(long userId) {
        return jdbcTemplate.queryForObject("SELECT username FROM sys_user WHERE id = ?", String.class, userId);
    }

    private void seedLockKeys() {
        redisTemplate.opsForValue().set(ACCOUNT_PREFIX + ":failures", "10");
        redisTemplate.opsForValue().set(ACCOUNT_PREFIX + ":last-failure", "1790000000000");
        redisTemplate.opsForValue().set(ACCOUNT_PREFIX + ":lock", "1790000900000");
        redisTemplate.opsForValue().set(SOURCE_KEY, "99");
    }

    private void resetRedis() {
        List.of(ACCOUNT_PREFIX + ":failures", ACCOUNT_PREFIX + ":last-failure",
                ACCOUNT_PREFIX + ":lock", SOURCE_KEY).forEach(key -> redisTemplate.delete(key));
    }

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "登录日志角色" + id, "login_log_query_" + id, dataScope);
    }

    private void insertUser(long id, String username, Long deptId) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, dept_id, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, ?, 0)", id, username, username, deptId);
    }

    private void grantPage(long roleId) {
        Integer pageMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/log/loginLog' AND type = 1 AND is_deleted = 0",
                Integer.class);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, pageMenuId);
    }

    private void grantButton(long roleId, String permission) {
        Integer buttonMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Integer.class, permission);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, buttonMenuId);
    }

    private void insertLog(long id, String username, Long userId, Long deptId, String ip, int status,
                           String createTime) {
        jdbcTemplate.update("INSERT INTO sys_login_log (id, username, user_id, dept_id, ip, browser, os, "
                        + "status, msg, create_time) VALUES (?, ?, ?, ?, ?, 'Chrome', 'Windows', ?, ?, ?)",
                id, username, userId, deptId, ip, status,
                status == 3 ? "手动解锁" : null, createTime);
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_login_log", Integer.class);
    }

    private int rowCount(String condition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_login_log WHERE " + condition, Integer.class);
    }

    private int operLogCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_oper_log", Integer.class);
    }

    private Map<String, Object> singleRowByTitle(String title) {
        return jdbcTemplate.queryForMap("SELECT * FROM sys_oper_log WHERE title = ?", title);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_login_log");
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 281 AND 289");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 181 AND 189");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 181 AND 189");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 81 AND 89");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 281 AND 289");
    }
}
