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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #280 的假期类型、发放与余额账本合同（ADR 0073）。
 *
 * <p>覆盖：类型 CRUD（引用历史只停用不删、{@code overtime_credit} 全局唯一且更新时
 * 排除自身、按钮点与操作日志）；发放端点一次多人每人一行、负数调整冲抵、
 * append-only（无更新/删除入口，旧行数值不变）；发放流水五档数据范围与
 * 类型/日期筛选 join 姓名/类型/操作人；可用余额口径
 * = Σ发放 + Σ入账 − Σ扣减 − Σ在途（在途只认审批中请假单，审批通过不重复算）；
 * 停用类型的历史流水照读；页面授予与按钮点分别拒绝。
 *
 * <p>夹具统一 t280 前缀。流水 create_time 用相对日期构造，避免日期筛选在任意
 * 运行日翻红；H2 真迁移提供 V139 的五项预置（调休是全局唯一加班入账目标）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_leave;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class AttendanceLeaveContractTest {

    private static final long TYPE_BALANCE_ID = 2801L;
    private static final long TYPE_NO_BALANCE_ID = 2802L;
    private static final long TYPE_DISABLED_ID = 2803L;
    private static final long TYPE_UNREFERENCED_ID = 2804L;

    private static final long ALL_ROLE = 2801L;
    private static final long CUSTOM_ROLE = 2802L;
    private static final long DEPT_ROLE = 2803L;
    private static final long BELOW_ROLE = 2804L;
    private static final long SELF_ROLE = 2805L;
    private static final long PAGE_ONLY_ROLE = 2806L;
    private static final long ADD_ONLY_ROLE = 2807L;
    private static final long NO_GRANT_ROLE = 2809L;

    private static final long ALL_ACTOR = 28001L;
    private static final long CUSTOM_ACTOR = 28002L;
    private static final long DEPT_ACTOR = 28003L;
    private static final long BELOW_ACTOR = 28004L;
    private static final long SELF_ACTOR = 28005L;
    private static final long NO_GRANT_ACTOR = 28006L;
    private static final long PAGE_ONLY_ACTOR = 28007L;
    private static final long ADD_ONLY_ACTOR = 28008L;
    private static final long ROOT_TARGET = 28011L;
    private static final long CHILD_TARGET = 28012L;
    private static final long GRAND_TARGET = 28013L;
    private static final long NODEPT_ACTOR = 28014L;
    private static final long OPERATOR_ID = 28019L;

    private static final LocalDate OLD_DAY = LocalDate.now().minusDays(5);
    private static final LocalDate RECENT_DAY = LocalDate.now().minusDays(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @BeforeEach
    void seedFixtures() {
        resetFixtures();

        insertDept(2800, 0, ",0,2800,", "t280本部");
        insertDept(2801, 2800, ",0,2800,2801,", "t280子部");
        insertDept(2802, 2801, ",0,2800,2801,2802,", "t280孙部");

        insertRole(ALL_ROLE, 1);
        insertRole(CUSTOM_ROLE, 2);
        insertRole(DEPT_ROLE, 3);
        insertRole(BELOW_ROLE, 4);
        insertRole(SELF_ROLE, 5);
        insertRole(PAGE_ONLY_ROLE, 3);
        insertRole(ADD_ONLY_ROLE, 3);
        insertRole(NO_GRANT_ROLE, 3);
        jdbcTemplate.update("INSERT INTO sys_role_dept (role_id, dept_id, is_deleted) "
                + "VALUES (2802, 2802, 0)");
        grantLeavePage(ALL_ROLE, CUSTOM_ROLE, DEPT_ROLE, BELOW_ROLE, SELF_ROLE,
                PAGE_ONLY_ROLE, ADD_ONLY_ROLE);
        grantLeaveButtons(ALL_ROLE, CUSTOM_ROLE, DEPT_ROLE, BELOW_ROLE, SELF_ROLE);
        grantButton(ADD_ONLY_ROLE, "attendance:type:add");

        insertUser(ALL_ACTOR, "t280-all", "范围全部", 2800L, ALL_ROLE);
        insertUser(CUSTOM_ACTOR, "t280-custom", "范围自定", 2800L, CUSTOM_ROLE);
        insertUser(DEPT_ACTOR, "t280-dept", "范围本部门", 2801L, DEPT_ROLE);
        insertUser(BELOW_ACTOR, "t280-below", "范围本部门及以下", 2801L, BELOW_ROLE);
        insertUser(SELF_ACTOR, "t280-self", "范围本人", 2802L, SELF_ROLE);
        insertUser(NO_GRANT_ACTOR, "t280-nogrant", "无授予", 2800L, NO_GRANT_ROLE);
        insertUser(PAGE_ONLY_ACTOR, "t280-pageonly", "仅页面", 2800L, PAGE_ONLY_ROLE);
        insertUser(ADD_ONLY_ACTOR, "t280-addonly", "仅新增", 2800L, ADD_ONLY_ROLE);
        insertUser(ROOT_TARGET, "t280-root", "目标本部", 2800L);
        insertUser(CHILD_TARGET, "t280-child", "目标子部", 2801L);
        insertUser(GRAND_TARGET, "t280-grand", "目标孙部", 2802L);
        insertUser(NODEPT_ACTOR, "t280-nodept", "无部门", null, DEPT_ROLE);
        insertUser(OPERATOR_ID, "t280-operator", "发放操作员", 2800L);

        insertLeaveType(TYPE_BALANCE_ID, "t280年假", 1, 0, 1, 10);
        insertLeaveType(TYPE_NO_BALANCE_ID, "t280事假", 0, 0, 1, 20);
        insertLeaveType(TYPE_DISABLED_ID, "t280停用假", 1, 0, 0, 30);
        insertLeaveType(TYPE_UNREFERENCED_ID, "t280备用假", 1, 0, 1, 40);

        // 余额夹具：发放 8 与负调整 -2、扣减 -1.5、加班入账 3；在途请假 2、已通过 4（不计）。
        insertLedger(28001, ROOT_TARGET, TYPE_BALANCE_ID, 1, "8.00", null, null, OPERATOR_ID,
                "初始发放", OLD_DAY);
        insertLedger(28002, ROOT_TARGET, TYPE_BALANCE_ID, 1, "-2.00", null, null, OPERATOR_ID,
                "负调整", OLD_DAY);
        insertLedger(28003, ROOT_TARGET, TYPE_BALANCE_ID, 2, "-1.50", "attendance.leave", 29001L,
                null, "审批扣减", OLD_DAY);
        insertLedger(28004, ROOT_TARGET, TYPE_BALANCE_ID, 3, "3.00", "attendance.overtime",
                29002L, null, "加班入账", OLD_DAY);

        // 其余部门各一条流水；子部另有一条近期流水与一条停用类型的流水。
        insertLedger(28005, CHILD_TARGET, TYPE_BALANCE_ID, 1, "4.00", null, null, ALL_ACTOR,
                "子部发放", OLD_DAY);
        insertLedger(28006, CHILD_TARGET, TYPE_BALANCE_ID, 1, "1.00", null, null, ALL_ACTOR,
                "子部补发", RECENT_DAY);
        insertLedger(28007, CHILD_TARGET, TYPE_DISABLED_ID, 1, "1.00", null, null, ALL_ACTOR,
                "停用类型发放", OLD_DAY);
        insertLedger(28008, GRAND_TARGET, TYPE_BALANCE_ID, 1, "6.00", null, null, ALL_ACTOR,
                "孙部发放", OLD_DAY);
        insertLedger(28009, SELF_ACTOR, TYPE_BALANCE_ID, 1, "5.00", null, null, ALL_ACTOR,
                "本人发放", OLD_DAY);

        insertLeave(29001, ROOT_TARGET, TYPE_BALANCE_ID, 1, "2.00");
        insertLeave(29002, ROOT_TARGET, TYPE_BALANCE_ID, 2, "4.00");
    }

    @AfterEach
    void cleanFixtures() {
        resetFixtures();
    }

    private void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_leave_ledger WHERE id BETWEEN 28000 AND 28099 "
                + "OR user_id BETWEEN 28000 AND 28099");
        jdbcTemplate.update("DELETE FROM attendance_leave WHERE id BETWEEN 29000 AND 29099");
        jdbcTemplate.update("DELETE FROM attendance_leave_type "
                + "WHERE id BETWEEN 2800 AND 2809 OR name LIKE 't280%'");
        jdbcTemplate.update("DELETE FROM sys_oper_log WHERE title IN "
                + "('考勤·新增假期类型','考勤·修改假期类型','考勤·删除假期类型','考勤·假期发放')");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 2801 AND 2809");
        jdbcTemplate.update("DELETE FROM sys_role_dept WHERE role_id BETWEEN 2801 AND 2809");
        jdbcTemplate.update("DELETE FROM sys_user_role "
                + "WHERE user_id BETWEEN 28000 AND 28099 OR role_id BETWEEN 2801 AND 2809");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 28000 AND 28099");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 2801 AND 2809");
        jdbcTemplate.update("DELETE FROM sys_dept WHERE id BETWEEN 2800 AND 2809");
    }

    @Test
    void leaveTypeCrudKeepsReferencedTypesAndEnforcesOvertimeCreditUniqueness() throws Exception {
        mockMvc.perform(get("/admin/attendance/leave-type/1/50").header("token", tokenOf(ALL_ACTOR))
                        .param("name", "t280"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[*].name", hasItems(
                        "t280年假", "t280事假", "t280停用假", "t280备用假")));

        mockMvc.perform(post("/admin/attendance/leave-type/save").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t280新增假\",\"balanceRequired\":1,"
                                + "\"overtimeCredit\":0,\"status\":1,\"sortNo\":50,\"remark\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE name = 't280新增假' AND balance_required = 1 AND status = 1 AND is_deleted = 0"));

        List<Map<String, Object>> addLogs = jdbcTemplate.queryForList(
                "SELECT * FROM sys_oper_log WHERE title = '考勤·新增假期类型'");
        assertEquals(1, addLogs.size());
        assertEquals(1, ((Number) addLogs.get(0).get("business_type")).intValue());
        assertEquals(ALL_ACTOR, ((Number) addLogs.get(0).get("oper_id")).longValue());
        assertEquals("/admin/attendance/leave-type/save", addLogs.get(0).get("oper_url"));

        // 名称为空走 Bean Validation 的命名拒绝。
        mockMvc.perform(post("/admin/attendance/leave-type/save").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"balanceRequired\":0,\"overtimeCredit\":0,"
                                + "\"status\":1,\"sortNo\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型名称不能为空"));

        // 停用：status 置 0，历史流水照读（上面种子给 t280年假 配了流水）。
        mockMvc.perform(put("/admin/attendance/leave-type/update").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2801,\"name\":\"t280年假\",\"balanceRequired\":1,"
                                + "\"overtimeCredit\":0,\"status\":0,\"sortNo\":10}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE id = 2801 AND status = 0 AND is_deleted = 0"));
        mockMvc.perform(get("/admin/attendance/ledger/1/50").header("token", tokenOf(ALL_ACTOR))
                        .param("leaveTypeId", String.valueOf(TYPE_BALANCE_ID))
                        .param("beginDate", OLD_DAY.toString())
                        .param("endDate", OLD_DAY.toString()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(7))
                .andExpect(jsonPath("$.data.records[*].leaveTypeName", hasItem("t280年假")));

        // overtime_credit 全局唯一：预置「调休」占用目标时任何改动都 201；自身更新排除自身。
        mockMvc.perform(put("/admin/attendance/leave-type/update").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2801,\"name\":\"t280年假\",\"balanceRequired\":1,"
                                + "\"overtimeCredit\":1,\"status\":1,\"sortNo\":10}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("加班入账目标已存在"));

        long overtimeTypeId = leaveTypeId("调休");
        mockMvc.perform(put("/admin/attendance/leave-type/update").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + overtimeTypeId + ",\"name\":\"调休\","
                                + "\"balanceRequired\":1,\"overtimeCredit\":1,\"status\":1,\"sortNo\":4}"))
                .andExpect(jsonPath("$.code").value(200));

        // 让出目标后可转移；再次更新自己仍是合法。
        mockMvc.perform(put("/admin/attendance/leave-type/update").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + overtimeTypeId + ",\"name\":\"调休\","
                                + "\"balanceRequired\":1,\"overtimeCredit\":0,\"status\":1,\"sortNo\":4}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/admin/attendance/leave-type/update").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2801,\"name\":\"t280年假\",\"balanceRequired\":1,"
                                + "\"overtimeCredit\":1,\"status\":1,\"sortNo\":10}"))
                .andExpect(jsonPath("$.code").value(200));

        // 引用历史不可删：有流水的类型 201 提示停用；无引用的类型可删（逻辑删）。
        mockMvc.perform(delete("/admin/attendance/leave-type/remove/2801")
                        .header("token", tokenOf(ALL_ACTOR)))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型已被引用，只能停用"));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE id = 2801 AND is_deleted = 0"));

        mockMvc.perform(delete("/admin/attendance/leave-type/remove/2804")
                        .header("token", tokenOf(ALL_ACTOR)))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE id = 2804 AND is_deleted = 1"));

        mockMvc.perform(put("/admin/attendance/leave-type/update").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2899,\"name\":\"t280不存在\",\"balanceRequired\":0,"
                                + "\"overtimeCredit\":0,\"status\":1,\"sortNo\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型不存在"));
        mockMvc.perform(delete("/admin/attendance/leave-type/remove/2899")
                        .header("token", tokenOf(ALL_ACTOR)))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型不存在"));

        // 成功的修改 4 次；业务失败（目标被占用、类型不存在）另记失败行。
        assertEquals(4, count("SELECT COUNT(*) FROM sys_oper_log "
                + "WHERE title = '考勤·修改假期类型' AND status = 0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_oper_log "
                + "WHERE title = '考勤·删除假期类型' AND status = 0"));
    }

    @Test
    void ledgerGrantWritesOneRowPerUserWithOperatorAndNeverUpdatesRows() throws Exception {
        mockMvc.perform(post("/admin/attendance/ledger/grant").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveTypeId\":2801,\"userIds\":[28011,28012],"
                                + "\"changeHours\":12.5,\"remark\":\"t280 多人发放\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(2, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE leave_type_id = 2801 AND change_hours = 12.50 AND entry_type = 1 "
                + "AND operator_user_id = 28001 AND remark = 't280 多人发放' AND is_deleted = 0"));

        // 负数调整行冲抵，仍是一人一行；原发放行数值与操作人不变。
        mockMvc.perform(post("/admin/attendance/ledger/grant").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveTypeId\":2801,\"userIds\":[28011],"
                                + "\"changeHours\":-2.25,\"remark\":\"t280 错发冲抵\"}"))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE user_id = 28011 AND change_hours = -2.25 AND entry_type = 1 "
                + "AND operator_user_id = 28001 AND is_deleted = 0"));
        assertEquals(2, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE leave_type_id = 2801 AND change_hours = 12.50 AND operator_user_id = 28001"));

        // 流水列表按日期（今天）读出本次三行，join 姓名/类型/操作人。
        mockMvc.perform(get("/admin/attendance/ledger/1/50").header("token", tokenOf(ALL_ACTOR))
                        .param("leaveTypeId", String.valueOf(TYPE_BALANCE_ID))
                        .param("beginDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[*].name", hasItems("目标本部", "目标子部")))
                .andExpect(jsonPath("$.data.records[*].leaveTypeName", hasItem("t280年假")))
                .andExpect(jsonPath("$.data.records[*].operatorName", hasItem("范围全部")))
                .andExpect(jsonPath("$.data.records[0].changeHours").value(-2.25))
                .andExpect(jsonPath("$.data.records[0].entryType").value(1));

        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                "SELECT * FROM sys_oper_log WHERE title = '考勤·假期发放' AND status = 0");
        assertEquals(2, logs.size());
        Map<String, Object> log = logs.get(0);
        assertEquals(1, ((Number) log.get("business_type")).intValue());
        assertEquals(ALL_ACTOR, ((Number) log.get("oper_id")).longValue());
        assertEquals("/admin/attendance/ledger/grant", log.get("oper_url"));

        // 零调整是空操作，命名拒绝；空成员同样拒绝。
        mockMvc.perform(post("/admin/attendance/ledger/grant").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveTypeId\":2801,\"userIds\":[28011],\"changeHours\":0}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("调整小时数不能为 0"));
        mockMvc.perform(post("/admin/attendance/ledger/grant").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveTypeId\":2801,\"userIds\":[],\"changeHours\":1}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("发放成员不能为空"));
        mockMvc.perform(post("/admin/attendance/ledger/grant").header("token", tokenOf(ALL_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[28011],\"changeHours\":1}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型不能为空"));
    }

    @Test
    void ledgerPageAppliesFiveTierScopeAndTypeDateFilters() throws Exception {
        // 1 全部：7 条类型 2801 的流水（本部 4 + 子部 2 + 孙部 1）+ 本人列无额外行。
        mockMvc.perform(ledgerOf(ALL_ACTOR, TYPE_BALANCE_ID))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(8))
                .andExpect(jsonPath("$.data.records[*].username", hasItems(
                        "t280-root", "t280-child", "t280-grand")))
                .andExpect(jsonPath("$.data.records[*].deptName", hasItems("t280本部", "t280子部")))
                .andExpect(jsonPath("$.data.records[*].leaveTypeName", hasItem("t280年假")));

        // 2 自定部门(孙部 2802)：孙部目标与本人档账号都在孙部，共两条。
        mockMvc.perform(ledgerOf(CUSTOM_ACTOR, TYPE_BALANCE_ID))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].username", hasItems("t280-grand", "t280-self")));

        // 3 本部门(子部 2801)：子部两条。
        mockMvc.perform(ledgerOf(DEPT_ACTOR, TYPE_BALANCE_ID))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].username", hasItem("t280-child")));

        // 4 本部门及以下(子部+孙部)：子部两条 + 孙部两条。
        mockMvc.perform(ledgerOf(BELOW_ACTOR, TYPE_BALANCE_ID))
                .andExpect(jsonPath("$.data.total").value(4));

        // 5 仅本人：本人一条。
        mockMvc.perform(ledgerOf(SELF_ACTOR, TYPE_BALANCE_ID))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("t280-self"));

        // 本部门档但本人无部门：空集零行，不是全部。
        mockMvc.perform(ledgerOf(NODEPT_ACTOR, TYPE_BALANCE_ID))
                .andExpect(jsonPath("$.data.total").value(0));

        // 类型筛选：停用类型的历史流水照读。
        mockMvc.perform(ledgerOf(ALL_ACTOR, TYPE_DISABLED_ID))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].leaveTypeName").value("t280停用假"));

        // 日期筛选按自然日闭区间：只命中近期那一条。
        mockMvc.perform(get("/admin/attendance/ledger/1/50").header("token", tokenOf(ALL_ACTOR))
                        .param("leaveTypeId", String.valueOf(TYPE_BALANCE_ID))
                        .param("beginDate", RECENT_DAY.toString())
                        .param("endDate", RECENT_DAY.toString()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("t280-child"))
                .andExpect(jsonPath("$.data.records[0].remark").value("子部补发"));

        // 页面授予：没有授予的用户 205。
        mockMvc.perform(get("/admin/attendance/ledger/1/50")
                        .header("token", tokenOf(NO_GRANT_ACTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
    }

    @Test
    void balancesSumLedgerMinusPendingForCurrentUserOnly() throws Exception {
        int ledgersBefore = count("SELECT COUNT(*) FROM attendance_leave_ledger");

        mockMvc.perform(get("/admin/attendance/my/balances").header("token", tokenOf(ROOT_TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].grantedHours").value(hasItem(6.0)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].creditedHours").value(hasItem(3.0)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].deductedHours").value(hasItem(1.5)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].pendingHours").value(hasItem(2.0)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].availableHours").value(hasItem(5.5)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].leaveTypeName", hasItem("t280年假")));

        // 只读本人：子部目标只看到自己的发放，不并入本部流水与在途。
        mockMvc.perform(get("/admin/attendance/my/balances").header("token", tokenOf(CHILD_TARGET)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].grantedHours").value(hasItem(5.0)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].pendingHours").value(hasItem(0)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2801)].availableHours").value(hasItem(5.0)));

        // 需要余额但无流水的类型也返回 0，供页面完整展示。
        mockMvc.perform(get("/admin/attendance/my/balances").header("token", tokenOf(GRAND_TARGET)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 2803)].availableHours").value(hasItem(0)));

        assertEquals(ledgersBefore, count("SELECT COUNT(*) FROM attendance_leave_ledger"));
    }

    @Test
    void grantRejectsOutOfScopeDisabledAndNonBalanceTypes() throws Exception {
        // 本部门档不能给孙部发放，可以给子部。
        mockMvc.perform(grantOf(DEPT_ACTOR, TYPE_BALANCE_ID, GRAND_TARGET, "1.0"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("成员不在数据范围内"));
        mockMvc.perform(grantOf(DEPT_ACTOR, TYPE_BALANCE_ID, CHILD_TARGET, "1.0"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(grantOf(ALL_ACTOR, TYPE_DISABLED_ID, ROOT_TARGET, "1.0"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型已停用"));
        mockMvc.perform(grantOf(ALL_ACTOR, TYPE_NO_BALANCE_ID, ROOT_TARGET, "1.0"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该假期类型不需要余额"));
        mockMvc.perform(grantOf(ALL_ACTOR, 2899L, ROOT_TARGET, "1.0"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("假期类型不存在"));

        // 页面授予与按钮点分开：只有页面能读，写 205；只有新增按钮能新增，改/删 205。
        mockMvc.perform(get("/admin/attendance/leave-type/1/50")
                        .header("token", tokenOf(PAGE_ONLY_ACTOR)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/attendance/leave-type/save")
                        .header("token", tokenOf(PAGE_ONLY_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t280越权\",\"balanceRequired\":0,"
                                + "\"overtimeCredit\":0,\"status\":1,\"sortNo\":0}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(post("/admin/attendance/ledger/grant")
                        .header("token", tokenOf(PAGE_ONLY_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveTypeId\":2801,\"userIds\":[28011],\"changeHours\":1}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        mockMvc.perform(post("/admin/attendance/leave-type/save")
                        .header("token", tokenOf(ADD_ONLY_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t280仅新增\",\"balanceRequired\":0,"
                                + "\"overtimeCredit\":0,\"status\":1,\"sortNo\":0}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/admin/attendance/leave-type/update")
                        .header("token", tokenOf(ADD_ONLY_ACTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2801,\"name\":\"t280年假\",\"balanceRequired\":1,"
                                + "\"overtimeCredit\":0,\"status\":1,\"sortNo\":10}"))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/attendance/leave-type/remove/2804")
                        .header("token", tokenOf(ADD_ONLY_ACTOR)))
                .andExpect(jsonPath("$.code").value(205));
    }

    private MockHttpServletRequestBuilder ledgerOf(long userId, long leaveTypeId) {
        return get("/admin/attendance/ledger/1/50").header("token", tokenOf(userId))
                .param("leaveTypeId", String.valueOf(leaveTypeId));
    }

    private MockHttpServletRequestBuilder grantOf(long userId, long leaveTypeId,
                                                  long targetUserId, String hours) {
        return post("/admin/attendance/ledger/grant").header("token", tokenOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"leaveTypeId\":" + leaveTypeId + ",\"userIds\":[" + targetUserId
                        + "],\"changeHours\":" + hours + "}");
    }

    private String tokenOf(long userId) {
        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM sys_user WHERE id = ?", String.class, userId);
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private long leaveTypeId(String name) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_leave_type WHERE name = ? AND is_deleted = 0",
                Long.class, name);
        assertNotNull(id);
        return id;
    }

    private void insertDept(long id, long parentId, String ancestors, String name) {
        jdbcTemplate.update("INSERT INTO sys_dept (id, parent_id, ancestors, name, status, is_deleted) "
                + "VALUES (?, ?, ?, ?, 1, 0)", id, parentId, ancestors, name);
    }

    private void insertRole(long id, int dataScope) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, ?, ?, ?, 0)", id, "t280角色" + id, "t280_role_" + id, dataScope);
    }

    private void insertUser(long id, String username, String name, Long deptId, long... roleIds) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, dept_id, status, is_deleted) "
                + "VALUES (?, ?, '', ?, ?, 1, 0)", id, username, name, deptId);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                    id, roleId);
        }
    }

    private void grantLeavePage(long... roleIds) {
        long pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/business/attendance/leave' "
                        + "AND type = 1 AND is_deleted = 0", Long.class);
        for (long roleId : roleIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, pageId);
        }
    }

    private void grantLeaveButtons(long... roleIds) {
        for (long roleId : roleIds) {
            for (String perms : List.of("attendance:type:add", "attendance:type:edit",
                    "attendance:type:remove", "attendance:ledger:grant")) {
                grantButton(roleId, perms);
            }
        }
    }

    private void grantButton(long roleId, String perms) {
        long buttonId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Long.class, perms);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, buttonId);
    }

    private void insertLeaveType(long id, String name, int balanceRequired, int overtimeCredit,
                                 int status, int sortNo) {
        jdbcTemplate.update("INSERT INTO attendance_leave_type (id, name, balance_required, "
                        + "overtime_credit, status, sort_no, remark, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, name, balanceRequired, overtimeCredit, status, sortNo);
    }

    private void insertLedger(long id, long userId, long leaveTypeId, int entryType,
                              String changeHours, String businessType, Long businessId,
                              Long operatorUserId, String remark, LocalDate createDate) {
        jdbcTemplate.update("INSERT INTO attendance_leave_ledger (id, user_id, leave_type_id, "
                        + "entry_type, change_hours, business_type, business_id, operator_user_id, "
                        + "remark, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                id, userId, leaveTypeId, entryType, new java.math.BigDecimal(changeHours),
                businessType, businessId, operatorUserId, remark,
                createDate.atTime(10, 0), createDate.atTime(10, 0));
    }

    private void insertLeave(long id, long applicantId, long leaveTypeId, int status,
                             String hours) {
        jdbcTemplate.update("INSERT INTO attendance_leave (id, applicant_id, leave_type_id, "
                        + "start_date, end_date, days, hours, reason, status, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, ?, '', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, applicantId, leaveTypeId, LocalDate.now(), LocalDate.now(),
                new java.math.BigDecimal(hours), status);
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
