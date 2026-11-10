package com.lautung.oa.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lautung.oa.attendance.service.AttendanceClock;
import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #283 的月度统计合同（ADR 0073 §统计口径）。
 *
 * <p>固定时钟 2026-03-09 10:00；统计月固定 2026-02（整月已过，避免「未到下班时刻」
 * 干扰）。夹具同时覆盖正常/迟到/早退/迟到且早退/缺卡/休息日/休假日/补班日，
 * 应出勤按工作日判定公式逐日计：2 月 20 个工作日 − 2/12 休假日 + 2/14 补班日 = 20。
 *
 * <p>覆盖：口径与推导 service 一致（逐日汇总对比）、行集合（组内启用账号 ∪ 当月
 * 有流水账号，停用账号只出现在有流水的月份）、五档数据范围与下钻不越范围、页面授予。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_stat;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LoginAuthContractTest.InMemoryRedisConfig.class,
        AttendanceStatContractTest.FixedAttendanceClockConfig.class})
class AttendanceStatContractTest {

    private static final String MONTH = "2026-02";
    private static final LocalDate FEB_BEGIN = LocalDate.of(2026, 2, 1);
    private static final LocalDate FEB_END = LocalDate.of(2026, 2, 28);

    private static final long GROUP_ID = 2830L;
    private static final long SHIFT_ID = 2831L;
    private static final long NORMAL_ID = 28301L;
    private static final long LEAVE_ID = 28302L;
    private static final long DISABLED_PUNCH_ID = 28303L;
    private static final long MEMBER_NO_PUNCH_ID = 28304L;
    private static final long NON_MEMBER_PUNCH_ID = 28305L;
    private static final long DISABLED_NO_PUNCH_ID = 28306L;
    private static final long SOFT_DELETED_PUNCH_ID = 28307L;
    private static final long OPERATOR_ALL_ID = 28351L;
    private static final long OPERATOR_CUSTOM_ID = 28352L;
    private static final long OPERATOR_DEPT_ID = 28353L;
    private static final long OPERATOR_BELOW_ID = 28354L;
    private static final long OPERATOR_SELF_ID = 28355L;
    private static final long OPERATOR_NO_DEPT_ID = 28359L;
    private static final long TARGET_ROOT_ID = 28361L;
    private static final long TARGET_CHILD_ID = 28362L;
    private static final long TARGET_GRAND_ID = 28363L;
    private static final long TARGET_CHILD_DISABLED_ID = 28364L;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedAttendanceClockConfig {

        @Bean
        @Primary
        AttendanceClock statTestClock() {
            return () -> LocalDateTime.of(2026, 3, 9, 10, 0);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void seedFixtures() {
        resetFixtures();

        insertDept(2830, 0, ",0,2830,", "t283本部");
        insertDept(2831, 2830, ",0,2830,2831,", "t283子部");
        insertDept(2832, 2831, ",0,2830,2831,2832,", "t283孙部");

        insertRole(2831, 1);
        insertRole(2832, 2);
        insertRole(2833, 3);
        insertRole(2834, 4);
        insertRole(2835, 5);
        insertRole(2839, 5);
        jdbcTemplate.update("INSERT INTO sys_role_dept (role_id, dept_id, is_deleted) VALUES (2832, 2832, 0)");
        grantStatPage(2831, 2832, 2833, 2834, 2835);

        insertUser(NORMAL_ID, "t283-normal", "统计甲", 2830L, 1, 2839);
        insertUser(LEAVE_ID, "t283-leave", "请假乙", 2830L, 1, 2839);
        insertUser(DISABLED_PUNCH_ID, "t283-disabled-punch", "停用丙", 2830L, 0, 2839);
        insertUser(MEMBER_NO_PUNCH_ID, "t283-member-nopunch", "在组丁", 2830L, 1, 2839);
        insertUser(NON_MEMBER_PUNCH_ID, "t283-nonmember-punch", "流水戊", 2830L, 1, 2839);
        insertUser(DISABLED_NO_PUNCH_ID, "t283-off-nopunch", "停用己", 2830L, 0, 2839);
        insertUser(SOFT_DELETED_PUNCH_ID, "t283-soft-deleted", "软删庚", 2830L, 0, 2839);
        insertUser(OPERATOR_ALL_ID, "t283-op-all", "范围全部", 2830L, 1, 2831);
        insertUser(OPERATOR_CUSTOM_ID, "t283-op-custom", "范围自定", 2830L, 1, 2832);
        insertUser(OPERATOR_DEPT_ID, "t283-op-dept", "范围本部门", 2831L, 1, 2833);
        insertUser(OPERATOR_BELOW_ID, "t283-op-below", "范围本部门及以下", 2831L, 1, 2834);
        insertUser(OPERATOR_SELF_ID, "t283-op-self", "范围本人", 2830L, 1, 2835);
        insertUser(OPERATOR_NO_DEPT_ID, "t283-op-nodept", "无部门", null, 1, 2833);
        insertUser(TARGET_ROOT_ID, "t283-target-root", "目标本部", 2830L, 1, 2839);
        insertUser(TARGET_CHILD_ID, "t283-target-child", "目标子部", 2831L, 1, 2839);
        insertUser(TARGET_GRAND_ID, "t283-target-grand", "目标孙部", 2832L, 1, 2839);
        insertUser(TARGET_CHILD_DISABLED_ID, "t283-target-off", "目标子部停用", 2831L, 0, 2839);

        jdbcTemplate.update("INSERT INTO attendance_shift (id, name, start_time, end_time, "
                        + "lunch_start, lunch_end, grace_minutes, create_time, update_time, is_deleted) "
                        + "VALUES (?, 't283标准班', ?, ?, ?, ?, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                SHIFT_ID, LocalTime.of(9, 0), LocalTime.of(18, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0));
        jdbcTemplate.update("INSERT INTO attendance_group (id, name, shift_id, workdays, remark, "
                        + "create_time, update_time, is_deleted) "
                        + "VALUES (?, 't283考勤组', ?, '1,2,3,4,5', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                GROUP_ID, SHIFT_ID);
        for (long userId : List.of(NORMAL_ID, LEAVE_ID, DISABLED_PUNCH_ID, MEMBER_NO_PUNCH_ID,
                DISABLED_NO_PUNCH_ID, SOFT_DELETED_PUNCH_ID, OPERATOR_ALL_ID, OPERATOR_CUSTOM_ID,
                OPERATOR_DEPT_ID, OPERATOR_BELOW_ID, OPERATOR_SELF_ID, OPERATOR_NO_DEPT_ID,
                TARGET_ROOT_ID, TARGET_CHILD_ID, TARGET_GRAND_ID, TARGET_CHILD_DISABLED_ID)) {
            insertMember(userId);
        }

        insertHoliday(LocalDate.of(2026, 2, 12), "t283休假日", 1);
        insertHoliday(LocalDate.of(2026, 2, 14), "t283补班日", 2);

        seedNormalPunches();
        seedLeaveUser();
        seedRowSetPunches();
        seedOvertime();
    }

    @AfterEach
    void cleanFixtures() {
        resetFixtures();
    }

    private void seedNormalPunches() {
        for (LocalDate date : febWorkdays()) {
            if (date.equals(LocalDate.of(2026, 2, 3))) {
                insertPunch(NORMAL_ID, date, 1, LocalTime.of(9, 15), 1, null);
                insertPunch(NORMAL_ID, date, 2, LocalTime.of(18, 0), 1, null);
            } else if (date.equals(LocalDate.of(2026, 2, 4))) {
                insertPunch(NORMAL_ID, date, 1, LocalTime.of(9, 0), 1, null);
                insertPunch(NORMAL_ID, date, 2, LocalTime.of(17, 40), 1, null);
            } else if (date.equals(LocalDate.of(2026, 2, 5))) {
                insertPunch(NORMAL_ID, date, 1, LocalTime.of(9, 15), 1, null);
                insertPunch(NORMAL_ID, date, 2, LocalTime.of(17, 40), 1, null);
            } else if (date.equals(LocalDate.of(2026, 2, 6))) {
                // 缺卡：只有上班卡且已过下班时刻；上班卡本身迟到，迟到次数按卡计。
                insertPunch(NORMAL_ID, date, 1, LocalTime.of(9, 15), 1, null);
            } else {
                punchNormal(NORMAL_ID, date);
            }
        }
        // 休息日有打卡也照常是休息日，不计迟到/早退。
        insertPunch(NORMAL_ID, LocalDate.of(2026, 2, 7), 1, LocalTime.of(19, 0), 1, null);
        insertPunch(NORMAL_ID, LocalDate.of(2026, 2, 7), 2, LocalTime.of(20, 0), 1, null);
        // 休假日有打卡也不计应出勤。
        punchNormal(NORMAL_ID, LocalDate.of(2026, 2, 12));
    }

    private void seedLeaveUser() {
        for (LocalDate date : febWorkdays()) {
            if (date.equals(LocalDate.of(2026, 2, 10)) || date.equals(LocalDate.of(2026, 2, 11))
                    || date.equals(LocalDate.of(2026, 2, 17))) {
                continue;
            }
            punchNormal(LEAVE_ID, date);
        }
        insertLeave(LEAVE_ID, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 11), "年假");
        insertLeave(LEAVE_ID, LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 17), "事假");
    }

    private void seedRowSetPunches() {
        punchNormal(DISABLED_PUNCH_ID, LocalDate.of(2026, 2, 3));
        punchNormal(NON_MEMBER_PUNCH_ID, LocalDate.of(2026, 2, 4));
        punchNormal(TARGET_ROOT_ID, LocalDate.of(2026, 2, 4));
        punchNormal(TARGET_CHILD_ID, LocalDate.of(2026, 2, 4));
        punchNormal(TARGET_GRAND_ID, LocalDate.of(2026, 2, 4));
        punchNormal(TARGET_CHILD_DISABLED_ID, LocalDate.of(2026, 2, 4));
        // 只有软删流水：不得进入行集合。
        jdbcTemplate.update("INSERT INTO attendance_punch (user_id, punch_date, slot, punch_time, "
                        + "source, operator_user_id, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, 1, ?, 1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)",
                SOFT_DELETED_PUNCH_ID, LocalDate.of(2026, 2, 4), LocalDate.of(2026, 2, 4).atTime(9, 0));
    }

    private void seedOvertime() {
        insertOvertime(NORMAL_ID, LocalDate.of(2026, 2, 7), LocalTime.of(19, 0),
                LocalTime.of(21, 0), new BigDecimal("2.00"), 2);
        insertOvertime(NORMAL_ID, LocalDate.of(2026, 2, 14), LocalTime.of(10, 0),
                LocalTime.of(12, 30), new BigDecimal("2.50"), 2);
        insertOvertime(NORMAL_ID, LocalDate.of(2026, 2, 9), LocalTime.of(19, 0),
                LocalTime.of(21, 0), new BigDecimal("2.00"), 1);
        insertOvertime(NORMAL_ID, LocalDate.of(2026, 2, 16), LocalTime.of(19, 0),
                LocalTime.of(21, 0), new BigDecimal("2.00"), -1);
        insertOvertime(NORMAL_ID, LocalDate.of(2026, 3, 1), LocalTime.of(19, 0),
                LocalTime.of(21, 0), new BigDecimal("2.00"), 2);
    }

    @Test
    void summaryAggregatesDerivedDaysLeaveTypesAndOvertime() throws Exception {
        JsonNode normal = statRow(OPERATOR_ALL_ID, MONTH, NORMAL_ID);
        assertNotNull(normal, "正常账号应在汇总行集合内");
        assertEquals(20, normal.path("requiredDays").asInt());
        assertEquals(19, normal.path("actualDays").asInt());
        assertEquals(3, normal.path("lateCount").asInt());
        assertEquals(2, normal.path("earlyCount").asInt());
        assertEquals(1, normal.path("missingDays").asInt());
        assertEquals(0, normal.path("leaveDays").size());
        assertEquals(0, new BigDecimal("4.50").compareTo(normal.path("overtimeHours").decimalValue()));

        JsonNode leave = statRow(OPERATOR_ALL_ID, MONTH, LEAVE_ID);
        assertNotNull(leave, "请假账号应在汇总行集合内");
        assertEquals(20, leave.path("requiredDays").asInt());
        assertEquals(17, leave.path("actualDays").asInt());
        assertEquals(0, leave.path("missingDays").asInt());
        assertEquals(0, leave.path("lateCount").asInt());
        assertEquals(0, leave.path("earlyCount").asInt());
        Map<String, Integer> leaveDays = leaveTypeDays(leave);
        assertEquals(Map.of("年假", 2, "事假", 1), leaveDays);

        // 组内启用但无打卡、无考勤组但有流水：行集合两种来源都被列出。
        assertNotNull(statRow(OPERATOR_ALL_ID, MONTH, MEMBER_NO_PUNCH_ID));
        assertNotNull(statRow(OPERATOR_ALL_ID, MONTH, NON_MEMBER_PUNCH_ID));
    }

    @Test
    void summaryIsTheDayByDayAggregationOfTheDerivationService() throws Exception {
        for (long userId : List.of(NORMAL_ID, LEAVE_ID)) {
            JsonNode days = readJson("/admin/attendance/my/records", tokenOf(userId),
                    "month", MONTH).path("data").path("days");
            int required = 0;
            int missing = 0;
            int late = 0;
            int early = 0;
            Map<String, Integer> leaveDays = new HashMap<>();
            for (JsonNode day : days) {
                if (day.path("workday").asBoolean()) {
                    required++;
                }
                int status = day.path("status").asInt(0);
                if (status == 5) {
                    missing++;
                }
                if (status == 6) {
                    leaveDays.merge(day.path("leaveTypeName").asText(), 1, Integer::sum);
                }
                if (day.path("late").asBoolean()) {
                    late++;
                }
                if (day.path("early").asBoolean()) {
                    early++;
                }
            }
            int leaveTotal = leaveDays.values().stream().mapToInt(Integer::intValue).sum();

            JsonNode row = statRow(OPERATOR_ALL_ID, MONTH, userId);
            assertNotNull(row);
            assertEquals(required, row.path("requiredDays").asInt(), "应出勤与逐日汇总不一致");
            assertEquals(required - leaveTotal - missing, row.path("actualDays").asInt(),
                    "实出勤口径与逐日汇总不一致");
            assertEquals(missing, row.path("missingDays").asInt());
            assertEquals(late, row.path("lateCount").asInt(), "迟到次数按卡计");
            assertEquals(early, row.path("earlyCount").asInt(), "早退次数按卡计");
            assertEquals(leaveDays, leaveTypeDays(row));
        }
    }

    @Test
    void rowSetIsActiveMembersUnionAccountsWithMonthPunchRows() throws Exception {
        JsonNode february = statPage(OPERATOR_ALL_ID, MONTH);
        assertTrue(hasUsername(february, "t283-normal"));
        assertTrue(hasUsername(february, "t283-member-nopunch"), "组内启用账号无流水也要出现");
        assertTrue(hasUsername(february, "t283-disabled-punch"), "停用账号当月有流水要出现");
        assertTrue(hasUsername(february, "t283-nonmember-punch"), "当月有流水的非组员要出现");
        assertTrue(hasUsername(february, "t283-target-off"), "停用的范围内账号有流水要出现");
        assertFalse(hasUsername(february, "t283-off-nopunch"), "停用账号无流水不得出现");
        assertFalse(hasUsername(february, "t283-soft-deleted"), "软删流水不算有流水");

        JsonNode january = statPage(OPERATOR_ALL_ID, "2026-01");
        assertTrue(hasUsername(january, "t283-member-nopunch"), "组内启用账号在没有流水的月份仍要出现");
        assertFalse(hasUsername(january, "t283-disabled-punch"),
                "停用账号只出现在有流水的月份");
        assertFalse(hasUsername(january, "t283-nonmember-punch"),
                "无流水的非组员不得出现");
    }

    @Test
    void listAppliesFiveTierDataScope() throws Exception {
        JsonNode all = statPage(OPERATOR_ALL_ID, MONTH);
        assertTrue(hasUsername(all, "t283-target-root"));
        assertTrue(hasUsername(all, "t283-target-child"));
        assertTrue(hasUsername(all, "t283-target-grand"));

        JsonNode custom = statPage(OPERATOR_CUSTOM_ID, MONTH);
        assertTrue(hasUsername(custom, "t283-target-grand"));
        assertFalse(hasUsername(custom, "t283-target-child"));
        assertFalse(hasUsername(custom, "t283-target-root"));

        JsonNode dept = statPage(OPERATOR_DEPT_ID, MONTH);
        assertTrue(hasUsername(dept, "t283-target-child"));
        assertTrue(hasUsername(dept, "t283-target-off"));
        assertFalse(hasUsername(dept, "t283-target-grand"));
        assertFalse(hasUsername(dept, "t283-target-root"));

        JsonNode below = statPage(OPERATOR_BELOW_ID, MONTH);
        assertTrue(hasUsername(below, "t283-target-child"));
        assertTrue(hasUsername(below, "t283-target-grand"));
        assertFalse(hasUsername(below, "t283-target-root"));

        JsonNode self = statPage(OPERATOR_SELF_ID, MONTH);
        assertTrue(hasUsername(self, "t283-op-self"));
        assertFalse(hasUsername(self, "t283-target-root"));
        assertFalse(hasUsername(self, "t283-target-child"));

        JsonNode noDept = statPage(OPERATOR_NO_DEPT_ID, MONTH);
        assertEquals(0, noDept.path("data").path("total").asInt(), "空集得零行，不是全部");
    }

    @Test
    void dailyDrillReusesDerivationAndCannotBypassListScope() throws Exception {
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_ALL_ID))
                        .param("userId", String.valueOf(NORMAL_ID)).param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.month").value(MONTH))
                .andExpect(jsonPath("$.data.days.length()").value(28))
                .andExpect(jsonPath("$.data.days[5].date").value("2026-02-06"))
                .andExpect(jsonPath("$.data.days[5].status").value(5))
                .andExpect(jsonPath("$.data.days[5].late").value(true));

        // 下钻与列表同套范围：本部门负责人不能靠 userId 看孙部账号。
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_DEPT_ID))
                        .param("userId", String.valueOf(TARGET_GRAND_ID)).param("month", MONTH))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));

        // 仅本人档也不能看同部门他人。
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_SELF_ID))
                        .param("userId", String.valueOf(TARGET_ROOT_ID)).param("month", MONTH))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_SELF_ID))
                        .param("userId", String.valueOf(OPERATOR_SELF_ID)).param("month", MONTH))
                .andExpect(jsonPath("$.code").value(200));

        // 停用但有当月流水的范围账号可下钻。
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_DEPT_ID))
                        .param("userId", String.valueOf(TARGET_CHILD_DISABLED_ID)).param("month", MONTH))
                .andExpect(jsonPath("$.code").value(200));

        // 不存在账号与范围外同一句，不泄露是否存在。
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_ALL_ID))
                        .param("userId", "28999").param("month", MONTH))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
    }

    @Test
    void invalidMonthAndMissingPageGrantAreRejected() throws Exception {
        mockMvc.perform(get("/admin/attendance/stat/1/50").header("token", tokenOf(OPERATOR_ALL_ID))
                        .param("month", "2026-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("月份格式不正确"));

        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(OPERATOR_ALL_ID))
                        .param("userId", String.valueOf(NORMAL_ID)).param("month", "2026-13"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("月份格式不正确"));

        // 无统计页授予：读端点一律 205。
        long noGrantId = 28399L;
        insertUser(noGrantId, "t283-nogrant", "无统计页", 2830L, 1, 2839);
        insertPunch(noGrantId, LocalDate.of(2026, 2, 4), 1, LocalTime.of(9, 0), 1, null);
        mockMvc.perform(get("/admin/attendance/stat/1/50").header("token", tokenOf(noGrantId))
                        .param("month", MONTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(get("/admin/attendance/stat/daily").header("token", tokenOf(noGrantId))
                        .param("userId", String.valueOf(NORMAL_ID)).param("month", MONTH))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
    }

    private JsonNode statPage(long operatorId, String month) throws Exception {
        return readJson("/admin/attendance/stat/1/100", tokenOf(operatorId), "month", month);
    }

    private JsonNode statRow(long operatorId, String month, long userId) throws Exception {
        for (JsonNode row : statPage(operatorId, month).path("data").path("records")) {
            if (row.path("userId").asLong() == userId) {
                return row;
            }
        }
        return null;
    }

    private JsonNode readJson(String path, String token, String... params) throws Exception {
        var request = get(path).header("token", token);
        for (int index = 0; index + 1 < params.length; index += 2) {
            request.param(params[index], params[index + 1]);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Integer> leaveTypeDays(JsonNode row) {
        Map<String, Integer> days = new HashMap<>();
        for (JsonNode leave : row.path("leaveDays")) {
            days.put(leave.path("leaveTypeName").asText(), leave.path("days").asInt());
        }
        return days;
    }

    private boolean hasUsername(JsonNode page, String username) {
        for (JsonNode row : page.path("data").path("records")) {
            if (username.equals(row.path("username").asText())) {
                return true;
            }
        }
        return false;
    }

    private List<LocalDate> febWorkdays() {
        List<LocalDate> workdays = new ArrayList<>();
        for (LocalDate date = FEB_BEGIN; !date.isAfter(FEB_END); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            boolean workday = weekday <= 5;
            if (date.equals(LocalDate.of(2026, 2, 12))) {
                workday = false;
            }
            if (date.equals(LocalDate.of(2026, 2, 14))) {
                workday = true;
            }
            if (workday) {
                workdays.add(date);
            }
        }
        return workdays;
    }

    private String tokenOf(long userId) {
        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM sys_user WHERE id = ?", String.class, userId);
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_punch WHERE user_id BETWEEN 28300 AND 28399");
        jdbcTemplate.update("DELETE FROM attendance_leave WHERE applicant_id BETWEEN 28300 AND 28399");
        jdbcTemplate.update("DELETE FROM attendance_overtime WHERE applicant_id BETWEEN 28300 AND 28399");
        jdbcTemplate.update("DELETE FROM attendance_group_member WHERE user_id BETWEEN 28300 AND 28399");
        jdbcTemplate.update("DELETE FROM attendance_group WHERE id BETWEEN 2830 AND 2839");
        jdbcTemplate.update("DELETE FROM attendance_shift WHERE id BETWEEN 2830 AND 2839");
        jdbcTemplate.update("DELETE FROM attendance_holiday WHERE name LIKE 't283%'");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 2830 AND 2839");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id BETWEEN 2830 AND 2839");
        jdbcTemplate.update("DELETE FROM sys_user_role "
                + "WHERE user_id BETWEEN 28300 AND 28399 OR role_id BETWEEN 2830 AND 2839");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 28300 AND 28399");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 2830 AND 2839");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 2830 AND 2839");
    }

    private void insertDept(long id, long parentId, String ancestors, String name) {
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, 1, 0)", id, parentId, ancestors, name);
    }

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "t283角色" + id, "t283_role_" + id, dataScope);
    }

    private void insertUser(long id, String username, String name, Long deptId,
                            int status, long... roleIds) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, dept_id, status, is_deleted) "
                + "VALUES (?, ?, '', ?, ?, ?, 0)", id, username, name, deptId, status);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                    id, roleId);
        }
    }

    private void grantStatPage(long... roleIds) {
        long pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/business/attendance/stat' "
                        + "AND type = 1 AND is_deleted = 0", Long.class);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, pageId);
        }
    }

    private void insertMember(long userId) {
        jdbcTemplate.update("INSERT INTO attendance_group_member (group_id, user_id, "
                + "create_time, update_time) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                GROUP_ID, userId);
    }

    private void insertHoliday(LocalDate date, String name, int dayType) {
        jdbcTemplate.update("INSERT INTO attendance_holiday (holiday_date, name, day_type, "
                        + "create_time, update_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                date, name, dayType);
    }

    private void punchNormal(long userId, LocalDate date) {
        insertPunch(userId, date, 1, LocalTime.of(9, 0), 1, null);
        insertPunch(userId, date, 2, LocalTime.of(18, 0), 1, null);
    }

    private void insertPunch(long userId, LocalDate date, int slot, LocalTime time,
                             int source, Long operatorId) {
        jdbcTemplate.update("INSERT INTO attendance_punch (user_id, punch_date, slot, punch_time, "
                        + "source, operator_user_id, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, date, slot, date.atTime(time), source, operatorId);
    }

    private void insertLeave(long userId, LocalDate begin, LocalDate end, String typeName) {
        Long typeId = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_leave_type WHERE name = ? AND is_deleted = 0",
                Long.class, typeName);
        int days = 0;
        for (LocalDate date = begin; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() <= 5
                    && !date.equals(LocalDate.of(2026, 2, 12))) {
                days++;
            }
        }
        jdbcTemplate.update("INSERT INTO attendance_leave (applicant_id, leave_type_id, start_date, "
                        + "end_date, days, hours, reason, status, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 't283', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, typeId, begin, end, days, BigDecimal.valueOf(days * 8L));
    }

    private void insertOvertime(long userId, LocalDate date, LocalTime start, LocalTime end,
                                BigDecimal hours, int status) {
        jdbcTemplate.update("INSERT INTO attendance_overtime (applicant_id, overtime_date, start_time, "
                        + "end_time, hours, reason, status, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 't283', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, date, start, end, hours, status);
    }
}
