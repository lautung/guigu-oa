package com.lautung.oa.auth;

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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #249 的用户页加料合同：列表 {@code deptId} 过滤按「本部门及以下」并叠加 DataScope（含空集），
 * 部门/岗位写校验的命名 201，以及只授予 {@code /system/sysUser} 页面时也能读到的只读部门/岗位选项。
 *
 * <p>用真迁移 + 真种子（Flyway）起库：部门树 10 → 11 → 12 与独立根 20；角色覆盖五档，
 * 每个夹具用户都只被授予 {@code /system/sysUser} 页面（不含 {@code /system/sysDept}）。
 * 夹具统一 1xx / 2xx（角色）/ 3xx（岗位）段，结束时按段清理。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_user_dept;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysUserDeptContractTest {

    private static final String PLAIN_PASSWORD = "111111";

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

        insertPost(301, "t249_post_on", "T249在岗", 1, 1);
        insertPost(302, "t249_post_off", "T249停用", 2, 0);

        insertRole(201, 1);
        insertRole(202, 2);
        insertRole(203, 3);
        insertRole(204, 4);
        insertRole(205, 5);
        insertRole(206, 3);
        insertRoleDept(202, 20);

        // 101 全部 / 102 自定 20 / 103 本部门 11 / 104 本部门及以下 10 / 105 仅本人（12）/
        // 106 本部门但无部门（空集）。107/108/109 无角色，只作为被过滤的行。
        insertUser(101, "t249-all", 10);
        insertUser(102, "t249-custom", 20);
        insertUser(103, "t249-own", 11);
        insertUser(104, "t249-below", 10);
        insertUser(105, "t249-self", 12);
        insertUser(106, "t249-no-dept", null);
        insertUser(107, "t249-child", 11);
        insertUser(108, "t249-grandchild", 12);
        insertUser(109, "t249-other", 20);
        insertUser(110, "t249-disabled-dept", 21);

        assignRole(101, 201);
        assignRole(102, 202);
        assignRole(103, 203);
        assignRole(104, 204);
        assignRole(105, 205);
        assignRole(106, 206);
        grantUserPage(201, 202, 203, 204, 205, 206);
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
    }

    @Test
    void deptFilterIncludesTheSelectedDepartmentAndItsDescendants() throws Exception {
        mockMvc.perform(userPage(101, "?deptId=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder(
                        "t249-all", "t249-own", "t249-below", "t249-self", "t249-child", "t249-grandchild")))
                .andExpect(jsonPath("$.data.total").value(6));

        mockMvc.perform(userPage(101, "?deptId=11"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder(
                        "t249-own", "t249-child", "t249-self", "t249-grandchild")))
                .andExpect(jsonPath("$.data.total").value(4));

        // 部门不存在（含已删/已挪出树）：子树解析为空 → 零行。
        mockMvc.perform(userPage(101, "?deptId=99999"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void deptFilterIntersectsWithEachDataScope() throws Exception {
        // 档位 2（自定 20）：过滤 10 是空集，过滤 20 只剩自定部门行。
        mockMvc.perform(userPage(102, "?deptId=10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(userPage(102, "?deptId=20"))
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder(
                        "t249-custom", "t249-other")))
                .andExpect(jsonPath("$.data.total").value(2));

        // 档位 3（本部门 11）：子树过滤被本人的部门集裁剪到 11 这一层。
        mockMvc.perform(userPage(103, "?deptId=10"))
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder(
                        "t249-own", "t249-child")))
                .andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(userPage(103, "?deptId=12"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 档位 4（本部门 10 及以下）：过滤 11 只剩 11 子树（含 12 孙层）。
        mockMvc.perform(userPage(104, "?deptId=11"))
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder(
                        "t249-own", "t249-child", "t249-self", "t249-grandchild")))
                .andExpect(jsonPath("$.data.total").value(4));
        mockMvc.perform(userPage(104, "?deptId=20"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 档位 5（仅本人，部门 12）：过滤自己的部门只留本人行；过滤不含本人的部门为空。
        mockMvc.perform(userPage(105, "?deptId=12"))
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder("t249-self")))
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(userPage(105, "?deptId=20"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 空集 DataScope（档位 3 但本人无部门）：部门过滤不能把范围放大。
        mockMvc.perform(userPage(106, "?deptId=10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void deptAndPostValidationRejectWithNamedMessages() throws Exception {
        String adminToken = adminToken();

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t249_bad_dept\",\"name\":\"坏部门\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"deptId\":99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门不存在"));

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t249_off_dept\",\"name\":\"停用部门\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"deptId\":21}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门已停用"));

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t249_missing_post\",\"name\":\"坏岗位\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"deptId\":10,\"postIds\":[99999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位不存在"));

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t249_off_post\",\"name\":\"停用岗位\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"deptId\":10,\"postIds\":[302]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位已停用"));

        long target = createUser(adminToken, "t249_update_bad_dept", 10);
        mockMvc.perform(put("/admin/system/sysUser/update").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + target + ",\"deptId\":99999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("部门不存在"));
    }

    @Test
    void userDetailEchoesDeptAndPostsForTheEditForm() throws Exception {
        String adminToken = adminToken();
        long id = createUser(adminToken, "t249_detail", 11);

        mockMvc.perform(put("/admin/system/sysUser/update").header("token", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"postIds\":[301]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/sysUser/get/" + id).header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deptId").value(11))
                .andExpect(jsonPath("$.data.postIds", containsInAnyOrder(301)));
    }

    @Test
    void aWellFormedBodyReachesTheGateBeforeAnyBusinessCheck() throws Exception {
        // 良构体（部门与岗位都存在且启用）先过校验，再被配置 gate 拒成 205；无授予用户没有写按钮点。
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", tokenOf(102))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t249_gate\",\"name\":\"gate\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"deptId\":20,\"postIds\":[301]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    @Test
    void theUserPageReadsDeptAndPostOptionsWithOnlyTheUserPageGrant() throws Exception {
        String token = tokenOf(101);

        mockMvc.perform(get("/admin/system/sysUser/depts").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[*].name", hasItems("部门10", "部门20")))
                .andExpect(jsonPath("$.data[0].name").value("部门10"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("部门11"))
                .andExpect(jsonPath("$.data[0].children[0].children[0].name").value("部门12"));

        mockMvc.perform(get("/admin/system/sysUser/posts").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("T249在岗")));

        // 只授予用户页：部门管理页的树读仍是 205，说明选项端点没有借道部门页 gate。
        mockMvc.perform(get("/admin/system/sysDept/findTree").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder userPage(
            long userId, String query) {
        return get("/admin/system/sysUser/1/50" + query).header("token", tokenOf(userId));
    }

    private long createUser(String token, String username, long deptId) throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"" + username
                                + "\",\"password\":\"" + PLAIN_PASSWORD + "\",\"deptId\":" + deptId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        String page = mockMvc.perform(get("/admin/system/sysUser/1/50?keyword=" + username)
                        .header("token", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(page, "$.data.records[0].id")).longValue();
    }

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.data.token");
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
                + "VALUES (?, ?, ?, ?, 0)", id, "用户页角色" + id, "t249_role_" + id, dataScope);
    }

    private void insertRoleDept(long roleId, long deptId) {
        jdbcTemplate.update("INSERT INTO sys_role_dept (role_id, dept_id, is_deleted) VALUES (?, ?, 0)",
                roleId, deptId);
    }

    private void insertUser(long id, String username, Integer deptId) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, dept_id, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, ?, 0)", id, username, username, deptId);
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

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id BETWEEN 101 AND 199 "
                + "OR post_id BETWEEN 301 AND 399");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id BETWEEN 201 AND 299 "
                + "OR user_id BETWEEN 101 AND 199");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 101 AND 199 "
                + "OR username LIKE 't249_%'");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 10 AND 99");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_post WHERE id BETWEEN 301 AND 399");
    }
}
