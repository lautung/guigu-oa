package com.lautung.oa.auth;

import com.lautung.oa.common.jwt.JwtHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户导出的 HTTP 合同（#259）：{@code GET /admin/system/sysUser/export} 按与列表相同的
 * 关键字/部门/状态筛选和 DataScope 输出 xlsx，列固定为
 * 用户名、姓名、部门、岗位、手机、状态、创建时间，永不包含密码；按钮点
 * {@code system:user:export} 缺失即 205 且不产生操作日志。
 *
 * <p>用真迁移 + 真种子起库，夹具落在 1xx/2xx/3xx 段，测试结束按段清理。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_user_export;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysUserExportContractTest {

    private static final String EXPORT_PATH = "/admin/system/sysUser/export";

    private static final List<String> HEADERS = List.of(
            "用户名", "姓名", "部门", "岗位", "手机", "状态", "创建时间");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @BeforeEach
    void seedFixtures() {
        cleanFixtures();
        insertDept(10, 0, ",0,10,", 10, 1);
        insertDept(11, 10, ",0,10,11,", 11, 1);
        insertDept(12, 11, ",0,10,11,12,", 12, 1);
        insertDept(20, 0, ",0,20,", 20, 1);
        insertDept(21, 0, ",0,21,", 21, 0);

        insertPost(301, "t259_export_on_a", "岗位A", 1, 1);
        insertPost(302, "t259_export_on_b", "岗位B", 2, 1);
        insertPost(303, "t259_export_off", "岗位C", 3, 0);

        insertRole(201, 1);
        insertRole(202, 3);
        insertRole(203, 5);
        insertRole(205, 1);

        insertUser(101, "t259e-all", 10, 1, "13800000101", Timestamp.valueOf("2026-03-04 05:06:07"));
        insertUser(102, "t259e-own", 11, 1, "13800000102", null);
        insertUser(103, "t259e-child", 11, 0, "13800000103", null);
        insertUser(104, "t259e-self", 12, 1, "13800000104", null);
        insertUser(105, "t259e-other", 20, 1, "13800000105", null);
        insertUser(106, "t259e-disabled-dept", 21, 1, "13800000106", null);
        insertUser(107, "t259e-deleted", 10, 1, "13800000107", null, 1);

        assignRole(101, 201);
        assignRole(102, 202);
        assignRole(104, 203);
        assignRole(105, 205);
        assignPosts(101, 301, 302);
        grantUserPage(201, 202, 203, 205);
        grantButton("system:user:export", 201, 202, 203);
        // 105 只有页面授予、没有导出按钮点：用来钉 205 与「不产操作日志」。
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
    }

    @Test
    void exportsTheAgreedColumnsForTheCurrentFiltersAndNeverThePassword() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_oper_log");

        MvcResult result = mockMvc.perform(get(EXPORT_PATH)
                        .param("keyword", "t259e-")
                        .param("deptId", "10")
                        .header("token", tokenOf(101)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains("attachment"));
        assertTrue(result.getResponse().getHeader("Content-Disposition").contains(".xlsx"));

        List<List<String>> rows = readWorkbook(result.getResponse().getContentAsByteArray());
        assertEquals(HEADERS, rows.get(0), "导出列固定且不含密码/口令版本");
        assertEquals(List.of("t259e-all", "t259e-own", "t259e-child", "t259e-self"),
                rows.subList(1, rows.size()).stream().map(row -> row.get(0)).toList(),
                "部门过滤是子树（含自身），软删用户不导出");

        Map<String, List<String>> byUsername = new LinkedHashMap<>();
        for (List<String> row : rows.subList(1, rows.size())) {
            byUsername.put(row.get(0), row);
        }
        assertEquals(List.of("t259e-all", "用户101", "部门10", "岗位A、岗位B", "13800000101",
                "正常", "2026-03-04 05:06:07"), byUsername.get("t259e-all"));
        assertEquals(List.of("t259e-child", "用户103", "部门11", "", "13800000103",
                "停用", ""), byUsername.get("t259e-child"));

        assertEquals(1, count("SELECT COUNT(*) FROM sys_oper_log WHERE title = '用户·导出'"));
    }

    @Test
    void exportIsLimitedToTheOperatorsDataScope() throws Exception {
        // 档位 3（本部门 11）：自己的部门行；12 的孙层与 20/10 根都不在范围。
        assertEquals(List.of("t259e-own", "t259e-child"),
                exportedUsernames(102, "?keyword=t259e-"));

        // 档位 5（仅本人）：即使部门树过滤命中同部门，也只有自己。
        assertEquals(List.of("t259e-self"), exportedUsernames(104, "?keyword=t259e-&deptId=10"));
    }

    @Test
    void keywordDeptAndStatusFiltersAllApply() throws Exception {
        assertEquals(List.of("t259e-self"), exportedUsernames(101, "?keyword=t259e-sel"));
        assertEquals(List.of("t259e-own", "t259e-child", "t259e-self"),
                exportedUsernames(101, "?keyword=t259e-&deptId=11"));
        assertEquals(List.of("t259e-child"), exportedUsernames(101, "?keyword=t259e-&status=0"));
        assertEquals(List.of(), exportedUsernames(101, "?keyword=t259e-&deptId=99999"),
                "部门不存在时子树为空，不得退化成整表");
    }

    @Test
    void exportRequiresTheButtonPointAndLeavesNoAuditRowWhenRejected() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_oper_log");

        mockMvc.perform(get(EXPORT_PATH)
                        .param("keyword", "t259e-")
                        .header("token", tokenOf(105)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        assertEquals(0, count("SELECT COUNT(*) FROM sys_oper_log"),
                "被 gate 挡下的导出不得产生操作日志行");
    }

    private List<String> exportedUsernames(long userId, String query) throws Exception {
        MvcResult result = mockMvc.perform(get(EXPORT_PATH + query).header("token", tokenOf(userId)))
                .andExpect(status().isOk())
                .andReturn();
        List<List<String>> rows = readWorkbook(result.getResponse().getContentAsByteArray());
        return rows.subList(1, rows.size()).stream().map(row -> row.get(0)).toList();
    }

    private List<List<String>> readWorkbook(byte[] content) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                for (int index = 0; index < HEADERS.size(); index++) {
                    values.add(cellText(row, index));
                }
                rows.add(values);
            }
        }
        return rows;
    }

    private static String cellText(Row row, int index) {
        if (row.getCell(index) == null) {
            return "";
        }
        return row.getCell(index).getStringCellValue();
    }

    private String tokenOf(long userId) {
        return jwtHelper.createTokenPair(userId, usernameOf(userId), 0L).token();
    }

    private String usernameOf(long userId) {
        return jdbcTemplate.queryForObject("SELECT username FROM sys_user WHERE id = ?", String.class, userId);
    }

    private void insertDept(long id, long parentId, String ancestors, int sortValue, int status) {
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, sort_value, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", id, parentId, ancestors, "部门" + id, sortValue, status);
    }

    private void insertPost(long id, String postCode, String name, int sortValue, int status) {
        jdbcTemplate.update("INSERT INTO sys_post (id, post_code, name, sort_value, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, ?, 0)", id, postCode, name, sortValue, status);
    }

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "导出角色" + id, "t259_export_role_" + id, dataScope);
    }

    private void insertUser(long id, String username, Integer deptId, int status, String phone,
                            Timestamp createTime) {
        insertUser(id, username, deptId, status, phone, createTime, 0);
    }

    private void insertUser(long id, String username, Integer deptId, int status, String phone,
                            Timestamp createTime, int deleted) {
        jdbcTemplate.update("INSERT INTO sys_user "
                        + "(id, username, password, name, status, dept_id, phone, create_time, is_deleted) "
                        + "VALUES (?, ?, 'secret-hash', ?, ?, ?, ?, ?, ?)",
                id, username, "用户" + id, status, deptId, phone, createTime, deleted);
    }

    private void assignRole(long userId, long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                userId, roleId);
    }

    private void assignPosts(long userId, long... postIds) {
        for (long postId : postIds) {
            jdbcTemplate.update("INSERT INTO sys_user_post (user_id, post_id, is_deleted) VALUES (?, ?, 0)",
                    userId, postId);
        }
    }

    private void grantUserPage(long... roleIds) {
        int userPage = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysUser' AND type = 1 AND is_deleted = 0",
                Integer.class);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, userPage);
        }
    }

    private void grantButton(String permission, long... roleIds) {
        int button = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Integer.class, permission);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, button);
        }
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id BETWEEN 201 AND 299 "
                + "OR user_id BETWEEN 101 AND 199");
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id BETWEEN 101 AND 199 "
                + "OR post_id BETWEEN 301 AND 399");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 101 AND 199 "
                + "OR username LIKE 't259e_%'");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 10 AND 99");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_post WHERE id BETWEEN 301 AND 399");
    }
}
