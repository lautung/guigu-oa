package com.lautung.oa.auth;

import db.migration.V110__BackofficePermissionBase;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lautung.oa.common.jwt.JwtHelper;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
            "sys_dept",
            "sys_post",
            "sys_user_post",
            "sys_role_dept",
            "oa_process_type",
            "oa_process_template",
            "oa_process",
            "oa_process_record",
            "oa_attachment",
            "sys_oper_log",
            "sys_login_log");
    private static final List<String> BUTTON_PERMS = List.of(
            "system:user:add", "system:user:edit", "system:user:remove", "system:user:assignRole",
            "system:role:add", "system:role:edit", "system:role:remove", "system:role:assignAuth",
            "system:menu:add", "system:menu:edit", "system:menu:remove",
            "process:type:add", "process:type:edit", "process:type:remove",
            "process:template:add", "process:template:edit", "process:template:remove",
            "process:template:upload", "process:template:publish",
            "system:operLog:remove", "system:operLog:clean");
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

    @Autowired
    private JwtHelper jwtHelper;

    @AfterEach
    void clearBackofficeGrantFixtures() {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN (101, 102, 103)");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id IN (101, 102, 103)");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id IN (900, 901, 902, 1000, 1001)");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id IN (101, 102, 103)");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN ('user-manager', 'ordinary', 'no-config', 'menu-manager')");
    }

    @Test
    void flywayCreatesBusinessSchemaAndSeedsWhileFlowableCreatesEngineSchema() throws SQLException {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '102' AND success = TRUE"));
        // ADR 0031：公共字段默认值与引擎历史回填都必须真的跑过。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '103' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '104' AND success = TRUE"));
        // ADR 0032：发起提交标识的列与按用户唯一索引。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '105' AND success = TRUE"));
        // 权限底座（#221）：组织表、data_scope、菜单 0/1/2 与 super_admin 种子。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '110' AND success = TRUE"));
        // 后台页面路径绝对化（#222）。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '111' AND success = TRUE"));
        // 操作日志表（#213，ADR 0054）。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '112' AND success = TRUE"));
        // 日志管理菜单与操作日志按钮点（#214）。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '113' AND success = TRUE"));
        // 登录日志表（#215，ADR 0054）。
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '114' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = 'log' AND c.type = 0 AND p.path = '/system' AND p.type = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/log/operLog' AND c.type = 1 "
                + "AND c.component = 'system/operLog/list' AND p.path = 'log' AND p.type = 0"));
        assertEquals(2, count("SELECT COUNT(*) FROM sys_menu b JOIN sys_menu p ON p.id = b.parent_id "
                + "WHERE b.type = 2 AND b.is_deleted = 0 AND p.path = '/system/log/operLog' "
                + "AND b.perms IN ('system:operLog:remove', 'system:operLog:clean')"));
        assertEquals(4, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                + "AND (m.path IN ('log', '/system/log/operLog') "
                + "OR m.perms IN ('system:operLog:remove', 'system:operLog:clean'))"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu WHERE type = 1 AND path IN "
                + "('sysUser', 'sysRole', 'sysMenu', 'assignAuth') AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'oa_process' AND LOWER(COLUMN_NAME) = 'idempotency_key'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE token_version <> 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'sys_role' AND LOWER(COLUMN_NAME) = 'data_scope'"));
        assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'sys_user' AND LOWER(COLUMN_NAME) = 'dept_id'"));
        assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'sys_user' AND LOWER(COLUMN_NAME) = 'post_id'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 'config_admin' AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 'super_admin' AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 'config_admin' AND data_scope = 4"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 'super_admin' AND data_scope = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                + "WHERE r.role_code = 'config_admin' AND ur.is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                + "WHERE ur.user_id = 1 AND r.role_code = 'super_admin' AND ur.is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'config_admin' AND m.path = '/system/processSet/templateSet' AND rm.is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = 'admin' AND id = 1"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = 'user'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE path = '/system' AND parent_id = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu WHERE path IN ('/processSet', '/processMgr') AND is_deleted = 0"));
        assertEquals(BUTTON_PERMS.size(), count("SELECT COUNT(*) FROM sys_menu WHERE type = 2 AND is_deleted = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu c LEFT JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.type = 2 AND c.is_deleted = 0 AND (p.id IS NULL OR p.type <> 1 "
                + "OR COALESCE(c.path, '') <> '' OR COALESCE(c.component, '') <> '' "
                + "OR COALESCE(c.perms, '') = '')"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = 'processSet' AND c.type = 0 AND p.path = '/system' AND p.type = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = 'processMgr' AND c.type = 0 AND p.path = '/system' AND p.type = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/processSet/processType' AND c.type = 1 AND p.path = 'processSet'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/processSet/processTemplate' AND c.type = 1 AND p.path = 'processSet'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/processSet/templateSet' AND c.type = 1 AND p.path = '/system/processSet/processTemplate'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu p ON p.id = c.parent_id "
                + "WHERE c.path = '/system/processMgr/process' AND c.type = 1 AND p.path = 'processMgr'"));
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
    void adminInfoCarriesSeededPermissionsAndAbsoluteMenusOnTheNewContract() throws Exception {
        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles", hasItem("super_admin")))
                .andExpect(jsonPath("$.data.permissions", hasItems(BUTTON_PERMS.toArray(new String[0]))))
                .andExpect(jsonPath("$.data.menus[*].path", hasItems(
                        "/system",
                        "/system/sysUser",
                        "/system/sysRole",
                        "/system/assignAuth",
                        "/system/processSet",
                        "/system/processSet/processType",
                        "/system/processSet/processTemplate",
                        "/system/processSet/templateSet",
                        "/system/processMgr",
                        "/system/processMgr/process",
                        "/system/log",
                        "/system/log/operLog")))
                .andExpect(jsonPath("$.data.menus[*].path", not(hasItem("/processSet"))))
                .andExpect(jsonPath("$.data.menus[*].path", not(hasItem("/processMgr"))))
                .andExpect(jsonPath("$.data.configMenus").doesNotExist())
                .andExpect(jsonPath("$.data.buttons").doesNotExist());
    }

    @Test
    void reapplyingThePermissionBaseMigrationKeepsSeedRowsStable() throws Exception {
        int menus = count("SELECT COUNT(*) FROM sys_menu");
        int buttons = count("SELECT COUNT(*) FROM sys_menu WHERE type = 2");
        int grants = count("SELECT COUNT(*) FROM sys_role_menu");
        int userRoles = count("SELECT COUNT(*) FROM sys_user_role");
        int roles = count("SELECT COUNT(*) FROM sys_role");

        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V110__BackofficePermissionBase().migrate(context);
            // 日志管理种子是纯 SQL：重复执行也不得多出菜单/按钮/授予行。
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V113__audit_oper_log_menu.sql"));
        }

        assertEquals(menus, count("SELECT COUNT(*) FROM sys_menu"));
        assertEquals(buttons, count("SELECT COUNT(*) FROM sys_menu WHERE type = 2"));
        assertEquals(grants, count("SELECT COUNT(*) FROM sys_role_menu"));
        assertEquals(userRoles, count("SELECT COUNT(*) FROM sys_user_role"));
        assertEquals(roles, count("SELECT COUNT(*) FROM sys_role"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu WHERE path IN ('/processSet', '/processMgr') AND is_deleted = 0"));
    }

    @Test
    void menuMaintenanceAcceptsButtonsOnlyUnderAPageWithPerms() throws Exception {
        int userPageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysUser' AND type = 1", Integer.class);
        int systemRootId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system' AND parent_id = 0", Integer.class);

        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试按钮\",\"parentId\":" + userPageId
                                + ",\"type\":2,\"path\":\"\",\"component\":\"\",\"perms\":\"system:user:test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE perms = 'system:user:test' AND type = 2"));
        jdbcTemplate.update("DELETE FROM sys_menu WHERE perms = 'system:user:test'");

        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"按钮挂目录\",\"parentId\":" + systemRootId
                                + ",\"type\":2,\"path\":\"\",\"component\":\"\",\"perms\":\"system:user:test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("按钮必须挂在页面下"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu WHERE perms = 'system:user:test'"));

        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"按钮缺标识\",\"parentId\":" + userPageId
                                + ",\"type\":2,\"path\":\"\",\"component\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("按钮权限标识不能为空"));
    }

    @Test
    void configurationMenuGrantsAuthorizeTheMatchingRoleAndUserOperations() throws Exception {
        int roleMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysRole'", Integer.class);
        int userMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysUser'", Integer.class);
        int menuAdminMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysMenu'", Integer.class);
        int processTypeMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/processSet/processType'", Integer.class);
        int systemRootId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system' AND parent_id = 0", Integer.class);
        int roleAssignAuthId = buttonId("system:role:assignAuth");
        int userAssignRoleId = buttonId("system:user:assignRole");
        int menuAddId = buttonId("system:menu:add");
        int menuEditId = buttonId("system:menu:edit");
        int menuRemoveId = buttonId("system:menu:remove");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, is_deleted) VALUES (101, '角色页权限', 0)");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, is_deleted) VALUES (102, '用户页权限', 0)");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, is_deleted) VALUES (103, '菜单页权限', 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (101, 101, ?, 0)", roleMenuId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (102, 102, ?, 0)", userMenuId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (103, 103, ?, 0)", menuAdminMenuId);
        jdbcTemplate.update(
                "INSERT INTO sys_menu (id, parent_id, name, type, path, status, is_deleted) VALUES (1000, ?, '角色新增按钮', 2, '', 1, 0)",
                roleMenuId);
        jdbcTemplate.update(
                "INSERT INTO sys_menu (id, parent_id, name, type, path, component, status, is_deleted) VALUES (900, 0, '办事', 0, '/work', 'Layout', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_menu (id, parent_id, name, type, path, component, status, is_deleted) VALUES (901, 900, '办事设置', 1, 'settings', 'system/settings/list', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_menu (id, parent_id, name, type, path, status, is_deleted) VALUES (902, 0, '坏类型', 3, 'badType', 1, 0)");
        jdbcTemplate.execute("ALTER TABLE sys_role_menu ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (101, 101, 2, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (102, 102, 3, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (103, 103, 6, 0)");
        jdbcTemplate.execute("ALTER TABLE sys_user_role ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (3, 'user-manager', '', '用户管理员', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (4, 'ordinary', '', '普通用户', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (5, 'no-config', '', '无配置用户', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (6, 'menu-manager', '', '菜单管理员', 1, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (104, 101, 1000, 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (105, 101, ?, 0)", roleAssignAuthId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (106, 102, ?, 0)", userAssignRoleId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (107, 103, ?, 0)", menuAddId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (108, 103, ?, 0)", menuEditId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (109, 103, ?, 0)", menuRemoveId);

        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", jwtHelper.createTokenPair(1L, "admin", 0L).token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/system/sysMenu/toAssign/1")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":101,\"menuIdList\":[" + roleMenuId + "," + processTypeMenuId
                                + ",1000," + roleAssignAuthId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + roleMenuId + " AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + processTypeMenuId + " AND is_deleted = 0"));
        // 按钮点可授予；不在提交里的旧授予随重提交被丢弃。
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = 1000 AND is_deleted = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = 901 AND is_deleted = 0"));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":101,\"menuIdList\":[" + roleMenuId + "," + processTypeMenuId + ",1000,902]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只能授予配置菜单"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + roleMenuId + " AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + processTypeMenuId + " AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = 1000 AND is_deleted = 0"));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":101,\"menuIdList\":[" + roleMenuId + "," + processTypeMenuId + ",902]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只能授予配置菜单"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + roleMenuId + " AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + processTypeMenuId + " AND is_deleted = 0"));
        mockMvc.perform(get("/admin/system/sysMenu/toAssign/1")
                        .header("token", jwtHelper.createTokenPair(3L, "user-manager", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", jwtHelper.createTokenPair(3L, "user-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":101,\"menuIdList\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysMenu/toAssign/1")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":101,\"menuIdList\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id = " + roleMenuId + " AND is_deleted = 0"));
        mockMvc.perform(get("/admin/system/sysMenu/findNodes")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysMenu/findNodes")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + menuAdminMenuId + ",\"name\":\"角色管理\",\"parentId\":" + systemRootId
                                + ",\"type\":1,\"path\":\"/system/sysRole\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单路由不可重定义"));
        // 配置入口不能改成办事路径：保护路由的身份变更先于任何目录规则拒绝。
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + systemRootId + ",\"name\":\"系统管理\",\"parentId\":0,\"type\":0,\"path\":\"/work\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单路由不可重定义"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE id = " + systemRootId + " AND path = '/system' AND is_deleted = 0"));
        // 保护路由本身不能改名：换到受保护页面路径也仍是「不可重定义」。
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + systemRootId + ",\"name\":\"系统管理\",\"parentId\":0,\"type\":0,\"path\":\"/system/sysUser\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单路由不可重定义"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE id = " + systemRootId + " AND path = '/system' AND is_deleted = 0"));
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + systemRootId
                                + ",\"name\":\"系统管理\",\"parentId\":0,\"type\":0,\"path\":\"/system\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单路由不可重定义"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE id = " + systemRootId + " AND status = 1 AND is_deleted = 0"));
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + menuAdminMenuId + ",\"name\":\"菜单管理\",\"parentId\":" + systemRootId
                                + ",\"type\":1,\"path\":\"/system/sysMenu\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单路由不可重定义"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE id = " + menuAdminMenuId + " AND status = 1 AND is_deleted = 0"));
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"角色管理\",\"parentId\":" + systemRootId
                                + ",\"type\":1,\"path\":\"/system/sysRole\",\"component\":\"system/sysRole/list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单路由不可重定义"));
        mockMvc.perform(delete("/admin/system/sysMenu/remove/" + menuAdminMenuId)
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("配置菜单入口不能删除"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE id = " + menuAdminMenuId + " AND is_deleted = 0"));
        // 未授权的判据用良构请求体：畸形体会在校验期就 201 返回，那样这条用例就不再证明 gate 拦住了谁（ADR 0034）。
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"审计\",\"parentId\":" + systemRootId
                                + ",\"type\":1,\"path\":\"audit\",\"component\":\"system/audit/list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        // 祖先不存在的目录不是后台菜单：type 合法且体良构，save 以「只能维护配置菜单」拒绝。
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"孤儿页\",\"parentId\":999999,\"type\":1,\"path\":\"orphanChild\","
                                + "\"component\":\"system/audit/list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只能维护配置菜单"));
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + menuAdminMenuId + ",\"name\":\"菜单管理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        // 顺序契约：校验先于配置 gate，所以空体对未授权用户也是字段文案的 201（ADR 0034）。
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单名称不能为空；路由地址不能为空；上级菜单不能为空；菜单类型不能为空"));
        // 目录转菜单页而不发 component：请求为空即沿用旧行，service 按落库形状判（ADR 0036）。
        jdbcTemplate.update(
                "INSERT INTO sys_menu (id, parent_id, name, type, path, component, status, is_deleted) VALUES (1001, ?, '报表目录', 0, 'reports', '', 1, 0)",
                systemRootId);
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", jwtHelper.createTokenPair(6L, "menu-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1001,\"parentId\":" + systemRootId
                                + ",\"type\":1,\"name\":\"报表页\",\"path\":\"reports\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("类型为菜单时组件路径不能为空"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE id = 1001 AND type = 0 AND is_deleted = 0"));
        mockMvc.perform(delete("/admin/system/sysMenu/remove/999")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/toAssign/4")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4,\"roleIdList\":[101]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", jwtHelper.createTokenPair(3L, "user-manager", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/toAssign/4")
                        .header("token", jwtHelper.createTokenPair(3L, "user-manager", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", jwtHelper.createTokenPair(3L, "user-manager", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4,\"roleIdList\":[101]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/sysRole/findAll")
                        .header("token", jwtHelper.createTokenPair(3L, "user-manager", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/system/sysRole/toAssign/4")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":4,\"roleIdList\":[101]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 5"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_role WHERE user_id = 4 AND role_id = 101"));
        mockMvc.perform(get("/admin/system/sysRole/1/10")
                        .header("token", jwtHelper.createTokenPair(4L, "ordinary", 0L).token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id IN (1000, 900, 901, 902, 1001)");
    }

    @Test
    void sysRoleBatchRemoveRejectsAnEmptyIdListAfterTheGate() throws Exception {
        int roleMenuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysRole'", Integer.class);
        int roleRemoveId = buttonId("system:role:remove");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, is_deleted) VALUES (101, '角色页权限', 0)");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (101, 101, ?, 0)", roleMenuId);
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu (id, role_id, menu_id, is_deleted) VALUES (102, 101, ?, 0)", roleRemoveId);
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (id, role_id, user_id, is_deleted) VALUES (101, 101, 2, 0)");

        // 授予用户：空列表不是幂等无操作，由 service 明确拒绝并给出命名文案（#156，ADR 0040）。
        mockMvc.perform(delete("/admin/system/sysRole/batchRemove")
                        .header("token", jwtHelper.createTokenPair(2L, "user", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("删除列表不能为空"));

        // gate 先于 service：未授予用户提交空列表仍是 205，不冒充校验文案（ADR 0034 顺序）。
        jdbcTemplate.update(
                "INSERT INTO sys_user (id, username, password, name, status, is_deleted) VALUES (5, 'no-config', '', '无配置用户', 1, 0)");
        mockMvc.perform(delete("/admin/system/sysRole/batchRemove")
                        .header("token", jwtHelper.createTokenPair(5L, "no-config", 0L).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private int buttonId(String permission) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Integer.class,
                permission);
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
