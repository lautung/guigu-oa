package com.lautung.oa.auth;

import com.lautung.oa.common.jwt.JwtHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #225 的后台维护合同：角色数据范围五档与自定部门、按钮点授予往返、隐藏页单独授予不落库、
 * 菜单按钮的权限标识唯一。
 *
 * <p>用真迁移 + 真种子（Flyway）起库，种子 {@code admin} 是 {@code super_admin}；
 * 夹具统一 {@code t225} / {@code T225} 前缀，结束时按前缀清理。所有请求都用良构体，
 * 避免校验先于 gate 的顺序把断言带偏（ADR 0034）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_role_permission_maintenance;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class RolePermissionMaintenanceContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @AfterEach
    void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code LIKE 't225%') "
                + "OR menu_id IN (SELECT id FROM sys_menu WHERE perms LIKE 't225%' OR name LIKE 'T225%')");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 't225%') "
                + "OR role_id IN (SELECT id FROM sys_role WHERE role_code LIKE 't225%')");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code LIKE 't225%')");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE perms LIKE 't225%' OR name LIKE 'T225%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 't225%'");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 't225%'");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 21 AND 24");
    }

    @Test
    void dataScopeFiveTiersRoundTripWithCustomDepartments() throws Exception {
        seedDepartments();
        seedDepartments();

        for (int scope = 1; scope <= 5; scope++) {
            String roleCode = "t225role" + scope;
            saveRole(roleCode, scope, scope == 2 ? "[21,22]" : null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            Long roleId = roleId(roleCode);
            assertEquals(scope, jdbcTemplate.queryForObject(
                    "SELECT data_scope FROM sys_role WHERE id = ?", Integer.class, roleId));
            assertEquals(scope == 2 ? 2 : 0, count(
                    "SELECT COUNT(*) FROM sys_role_dept WHERE role_id = " + roleId + " AND is_deleted = 0"));

            var detail = mockMvc.perform(get("/admin/system/sysRole/get/" + roleId)
                            .header("token", adminToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.dataScope").value(scope));
            if (scope == 2) {
                detail.andExpect(jsonPath("$.data.deptIds", containsInAnyOrder(21, 22)));
            } else {
                detail.andExpect(jsonPath("$.data.deptIds").isEmpty());
            }
        }
    }

    @Test
    void aNewRoleWithoutADataScopeDefaultsToSelfOnly() throws Exception {
        saveRole("t225roleDefault", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Long roleId = roleId("t225roleDefault");
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT data_scope FROM sys_role WHERE id = ?", Integer.class, roleId));
        mockMvc.perform(get("/admin/system/sysRole/get/" + roleId).header("token", adminToken()))
                .andExpect(jsonPath("$.data.dataScope").value(5))
                .andExpect(jsonPath("$.data.deptIds").isEmpty());
    }

    @Test
    void updatingTheScopeReplacesOrClearsCustomDepartments() throws Exception {
        seedDepartments();
        saveRole("t225roleUpdate", 2, "[21,21,22]")
                .andExpect(jsonPath("$.code").value(200));
        Long roleId = roleId("t225roleUpdate");
        assertEquals(2, count("SELECT COUNT(*) FROM sys_role_dept WHERE role_id = " + roleId
                + " AND is_deleted = 0"));

        updateRole(roleId, "{\"id\":" + roleId + ",\"dataScope\":3}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_dept WHERE role_id = " + roleId
                + " AND is_deleted = 0"));
        mockMvc.perform(get("/admin/system/sysRole/get/" + roleId).header("token", adminToken()))
                .andExpect(jsonPath("$.data.dataScope").value(3))
                .andExpect(jsonPath("$.data.deptIds").isEmpty());

        updateRole(roleId, "{\"id\":" + roleId + ",\"dataScope\":2,\"deptIds\":[22]}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_dept WHERE role_id = " + roleId
                + " AND is_deleted = 0"));

        // 只改名称：dataScope 与 deptIds 都不发，按 ADR 0034 保留既有的 2 档与部门行。
        updateRole(roleId, "{\"id\":" + roleId + ",\"roleName\":\"T225改名角色\"}")
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/sysRole/get/" + roleId).header("token", adminToken()))
                .andExpect(jsonPath("$.data.dataScope").value(2))
                .andExpect(jsonPath("$.data.deptIds", containsInAnyOrder(22)));

        // 显式空列表替换为空集。
        updateRole(roleId, "{\"id\":" + roleId + ",\"dataScope\":2,\"deptIds\":[]}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_dept WHERE role_id = " + roleId
                + " AND is_deleted = 0"));
    }

    @Test
    void customDepartmentsMustExistAndStayActive() throws Exception {
        seedDepartments();
        saveRole("t225roleMissingDept", 2, "[999999]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只能选择已存在的部门"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 't225roleMissingDept'"));

        saveRole("t225roleDeletedDept", 2, "[23]")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("只能选择已存在的部门"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 't225roleDeletedDept'"));

        saveRole("t225roleEmptyDept", 2, "[]")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("自定部门不能为空"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 't225roleEmptyDept'"));

        saveRole("t225roleOutOfRange", 6, null)
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("数据范围只能为 1、2、3、4 或 5"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role WHERE role_code = 't225roleOutOfRange'"));
    }

    @Test
    void departmentOptionsAreReadOnlyAndListActiveDepartmentsOnly() throws Exception {
        seedDepartments();
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (24, 0, ',0,24,', 'T225停用部门', 0, 0)");

        mockMvc.perform(get("/admin/system/sysRole/depts").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[*].name", hasItems("T225启用部门A", "T225启用部门B")))
                .andExpect(jsonPath("$.data[*].name", not(hasItem("T225停用部门"))))
                .andExpect(jsonPath("$.data[*].name", not(hasItem("T225已删部门"))))
                .andExpect(jsonPath("$.data[?(@.name == 'T225启用部门B')].parentId", hasItem(21)));

        mockMvc.perform(get("/admin/system/sysRole/depts").header("token", token(2L, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
    }

    @Test
    void grantingAPageAndItsButtonRoundTripsIntoInfoPermissions() throws Exception {
        long userPageId = menuIdByPath("/system/sysUser");
        createButton("T225授权按钮", "t225:user:grant").andExpect(jsonPath("$.code").value(200));
        long buttonId = menuIdByPerms("t225:user:grant");

        // 新按钮不会自动落到 super_admin：ADR 0056 要求显式授予。
        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken()))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("t225:user:grant"))))
                .andExpect(jsonPath("$.data.menus[*].type", not(hasItem(2))));

        saveRole("t225roleGrant", 5, null).andExpect(jsonPath("$.code").value(200));
        long roleId = roleId("t225roleGrant");

        var treeResult = mockMvc.perform(get("/admin/system/sysMenu/toAssign/" + roleId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        List<java.util.Map<String, Object>> buttonRows = com.jayway.jsonpath.JsonPath.read(
                treeResult.getResponse().getContentAsString(),
                "$..[?(@.perms == 't225:user:grant')]");
        assertEquals(1, buttonRows.size());
        assertEquals(2, buttonRows.get(0).get("type"));
        assertEquals(buttonId, ((Number) buttonRows.get(0).get("id")).longValue());

        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleId + ",\"menuIdList\":["
                                + userPageId + "," + buttonId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = " + roleId
                + " AND is_deleted = 0"));

        insertUser(201L, "t225-granted");
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":201,\"roleIdList\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", token(201L, "t225-granted")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", hasItem("t225:user:grant")))
                .andExpect(jsonPath("$.data.menus[*].path", hasItem("/system/sysUser")))
                .andExpect(jsonPath("$.data.menus[*].type", not(hasItem(2))));

        // 显式把按钮授予 super_admin 后才对管理员生效。
        grantToSuperAdmin(buttonId);
        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken()))
                .andExpect(jsonPath("$.data.permissions", hasItem("t225:user:grant")));
    }

    @Test
    void aHiddenPageIsNotPersistedWithoutItsVisibleParent() throws Exception {
        long hiddenPageId = menuIdByPath("/system/assignAuth");
        long visibleParentId = menuIdByPath("/system/sysRole");
        saveRole("t225roleHidden", 5, null).andExpect(jsonPath("$.code").value(200));
        long roleId = roleId("t225roleHidden");

        assignMenus(roleId, hiddenPageId);
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = " + roleId
                + " AND menu_id = " + hiddenPageId + " AND is_deleted = 0"));

        assignMenus(roleId, visibleParentId);
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = " + roleId
                + " AND menu_id = " + visibleParentId + " AND is_deleted = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = " + roleId
                + " AND menu_id = " + hiddenPageId + " AND is_deleted = 0"));

        assignMenus(roleId, visibleParentId, hiddenPageId);
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu WHERE role_id = " + roleId
                + " AND menu_id = " + hiddenPageId + " AND is_deleted = 0"));

        // 可见父页面授予仍把隐藏子页带进闭包。
        insertUser(202L, "t225-hidden");
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":202,\"roleIdList\":[" + roleId + "]}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/index/info").header("token", token(202L, "t225-hidden")))
                .andExpect(jsonPath("$.data.menus[*].path", hasItem("/system/assignAuth")))
                .andExpect(jsonPath("$.data.menus[?(@.path == '/system/assignAuth')].hidden",
                        hasItem(true)));
    }

    @Test
    void buttonPermsMustBeUniqueAcrossNonDeletedRows() throws Exception {
        long userPageId = menuIdByPath("/system/sysUser");
        createButton("T225唯一按钮A", "t225:menu:a").andExpect(jsonPath("$.code").value(200));
        createButton("T225重复按钮", "t225:menu:a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("权限标识已存在"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE perms = 't225:menu:a' AND is_deleted = 0"));

        createButton("T225唯一按钮B", "t225:menu:b").andExpect(jsonPath("$.code").value(200));
        long buttonB = menuIdByPerms("t225:menu:b");
        updateButton(buttonB, "{\"id\":" + buttonB + ",\"type\":2,\"parentId\":" + userPageId
                + ",\"perms\":\"t225:menu:a\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("权限标识已存在"));

        // 改自己的名称/保持自己的 perms 不算重复。
        long buttonA = menuIdByPerms("t225:menu:a");
        updateButton(buttonA, "{\"id\":" + buttonA + ",\"type\":2,\"parentId\":" + userPageId
                + ",\"perms\":\"t225:menu:a\",\"name\":\"T225唯一按钮A改名\"}")
                .andExpect(jsonPath("$.code").value(200));
        updateButton(buttonB, "{\"id\":" + buttonB + ",\"type\":2,\"parentId\":" + userPageId
                + ",\"perms\":\"t225:menu:b\"}")
                .andExpect(jsonPath("$.code").value(200));

        createButton("T225撞种子按钮", "system:user:add")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("权限标识已存在"));
        assertEquals(1, count(
                "SELECT COUNT(*) FROM sys_menu WHERE perms = 'system:user:add' AND is_deleted = 0"));
    }

    private void seedDepartments() {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 21 AND 24");
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (21, 0, ',0,21,', 'T225启用部门A', 1, 0)");
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (22, 21, ',0,21,22,', 'T225启用部门B', 1, 0)");
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (23, 0, ',0,23,', 'T225已删部门', 1, 1)");
    }

    private org.springframework.test.web.servlet.ResultActions saveRole(String roleCode, Integer dataScope,
                                                                        String deptIdsJson) throws Exception {
        StringBuilder body = new StringBuilder("{\"roleName\":\"T225角色\",\"roleCode\":\"")
                .append(roleCode).append("\"");
        if (dataScope != null) {
            body.append(",\"dataScope\":").append(dataScope);
        }
        if (deptIdsJson != null) {
            body.append(",\"deptIds\":").append(deptIdsJson);
        }
        body.append("}");
        return mockMvc.perform(post("/admin/system/sysRole/save")
                .header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private org.springframework.test.web.servlet.ResultActions updateRole(Long roleId, String body)
            throws Exception {
        return mockMvc.perform(put("/admin/system/sysRole/update")
                .header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions createButton(String name, String perms)
            throws Exception {
        long userPageId = menuIdByPath("/system/sysUser");
        return mockMvc.perform(post("/admin/system/sysMenu/save")
                .header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":" + userPageId + ",\"name\":\"" + name
                        + "\",\"type\":2,\"path\":\"\",\"component\":\"\",\"perms\":\"" + perms + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions updateButton(long id, String body)
            throws Exception {
        return mockMvc.perform(put("/admin/system/sysMenu/update")
                .header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void assignMenus(long roleId, long... menuIds) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (long id : menuIds) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(id);
        }
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + roleId + ",\"menuIdList\":[" + ids + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void grantToSuperAdmin(long menuId) throws Exception {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = 'super_admin' AND is_deleted = 0", Long.class);
        List<Long> menuIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? AND is_deleted = 0",
                Long.class, roleId);
        menuIds.add(menuId);
        assignMenus(roleId, menuIds.stream().mapToLong(Long::longValue).toArray());
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, 0)", id, username, username);
    }

    private long menuIdByPath(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0", Long.class, path);
    }

    private long menuIdByPerms(String perms) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND is_deleted = 0", Long.class, perms);
    }

    private Long roleId(String roleCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ? AND is_deleted = 0", Long.class, roleCode);
    }

    private String adminToken() {
        return token(1L, "admin");
    }

    private String token(long userId, String username) {
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
