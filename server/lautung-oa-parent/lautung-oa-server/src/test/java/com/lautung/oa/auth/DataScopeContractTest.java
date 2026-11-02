package com.lautung.oa.auth;

import com.lautung.oa.auth.datascope.DataScope;
import com.lautung.oa.auth.datascope.DataScopeResolver;
import com.lautung.oa.common.jwt.JwtHelper;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据范围合同（ADR 0053/0057）：五档 × 单角色/多角色并集、无角色、本人无部门、部门被删/缺失，
 * 以及空集必须是零行而不是全部。用户列表（部门列 = {@code sys_user.dept_id}，本人列 =
 * {@code sys_user.id}）与审批实例查询（部门列 = 发起人部门，本人列 = 发起人）各验一遍。
 *
 * <p>用真迁移 + 真种子（Flyway）起库，seed 里 {@code admin} 的 {@code super_admin} 不参与断言；
 * 夹具全部用 101+ / 10+ / 201+ 段，结束时按段清理。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_data_scope_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class DataScopeContractTest {

    private static final String[] FIXTURE_USERS = {
            "dc-own", "dc-below", "dc-self", "dc-custom", "dc-no-dept", "dc-union",
            "dc-union-self", "dc-no-role", "dc-missing-dept", "dc-deleted-dept", "dc-all"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private DataScopeResolver dataScopeResolver;

    @BeforeEach
    void seedDataScopeFixtures() {
        cleanFixtures();
        insertDept(10, 0, ",0,10,", 0);
        insertDept(11, 10, ",0,10,11,", 0);
        insertDept(12, 11, ",0,10,11,12,", 0);
        insertDept(20, 0, ",0,20,", 0);
        insertDept(21, 20, ",0,20,21,", 0);
        // 角色自定部门指向一条已软删的部门行：按当前树解析时不应可见。
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (98, 20, ',0,20,98,', '已删部门', 1, 1)");

        insertRole(201, 1);
        insertRole(202, 2);
        insertRole(203, 3);
        insertRole(204, 4);
        insertRole(205, 5);
        insertRole(206, 2);
        insertRoleDept(202, 20);
        insertRoleDept(206, 98);

        // 10 根 / 11 子 / 12 孙；20 根 / 21 子；另有不存在于 sys_dept 的 99。
        insertUser(101, "dc-own", 10);
        insertUser(102, "dc-below", 11);
        insertUser(103, "dc-self", 12);
        insertUser(104, "dc-custom", 20);
        insertUser(105, "dc-no-dept", null);
        insertUser(106, "dc-union", 21);
        insertUser(107, "dc-union-self", 12);
        insertUser(108, "dc-no-role", 11);
        insertUser(109, "dc-missing-dept", 99);
        insertUser(110, "dc-deleted-dept", 12);
        insertUser(111, "dc-all", 10);

        assignRole(101, 203);
        assignRole(102, 204);
        assignRole(103, 205);
        assignRole(104, 202);
        assignRole(105, 203);
        assignRole(106, 202);
        assignRole(106, 203);
        assignRole(107, 202);
        assignRole(107, 205);
        assignRole(109, 203);
        assignRole(110, 206);
        assignRole(111, 203);
        assignRole(111, 201);

        grantBackofficePages(201, 202, 203, 204, 205, 206);
        for (long userId = 101; userId <= 111; userId++) {
            insertProcess(9000 + userId - 100, userId, "P-" + userId);
        }
        insertProcess(9012, 2, "P-2");
    }

    @AfterEach
    void cleanFixturesAfterTest() {
        cleanFixtures();
    }

    @Test
    void scopeOneSeesEveryUserAndEveryProcess() throws Exception {
        mockMvc.perform(userPage(111))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].username", hasItems(FIXTURE_USERS)))
                .andExpect(jsonPath("$.data.total").value(13));

        mockMvc.perform(processPage(111))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].title", hasItems(
                        "P-101", "P-102", "P-103", "P-104", "P-105", "P-106", "P-107",
                        "P-108", "P-109", "P-110", "P-111", "P-2")))
                .andExpect(jsonPath("$.data.total").value(12));
    }

    @Test
    void scopeThreeSeesOnlyTheOwnDepartment() throws Exception {
        mockMvc.perform(userPage(101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder("dc-own", "dc-all")))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(processPage(101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", containsInAnyOrder("P-101", "P-111")))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void scopeFourSeesTheDepartmentSubtreeIncludingItself() throws Exception {
        mockMvc.perform(userPage(102))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder(
                        "dc-below", "dc-self", "dc-union-self", "dc-deleted-dept", "dc-no-role")))
                .andExpect(jsonPath("$.data.total").value(5));

        mockMvc.perform(processPage(102))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", containsInAnyOrder(
                        "P-102", "P-103", "P-107", "P-110", "P-108")))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    void scopeTwoSeesOnlyTheDepartmentsConfiguredOnTheRole() throws Exception {
        mockMvc.perform(userPage(104))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder("dc-custom")))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(processPage(104))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", containsInAnyOrder("P-104")))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void scopeFiveSeesOnlyTheOwnRow() throws Exception {
        mockMvc.perform(userPage(103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder("dc-self")))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(processPage(103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", containsInAnyOrder("P-103")))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void multipleDepartmentRolesUnionTheirDepartmentSets() throws Exception {
        mockMvc.perform(userPage(106))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].username", containsInAnyOrder("dc-custom", "dc-union")))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(processPage(106))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", containsInAnyOrder("P-104", "P-106")))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void aSelfOnlyRoleAddsTheOwnRowOutsideTheDepartmentSet() throws Exception {
        mockMvc.perform(userPage(107))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].username",
                        containsInAnyOrder("dc-custom", "dc-union-self")))
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(processPage(107))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", containsInAnyOrder("P-104", "P-107")))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void aUserWithoutDepartmentSeesZeroRowsInsteadOfEverything() throws Exception {
        // 档位 3 但本人无部门：gate 能过（角色给了页面），数据范围解析成空集。
        assertZeroRows(105);
    }

    @Test
    void aUserWithoutRolesResolvesToZeroRows() {
        // 无角色连页面授予都没有，HTTP 层先被 gate 拒成 205；零行语义直接在解析器上验。
        DataScope scope = dataScopeResolver.resolve(108L);

        assertFalse(scope.isUnrestricted());
        assertTrue(scope.getDeptIds().isEmpty());
        assertNull(scope.getSelfUserId());
    }

    @Test
    void deletedOrMissingDepartmentsYieldZeroRows() throws Exception {
        // 档位 3，但 dept_id 指向不存在的部门。
        assertZeroRows(109);
        // 档位 2，但 sys_role_dept 只指向已软删的部门。
        assertZeroRows(110);
    }

    private void assertZeroRows(long userId) throws Exception {
        mockMvc.perform(userPage(userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItem(FIXTURE_USERS[0]))))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItem("dc-all"))));

        mockMvc.perform(processPage(userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records[*].title", not(hasItem("P-101"))))
                .andExpect(jsonPath("$.data.records[*].title", not(hasItem("P-2"))));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder userPage(long userId) {
        return get("/admin/system/sysUser/1/500").header("token", tokenOf(userId));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder processPage(long userId) {
        return get("/admin/process/1/500").header("token", tokenOf(userId));
    }

    private String tokenOf(long userId) {
        return jwtHelper.createTokenPair(userId, usernameOf(userId), 0L).token();
    }

    private String usernameOf(long userId) {
        return jdbcTemplate.queryForObject("SELECT username FROM sys_user WHERE id = ?", String.class, userId);
    }

    private void insertDept(long id, long parentId, String ancestors, int isDeleted) {
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, 1, ?)", id, parentId, ancestors, "部门" + id, isDeleted);
    }

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "范围角色" + id, "dc_role_" + id, dataScope);
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

    private void grantBackofficePages(long... roleIds) {
        int userPage = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/sysUser' AND type = 1 AND is_deleted = 0",
                Integer.class);
        int processPage = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/processMgr/process' AND type = 1 AND is_deleted = 0",
                Integer.class);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, userPage);
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, processPage);
        }
    }

    private void insertProcess(long id, long userId, String title) {
        jdbcTemplate.update("INSERT INTO oa_process (id, process_code, user_id, title, status, create_time, "
                + "update_time, is_deleted) VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, "PC-" + id, userId, title);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM oa_process WHERE id BETWEEN 9000 AND 9099");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id BETWEEN 201 AND 299 "
                + "OR user_id BETWEEN 101 AND 199");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id BETWEEN 201 AND 299");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 101 AND 199");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 10 AND 99");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 201 AND 299");
    }
}
