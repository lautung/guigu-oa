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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户导入的 HTTP 合同（#259）：模板列与导出一致；逐行校验用户名必填/唯一、部门与岗位
 * 存在且启用；失败行不写、成功与失败行号/原因汇总返回；已有用户不被覆盖；xlsx 类型、
 * 2MB 上限与畸形文件在解析前拒绝；缺按钮点 205 且不写任何行。
 *
 * <p>用真迁移 + 真种子起库；夹具落在 1xx/2xx/3xx 段，测试结束按段清理。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_user_import;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysUserImportContractTest {

    private static final List<String> HEADERS = List.of(
            "用户名", "姓名", "部门", "岗位", "手机", "状态", "创建时间");

    private static final String IMPORT_PATH = "/admin/system/sysUser/import";

    private static final String TEMPLATE_PATH = "/admin/system/sysUser/importTemplate";

    private static final String DEFAULT_PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedFixtures() {
        cleanFixtures();
        insertDept(10, 0, ",0,10,", 10, 1);
        insertDept(11, 10, ",0,10,11,", 11, 1);
        insertDept(21, 0, ",0,21,", 21, 0);

        insertPost(301, "t259_import_on", "岗位A", 1, 1);
        insertPost(303, "t259_import_off", "岗位C", 3, 0);

        insertRole(201, 1);
        insertRole(202, 1);
        insertUser(101, "t259i-operator", 10, 1);
        insertUser(102, "t259i-existing", 10, 1);
        insertUser(104, "t259i-denied", 10, 1);
        assignRole(101, 201);
        assignRole(104, 202);
        grantUserPage(201, 202);
        grantButton("system:user:import", 201);
        // 104 只有页面授予、没有导入按钮点：用来钉 205 与「不写行」。
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
    }

    @Test
    void templateHasTheSameColumnsAsExportAndNoDataRows() throws Exception {
        byte[] content = mockMvc.perform(get(TEMPLATE_PATH).header("token", tokenOf(101)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(HEADERS, rowValues(sheet.getRow(0), HEADERS.size()));
            assertEquals(0, sheet.getLastRowNum(), "模板只有表头，没有示例用户行");
        }
    }

    @Test
    void importsValidRowsAndReportsEachFailedRowWithoutWritingIt() throws Exception {
        byte[] file = workbook(List.<String[]>of(
                new String[]{"t259i-ok", "导入用户", "部门10", "岗位A", "13800000111", "正常", "2026-01-01"},
                new String[]{"", "无用户名", "", "", "", "正常", ""},
                new String[]{"t259i-existing", "覆盖尝试", "部门10", "", "", "正常", ""},
                new String[]{"t259i-bad-dept", "坏部门", "不存在部门", "", "", "正常", ""},
                new String[]{"t259i-off-dept", "停用部门", "部门21", "", "", "正常", ""},
                new String[]{"t259i-off-post", "停用岗位", "部门10", "岗位C", "", "正常", ""},
                new String[]{"t259i-ok", "重复用户名", "部门10", "", "", "正常", ""},
                new String[]{"t259i-second", "第二人", "", "", "", "", ""},
                new String[]{"t259i-bad-name", "", "", "", "", "正常", ""}));

        mockMvc.perform(multipart(IMPORT_PATH)
                        .file(new MockMultipartFile("file", "users.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file))
                        .header("token", tokenOf(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(9))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failureCount").value(7))
                .andExpect(jsonPath("$.data.failures[0].rowNumber").value(3))
                .andExpect(jsonPath("$.data.failures[0].reason").value("用户名不能为空"))
                .andExpect(jsonPath("$.data.failures[1].rowNumber").value(4))
                .andExpect(jsonPath("$.data.failures[1].reason").value("用户名已存在"))
                .andExpect(jsonPath("$.data.failures[2].reason").value("部门不存在：不存在部门"))
                .andExpect(jsonPath("$.data.failures[3].reason").value("部门已停用：部门21"))
                .andExpect(jsonPath("$.data.failures[4].reason").value("岗位已停用：岗位C"))
                .andExpect(jsonPath("$.data.failures[5].reason").value("用户名已存在"))
                .andExpect(jsonPath("$.data.failures[6].rowNumber").value(10))
                .andExpect(jsonPath("$.data.failures[6].reason").value("姓名不能为空"));

        // 成功行落库：部门/岗位/手机/状态按行值，初始密码 BCrypt 后写入。
        Map<String, Object> created = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_user WHERE username = 't259i-ok' AND is_deleted = 0");
        assertEquals(10L, ((Number) created.get("dept_id")).longValue());
        assertEquals("导入用户", created.get("name"));
        assertEquals("13800000111", created.get("phone"));
        assertEquals(1, ((Number) created.get("status")).intValue());
        assertTrue(passwordEncoder.matches(DEFAULT_PASSWORD, (String) created.get("password")),
                "导入用户必须写入 BCrypt 初始密码");
        assertEquals(List.of(301L), jdbcTemplate.queryForList(
                "SELECT post_id FROM sys_user_post WHERE user_id = ?", Long.class, created.get("id")));

        Map<String, Object> second = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_user WHERE username = 't259i-second' AND is_deleted = 0");
        assertNull(second.get("dept_id"));
        assertEquals(1, ((Number) second.get("status")).intValue(), "空状态按正常处理");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_post WHERE user_id = ?", Integer.class, second.get("id")));

        // 已有用户不被覆盖；失败行一个都不写。
        assertEquals("用户102", jdbcTemplate.queryForObject(
                "SELECT name FROM sys_user WHERE username = 't259i-existing'", String.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username IN "
                        + "('t259i-bad-dept','t259i-off-dept','t259i-off-post','t259i-bad-name')",
                Integer.class));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE title = '用户·导入'", Integer.class));
    }

    @Test
    void rejectsFilesThatAreNotXlsxBeforeReadingRows() throws Exception {
        byte[] validXlsx = workbook(List.of());

        assertRejected(new MockMultipartFile("file", "users.csv", "text/csv", validXlsx),
                "仅支持 xlsx 文件");
        assertRejected(new MockMultipartFile("file", "users.xlsx", "application/octet-stream",
                "not an excel file".getBytes(StandardCharsets.UTF_8)), "导入文件不是有效的 Excel 文件");
        assertRejected(new MockMultipartFile("file", "users.xlsx", "application/octet-stream",
                new byte[]{'P', 'K', 3, 4, 1, 2, 3}), "导入文件不是有效的 Excel 文件");
        byte[] oversized = new byte[(int) (2L * 1024L * 1024L + 1)];
        oversized[0] = 'P';
        oversized[1] = 'K';
        oversized[2] = 3;
        oversized[3] = 4;
        assertRejected(new MockMultipartFile("file", "big.xlsx", "application/octet-stream", oversized),
                "导入文件超过 2 MB 限制");
        assertRejected(new MockMultipartFile("file", "empty.xlsx", "application/octet-stream",
                new byte[0]), "导入文件不能为空");
        mockMvc.perform(multipart(IMPORT_PATH).header("token", tokenOf(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("导入文件不能为空"));
    }

    @Test
    void rejectsWrongTemplatesAndEmptyFilesWithNamedMessages() throws Exception {
        // 只有两列的表头仍然是一个能解析的 xlsx：拒绝理由必须是模板不正确，不是「文件坏了」。
        byte[] onlyTwoColumns = workbookWithHeaders(List.of("用户名", "姓名"));
        assertRejected(new MockMultipartFile("file", "wrong.xlsx", "application/octet-stream",
                onlyTwoColumns), "导入文件模板不正确");

        byte[] headerOnly = workbook(List.of());
        assertRejected(new MockMultipartFile("file", "empty-data.xlsx", "application/octet-stream",
                headerOnly), "导入文件没有数据");
    }

    @Test
    void importRequiresTheButtonPointAndWritesNoRowWhenRejected() throws Exception {
        byte[] file = workbook(List.<String[]>of(
                new String[]{"t259i-denied-row", "无权限", "", "", "", "正常", ""}));

        mockMvc.perform(multipart(IMPORT_PATH)
                        .file(new MockMultipartFile("file", "users.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file))
                        .header("token", tokenOf(104)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 't259i-denied-row'", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_oper_log WHERE title = '用户·导入'", Integer.class));
    }

    private void assertRejected(MockMultipartFile file, String message) throws Exception {
        mockMvc.perform(multipart(IMPORT_PATH).file(file).header("token", tokenOf(101)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(message));
    }

    private byte[] workbook(List<String[]> rows) throws Exception {
        return workbookWithHeadersAndRows(HEADERS, rows);
    }

    private byte[] workbookWithHeaders(List<String> headers) throws Exception {
        return workbookWithHeadersAndRows(headers, List.of());
    }

    private byte[] workbookWithHeadersAndRows(List<String> headers, List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("用户");
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                String[] values = rows.get(index);
                for (int column = 0; column < values.length; column++) {
                    row.createCell(column).setCellValue(values[column]);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static List<String> rowValues(Row row, int size) {
        List<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < size; index++) {
            values.add(row.getCell(index) == null ? "" : row.getCell(index).getStringCellValue());
        }
        return values;
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
                + "VALUES (?, ?, ?, ?, 0)", id, "导入角色" + id, "t259_import_role_" + id, dataScope);
    }

    private void insertUser(long id, String username, Integer deptId, int status) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, dept_id, is_deleted) "
                + "VALUES (?, ?, 'secret-hash', ?, ?, ?, 0)", id, username, "用户" + id, status, deptId);
    }

    private void assignRole(long userId, long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                userId, roleId);
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

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id BETWEEN 201 AND 299 "
                + "OR user_id BETWEEN 101 AND 199");
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id BETWEEN 101 AND 199 "
                + "OR post_id BETWEEN 301 AND 399");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 101 AND 199 "
                + "OR username LIKE 't259i-%'");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 10 AND 99");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_post WHERE id BETWEEN 301 AND 399");
    }
}
