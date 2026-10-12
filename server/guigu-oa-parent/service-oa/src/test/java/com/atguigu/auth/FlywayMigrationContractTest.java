package com.atguigu.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.atguigu.common.jwt.JwtHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_flyway_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class FlywayMigrationContractTest {

    private static final List<String> BUSINESS_TABLES = List.of(
            "sys_user",
            "sys_role",
            "sys_menu",
            "sys_role_menu",
            "sys_user_role",
            "oa_process_type",
            "oa_process_template",
            "oa_process",
            "oa_process_record");
    private static final String UNPUBLISHED_LEAVE_FORM_PROPS =
            "[{\"type\":\"input\",\"field\":\"reason\",\"title\":\"请假原因\",\"$required\":true,\"props\":{\"placeholder\":\"请输入请假原因\"}},"
                    + "{\"type\":\"input\",\"field\":\"days\",\"title\":\"请假天数\",\"$required\":true,\"props\":{\"type\":\"number\",\"placeholder\":\"请输入请假天数\"}}]";
    private static final String UNPUBLISHED_LEAVE_FORM_OPTIONS =
            "{\"submitBtn\":false,\"form\":{\"labelPosition\":\"right\",\"labelWidth\":\"125px\"}}";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void flywayCreatesBusinessSchemaAndSeedsWhileFlowableCreatesEngineSchema() throws SQLException {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = 'admin' AND id = 1"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = 'user'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE path = '/system' AND parent_id = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu WHERE type = 2"));
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process_template WHERE name = '请假申请' AND status = 1"));
        assertEquals(UNPUBLISHED_LEAVE_FORM_PROPS, jdbcTemplate.queryForObject(
                "SELECT form_props FROM oa_process_template WHERE name = '未发布病假'", String.class));
        assertEquals(UNPUBLISHED_LEAVE_FORM_OPTIONS, jdbcTemplate.queryForObject(
                "SELECT form_options FROM oa_process_template WHERE name = '未发布病假'", String.class));
        assertEquals(0, count("SELECT COUNT(*) FROM oa_process_template WHERE name LIKE '%草稿%'"));

        List<String> tableNames = tableNames();
        BUSINESS_TABLES.forEach(table -> assertTrue(
                tableNames.stream().anyMatch(table::equalsIgnoreCase),
                "Flyway should create business table " + table));
        assertTrue(tableNames.stream().anyMatch("ACT_GE_PROPERTY"::equalsIgnoreCase),
                "Flowable should create its engine tables independently of Flyway");
    }

    @Test
    void configurationMenuGrantsAuthorizeTheMatchingRoleAndUserOperations() throws Exception {
        int roleMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = 'sysRole'", Integer.class);
        int userMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = 'sysUser'", Integer.class);
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, is_deleted) VALUES (1, '角色页权限', 0)");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, is_deleted) VALUES (2, '用户页权限', 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (1, 1, ?, 0)", roleMenuId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (2, 2, ?, 0)", userMenuId);
        jdbcTemplate.execute("ALTER TABLE sys_role_menu ALTER COLUMN id RESTART WITH 100");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (1, 1, 2, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (2, 2, 3, 0)");
        jdbcTemplate.execute("ALTER TABLE sys_user_role ALTER COLUMN id RESTART WITH 100");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (3, 'user-manager', '', '用户管理员', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (4, 'ordinary', '', '普通用户', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (5, 'no-config', '', '无配置用户', 1, 0)");

        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", JwtHelper.createToken(1L, "admin")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", JwtHelper.createToken(2L, "user")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/system/sysMenu/toAssign/1")
                        .header("token", JwtHelper.createToken(2L, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", JwtHelper.createToken(2L, "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":1,\"menuIdList\":[" + roleMenuId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = " + roleMenuId + " AND is_deleted = 0"));
        mockMvc.perform(get("/admin/system/sysMenu/toAssign/1")
                        .header("token", JwtHelper.createToken(3L, "user-manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", JwtHelper.createToken(3L, "user-manager"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":1,\"menuIdList\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysMenu/toAssign/1")
                        .header("token", JwtHelper.createToken(5L, "no-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", JwtHelper.createToken(5L, "no-config"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":1,\"menuIdList\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id = " + roleMenuId + " AND is_deleted = 0"));
        mockMvc.perform(get("/admin/system/sysRole/toAssign/4")
                        .header("token", JwtHelper.createToken(2L, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", JwtHelper.createToken(2L, "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4,\"roleIdList\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", JwtHelper.createToken(3L, "user-manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/toAssign/4")
                        .header("token", JwtHelper.createToken(3L, "user-manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", JwtHelper.createToken(3L, "user-manager"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4,\"roleIdList\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/sysRole/findAll")
                        .header("token", JwtHelper.createToken(3L, "user-manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", JwtHelper.createToken(5L, "no-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/toAssign/4")
                        .header("token", JwtHelper.createToken(5L, "no-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", JwtHelper.createToken(5L, "no-config"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4,\"roleIdList\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 5"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 4 AND role_id = 1"));
        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", JwtHelper.createToken(4L, "ordinary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private List<String> tableNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (tableName != null) {
                    names.add(tableName.toUpperCase(Locale.ROOT));
                }
            }
        }
        return names;
    }
}
