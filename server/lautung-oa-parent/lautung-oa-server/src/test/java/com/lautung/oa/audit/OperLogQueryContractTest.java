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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作日志查询面的 HTTP 合同（ADR 0054/0057，#214）：真实 Flyway（含 V113）与种子授予，
 * 钉住分页形状与 {@code create_time DESC, id DESC} 排序、每个过滤条件、详情、批量删除
 * （含空列表命名拒绝）、清空、三种数据范围形状、范围外详情不泄漏，以及删除/清空各自
 * 产生一行操作日志（清空行在方法返回后写入，因而存活）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_oper_log_query_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class OperLogQueryContractTest {

    private static final long PAGE_ID = 171L;

    private static final long SELF_ONLY_ID = 172L;

    private static final long NO_DEPT_ID = 173L;

    private static final long ROLE_PAGE_ID = 271L;

    private static final long ROLE_SELF_ID = 272L;

    private static final long ROLE_NO_DEPT_ID = 273L;

    private static final long PARENT_DEPT_ID = 71L;

    private static final long CHILD_DEPT_ID = 72L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @BeforeEach
    void seedFixtures() {
        cleanFixtures();
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, 0, ',0,71,', '总部', 1, 0)", PARENT_DEPT_ID);
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, 71, ',0,71,72,', '研发部', 1, 0)", CHILD_DEPT_ID);

        // 档位 3（本部门）、档位 5（仅本人）、档位 3 但本人无部门（空集）。
        insertRole(ROLE_PAGE_ID, 3);
        insertRole(ROLE_SELF_ID, 5);
        insertRole(ROLE_NO_DEPT_ID, 3);
        insertUser(PAGE_ID, "oper-query-page", PARENT_DEPT_ID);
        insertUser(SELF_ONLY_ID, "oper-query-self", CHILD_DEPT_ID);
        insertUser(NO_DEPT_ID, "oper-query-nodept", null);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ID, ROLE_PAGE_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                SELF_ONLY_ID, ROLE_SELF_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                NO_DEPT_ID, ROLE_NO_DEPT_ID);
        grantPage(ROLE_PAGE_ID);
        grantPage(ROLE_SELF_ID);
        grantPage(ROLE_NO_DEPT_ID);

        // 501/503 在部门 71，502/504 在部门 72；503 与 504 同一 create_time，用 id 做稳定次序。
        insertLog(501L, "用户管理·新增用户", 1, 301L, "operator-alpha", PARENT_DEPT_ID, "总部", 0,
                "2026-05-01 10:00:00", "{\"password\":\"***\"}", 200, "成功", null);
        insertLog(502L, "角色管理·删除角色", 3, 302L, "operator-beta", CHILD_DEPT_ID, "研发部", 1,
                "2026-05-02 10:00:00", "{\"roleId\":9}", null, null, "BusinessException: 删除失败");
        insertLog(503L, "用户管理·修改用户", 2, 303L, "operator-gamma", PARENT_DEPT_ID, "总部", 0,
                "2026-05-03 10:00:00", "{\"userId\":9}", 200, "成功", null);
        insertLog(504L, "菜单管理·新增菜单", 1, 301L, "operator-alpha", CHILD_DEPT_ID, "研发部", 0,
                "2026-05-03 10:00:00", "{\"menuId\":9}", 200, "成功", null);
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
    }

    @Test
    void paginationCarriesTheWindowShapeAndStableDescendingOrder() throws Exception {
        mockMvc.perform(list(1L, 1, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                // 同一 create_time 时 id DESC 决定次序；只按时间排会在这两行上不稳定。
                .andExpect(jsonPath("$.data.records[0].id").value(504))
                .andExpect(jsonPath("$.data.records[1].id").value(503));

        mockMvc.perform(list(1L, 2, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[0].id").value(502))
                .andExpect(jsonPath("$.data.records[1].id").value(501));

        mockMvc.perform(get("/admin/system/sysOperLog/1/1000").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("每页记录数必须在 1 到 500 之间"));
    }

    @Test
    void everyListFilterNarrowsTheSameRead() throws Exception {
        mockMvc.perform(list(1L, 1, 10).param("operName", "operator-alpha"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(501, 504)));

        mockMvc.perform(list(1L, 1, 10).param("title", "用户管理"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(501, 503)));

        mockMvc.perform(list(1L, 1, 10).param("businessType", "3"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(502));

        mockMvc.perform(list(1L, 1, 10).param("status", "1"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(502));

        mockMvc.perform(list(1L, 1, 10)
                        .param("createTimeBegin", "2026-05-02 00:00:00")
                        .param("createTimeEnd", "2026-05-03 23:59:59"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(502, 503, 504)));
    }

    @Test
    void detailCarriesTheWholeRowAndAnUnknownIdIsNull() throws Exception {
        mockMvc.perform(get("/admin/system/sysOperLog/get/501").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.title").value("用户管理·新增用户"))
                .andExpect(jsonPath("$.data.businessType").value(1))
                .andExpect(jsonPath("$.data.operId").value(301))
                .andExpect(jsonPath("$.data.operName").value("operator-alpha"))
                .andExpect(jsonPath("$.data.deptId").value(71))
                .andExpect(jsonPath("$.data.deptName").value("总部"))
                .andExpect(jsonPath("$.data.operParam").value("{\"password\":\"***\"}"))
                .andExpect(jsonPath("$.data.resultCode").value(200))
                .andExpect(jsonPath("$.data.resultMessage").value("成功"))
                .andExpect(jsonPath("$.data.status").value(0));

        // 失败行同样返回 error_msg（排障要看失败原因）。
        mockMvc.perform(get("/admin/system/sysOperLog/get/502").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.resultCode").doesNotExist())
                .andExpect(jsonPath("$.data.errorMsg").value("BusinessException: 删除失败"));

        mockMvc.perform(get("/admin/system/sysOperLog/get/999999").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void threeDataScopeShapesKeepUnrestrictedVisibleDeptAndSelfOnlyDistinct() throws Exception {
        // unrestricted（admin 的 super_admin，data_scope=1）：全部。
        mockMvc.perform(list(1L, 1, 10))
                .andExpect(jsonPath("$.data.total").value(4));

        // visibleDeptIds（档位 3，部门 71）：只含部门 71 的 501/503。
        mockMvc.perform(list(PAGE_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItems(501, 503)))
                .andExpect(jsonPath("$.data.records[*].id", org.hamcrest.Matchers.not(hasItems(502, 504))));

        // selfOnly（档位 5，本人 172）：只含 oper_id=172 的行；夹具里 172 不是操作人，零行。
        mockMvc.perform(list(SELF_ONLY_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(0));

        // 档位 3 但无部门：空集是零行，不是全部。
        mockMvc.perform(list(NO_DEPT_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void selfOnlyScopeSeesTheOwnOperatorRowAndNoOtherRow() throws Exception {
        // 让 172 成为一条日志的操作人，验证 selfOnly 的本人列是 oper_id。
        jdbcTemplate.update("UPDATE sys_oper_log SET oper_id = ? WHERE id = 502", SELF_ONLY_ID);

        mockMvc.perform(list(SELF_ONLY_ID, 1, 10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(502));
    }

    @Test
    void anOutOfScopeDetailDoesNotLeakTheRow() throws Exception {
        // 172 是 selfOnly：501 落在部门 71，但操作人不是 172。
        mockMvc.perform(get("/admin/system/sysOperLog/get/501").header("token", tokenOf(SELF_ONLY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 范围外的 504（部门 72）也不可经详情读到。
        mockMvc.perform(get("/admin/system/sysOperLog/get/504").header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        // 范围内的行仍然可读：172 看自己的操作行。
        jdbcTemplate.update("UPDATE sys_oper_log SET oper_id = ? WHERE id = 502", SELF_ONLY_ID);
        mockMvc.perform(get("/admin/system/sysOperLog/get/502").header("token", tokenOf(SELF_ONLY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("角色管理·删除角色"));
    }

    @Test
    void deleteAndCleanAreNotScopeFiltered() throws Exception {
        // 删除/清空是按 id 与全表的写动作，不接数据范围（ADR 0054：日志模块不自己解析范围）。
        grantButton(ROLE_PAGE_ID, "system:operLog:remove");
        mockMvc.perform(delete("/admin/system/sysOperLog/remove")
                        .header("token", tokenOf(PAGE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[504]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, rowCount("id = 504"), "out-of-scope delete must still work by id");

        grantButton(ROLE_PAGE_ID, "system:operLog:clean");
        mockMvc.perform(delete("/admin/system/sysOperLog/clean").header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        // 503/501 在本人部门内、502 在部门外，都随全表清空一起删掉；只剩清空行。
        assertEquals(1, rowCount(), "clean is a full-table physical delete, not scope filtered");
        assertEquals("操作日志·清空", jdbcTemplate.queryForObject(
                "SELECT title FROM sys_oper_log", String.class));
    }

    @Test
    void pageGrantOpensReadsWhileMissingButtonsStay205() throws Exception {
        // 无页面授予：读也拒绝。
        mockMvc.perform(list(2L, 1, 10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        // 有页面授予、没有按钮点：读 200，删除/清空 205；被 gate 挡下的调用不写审计行。
        mockMvc.perform(list(PAGE_ID, 1, 10))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/admin/system/sysOperLog/remove")
                        .header("token", tokenOf(PAGE_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[501]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        mockMvc.perform(delete("/admin/system/sysOperLog/clean").header("token", tokenOf(PAGE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        assertEquals(4, rowCount(), "a 205 rejection must not leave an audit row");
    }

    @Test
    void batchRemoveDeletesTheRowsAndWritesItsOwnOperLogRow() throws Exception {
        mockMvc.perform(delete("/admin/system/sysOperLog/remove")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[501,502]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(0, rowCount("id IN (501,502)"), "both requested rows must be physically deleted");
        Map<String, Object> audit = singleRowByTitle("操作日志·删除");
        assertEquals(3, ((Number) audit.get("business_type")).intValue());
        assertEquals(0, ((Number) audit.get("status")).intValue());
        assertEquals(200, ((Number) audit.get("result_code")).intValue());
        assertTrue(((String) audit.get("oper_param")).contains("501"), String.valueOf(audit.get("oper_param")));
    }

    @Test
    void anEmptyRemoveListIsANamed201WithoutDeletingAnything() throws Exception {
        mockMvc.perform(delete("/admin/system/sysOperLog/remove")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("删除列表不能为空"));

        assertEquals(4, rowCount("id IN (501,502,503,504)"), "empty list must not delete rows");
        // 失败尝试同样留下审计行（ADR 0054：失败行记尝试）。
        Map<String, Object> failure = singleRowByTitle("操作日志·删除");
        assertEquals(1, ((Number) failure.get("status")).intValue());
    }

    @Test
    void removingOnlyMissingIdsKeepsTheExisting201Family() throws Exception {
        mockMvc.perform(delete("/admin/system/sysOperLog/remove")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[999998,999999]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("失败"));

        assertEquals(4, rowCount("id IN (501,502,503,504)"));
    }

    @Test
    void cleanPhysicallyEmptiesTheTableAndTheClearRowSurvives() throws Exception {
        mockMvc.perform(delete("/admin/system/sysOperLog/clean").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 清空先删全表，切面在方法返回后写入自己的行，所以只剩这一条。
        assertEquals(1, rowCount(), "clear must leave exactly its own audit row");
        Map<String, Object> clearRow = jdbcTemplate.queryForMap("SELECT * FROM sys_oper_log");
        assertEquals("操作日志·清空", clearRow.get("title"));
        assertEquals(6, ((Number) clearRow.get("business_type")).intValue());
        assertEquals(0, ((Number) clearRow.get("status")).intValue());
        assertNotNull(clearRow.get("create_time"));
        assertNull(clearRow.get("error_msg"));
    }

    private MockHttpServletRequestBuilder list(long userId, long page, long limit) {
        return get("/admin/system/sysOperLog/" + page + "/" + limit).header("token", tokenOf(userId));
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

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "日志角色" + id, "oper_log_query_" + id, dataScope);
    }

    private void insertUser(long id, String username, Long deptId) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, dept_id, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, ?, 0)", id, username, username, deptId);
    }

    private void grantPage(long roleId) {
        Integer pageMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/log/operLog' AND type = 1 AND is_deleted = 0",
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

    private void insertLog(long id, String title, int businessType, long operId, String operName,
                           long deptId, String deptName, int status, String createTime,
                           String operParam, Integer resultCode, String resultMessage, String errorMsg) {
        jdbcTemplate.update("INSERT INTO sys_oper_log (id, title, business_type, method, request_method, "
                        + "oper_id, oper_name, dept_id, dept_name, oper_url, oper_ip, oper_param, result_code, "
                        + "result_message, status, error_msg, cost_time, create_time) "
                        + "VALUES (?, ?, ?, 'com.lautung.oa.Sample#write', 'POST', ?, ?, ?, ?, "
                        + "'/admin/system/sample/write', '127.0.0.1', ?, ?, ?, ?, ?, 3, ?)",
                id, title, businessType, operId, operName, deptId, deptName,
                operParam, resultCode, resultMessage, status, errorMsg, createTime);
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_oper_log", Integer.class);
    }

    private int rowCount(String condition) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE " + condition, Integer.class);
    }

    private Map<String, Object> singleRowByTitle(String title) {
        return jdbcTemplate.queryForMap("SELECT * FROM sys_oper_log WHERE title = ?", title);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 271 AND 279");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 171 AND 179");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 171 AND 179");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 71 AND 79");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 271 AND 279");
    }
}
