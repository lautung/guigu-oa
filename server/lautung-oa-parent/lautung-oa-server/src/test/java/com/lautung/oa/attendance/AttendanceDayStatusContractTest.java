package com.lautung.oa.attendance;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #281 的日状态推导与补卡合同（ADR 0073）。
 *
 * <p>固定时钟 2026-03-09 10:00（周一）：「今天」之前的日子已过下班时刻，缺卡可判；
 * 今天未到 18:00 只显示「未打卡」；未来日期不落九枚举。稳定性来自可推进的
 * {@link MutableAttendanceClock}，不依赖真实时钟。
 *
 * <p>覆盖：九枚举逐日推导与优先级（请假 &gt; 缺卡 &gt; 迟到且早退 &gt; 迟到 &gt; 早退 &gt; 正常）、
 * 缺卡时刻边界（18:00 整）、补卡覆盖与重复补卡取最新、append-only + 操作日志、
 * 数据范围五档（读与写）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_day_status;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LoginAuthContractTest.InMemoryRedisConfig.class,
        AttendanceDayStatusContractTest.FixedAttendanceClockConfig.class})
class AttendanceDayStatusContractTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 3, 3);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 3, 4);
    private static final LocalDate THURSDAY = LocalDate.of(2026, 3, 5);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 3, 6);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 3, 7);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 3, 8);
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 9);
    private static final LocalDate MAKEUP_TUESDAY = LocalDate.of(2026, 3, 10);
    private static final LocalDate FUTURE_WEDNESDAY = LocalDate.of(2026, 3, 11);

    private static final long GROUP_ID = 2810L;
    private static final long SHIFT_ID = 2811L;
    private static final long OPERATOR_ID = 28115L;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedAttendanceClockConfig {

        @Bean
        @Primary
        MutableAttendanceClock attendanceTestClock() {
            return new MutableAttendanceClock(LocalDateTime.of(2026, 3, 9, 10, 0));
        }
    }

    /** 可推进时钟：同一个 bean 被服务持有，测试改时间即可跨过下班时刻。 */
    static final class MutableAttendanceClock implements AttendanceClock {

        private volatile LocalDateTime now;

        MutableAttendanceClock(LocalDateTime now) {
            this.now = now;
        }

        void set(LocalDateTime dateTime) {
            this.now = dateTime;
        }

        @Override
        public LocalDateTime now() {
            return now;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private MutableAttendanceClock clock;

    @BeforeEach
    void seedFixtures() {
        resetFixtures();
        clock.set(LocalDateTime.of(2026, 3, 9, 10, 0));

        insertDept(2810, 0, ",0,2810,", "t281本部");
        insertDept(2811, 2810, ",0,2810,2811,", "t281子部");
        insertDept(2812, 2811, ",0,2810,2811,2812,", "t281孙部");

        insertRole(2811, 1);
        insertRole(2812, 2);
        insertRole(2813, 3);
        insertRole(2814, 4);
        insertRole(2815, 5);
        insertRole(2819, 3);
        jdbcTemplate.update("INSERT INTO sys_role_dept (role_id, dept_id, is_deleted) VALUES (2812, 2812, 0)");
        grantRepairPage(2811, 2812, 2813, 2814, 2815);

        insertUser(28101, "t281-normal", "正常甲", 2810L, 2813);
        insertUser(28102, "t281-leave", "请假乙", 2810L, 2813);
        insertUser(28103, "t281-repair", "补卡丙", 2810L, 2813);
        insertUser(28104, "t281-boundary", "边界丁", 2810L, 2813);
        insertUser(28105, "t281-nogroup", "无组戊", 2810L, 2813);
        insertUser(OPERATOR_ID, "t281-operator", "操作员", 2810L, 2811);
        insertUser(28121, "t281-all", "范围全部", 2810L, 2811);
        insertUser(28122, "t281-custom", "范围自定", 2810L, 2812);
        insertUser(28123, "t281-dept", "范围本部门", 2811L, 2813);
        insertUser(28124, "t281-below", "范围本部门及以下", 2811L, 2814);
        insertUser(28125, "t281-self", "范围本人", 2810L, 2815);
        insertUser(28126, "t281-target-root", "目标本部", 2810L, 2815);
        insertUser(28127, "t281-target-child", "目标子部", 2811L, 2815);
        insertUser(28128, "t281-target-grand", "目标孙部", 2812L, 2815);
        insertUser(28129, "t281-nodept", "无部门", null, 2813);
        insertUser(28130, "t281-nogrant", "无补卡页", 2810L, 2819);

        jdbcTemplate.update("INSERT INTO attendance_shift (id, name, start_time, end_time, "
                        + "lunch_start, lunch_end, grace_minutes, create_time, update_time, is_deleted) "
                        + "VALUES (?, 't281标准班', ?, ?, ?, ?, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                SHIFT_ID, LocalTime.of(9, 0), LocalTime.of(18, 0),
                LocalTime.of(12, 0), LocalTime.of(13, 0));
        insertGroup(GROUP_ID, "t281考勤组", "1,2,3,4,5");
        for (long userId : List.of(28101L, 28102L, 28103L, 28104L, OPERATOR_ID,
                28121L, 28122L, 28123L, 28124L, 28125L, 28126L, 28127L, 28128L)) {
            insertMember(userId);
        }

        insertHoliday(SUNDAY, "t281休假日", 1);
        insertHoliday(MAKEUP_TUESDAY, "t281补班日", 2);

        // 正常：上班不晚于 09:10、下班不早于 17:50。
        insertPunch(28101L, MONDAY, 1, LocalTime.of(9, 0), 1, null);
        insertPunch(28101L, MONDAY, 2, LocalTime.of(18, 0), 1, null);
        // 迟到。
        insertPunch(28101L, TUESDAY, 1, LocalTime.of(9, 15), 1, null);
        insertPunch(28101L, TUESDAY, 2, LocalTime.of(18, 0), 1, null);
        // 早退。
        insertPunch(28101L, WEDNESDAY, 1, LocalTime.of(9, 0), 1, null);
        insertPunch(28101L, WEDNESDAY, 2, LocalTime.of(17, 40), 1, null);
        // 迟到且早退。
        insertPunch(28101L, THURSDAY, 1, LocalTime.of(9, 15), 1, null);
        insertPunch(28101L, THURSDAY, 2, LocalTime.of(17, 40), 1, null);
        // 缺卡：只有上班卡且已过下班时刻。
        insertPunch(28101L, FRIDAY, 1, LocalTime.of(9, 15), 1, null);
        // 休息日有打卡也照常显示休息日。
        insertPunch(28101L, SATURDAY, 1, LocalTime.of(9, 0), 1, null);
        insertPunch(28101L, SATURDAY, 2, LocalTime.of(18, 0), 1, null);

        // 请假优先级：迟到且早退、缺卡都被请假覆盖。
        insertPunch(28102L, WEDNESDAY, 1, LocalTime.of(9, 15), 1, null);
        insertPunch(28102L, WEDNESDAY, 2, LocalTime.of(17, 40), 1, null);
        insertLeave(28102L, WEDNESDAY, FRIDAY, 2);

        // 补卡覆盖：网页行 09:00/17:00（早退），两条上班补卡取最新 08:00。
        insertPunch(28103L, THURSDAY, 1, LocalTime.of(9, 0), 1, null);
        insertPunch(28103L, THURSDAY, 2, LocalTime.of(17, 0), 1, null);
        insertPunch(28103L, THURSDAY, 1, LocalTime.of(8, 30), 2, OPERATOR_ID);
        insertPunch(28103L, THURSDAY, 1, LocalTime.of(8, 0), 2, OPERATOR_ID);

        // 边界：今天只有上班卡。
        insertPunch(28104L, TODAY, 1, LocalTime.of(9, 0), 1, null);

        // 列表范围区分用：子部目标一条流水。
        insertPunch(28127L, THURSDAY, 1, LocalTime.of(9, 0), 1, null);
    }

    @AfterEach
    void cleanFixtures() {
        resetFixtures();
    }

    private void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_punch WHERE user_id BETWEEN 28100 AND 28199");
        jdbcTemplate.update("DELETE FROM attendance_leave WHERE applicant_id BETWEEN 28100 AND 28199");
        jdbcTemplate.update("DELETE FROM attendance_group_member WHERE user_id BETWEEN 28100 AND 28199");
        jdbcTemplate.update("DELETE FROM attendance_group WHERE id BETWEEN 2810 AND 2819");
        jdbcTemplate.update("DELETE FROM attendance_shift WHERE id BETWEEN 2810 AND 2819");
        jdbcTemplate.update("DELETE FROM attendance_holiday WHERE name LIKE 't281%'");
        jdbcTemplate.update("DELETE FROM sys_oper_log WHERE title LIKE '考勤·补卡%'");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 2810 AND 2819");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id BETWEEN 2810 AND 2819");
        jdbcTemplate.update("DELETE FROM sys_user_role "
                + "WHERE user_id BETWEEN 28100 AND 28199 OR role_id BETWEEN 2810 AND 2819");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 28100 AND 28199");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 2810 AND 2819");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 2810 AND 2819");
    }

    @Test
    void monthDerivesNineStatusesWithPriorityAndPendingPresentation() throws Exception {
        mockMvc.perform(monthOf(28101L, "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.month").value("2026-03"))
                .andExpect(jsonPath("$.data.days.length()").value(31))
                // 3/1 周日休息日；3/2 正常；3/3 迟到；3/4 早退；3/5 迟到且早退；3/6 缺卡。
                .andExpect(jsonPath("$.data.days[0].status").value(7))
                .andExpect(jsonPath("$.data.days[1].status").value(1))
                .andExpect(jsonPath("$.data.days[1].clockInTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.days[1].clockOutTime").value("18:00:00"))
                .andExpect(jsonPath("$.data.days[2].status").value(2))
                .andExpect(jsonPath("$.data.days[3].status").value(3))
                .andExpect(jsonPath("$.data.days[4].status").value(4))
                .andExpect(jsonPath("$.data.days[5].status").value(5))
                .andExpect(jsonPath("$.data.days[5].pending").value(false))
                // 3/7 周六休息日（有打卡也照常）；3/8 休假日。
                .andExpect(jsonPath("$.data.days[6].status").value(7))
                .andExpect(jsonPath("$.data.days[7].status").value(8))
                // 3/9 今天未到下班时刻且缺槽位：未打卡，不落九枚举。
                .andExpect(jsonPath("$.data.days[8].pending").value(true))
                .andExpect(jsonPath("$.data.days[8].status").value(nullValue()))
                // 3/10 补班日（未来且无打卡）；3/11 未来工作日不落状态也不叫未打卡。
                .andExpect(jsonPath("$.data.days[9].dayType").value(4))
                .andExpect(jsonPath("$.data.days[9].status").value(9))
                .andExpect(jsonPath("$.data.days[10].status").value(nullValue()))
                .andExpect(jsonPath("$.data.days[10].pending").value(false));

        // 请假(类型) 覆盖迟到且早退与缺卡。
        mockMvc.perform(monthOf(28102L, "2026-03"))
                .andExpect(jsonPath("$.data.days[3].status").value(6))
                .andExpect(jsonPath("$.data.days[3].leaveTypeName").value("年假"))
                .andExpect(jsonPath("$.data.days[4].status").value(6))
                .andExpect(jsonPath("$.data.days[5].status").value(6));

        // 补卡行取最新：上班 08:00 覆盖网页 09:00；下班仍取网页 17:00。
        mockMvc.perform(monthOf(28103L, "2026-03"))
                .andExpect(jsonPath("$.data.days[4].clockInTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.days[4].clockOutTime").value("17:00:00"))
                .andExpect(jsonPath("$.data.days[4].status").value(3));
    }

    @Test
    void missingCardOnlyAfterShiftEnd() throws Exception {
        mockMvc.perform(repairDay(OPERATOR_ID, 28104L, TODAY))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.pending").value(true))
                .andExpect(jsonPath("$.data.status").value(nullValue()));

        clock.set(LocalDateTime.of(2026, 3, 9, 17, 59, 59));
        mockMvc.perform(repairDay(OPERATOR_ID, 28104L, TODAY))
                .andExpect(jsonPath("$.data.pending").value(true))
                .andExpect(jsonPath("$.data.status").value(nullValue()));

        // 边界：到下班时刻整就算已过，缺槽位即缺卡。
        clock.set(LocalDateTime.of(2026, 3, 9, 18, 0, 0));
        mockMvc.perform(repairDay(OPERATOR_ID, 28104L, TODAY))
                .andExpect(jsonPath("$.data.pending").value(false))
                .andExpect(jsonPath("$.data.status").value(5));
    }

    @Test
    void repairAppendsRowsKeepsWebRowsAndWritesOperLog() throws Exception {
        mockMvc.perform(repairSave(OPERATOR_ID, 28104L, TODAY, "09:00:00", "18:05:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.date").value("2026-03-09"))
                .andExpect(jsonPath("$.data.clockInTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.clockOutTime").value("18:05:00"))
                .andExpect(jsonPath("$.data.status").value(1));

        assertEquals(2, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 28104 "
                + "AND punch_date = '2026-03-09' AND source = 2 AND is_deleted = 0"));
        assertEquals(2, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 28104 "
                + "AND punch_date = '2026-03-09' AND operator_user_id = " + OPERATOR_ID));
        // 原网页行保留：09:00 上班卡仍在，补卡不改不删。
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 28104 "
                + "AND punch_date = '2026-03-09' AND slot = 1 AND source = 1 AND punch_time = '2026-03-09 09:00:00'"));

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT * FROM sys_oper_log WHERE title = '考勤·补卡'");
        assertEquals(1, logs.size());
        Map<String, Object> log = logs.get(0);
        assertEquals(2, ((Number) log.get("business_type")).intValue());
        assertEquals(0, ((Number) log.get("status")).intValue());
        assertEquals(OPERATOR_ID, ((Number) log.get("oper_id")).longValue());
        assertEquals("/admin/attendance/repair/save", log.get("oper_url"));
    }

    @Test
    void repairOneSlotAndRepeatedRepairTakesLatest() throws Exception {
        mockMvc.perform(repairSave(OPERATOR_ID, 28103L, THURSDAY, null, "18:30:00"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.clockInTime").value("08:00:00"))
                .andExpect(jsonPath("$.data.clockOutTime").value("18:30:00"))
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(repairSave(OPERATOR_ID, 28103L, THURSDAY, "07:50:00", null))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.clockInTime").value("07:50:00"))
                .andExpect(jsonPath("$.data.clockOutTime").value("18:30:00"));

        // 只补一槽位时另一槽位沿用原值；每次补卡追加新行，网页行两条始终保留。
        assertEquals(4, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 28103 "
                + "AND punch_date = '" + THURSDAY + "' AND source = 2"));
        assertEquals(2, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 28103 "
                + "AND punch_date = '" + THURSDAY + "' AND source = 1"));
    }

    @Test
    void repairRejectsInvalidRequestsWithNamedMessages() throws Exception {
        mockMvc.perform(post("/admin/attendance/repair/save").header("token", tokenOf(OPERATOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":28101,\"repairDate\":\"2026-03-06\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("至少补一个槽位"));

        mockMvc.perform(repairSave(OPERATOR_ID, 28101L, FRIDAY, "18:00:00", "09:00:00"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("上班时间必须早于下班时间"));

        mockMvc.perform(repairSave(OPERATOR_ID, 28105L, FRIDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员未加入考勤组"));

        mockMvc.perform(repairSave(OPERATOR_ID, 28101L, null, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("补卡日期不能为空"));

        mockMvc.perform(post("/admin/attendance/repair/save").header("token", tokenOf(OPERATOR_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repairDate\":\"2026-03-06\",\"clockInTime\":\"09:00:00\"}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不能为空"));

        // 不存在的成员与范围外成员同一句，不泄露用户是否存在。
        mockMvc.perform(repairSave(OPERATOR_ID, 28999L, FRIDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_punch WHERE user_id = 28105"));
    }

    @Test
    void repairWriteEnforcesDataScopeFiveTiers() throws Exception {
        // 1 全部：可补孙部。
        mockMvc.perform(repairSave(28121L, 28128L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(200));
        // 2 自定部门(2812)：孙部可写，子部不可写。
        mockMvc.perform(repairSave(28122L, 28128L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(repairSave(28122L, 28127L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        // 3 本部门(2811)：子部可写，孙部不可写。
        mockMvc.perform(repairSave(28123L, 28127L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(repairSave(28123L, 28128L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        // 4 本部门及以下(2811 → 2811/2812)：孙部可写，本部不可写。
        mockMvc.perform(repairSave(28124L, 28128L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(repairSave(28124L, 28126L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        // 5 仅本人：可补自己，不可补同部门他人。
        mockMvc.perform(repairSave(28125L, 28125L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(repairSave(28125L, 28126L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        // 本部门档但本人无部门：空集得零行，不是全部。
        mockMvc.perform(repairSave(28129L, 28126L, FUTURE_WEDNESDAY, "09:00:00", null))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
    }

    @Test
    void repairDayAndUserOptionsApplyDataScope() throws Exception {
        mockMvc.perform(repairDay(28121L, 28128L, THURSDAY))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.date").value("2026-03-05"));

        mockMvc.perform(repairDay(28123L, 28128L, THURSDAY))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));

        mockMvc.perform(get("/admin/attendance/repair/users").header("token", tokenOf(28123L))
                        .param("name", "t281-target"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[*].username", hasItem("t281-target-child")))
                .andExpect(jsonPath("$.data[*].username", org.hamcrest.Matchers.not(
                        hasItem("t281-target-grand"))));

        mockMvc.perform(get("/admin/attendance/repair/users").header("token", tokenOf(28121L))
                        .param("name", "t281-target"))
                .andExpect(jsonPath("$.data[*].username", hasItem("t281-target-grand")))
                .andExpect(jsonPath("$.data[*].username", hasItem("t281-target-child")));
    }

    @Test
    void repairPageListsScopedRowsAndRequiresPageGrant() throws Exception {
        mockMvc.perform(get("/admin/attendance/repair/1/50").header("token", tokenOf(28121L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(19))
                .andExpect(jsonPath("$.data.records[*].username", hasItem("t281-repair")));

        // 本部门档(2811)只看到子部目标的 1 条流水，看不到本部/孙部。
        mockMvc.perform(get("/admin/attendance/repair/1/50").header("token", tokenOf(28123L)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("t281-target-child"));

        mockMvc.perform(get("/admin/attendance/repair/1/50").header("token", tokenOf(28130L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        mockMvc.perform(get("/admin/attendance/repair/day").header("token", tokenOf(28130L))
                        .param("userId", "28104").param("date", TODAY.toString()))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
    }

    @Test
    void monthRejectsInvalidMonthAndDefaultsToCurrent() throws Exception {
        mockMvc.perform(get("/admin/attendance/my/records").header("token", tokenOf(28101L))
                        .param("month", "2026-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("月份格式不正确"));

        mockMvc.perform(get("/admin/attendance/my/records").header("token", tokenOf(28101L)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.month").value("2026-03"));
    }

    private MockHttpServletRequestBuilder monthOf(long userId, String month) {
        return get("/admin/attendance/my/records").header("token", tokenOf(userId))
                .param("month", month);
    }

    private MockHttpServletRequestBuilder repairDay(long userId, long targetUserId, LocalDate date) {
        return get("/admin/attendance/repair/day").header("token", tokenOf(userId))
                .param("userId", String.valueOf(targetUserId))
                .param("date", date.toString());
    }

    private MockHttpServletRequestBuilder repairSave(long userId, long targetUserId, LocalDate date,
                                                     String clockIn, String clockOut) {
        StringBuilder body = new StringBuilder("{");
        body.append("\"userId\":").append(targetUserId);
        if (date != null) {
            body.append(",\"repairDate\":\"").append(date).append('"');
        }
        if (clockIn != null) {
            body.append(",\"clockInTime\":\"").append(clockIn).append('"');
        }
        if (clockOut != null) {
            body.append(",\"clockOutTime\":\"").append(clockOut).append('"');
        }
        body.append('}');
        return post("/admin/attendance/repair/save").header("token", tokenOf(userId))
                .contentType(MediaType.APPLICATION_JSON).content(body.toString());
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
                + "VALUES (?, ?, ?, ?, 0)", id, "t281角色" + id, "t281_role_" + id, dataScope);
    }

    private void insertUser(long id, String username, String name, Long deptId, long... roleIds) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, dept_id, status, is_deleted) "
                + "VALUES (?, ?, '', ?, ?, 1, 0)", id, username, name, deptId);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                    id, roleId);
        }
    }

    private void grantRepairPage(long... roleIds) {
        long pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/business/attendance/repair' "
                        + "AND type = 1 AND is_deleted = 0", Long.class);
        long buttonId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = 'attendance:record:repair' "
                        + "AND type = 2 AND is_deleted = 0", Long.class);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, pageId);
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, buttonId);
        }
    }

    private void insertGroup(long id, String name, String workdays) {
        jdbcTemplate.update("INSERT INTO attendance_group (id, name, shift_id, workdays, remark, "
                        + "create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, name, SHIFT_ID, workdays);
    }

    private void insertMember(long userId) {
        jdbcTemplate.update("INSERT INTO attendance_group_member (group_id, user_id, "
                + "create_time, update_time) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                GROUP_ID, userId);
    }

    private void insertPunch(long userId, LocalDate date, int slot, LocalTime time,
                             int source, Long operatorId) {
        jdbcTemplate.update("INSERT INTO attendance_punch (user_id, punch_date, slot, punch_time, "
                        + "source, operator_user_id, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, date, slot, date.atTime(time), source, operatorId);
    }

    private void insertHoliday(LocalDate date, String name, int dayType) {
        jdbcTemplate.update("INSERT INTO attendance_holiday (holiday_date, name, day_type, "
                        + "create_time, update_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                date, name, dayType);
    }

    private void insertLeave(long userId, LocalDate begin, LocalDate end, int status) {
        Long typeId = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_leave_type WHERE name = '年假' AND is_deleted = 0", Long.class);
        jdbcTemplate.update("INSERT INTO attendance_leave (applicant_id, leave_type_id, start_date, "
                        + "end_date, days, hours, reason, status, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, 3, 24, 't281', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, typeId, begin, end, status);
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
