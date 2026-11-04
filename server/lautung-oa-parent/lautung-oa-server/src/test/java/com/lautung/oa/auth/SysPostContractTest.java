package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #248 的岗位管理合同：CRUD、删除保护、用户写路径的岗位同步（空列表/未传两态）、
 * 岗位不存在/停用拒绝、页面/按钮 gate 与种子行。
 *
 * <p>用真迁移 + 真种子（Flyway）起库，{@code admin} 是 {@code super_admin}；
 * 夹具统一 {@code t248} / {@code T248} 前缀，结束时按前缀清理。所有请求都用良构体，
 * 避免校验先于 gate 的顺序把断言带偏（ADR 0034）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_sys_post;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class SysPostContractTest {

    private static final String PLAIN_PASSWORD = "111111";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @AfterEach
    void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_user_post WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 't248%') "
                + "OR post_id IN (SELECT id FROM sys_post WHERE post_code LIKE 't248%')");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 't248%') "
                + "OR role_id IN (SELECT id FROM sys_role WHERE role_code LIKE 't248%')");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code LIKE 't248%')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 't248%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 't248%'");
        jdbcTemplate.update("DELETE FROM sys_post WHERE post_code LIKE 't248%' OR name LIKE 'T248%'");
    }

    @Test
    void postsPageRoundTripsWithFiltersAndSort() throws Exception {
        long third = savePost("t248_c", "T248岗位丙", 3, 0);
        long first = savePost("t248_a", "T248岗位甲", 1, 1);
        long second = savePost("t248_b", "T248岗位乙", 2, 1);

        mockMvc.perform(get("/admin/system/sysPost/1/10?name=T248岗位").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].name")
                        .value(org.hamcrest.Matchers.contains("T248岗位甲", "T248岗位乙", "T248岗位丙")))
                .andExpect(jsonPath("$.data.records[*].id")
                        .value(org.hamcrest.Matchers.contains((int) first, (int) second, (int) third)));

        mockMvc.perform(get("/admin/system/sysPost/1/10?name=T248岗位&status=1")
                        .header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/admin/system/sysPost/1/10?postCode=t248_b").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("T248岗位乙"));
    }

    @Test
    void savingUpdatingAndRemovingAPostRoundTrips() throws Exception {
        long postId = savePost("t248_crud", "T248岗位", 4, 1);

        updatePost("{\"id\":" + postId + ",\"name\":\"T248岗位改名\",\"status\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        var row = jdbcTemplate.queryForMap(
                "SELECT post_code, name, sort_value, status FROM sys_post WHERE id = ?", postId);
        assertEquals("t248_crud", row.get("POST_CODE"));
        assertEquals("T248岗位改名", row.get("NAME"));
        assertEquals(4, ((Number) row.get("SORT_VALUE")).intValue());
        assertEquals(0, ((Number) row.get("STATUS")).intValue());

        // 空字段不覆盖（ADR 0034）：只发 id 时其余字段保持原值。
        updatePost("{\"id\":" + postId + "}").andExpect(jsonPath("$.code").value(200));
        assertEquals("T248岗位改名", jdbcTemplate.queryForObject(
                "SELECT name FROM sys_post WHERE id = ?", String.class, postId));

        mockMvc.perform(delete("/admin/system/sysPost/remove/" + postId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_post WHERE id = " + postId + " AND is_deleted = 1"));

        mockMvc.perform(delete("/admin/system/sysPost/remove/" + postId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位不存在"));

        updatePost("{\"id\":999999,\"name\":\"T248不存在\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位不存在"));
    }

    @Test
    void aBlankSaveIsRejectedWithFieldMessagesBeforeTheGate() throws Exception {
        mockMvc.perform(post("/admin/system/sysPost/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postCode\":\"\",\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(containsString("岗位编码不能为空")))
                .andExpect(jsonPath("$.message").value(containsString("岗位名称不能为空")));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_post WHERE post_code = ''"));
    }

    @Test
    void aPostWithUsersCannotBeRemoved() throws Exception {
        long postId = savePost("t248_used", "T248在岗岗位", 1, 1);
        saveUser("t248-holder", "[\"" + postId + "\"]");

        mockMvc.perform(delete("/admin/system/sysPost/remove/" + postId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位已分配用户，不能删除"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_post WHERE id = " + postId + " AND is_deleted = 1"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_post WHERE post_id = " + postId
                + " AND is_deleted = 0"));
    }

    @Test
    void userSaveAndUpdateSyncPostsInEmptyAndAbsentForms() throws Exception {
        long first = savePost("t248_sync_a", "T248同步甲", 1, 1);
        long second = savePost("t248_sync_b", "T248同步乙", 2, 1);
        long userId = saveUser("t248-sync", "[" + first + "," + second + "]");
        assertEquals(2, count("SELECT COUNT(*) FROM sys_user_post WHERE user_id = " + userId
                + " AND is_deleted = 0"));

        // 显式空列表 = 清空。
        updateUser("{\"id\":" + userId + ",\"postIds\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_post WHERE user_id = " + userId
                + " AND is_deleted = 0"));

        // 未传 postIds = 保留既有关联。
        updateUser("{\"id\":" + userId + ",\"postIds\":[" + second + "]}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_post WHERE user_id = " + userId
                + " AND post_id = " + second + " AND is_deleted = 0"));

        updateUser("{\"id\":" + userId + ",\"name\":\"T248同步改名\"}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_post WHERE user_id = " + userId
                + " AND post_id = " + second + " AND is_deleted = 0"));

        // 新提交替换旧集合。
        updateUser("{\"id\":" + userId + ",\"postIds\":[" + first + "]}")
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user_post WHERE user_id = " + userId
                + " AND post_id = " + first + " AND is_deleted = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user_post WHERE user_id = " + userId
                + " AND post_id = " + second + " AND is_deleted = 0"));
    }

    @Test
    void assigningMissingOrDisabledPostsIsRejected() throws Exception {
        long disabled = savePost("t248_disabled", "T248停用岗位", 1, 0);

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t248-missing\",\"name\":\"缺岗\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"postIds\":[999999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位不存在"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE username = 't248-missing'"));

        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t248-disabled\",\"name\":\"停岗\",\"password\":\""
                                + PLAIN_PASSWORD + "\",\"postIds\":[" + disabled + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位已停用"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_user WHERE username = 't248-disabled'"));

        long userId = saveUser("t248-update-guard", "[]");
        updateUser("{\"id\":" + userId + ",\"name\":\"不该落库\","
                + "\"password\":\"new-secret-248\",\"postIds\":[999999]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("岗位不存在"));
        assertEquals("t248-update-guard", jdbcTemplate.queryForObject(
                "SELECT name FROM sys_user WHERE id = ?", String.class, userId));
        // 校验先于改密（#260 二轮）：失败请求不落新密码、不递增口令版本，旧密码仍可登录。
        loginToken("t248-update-guard");
        assertEquals(0, count("SELECT token_version FROM sys_user WHERE id = " + userId));
    }

    @Test
    void pagesAndButtonsGateThePostRoutes() throws Exception {
        long postId = savePost("t248_gate", "T248网关岗位", 1, 1);
        String plainUserToken = loginToken("user");
        for (var request : java.util.List.of(
                get("/admin/system/sysPost/1/10"),
                post("/admin/system/sysPost/save"),
                put("/admin/system/sysPost/update"),
                delete("/admin/system/sysPost/remove/" + postId))) {
            mockMvc.perform(request.header("token", plainUserToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + postId + ",\"postCode\":\"t248_gate_2\","
                                    + "\"name\":\"T248网关岗位2\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(205))
                    .andExpect(jsonPath("$.message").value("没有操作权限"));
        }

        long roleId = insertRole("t248route");
        long userId = insertUser("t248-page");
        joinRole(roleId, userId);
        grantMenu(roleId, menuIdByPath("/system/sysPost"));
        String pageToken = jwtHelper.createTokenPair(userId, "t248-page", 0L).token();

        mockMvc.perform(get("/admin/system/sysPost/1/10").header("token", pageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/sysPost/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postCode\":\"t248_pageonly\",\"name\":\"T248页only\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        for (String perms : java.util.List.of(
                "system:post:add", "system:post:edit", "system:post:remove")) {
            grantMenu(roleId, menuIdByPerms(perms));
        }
        mockMvc.perform(post("/admin/system/sysPost/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postCode\":\"t248_pageplus\",\"name\":\"T248页加按钮\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        long created = savePost("t248_gate_edit", "T248网关编辑", 2, 1);
        mockMvc.perform(put("/admin/system/sysPost/update").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + created + ",\"name\":\"T248网关编辑改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/admin/system/sysPost/remove/" + created).header("token", pageToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken()))
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.hasItems(
                        "system:post:add", "system:post:edit", "system:post:remove")))
                .andExpect(jsonPath("$.data.menus[*].path",
                        org.hamcrest.Matchers.hasItem("/system/sysPost")));
    }

    private long savePost(String postCode, String name, Integer sortValue, Integer status) throws Exception {
        mockMvc.perform(post("/admin/system/sysPost/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postCode\":\"" + postCode + "\",\"name\":\"" + name + "\","
                                + "\"sortValue\":" + sortValue + ",\"status\":" + status + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_post WHERE post_code = ? AND is_deleted = 0", Long.class, postCode);
    }

    private org.springframework.test.web.servlet.ResultActions updatePost(String body) throws Exception {
        return mockMvc.perform(put("/admin/system/sysPost/update").header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long saveUser(String username, String postIdsJson) throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"name\":\"" + username
                                + "\",\"password\":\"" + PLAIN_PASSWORD + "\",\"postIds\":" + postIdsJson + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ? AND is_deleted = 0", Long.class, username);
    }

    private org.springframework.test.web.servlet.ResultActions updateUser(String body) throws Exception {
        return mockMvc.perform(put("/admin/system/sysUser/update").header("token", adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long insertUser(String username) {
        jdbcTemplate.update("INSERT INTO sys_user (username, password, name, status, is_deleted) "
                + "VALUES (?, '', ?, 1, 0)", username, username);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ? AND is_deleted = 0", Long.class, username);
    }

    private long insertRole(String roleCode) {
        jdbcTemplate.update("INSERT INTO sys_role (role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, 1, 0)", "T248角色", roleCode);
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
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0", Long.class, path);
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
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PLAIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(response, "$.data.token");
        return token;
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
