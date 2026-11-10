package com.lautung.oa.attendance;

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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #279 的打卡合同（ADR 0073）：办事今日打卡状态（班次/工作日判定/两卡状态，服务器时间）、
 * 上班/下班打卡写入（source=1 网页）、工作日公式（组工作日 + 休假日/补班日）、
 * 非工作日与无组账号 201 命名拒绝、重复打卡追加流水但状态取极值（上班最早/下班最晚，
 * 补卡行取最新），以及后台打卡记录页的五档数据范围（部门列 sys_user.dept_id 当前 +
 * 本人列 sys_user.id）与姓名/日期/来源筛选。
 *
 * <p>夹具统一 t279 前缀，日期一律相对 {@link LocalDate#now()} 构造，避免固定日期让
 * 工作日判定在周末翻红。拒绝用例都先钉 {@code $.message}（ADR 0034）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_punch;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class AttendancePunchContractTest {

    private static final long RECORD_PAGE_ROLE_BASE = 2791L;
    private static final long GROUP_WORKDAY_ID = 2791L;
    private static final long GROUP_REST_ID = 2792L;
    private static final long SHIFT_ID = 2791L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void seedFixtures() {
        resetFixtures();
        insertDept(2790, 0, ",0,2790,", "t279本部");
        insertDept(2791, 2790, ",0,2790,2791,", "t279子部");
        insertDept(2792, 2791, ",0,2790,2791,2792,", "t279孙部");

        insertRole(2791, 1);
        insertRole(2792, 2);
        insertRole(2793, 3);
        insertRole(2794, 4);
        insertRole(2795, 5);
        jdbcTemplate.update("INSERT INTO sys_role_dept (role_id, dept_id, is_deleted) VALUES (2792, 2792, 0)");
        grantRecordPage(2791, 2792, 2793, 2794, 2795);

        insertUser(27901, "t279-own", "甲一", 2790L, 2793);
        insertUser(27902, "t279-below", "甲二", 2791L, 2794);
        insertUser(27903, "t279-self", "甲三", 2792L, 2795);
        insertUser(27904, "t279-custom", "甲四", 2790L, 2792);
        insertUser(27905, "t279-union", "甲五", 2791L, 2792, 2795);
        insertUser(27906, "t279-all", "甲六", 2790L, 2791);
        insertUser(27907, "t279-nogroup", "甲七", 2790L, 2793);
        insertUser(27908, "t279-puncher", "甲八", 2790L, 2793);
        insertUser(27909, "t279-nodept", "甲九", null, 2793);
        insertUser(27910, "t279-clock", "甲十", 2790L, 2793);
        insertUser(27911, "t279-rest", "甲十一", 2790L, 2793);
        insertUser(27912, "t279-holiday", "甲十二", 2790L, 2793);
        insertUser(27913, "t279-makeup", "甲十三", 2790L, 2793);
        insertUser(27914, "t279-other", "甲十四", 2792L, 2795);
        insertUser(27915, "t279-operator", "操作员", 2790L, 2793);
        insertUser(27916, "t279-status", "甲十六", 2790L, 2793);
        insertUser(27917, "t279-nogroup2", "甲十七", 2790L, 2793);

        jdbcTemplate.update("INSERT INTO attendance_shift (id, name, start_time, end_time, "
                        + "lunch_start, lunch_end, grace_minutes, create_time, update_time, is_deleted) "
                        + "VALUES (?, 't279标准班', ?, ?, ?, ?, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                SHIFT_ID, LocalTime.of(9, 0), LocalTime.of(18, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0));
        insertGroup(GROUP_WORKDAY_ID, "t279工作日组", workdaysIncludingToday());
        insertGroup(GROUP_REST_ID, "t279休息组", workdaysExcludingToday());
        for (long userId : List.of(27901L, 27902L, 27903L, 27904L, 27905L, 27906L,
                27908L, 27910L, 27912L, 27916L)) {
            insertMember(GROUP_WORKDAY_ID, userId);
        }
        insertMember(GROUP_REST_ID, 27911L);
        insertMember(GROUP_REST_ID, 27913L);

        // 记录页夹具：27901 额外一条补卡行（操作员 27915），其余人各一条网页上班卡。
        List<Long> punched = List.of(27901L, 27902L, 27903L, 27904L, 27905L,
                27906L, 27907L, 27908L, 27909L, 27914L);
        for (int index = 0; index < punched.size(); index++) {
            insertPunch(punched.get(index), 1, LocalTime.of(9, 0).plusMinutes(index), 1, null);
        }
        insertPunch(27901L, 1, LocalTime.of(10, 30), 2, 27915L);

        // 今日状态夹具：补卡行覆盖网页行（上班取最新补卡、下班取最新补卡）。
        insertPunch(27916L, 1, LocalTime.of(9, 0), 1, null);
        insertPunch(27916L, 1, LocalTime.of(8, 30), 2, 27915L);
        insertPunch(27916L, 2, LocalTime.of(18, 10), 1, null);
        insertPunch(27916L, 2, LocalTime.of(18, 30), 2, 27915L);
    }

    @AfterEach
    void cleanFixtures() {
        resetFixtures();
    }

    private void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_punch WHERE user_id BETWEEN 27900 AND 27999");
        jdbcTemplate.update("DELETE FROM attendance_group_member "
                + "WHERE user_id BETWEEN 27900 AND 27999 OR group_id IN (?, ?)",
                GROUP_WORKDAY_ID, GROUP_REST_ID);
        jdbcTemplate.update("DELETE FROM attendance_group WHERE id IN (?, ?)",
                GROUP_WORKDAY_ID, GROUP_REST_ID);
        jdbcTemplate.update("DELETE FROM attendance_shift WHERE id = ?", SHIFT_ID);
        jdbcTemplate.update("DELETE FROM attendance_holiday WHERE name LIKE 't279%'");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 2791 AND 2799");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id BETWEEN 2791 AND 2799");
        jdbcTemplate.update("DELETE FROM sys_user_role "
                + "WHERE user_id BETWEEN 27900 AND 27999 OR role_id BETWEEN 2791 AND 2799");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 27900 AND 27999");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 2791 AND 2799");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 2790 AND 2799");
    }

    @Test
    void todayShowsShiftWorkdayAndEffectivePunchTimes() throws Exception {
        mockMvc.perform(get("/admin/attendance/my/today").header("token", tokenOf(27916L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.date").value(today.toString()))
                .andExpect(jsonPath("$.data.weekday").value(today.getDayOfWeek().getValue()))
                .andExpect(jsonPath("$.data.dayType").value(1))
                .andExpect(jsonPath("$.data.workday").value(true))
                .andExpect(jsonPath("$.data.hasGroup").value(true))
                .andExpect(jsonPath("$.data.groupName").value("t279工作日组"))
                .andExpect(jsonPath("$.data.shift.name").value("t279标准班"))
                .andExpect(jsonPath("$.data.shift.startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.shift.endTime").value("18:00:00"))
                .andExpect(jsonPath("$.data.shift.graceMinutes").value(10))
                // 补卡行取最新：上班 08:30、下班 18:30，而不是网页行 09:00 / 18:10。
                .andExpect(jsonPath("$.data.clockInTime").value("08:30:00"))
                .andExpect(jsonPath("$.data.clockOutTime").value("18:30:00"));
    }

    @Test
    void punchInAndOutAppendWebRowsAndReturnTodayStatus() throws Exception {
        String token = tokenOf(27910L);

        mockMvc.perform(post("/admin/attendance/my/punch/in").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.workday").value(true))
                .andExpect(jsonPath("$.data.clockInTime").isNotEmpty())
                .andExpect(jsonPath("$.data.clockOutTime").value(nullValue()));

        mockMvc.perform(post("/admin/attendance/my/punch/out").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.clockInTime").isNotEmpty())
                .andExpect(jsonPath("$.data.clockOutTime").isNotEmpty());

        assertEquals(1, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27910 "
                + "AND punch_date = '" + today + "' AND slot = 1 AND source = 1 AND is_deleted = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27910 "
                + "AND punch_date = '" + today + "' AND slot = 2 AND source = 1 AND is_deleted = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27910 "
                + "AND operator_user_id IS NOT NULL"));
    }

    @Test
    void repeatedPunchKeepsEarliestInAndLatestOut() throws Exception {
        insertPunch(27910L, 1, LocalTime.of(7, 0), 1, null);
        insertPunch(27910L, 2, LocalTime.of(23, 0), 1, null);
        String token = tokenOf(27910L);

        mockMvc.perform(post("/admin/attendance/my/punch/in").header("token", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.clockInTime").value("07:00:00"));
        mockMvc.perform(post("/admin/attendance/my/punch/out").header("token", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.clockOutTime").value("23:00:00"));

        // 每次打卡一行流水；状态取极值，不合并、不覆盖原行。
        assertEquals(2, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27910 "
                + "AND punch_date = '" + today + "' AND slot = 1 AND source = 1"));
        assertEquals(2, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27910 "
                + "AND punch_date = '" + today + "' AND slot = 2 AND source = 1"));
    }

    @Test
    void nonWorkdayPunchIsRejected() throws Exception {
        mockMvc.perform(post("/admin/attendance/my/punch/in").header("token", tokenOf(27911L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("今日无排班"));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27911"));

        mockMvc.perform(get("/admin/attendance/my/today").header("token", tokenOf(27911L)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasGroup").value(true))
                .andExpect(jsonPath("$.data.workday").value(false))
                .andExpect(jsonPath("$.data.dayType").value(2))
                .andExpect(jsonPath("$.data.shift.name").value("t279标准班"));
    }

    @Test
    void holidayOnWorkdayRejectsAndMakeupOnRestDayAllows() throws Exception {
        insertHoliday(today, "t279休假日", 1);
        mockMvc.perform(post("/admin/attendance/my/punch/in").header("token", tokenOf(27912L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("今日无排班"));
        mockMvc.perform(get("/admin/attendance/my/today").header("token", tokenOf(27912L)))
                .andExpect(jsonPath("$.data.dayType").value(3))
                .andExpect(jsonPath("$.data.workday").value(false));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27912"));

        jdbcTemplate.update("UPDATE attendance_holiday SET day_type = 2 WHERE holiday_date = ?", today);
        mockMvc.perform(post("/admin/attendance/my/punch/in").header("token", tokenOf(27911L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.dayType").value(4))
                .andExpect(jsonPath("$.data.workday").value(true));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27911 "
                + "AND punch_date = '" + today + "' AND slot = 1 AND source = 1"));
    }

    @Test
    void accountWithoutGroupCannotPunch() throws Exception {
        mockMvc.perform(post("/admin/attendance/my/punch/in").header("token", tokenOf(27917L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("未加入考勤组，不能打卡"));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 27917"));

        mockMvc.perform(get("/admin/attendance/my/today").header("token", tokenOf(27917L)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.hasGroup").value(false))
                .andExpect(jsonPath("$.data.workday").value(false))
                .andExpect(jsonPath("$.data.dayType").value(0))
                .andExpect(jsonPath("$.data.shift").value(nullValue()));
    }

    @Test
    void punchRecordPageAppliesDataScopeFiveTiers() throws Exception {
        // 1 全部：所有夹具流水（10 人 11 行 + 今日状态夹具 27916 的 4 行，共 15 行）。
        mockMvc.perform(punchPage(27906L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(15))
                .andExpect(jsonPath("$.data.records[*].username", hasItems(
                        "t279-own", "t279-below", "t279-self", "t279-custom", "t279-union",
                        "t279-all", "t279-nogroup", "t279-puncher", "t279-nodept", "t279-other",
                        "t279-status")));

        // 2 自定部门（2792）：只看孙部的人。
        mockMvc.perform(punchPage(27904L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].username", hasItems("t279-self", "t279-other")))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItem("t279-own"))));

        // 3 本部门（2790）：只看本部的人，27901 两条流水、27916 四条今日状态流水。
        mockMvc.perform(punchPage(27901L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.records[*].username",
                        hasItems("t279-own", "t279-custom", "t279-all", "t279-nogroup",
                                "t279-puncher", "t279-status")))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItem("t279-below"))));

        // 4 本部门及以下（2791 → 2791/2792）。
        mockMvc.perform(punchPage(27902L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[*].username",
                        hasItems("t279-below", "t279-self", "t279-union", "t279-other")))
                .andExpect(jsonPath("$.data.records[*].username", not(hasItem("t279-own"))));

        // 5 仅本人。
        mockMvc.perform(punchPage(27903L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("t279-self"));

        // 多角色并集：自定部门 2792 ∪ 仅本人（27905 在 2791）。
        mockMvc.perform(punchPage(27905L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].username",
                        hasItems("t279-self", "t279-other", "t279-union")));

        // 本部门档但本人无部门：零行，不是全部。
        mockMvc.perform(punchPage(27909L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    void punchRecordPageFiltersByNameDateAndSourceWithJoinedNames() throws Exception {
        mockMvc.perform(punchPage(27906L).param("name", "t279-own"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].username", hasItems("t279-own")));

        mockMvc.perform(punchPage(27906L).param("source", "2"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].source").value(2))
                .andExpect(jsonPath("$.data.records[0].slot").value(2))
                .andExpect(jsonPath("$.data.records[0].name").value("甲十六"))
                .andExpect(jsonPath("$.data.records[0].deptName").value("t279本部"))
                .andExpect(jsonPath("$.data.records[0].operatorName").value("操作员"));

        mockMvc.perform(punchPage(27906L)
                        .param("beginDate", today.toString())
                        .param("endDate", today.toString()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(15));
        mockMvc.perform(punchPage(27906L)
                        .param("beginDate", today.minusDays(1).toString())
                        .param("endDate", today.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.total").value(0));

        // 稳定排序：打卡时间倒序，最新一条（27916 的下班补卡 18:30）在最前。
        mockMvc.perform(punchPage(27906L))
                .andExpect(jsonPath("$.data.records[0].username").value("t279-status"))
                .andExpect(jsonPath("$.data.records[0].punchTime")
                        .value(today + " 18:30:00"));

        mockMvc.perform(punchPage(27906L).param("source", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("打卡来源不合法"));
    }

    @Test
    void punchRecordPageRequiresRecordPageGrant() throws Exception {
        mockMvc.perform(get("/admin/attendance/punch/1/10").header("token", tokenOf(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder punchPage(long userId) {
        return get("/admin/attendance/punch/1/500").header("token", tokenOf(userId));
    }

    private String tokenOf(long userId) {
        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM sys_user WHERE id = ?", String.class, userId);
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private void insertDept(long id, long parentId, String ancestors, String name) {
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, 1, 0)", id, parentId, ancestors, name);
    }

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "t279角色" + id, "t279_role_" + id, dataScope);
    }

    private void insertUser(long id, String username, String name, Long deptId, long... roleIds) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, dept_id, status, is_deleted) "
                + "VALUES (?, ?, '', ?, ?, 1, 0)", id, username, name, deptId);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                    id, roleId);
        }
    }

    private void grantRecordPage(long... roleIds) {
        long menuId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/business/attendance/record' "
                        + "AND type = 1 AND is_deleted = 0", Long.class);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, menuId);
        }
    }

    private void insertGroup(long id, String name, String workdays) {
        jdbcTemplate.update("INSERT INTO attendance_group (id, name, shift_id, workdays, remark, "
                        + "create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, name, SHIFT_ID, workdays);
    }

    private void insertMember(long groupId, long userId) {
        jdbcTemplate.update("INSERT INTO attendance_group_member (group_id, user_id, "
                + "create_time, update_time) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                groupId, userId);
    }

    private void insertPunch(long userId, int slot, LocalTime time, int source, Long operatorId) {
        jdbcTemplate.update("INSERT INTO attendance_punch (user_id, punch_date, slot, punch_time, "
                        + "source, operator_user_id, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, today, slot, today.atTime(time), source, operatorId);
    }

    private void insertHoliday(LocalDate date, String name, int dayType) {
        jdbcTemplate.update("INSERT INTO attendance_holiday (holiday_date, name, day_type, "
                        + "create_time, update_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                date, name, dayType);
    }

    private String workdaysIncludingToday() {
        return String.valueOf(today.getDayOfWeek().getValue());
    }

    private String workdaysExcludingToday() {
        return IntStream.rangeClosed(1, 7)
                .filter(day -> day != today.getDayOfWeek().getValue())
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
