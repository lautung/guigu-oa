package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.common.jwt.JwtHelper;
import db.migration.V123__SysDeptMenu;
import org.flywaydb.core.api.migration.Context;
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
import org.springframework.test.web.servlet.ResultActions;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #247 的部门维护合同：树形状与含自身包裹逗号路径、移动后整棵子树重算、
 * 删除保护两分支、页面/按钮 205 矩阵与 V123 菜单种子幂等。
 *
 * <p>真迁移 + 真种子（Flyway）起库，父级路径按 ADR 0056 的 {@code ,0,id,} 形状断言。
 * 夹具统一 {@code T247} 前缀与 9001 起的用户/角色 id，结束时物理清理。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_dept_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysDeptContractTest {

    private static final String DEPT_PAGE = "/system/sysDept";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 9001 AND 9099");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 9001 AND 9099");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 9001 AND 9099");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 9001 AND 9099");
        jdbcTemplate.update("DELETE FROM sys_dept");
    }

    @Test
    void treeListsChildrenWithWrappedAncestors() throws Exception {
        long root = createDept(0, "T247根", 2);
        long child = createDept(root, "T247子", 1);
        long grandchild = createDept(child, "T247孙", 1);
        long earlierRoot = createDept(0, "T247根二", 0);

        String body = mockMvc.perform(get("/admin/system/sysDept/findTree").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> roots = JsonPath.read(body, "$.data");
        assertEquals(List.of("T247根二", "T247根"), roots.stream().map(row -> row.get("name")).toList());
        assertEquals(earlierRoot, idOf(roots.get(0)));
        assertEquals(",0," + root + ",", roots.get(1).get("ancestors"));

        Map<String, Object> childNode = onlyChild(roots.get(1), "T247子");
        assertEquals(child, idOf(childNode));
        assertEquals(",0," + root + "," + child + ",", childNode.get("ancestors"));
        Map<String, Object> grandchildNode = onlyChild(childNode, "T247孙");
        assertEquals(grandchild, idOf(grandchildNode));
        assertEquals(",0," + root + "," + child + "," + grandchild + ",", grandchildNode.get("ancestors"));
        assertEquals(List.of(), grandchildNode.get("children"));

        // 新建部门默认启用：status 不传时落 1。
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT status FROM sys_dept WHERE id = ?", Integer.class, root));
    }

    /**
     * 清空负责人显式置 null（#286）：updateById 的默认 NOT_NULL 策略会吞掉 null，
     * leader_user_id 走专用更新语句按提交值写——置空后重读为空，其余字段不被波及。
     */
    @Test
    void clearingTheLeaderWritesNullThroughTheUpdatePath() throws Exception {
        insertUser(9010, "t247-leader-clear", 0);
        long dept = createDept(0, "T247负责人", 1);

        // 先设上负责人。
        updateDept(dept, "{\"id\":" + dept + ",\"leaderUserId\":9010}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(9010L, jdbcTemplate.queryForObject(
                "SELECT leader_user_id FROM sys_dept WHERE id = ?", Long.class, dept).longValue());

        // 再清空：null 必须落库，而不是被字段策略吞掉。
        updateDept(dept, "{\"id\":" + dept + ",\"leaderUserId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT leader_user_id FROM sys_dept WHERE id = ?", Long.class, dept));

        // 同一次更新不波及其他字段：名称与状态原样。
        assertEquals("T247负责人", jdbcTemplate.queryForObject(
                "SELECT name FROM sys_dept WHERE id = ?", String.class, dept));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT status FROM sys_dept WHERE id = ?", Integer.class, dept).intValue());
    }

    @Test
    void movingADepartmentRecalculatesTheWholeSubtree() throws Exception {
        long a = createDept(0, "T247A", 1);
        long b = createDept(a, "T247B", 1);
        long c = createDept(b, "T247C", 1);
        long d = createDept(0, "T247D", 2);

        updateDept(b, "{\"id\":" + b + ",\"parentId\":" + d + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(",0," + d + "," + b + ",", ancestorsOf(b));
        assertEquals(",0," + d + "," + b + "," + c + ",", ancestorsOf(c));
        assertEquals(",0," + a + ",", ancestorsOf(a));
        assertEquals(d, jdbcTemplate.queryForObject(
                "SELECT parent_id FROM sys_dept WHERE id = ?", Long.class, b).longValue());
        assertEquals(b, jdbcTemplate.queryForObject(
                "SELECT parent_id FROM sys_dept WHERE id = ?", Long.class, c).longValue());

        // 移回顶层：路径收成 ,0,b, 且子树继续跟着重算。
        updateDept(b, "{\"id\":" + b + ",\"parentId\":0}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(",0," + b + ",", ancestorsOf(b));
        assertEquals(",0," + b + "," + c + ",", ancestorsOf(c));
    }

    @Test
    void rejectsMissingParentsSelfParentingAndCycles() throws Exception {
        long a = createDept(0, "T247根", 1);
        long b = createDept(a, "T247子", 1);
        long c = createDept(b, "T247孙", 1);
        saveDept(999999, "T247孤儿", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("上级部门不存在"));

        updateDept(a, "{\"id\":" + a + ",\"parentId\":" + a + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("上级部门不能是自己"));

        updateDept(a, "{\"id\":" + a + ",\"parentId\":" + c + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("不能把部门移动到自己的下级部门"));

        updateDept(999999, "{\"id\":999999,\"parentId\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门不存在"));

        updateDept(b, "{\"id\":" + b + ",\"parentId\":999999}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("上级部门不存在"));

        // 名称必填走 HTTP 边界的 201，不落行。
        saveDept(0, "   ", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门名称不能为空"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_dept WHERE id NOT IN (" + a + "," + b + "," + c + ")"));
    }

    @Test
    void deleteProtectionRejectsChildrenAndUsersWithoutCascade() throws Exception {
        long parent = createDept(0, "T247父", 1);
        long child = createDept(parent, "T247子", 1);

        mockMvc.perform(delete("/admin/system/sysDept/remove/" + parent).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该部门存在下级部门，不允许删除"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_dept WHERE id = " + parent + " AND is_deleted = 0"));

        insertUser(9001, "t247-dept-user", child);
        mockMvc.perform(delete("/admin/system/sysDept/remove/" + child).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该部门存在用户，不允许删除"));

        jdbcTemplate.update("DELETE FROM sys_user WHERE id = 9001");
        mockMvc.perform(delete("/admin/system/sysDept/remove/" + child).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_dept WHERE id = " + child + " AND is_deleted = 0"));

        mockMvc.perform(delete("/admin/system/sysDept/remove/" + parent).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/admin/system/sysDept/remove/999999").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门不存在"));
    }

    @Test
    void pageGrantCoversReadsAndEachButtonPointCoversItsWrite() throws Exception {
        long pageId = menuIdByPath(DEPT_PAGE);
        long addId = menuIdByPerms("system:dept:add");
        long editId = menuIdByPerms("system:dept:edit");
        long removeId = menuIdByPerms("system:dept:remove");

        insertRole(9001, "t247page");
        grantMenus(9001, pageId);
        insertUser(9001, "t247page", 0);
        assignRole(9001, 9001);
        String pageToken = token(9001, "t247page");

        mockMvc.perform(get("/admin/system/sysDept/findTree").header("token", pageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysDept/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"name\":\"T247越权\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(put("/admin/system/sysDept/update").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"T247越权\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/system/sysDept/remove/1").header("token", pageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        // 校验先于 gate：无权用户送畸形体得到 201，不是 205（ADR 0034）。
        mockMvc.perform(post("/admin/system/sysDept/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门名称不能为空"));

        // 只有按钮没有页面：页面先拒，按钮点不构成后台权限。
        insertRole(9002, "t247buttononly");
        grantMenus(9002, addId, editId, removeId);
        insertUser(9002, "t247buttononly", 0);
        assignRole(9002, 9002);
        String buttonOnlyToken = token(9002, "t247buttononly");
        mockMvc.perform(get("/admin/system/sysDept/findTree").header("token", buttonOnlyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/sysDept/save").header("token", buttonOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"name\":\"T247无页\"}"))
                .andExpect(jsonPath("$.code").value(205));

        // 页面 + add：新增放行；edit/remove 仍要各自的按钮点。
        insertRole(9003, "t247add");
        grantMenus(9003, pageId, addId);
        insertUser(9003, "t247add", 0);
        assignRole(9003, 9003);
        String addToken = token(9003, "t247add");
        mockMvc.perform(post("/admin/system/sysDept/save").header("token", addToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":0,\"name\":\"T247可新增\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        long created = idByName("T247可新增");
        mockMvc.perform(put("/admin/system/sysDept/update").header("token", addToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + created + ",\"name\":\"T247改\"}"))
                .andExpect(jsonPath("$.code").value(205));
    }

    /**
     * 部门负责人选择器的启用用户选项（#286，ADR 0069）：只判部门页授予、不要求用户管理页；
     * 只列启用且未删用户，响应不带 password 列。
     */
    @Test
    void userOptionsListsEnabledUsersForTheDeptPageOnly() throws Exception {
        insertUser(9001, "t247-leader-a", 0);
        insertUser(9002, "t247-leader-b", 0);
        insertUser(9003, "t247-leader-c", 0);
        jdbcTemplate.update("UPDATE sys_user SET status = 0 WHERE id = 9002");
        jdbcTemplate.update("UPDATE sys_user SET is_deleted = 1 WHERE id = 9003");

        String body = mockMvc.perform(get("/admin/system/sysDept/userOptions").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> options = JsonPath.read(body, "$.data");
        List<String> usernames = options.stream().map(row -> (String) row.get("username")).toList();
        assertTrue(usernames.contains("t247-leader-a"));
        // 停用与已删用户都不进选项；种子账号（admin/user）不碍事。
        assertTrue(!usernames.contains("t247-leader-b"));
        assertTrue(!usernames.contains("t247-leader-c"));
        Map<String, Object> first = options.stream()
                .filter(row -> "t247-leader-a".equals(row.get("username")))
                .findFirst()
                .orElseThrow();
        assertEquals(9001, ((Number) first.get("id")).intValue());
        assertEquals("t247-leader-a", first.get("name"));
        assertTrue(!first.containsKey("password"));

        // 没有部门页授予的用户读不到选项：页面 gate 是唯一强制点。
        insertRole(9004, "t247-nooption");
        insertUser(9004, "t247-nooption", 0);
        assignRole(9004, 9004);
        mockMvc.perform(get("/admin/system/sysDept/userOptions").header("token", token(9004, "t247-nooption")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    @Test
    void v123SeedsTheDeptPageButtonsAndGrantsSuperAdminIdempotently() throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '123' AND success = TRUE"));
        long pageId = menuIdByPath(DEPT_PAGE);
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu p JOIN sys_menu d ON d.parent_id = p.id "
                + "WHERE p.path = '/system' AND p.parent_id = 0 AND p.type = 0 "
                + "AND d.path = '" + DEPT_PAGE + "' AND d.type = 1 AND d.is_deleted = 0"));
        assertEquals(3, count("SELECT COUNT(*) FROM sys_menu b WHERE b.type = 2 AND b.parent_id = "
                + pageId + " AND b.is_deleted = 0 AND b.perms IN "
                + "('system:dept:add','system:dept:edit','system:dept:remove')"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id = rm.role_id "
                + "JOIN sys_menu m ON m.id = rm.menu_id "
                + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                + "AND m.path = '" + DEPT_PAGE + "'"));

        int menus = count("SELECT COUNT(*) FROM sys_menu");
        int grants = count("SELECT COUNT(*) FROM sys_role_menu");
        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V123__SysDeptMenu().migrate(context);
            new V123__SysDeptMenu().migrate(context);
        }
        assertEquals(menus, count("SELECT COUNT(*) FROM sys_menu"));
        assertEquals(grants, count("SELECT COUNT(*) FROM sys_role_menu"));

        // super_admin 显式补授页面 + 三个按钮：管理员不走身份旁路。
        List<String> grantedPerms = new ArrayList<>();
        jdbcTemplate.queryForList("SELECT m.perms FROM sys_role_menu rm "
                        + "JOIN sys_role r ON r.id = rm.role_id JOIN sys_menu m ON m.id = rm.menu_id "
                        + "WHERE r.role_code = 'super_admin' AND rm.is_deleted = 0 "
                        + "AND m.parent_id = ? AND m.perms <> ''", String.class, pageId)
                .forEach(grantedPerms::add);
        assertEquals(3, grantedPerms.size());
        assertTrue(grantedPerms.containsAll(
                List.of("system:dept:add", "system:dept:edit", "system:dept:remove")));
        assertNotNull(pageId);
    }

    private long createDept(long parentId, String name, int sortValue) throws Exception {
        saveDept(parentId, name, sortValue)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return idByName(name);
    }

    private ResultActions saveDept(long parentId, String name, int sortValue) throws Exception {
        return mockMvc.perform(post("/admin/system/sysDept/save").header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parentId\":" + parentId + ",\"name\":\"" + name
                        + "\",\"sortValue\":" + sortValue + "}"));
    }

    private ResultActions updateDept(long id, String body) throws Exception {
        return mockMvc.perform(put("/admin/system/sysDept/update").header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long idOf(Map<String, Object> node) {
        return ((Number) node.get("id")).longValue();
    }

    private Map<String, Object> onlyChild(Map<String, Object> parent, String name) {
        List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
        return children.stream()
                .filter(child -> name.equals(child.get("name")))
                .findFirst()
                .orElseThrow();
    }

    private long idByName(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dept WHERE name = ? AND is_deleted = 0", Long.class, name);
    }

    private String ancestorsOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT ancestors FROM sys_dept WHERE id = ?", String.class, id);
    }

    private long menuIdByPath(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0", Long.class, path);
    }

    private long menuIdByPerms(String perms) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND is_deleted = 0", Long.class, perms);
    }

    private void insertUser(long id, String username, long deptId) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, token_version, "
                + "dept_id, is_deleted) VALUES (?, ?, '', ?, 1, 0, ?, 0)", id, username, username, deptId);
    }

    private void insertRole(long id, String roleCode) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, "
                + "create_time, update_time, is_deleted) "
                + "VALUES (?, ?, ?, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", id, roleCode, roleCode);
    }

    private void assignRole(long userId, long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, create_time, update_time, is_deleted) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", userId, roleId);
    }

    private void grantMenus(long roleId, long... menuIds) {
        for (long menuId : menuIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, create_time, update_time, is_deleted) "
                    + "VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", roleId, menuId);
        }
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
