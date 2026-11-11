package com.lautung.oa.attendance;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.attendance.AttendanceApplyStatus;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import com.lautung.oa.attendance.approval.AttendanceLeaveApprovalCallback;
import com.lautung.oa.process.service.Approval;
import com.lautung.oa.process.service.ApprovalTemplate;
import com.lautung.oa.vo.process.ApprovalVo;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #282 的请假/加班单合同（ADR 0073/0067）。
 *
 * <p>覆盖：服务端提交时定稿天数/小时（休假日剔除、补班日照算）；全休假日、区间重叠、
 * 余额不足（含在途）与事由必填的 201 命名拒绝；状态 CAS 与同幂等键重放同一条审批、
 * 不同键的二次提交拒绝；模板映射缺失/未发布/发布证据失效 → 201 且单据留草稿；
 * 办事五种审批列表照常包含业务审批；加班单时长服务端算、结束晚于开始、休息日允许、
 * 未配置调休入账目标 201；拒绝后改单重提（单据号不变）。
 *
 * <p>夹具统一 t282 前缀；请假区间用固定周（2026-12-07 周一至 2026-12-13 周日），
 * 周中休假日 12-09、周六补班 12-12，避免依赖运行日。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_application;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class AttendanceApplicationContractTest {

    private static final long APPLICANT = 28201L;
    private static final long OTHER_APPLICANT = 28202L;
    private static final long POOR_APPLICANT = 28203L;
    private static final long APPROVER = 28210L;

    private static final long SHIFT_ID = 2821L;
    private static final long GROUP_ID = 2821L;
    private static final long TYPE_BALANCE_ID = 28201L;
    private static final long TYPE_NO_BALANCE_ID = 28202L;

    private static final LocalDate WEEK_START = LocalDate.of(2026, 12, 7);
    private static final LocalDate WEEK_END = LocalDate.of(2026, 12, 13);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private AttendanceLeaveApprovalCallback leaveCallback;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private Approval approval;

    @BeforeEach
    void seedFixtures() throws Exception {
        resetFixtures();
        insertUser(APPLICANT, "t282-applicant", "申请人");
        insertUser(OTHER_APPLICANT, "t282-other", "他人");
        insertUser(POOR_APPLICANT, "t282-poor", "余额不足");
        insertUser(APPROVER, "t282-approver", "审批人");

        jdbcTemplate.update("INSERT INTO attendance_shift (id, name, start_time, end_time, "
                        + "lunch_start, lunch_end, grace_minutes, create_time, update_time, is_deleted) "
                        + "VALUES (?, 't282标准班', '09:00:00', '18:00:00', '12:00:00', '13:00:00', "
                        + "0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", SHIFT_ID);
        jdbcTemplate.update("INSERT INTO attendance_group (id, name, shift_id, workdays, remark, "
                        + "create_time, update_time, is_deleted) "
                        + "VALUES (?, 't282考勤组', ?, '1,2,3,4,5', '', CURRENT_TIMESTAMP, "
                        + "CURRENT_TIMESTAMP, 0)", GROUP_ID, SHIFT_ID);
        insertMember(APPLICANT);
        insertMember(OTHER_APPLICANT);
        insertMember(POOR_APPLICANT);

        insertLeaveType(TYPE_BALANCE_ID, "t282年假", 1);
        insertLeaveType(TYPE_NO_BALANCE_ID, "t282事假", 0);
        insertHoliday(LocalDate.of(2026, 12, 9), "t282休假日", 1);
        insertHoliday(LocalDate.of(2026, 12, 12), "t282补班日", 2);

        // 正常申请人余额充足；余额不足申请人：发放 8、在途 6 → 可用 2。
        insertLedger(APPLICANT, TYPE_BALANCE_ID, 1, "100.00", null, null);
        insertLedger(POOR_APPLICANT, TYPE_BALANCE_ID, 1, "8.00", null, null);
        insertLeave(POOR_APPLICANT, TYPE_BALANCE_ID, LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 3), 6, "6.00",
                AttendanceApplyStatus.SUBMITTED.code());

        publishTemplate();
        seedMapping("attendance.leave");
        seedMapping("attendance.overtime");
    }

    @AfterEach
    void cleanFixtures() {
        resetFixtures();
    }

    @Test
    void leaveSubmitSnapshotsWorkdaysAndSupportsSameKeyReplay() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_BALANCE_ID, WEEK_START, WEEK_END);
        assertLeaveStatus(id, AttendanceApplyStatus.DRAFT.code());
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave WHERE id = " + id
                + " AND days = 0 AND hours = 0"));

        submitLeave(id, APPLICANT, "t282-leave-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.SUBMITTED.code()))
                // 周一/二/四/五 + 周六补班 = 5 天，周三休假日剔除；5 × 8 小时。
                .andExpect(jsonPath("$.data.days").value(5))
                .andExpect(jsonPath("$.data.hours").value(40.0))
                .andExpect(jsonPath("$.data.approval.status").value(1))
                .andExpect(jsonPath("$.data.approval.processId").isNumber());

        Map<String, Object> process = jdbcTemplate.queryForMap(
                "SELECT id, business_type, business_id, idempotency_key, status FROM oa_process "
                        + "WHERE business_type = 'attendance.leave' AND business_id = ?", id);
        assertEquals("attendance.leave", process.get("business_type"));
        assertEquals(id, ((Number) process.get("business_id")).longValue());
        assertEquals("t282-leave-1", process.get("idempotency_key"));
        assertEquals(1, ((Number) process.get("status")).intValue());
        long processId = ((Number) process.get("id")).longValue();

        // 同幂等键重放：返回同一条审批，不新增 process 行。
        submitLeave(id, APPLICANT, "t282-leave-1")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.approval.processId").value(processId));
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id));

        // 不同键的二次提交由单据状态 CAS 拒绝。
        submitLeave(id, APPLICANT, "t282-leave-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("单据已在审批中"));
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id));

        mockMvc.perform(get("/admin/attendance/my/leave/" + id).header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.days").value(5))
                .andExpect(jsonPath("$.data.approval.processId").value(processId))
                .andExpect(jsonPath("$.data.approval.currentApprover").value("审批人"));

        mockMvc.perform(get("/admin/attendance/my/leave/1/10").header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(id))
                .andExpect(jsonPath("$.data.records[0].leaveTypeName").value("t282年假"));

        // 提交后在途 40 小时计入预计：可用 = 发放 100 − 在途 40 = 60。
        mockMvc.perform(get("/admin/attendance/my/leave/types").header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.id == 28201)].availableHours", contains(60.0)))
                .andExpect(jsonPath("$.data[?(@.id == 28201)].balanceRequired", contains(1)))
                .andExpect(jsonPath("$.data[?(@.id == 28202)].balanceRequired", contains(0)));
    }

    @Test
    void leaveSubmitRejectsAllHolidayRangeOverlapAndInsufficientBalance() throws Exception {
        // 区间全为休息日/休假日：2026-12-13 周日。
        long holidayOnly = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_END, WEEK_END);
        submitLeave(holidayOnly, APPLICANT, "t282-holiday")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请假区间没有工作日"));
        assertLeaveStatus(holidayOnly, AttendanceApplyStatus.DRAFT.code());

        // 与本人已通过/在途请假重叠（在途 12-10 至 12-11）。
        insertLeave(APPLICANT, TYPE_NO_BALANCE_ID, LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 11), 2, "16.00", AttendanceApplyStatus.SUBMITTED.code());
        long overlap = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID,
                LocalDate.of(2026, 12, 9), LocalDate.of(2026, 12, 10));
        submitLeave(overlap, APPLICANT, "t282-overlap")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("与本人已通过或在途的请假日期重叠"));
        assertLeaveStatus(overlap, AttendanceApplyStatus.DRAFT.code());

        // 余额不足：可用 2（发放 8 − 在途 6），一天 8 小时。
        long poor = createLeaveDraft(POOR_APPLICANT, TYPE_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(poor, POOR_APPLICANT, "t282-poor")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("可用余额不足"));
        assertLeaveStatus(poor, AttendanceApplyStatus.DRAFT.code());
        assertEquals(0, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + poor));

        // 补发 10 小时后再提交，在途预计随之变化。
        insertLedger(POOR_APPLICANT, TYPE_BALANCE_ID, 1, "10.00", null, null);
        submitLeave(poor, POOR_APPLICANT, "t282-poor-retry")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.SUBMITTED.code()));
        mockMvc.perform(get("/admin/attendance/my/balances").header("token", tokenOf(POOR_APPLICANT)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 28201)].availableHours", contains(4.0)))
                .andExpect(jsonPath("$.data[?(@.leaveTypeId == 28201)].pendingHours", contains(14.0)));
    }

    @Test
    void leaveSubmitRequiresReasonAndKeepsDocumentDraftOnTemplateFailures() throws Exception {
        long noReason = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        jdbcTemplate.update("UPDATE attendance_leave SET reason = NULL WHERE id = ?", noReason);
        submitLeave(noReason, APPLICANT, "t282-noreason")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请假事由不能为空"));
        assertLeaveStatus(noReason, AttendanceApplyStatus.DRAFT.code());

        // 映射缺失
        jdbcTemplate.update("DELETE FROM oa_business_template WHERE business_type = 'attendance.leave'");
        long unmapped = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(unmapped, APPLICANT, "t282-unmapped")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该业务类型未配置审批模板，不能发起"));
        assertLeaveStatus(unmapped, AttendanceApplyStatus.DRAFT.code());
        assertEquals(0, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + unmapped));

        // 映射指向未发布模板
        jdbcTemplate.update("DELETE FROM oa_business_template WHERE business_type = 'attendance.leave'");
        jdbcTemplate.update("INSERT INTO oa_business_template (business_type, process_template_id) "
                + "VALUES ('attendance.leave', 2)");
        long unpublished = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(unpublished, APPLICANT, "t282-unpublished")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("未发布的审批模板不能发起"));
        assertLeaveStatus(unpublished, AttendanceApplyStatus.DRAFT.code());

        // 发布证据失效（sha256 与存档内容不符）
        seedMapping("attendance.leave");
        jdbcTemplate.update("UPDATE oa_process_template_publication SET artifact_sha256 = ? "
                + "WHERE process_template_id = 1", "0".repeat(64));
        long broken = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(broken, APPLICANT, "t282-broken")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("暂不可发起，需核实原审批内容"));
        assertLeaveStatus(broken, AttendanceApplyStatus.DRAFT.code());
        assertEquals(0, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + broken));
    }

    @Test
    void overtimeSubmitComputesHoursAllowsRestDayAndRequiresCreditTarget() throws Exception {
        long overtime = createOvertimeDraft(POOR_APPLICANT, WEEK_END, "18:00:00", "20:30:00");
        submitOvertime(overtime, POOR_APPLICANT, "t282-ot-1")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.SUBMITTED.code()))
                .andExpect(jsonPath("$.data.hours").value(2.5))
                .andExpect(jsonPath("$.data.approval.status").value(1));

        // 结束不晚于开始：新建草稿即 201。
        mockMvc.perform(post("/admin/attendance/my/overtime/save").header("token", tokenOf(POOR_APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overtimeDate\":\"2026-12-12\",\"startTime\":\"18:00:00\","
                                + "\"endTime\":\"18:00:00\",\"reason\":\"t282等时\"}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("结束时刻必须晚于开始时刻"));

        // 未配置调休入账目标：提交 201 且单据留草稿。
        jdbcTemplate.update("UPDATE attendance_leave_type SET status = 0 WHERE overtime_credit = 1");
        long noTarget = createOvertimeDraft(APPLICANT, WEEK_START, "18:00:00", "19:00:00");
        submitOvertime(noTarget, APPLICANT, "t282-ot-2")
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("未配置加班入账的调休类型"));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_overtime WHERE id = " + noTarget
                + " AND status = 0"));
    }

    @Test
    void rejectThenEditAndResubmitKeepsSameDocumentId() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(id, APPLICANT, "t282-reject-1")
                .andExpect(jsonPath("$.code").value(200));

        leaveCallback.onTerminal(id, BusinessApprovalOutcome.REJECTED);
        assertLeaveStatus(id, AttendanceApplyStatus.REJECTED.code());

        mockMvc.perform(put("/admin/attendance/my/leave/update").header("token", tokenOf(APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"leaveTypeId\":28202,"
                                + "\"startDate\":\"2026-12-08\",\"endDate\":\"2026-12-08\","
                                + "\"reason\":\"t282改期\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.REJECTED.code()));

        submitLeave(id, APPLICANT, "t282-reject-2")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.SUBMITTED.code()));
        assertEquals(2, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave WHERE applicant_id = "
                + APPLICANT + " AND id = " + id));
    }

    @Test
    void draftCrudIsOwnOnlyAndBlockedWhileInFlight() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);

        mockMvc.perform(get("/admin/attendance/my/leave/" + id).header("token", tokenOf(OTHER_APPLICANT)))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请假单不存在"));

        mockMvc.perform(delete("/admin/attendance/my/leave/remove/" + id)
                        .header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(200));
        assertLeaveStatus(id, AttendanceApplyStatus.DRAFT.code());
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave WHERE id = " + id
                + " AND is_deleted = 1"));

        long submitted = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(submitted, APPLICANT, "t282-crud-1")
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(put("/admin/attendance/my/leave/update").header("token", tokenOf(APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + submitted + ",\"leaveTypeId\":28202,"
                                + "\"startDate\":\"2026-12-08\",\"endDate\":\"2026-12-08\","
                                + "\"reason\":\"t282改\"}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请假单当前状态不能修改或删除"));
        mockMvc.perform(delete("/admin/attendance/my/leave/remove/" + submitted)
                        .header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请假单当前状态不能修改或删除"));
    }

    @Test
    void startedAndPendingApprovalListsStillContainBusinessApproval() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(id, APPLICANT, "t282-started-1")
                .andExpect(jsonPath("$.code").value(200));
        Long processId = jdbcTemplate.queryForObject("SELECT id FROM oa_process "
                + "WHERE business_type = 'attendance.leave' AND business_id = ?", Long.class, id);

        mockMvc.perform(get("/admin/process/findStarted/1/50").header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(processId.intValue())))
                .andExpect(jsonPath("$.data.records[?(@.id == " + processId
                        + ")].businessType", contains("attendance.leave")));

        mockMvc.perform(get("/admin/process/findPending/1/50").header("token", tokenOf(APPROVER)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(processId.intValue())))
                .andExpect(jsonPath("$.data.records[?(@.id == " + processId
                        + ")].businessType", contains("attendance.leave")));
    }

    @Test
    void engineTerminalOutcomesSettleDocumentsAndLedgersThroughCallbacks() throws Exception {
        // 通过：2 + 同事务扣减流水。
        long approved = createLeaveDraft(APPLICANT, TYPE_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(approved, APPLICANT, "t282-engine-approve")
                .andExpect(jsonPath("$.code").value(200));
        decide(processIdOf(approved), 1);
        assertLeaveStatus(approved, AttendanceApplyStatus.APPROVED.code());
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.leave' AND business_id = " + approved
                + " AND entry_type = 2 AND change_hours = -8.00"));

        // 拒绝：-1，不写流水（换一天，避开已通过单的区间重叠）。
        long rejected = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID,
                WEEK_START.plusDays(1), WEEK_START.plusDays(1));
        submitLeave(rejected, APPLICANT, "t282-engine-reject")
                .andExpect(jsonPath("$.code").value(200));
        decide(processIdOf(rejected), -1);
        assertLeaveStatus(rejected, AttendanceApplyStatus.REJECTED.code());
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.leave' AND business_id = " + rejected));

        // 撤回：-2，不写流水。
        long withdrawn = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID,
                WEEK_START.plusDays(4), WEEK_START.plusDays(4));
        submitLeave(withdrawn, APPLICANT, "t282-engine-withdraw")
                .andExpect(jsonPath("$.code").value(200));
        approval.withdraw(applicantUser(), processIdOf(withdrawn));
        assertLeaveStatus(withdrawn, AttendanceApplyStatus.WITHDRAWN.code());
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.leave' AND business_id = " + withdrawn));
    }

    @Test
    void concurrentSubmitsWithDifferentKeysAreArbitratedByStatusCas() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        String token = tokenOf(APPLICANT);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String key : List.of("t282-race-1", "t282-race-2")) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/admin/attendance/my/leave/" + id + "/submit")
                                    .header("token", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"idempotencyKey\":\"" + key + "\"}"))
                            .andReturn().getResponse().getContentAsString();
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            int accepted = 0;
            int rejected = 0;
            for (Future<String> future : futures) {
                String body = future.get(20, TimeUnit.SECONDS);
                if (body.contains("\"code\":200")) {
                    accepted++;
                } else if (body.contains("\"code\":201")) {
                    rejected++;
                    assertTrue(body.contains("单据已在审批中")
                            || body.contains("单据状态已变化，请刷新后重试"), body);
                }
            }
            assertEquals(1, accepted, "只能有一个并发提交赢得状态 CAS");
            assertEquals(1, rejected, "后到的不同键提交必须被拒绝");
        } finally {
            pool.shutdownNow();
        }
        assertLeaveStatus(id, AttendanceApplyStatus.SUBMITTED.code());
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id));
    }

    @Test
    void concurrentSubmitsWithTheSameKeyStillCreateOneApproval() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        String token = tokenOf(APPLICANT);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < 2; attempt++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/admin/attendance/my/leave/" + id + "/submit")
                                    .header("token", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"idempotencyKey\":\"t282-same-key\"}"))
                            .andReturn().getResponse().getContentAsString();
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            int accepted = 0;
            for (Future<String> future : futures) {
                String body = future.get(20, TimeUnit.SECONDS);
                if (body.contains("\"code\":200")) {
                    accepted++;
                } else {
                    assertTrue(body.contains("\"code\":201"), body);
                    assertTrue(body.contains("单据已在审批中")
                            || body.contains("单据状态已变化，请刷新后重试"), body);
                }
            }
            assertTrue(accepted >= 1, "至少一个提交拿到同一条在途审批");
        } finally {
            pool.shutdownNow();
        }
        assertLeaveStatus(id, AttendanceApplyStatus.SUBMITTED.code());
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id), "同键并发只允许一条审批");
    }

    @Test
    void reusingFinishedSubmissionKeyIsRejectedAndKeepsDocumentEditable() throws Exception {
        long id = createLeaveDraft(APPLICANT, TYPE_NO_BALANCE_ID, WEEK_START, WEEK_START);
        submitLeave(id, APPLICANT, "t282-cycle-key")
                .andExpect(jsonPath("$.code").value(200));
        leaveCallback.onTerminal(id, BusinessApprovalOutcome.REJECTED);
        // 直接调回调绕过了审批动作：把审批行也置成终态，模拟「这条提交已经结束」。
        jdbcTemplate.update("UPDATE oa_process SET status = -1 WHERE business_type = 'attendance.leave' "
                + "AND business_id = ?", id);
        assertLeaveStatus(id, AttendanceApplyStatus.REJECTED.code());

        // 复用已完成的历史提交键：唯一索引拒绝，生命周期按 201 命名拒绝，单据回到拒绝态。
        submitLeave(id, APPLICANT, "t282-cycle-key")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("提交标识已被使用"));
        assertLeaveStatus(id, AttendanceApplyStatus.REJECTED.code());
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id));

        // 拒绝态仍可编辑，换新键重提成功（单据号不变，起第二条审批）。
        mockMvc.perform(put("/admin/attendance/my/leave/update").header("token", tokenOf(APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"leaveTypeId\":28202,"
                                + "\"startDate\":\"2026-12-08\",\"endDate\":\"2026-12-08\","
                                + "\"reason\":\"t282换键重提\"}"))
                .andExpect(jsonPath("$.code").value(200));
        submitLeave(id, APPLICANT, "t282-cycle-key-2")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.SUBMITTED.code()));
        assertEquals(2, count("SELECT COUNT(*) FROM oa_process WHERE business_type = 'attendance.leave' "
                + "AND business_id = " + id));
    }

    @Test
    void overtimePageAndDetailStayOwnOnly() throws Exception {
        long id = createOvertimeDraft(APPLICANT, WEEK_START, "18:00:00", "19:00:00");
        mockMvc.perform(get("/admin/attendance/my/overtime/1/10").header("token", tokenOf(APPLICANT)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(id));
        mockMvc.perform(get("/admin/attendance/my/overtime/" + id)
                        .header("token", tokenOf(OTHER_APPLICANT)))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("加班单不存在"));
    }

    // ---------------------------------------------------------------- helpers

    private long createLeaveDraft(long applicant, long typeId, LocalDate start, LocalDate end)
            throws Exception {
        mockMvc.perform(post("/admin/attendance/my/leave/save").header("token", tokenOf(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaveTypeId\":" + typeId + ",\"startDate\":\"" + start
                                + "\",\"endDate\":\"" + end + "\",\"reason\":\"t282请假\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.DRAFT.code()));
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM attendance_leave "
                + "WHERE applicant_id = ?", Long.class, applicant);
        assertNotNull(id);
        return id;
    }

    private long createOvertimeDraft(long applicant, LocalDate date,
                                     String start, String end) throws Exception {
        mockMvc.perform(post("/admin/attendance/my/overtime/save").header("token", tokenOf(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overtimeDate\":\"" + date + "\",\"startTime\":\"" + start
                                + "\",\"endTime\":\"" + end + "\",\"reason\":\"t282加班\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceApplyStatus.DRAFT.code()));
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM attendance_overtime "
                + "WHERE applicant_id = ?", Long.class, applicant);
        assertNotNull(id);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions submitLeave(
            long id, long applicant, String key) throws Exception {
        return mockMvc.perform(post("/admin/attendance/my/leave/" + id + "/submit")
                .header("token", tokenOf(applicant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"" + key + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions submitOvertime(
            long id, long applicant, String key) throws Exception {
        return mockMvc.perform(post("/admin/attendance/my/overtime/" + id + "/submit")
                .header("token", tokenOf(applicant))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"" + key + "\"}"));
    }

    private long processIdOf(long businessId) {
        return jdbcTemplate.queryForObject("SELECT id FROM oa_process "
                + "WHERE business_type = 'attendance.leave' AND business_id = ?", Long.class, businessId);
    }

    private void decide(long processId, int status) {
        String instanceId = jdbcTemplate.queryForObject(
                "SELECT process_instance_id FROM oa_process WHERE id = ?", String.class, processId);
        String taskId = taskService.createTaskQuery().processInstanceId(instanceId)
                .singleResult().getId();
        ApprovalVo vo = new ApprovalVo();
        vo.setProcessId(processId);
        vo.setTaskId(taskId);
        vo.setStatus(status);
        approval.approve(approverUser(), vo);
    }

    private SysUser approverUser() {
        SysUser user = new SysUser();
        user.setId(APPROVER);
        user.setUsername("t282-approver");
        user.setName("审批人");
        return user;
    }

    private SysUser applicantUser() {
        SysUser user = new SysUser();
        user.setId(APPLICANT);
        user.setUsername("t282-applicant");
        user.setName("申请人");
        return user;
    }

    private void assertLeaveStatus(long id, int expected) {
        assertEquals(expected, jdbcTemplate.queryForObject(
                "SELECT status FROM attendance_leave WHERE id = ?", Integer.class, id));
    }

    private String tokenOf(long userId) {
        String username = jdbcTemplate.queryForObject(
                "SELECT username FROM sys_user WHERE id = ?", String.class, userId);
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private void insertUser(long id, String username, String name) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, 0)", id, username, name);
    }

    private void insertMember(long userId) {
        jdbcTemplate.update("INSERT INTO attendance_group_member (group_id, user_id, "
                + "create_time, update_time) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                GROUP_ID, userId);
    }

    private void insertLeaveType(long id, String name, int balanceRequired) {
        jdbcTemplate.update("INSERT INTO attendance_leave_type (id, name, balance_required, "
                + "overtime_credit, status, sort_no, remark, create_time, update_time, is_deleted) "
                + "VALUES (?, ?, ?, 0, 1, 0, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                id, name, balanceRequired);
    }

    private void insertHoliday(LocalDate date, String name, int dayType) {
        jdbcTemplate.update("INSERT INTO attendance_holiday (holiday_date, name, day_type, "
                + "create_time, update_time) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                date, name, dayType);
    }

    private void insertLedger(long userId, long typeId, int entryType, String hours,
                              String businessType, Long businessId) {
        jdbcTemplate.update("INSERT INTO attendance_leave_ledger (user_id, leave_type_id, entry_type, "
                        + "change_hours, business_type, business_id, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                userId, typeId, entryType, hours, businessType, businessId);
    }

    private void insertLeave(long applicant, long typeId, LocalDate start, LocalDate end,
                             int days, String hours, int status) {
        jdbcTemplate.update("INSERT INTO attendance_leave (applicant_id, leave_type_id, start_date, "
                        + "end_date, days, hours, reason, status, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 't282夹具', ?, CURRENT_TIMESTAMP, "
                        + "CURRENT_TIMESTAMP, 0)",
                applicant, typeId, start, end, days, hours, status);
    }

    private void seedMapping(String businessType) {
        jdbcTemplate.update("DELETE FROM oa_business_template WHERE business_type = ?", businessType);
        jdbcTemplate.update("INSERT INTO oa_business_template (business_type, process_template_id) "
                + "VALUES (?, 1)", businessType);
    }

    private void publishTemplate() throws IOException {
        repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("attendanceApply")
                .list()
                .forEach(definition ->
                        repositoryService.deleteDeployment(definition.getDeploymentId(), true));
        jdbcTemplate.update("DELETE FROM oa_process_template_publication WHERE process_template_id = 1");
        jdbcTemplate.update("UPDATE oa_process_template SET status = 0 WHERE id = 1");
        ProcessTemplate template = approvalTemplate.read(1L);
        var uploaded = approvalTemplate.uploadProcessDefinition(new MockMultipartFile(
                "file", "attendanceApply.zip", "application/zip", bpmnZip()));
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(template.getId());
    }

    private static byte[] bpmnZip() throws IOException {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:activiti="http://activiti.org/bpmn"
                             targetNamespace="http://www.activiti.org/processdef">
                  <process id="attendanceApply" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="approve" name="审批" activiti:assignee="t282-approver"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("attendanceApply.bpmn20.xml"));
            zip.write(bpmn.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_leave WHERE applicant_id BETWEEN 28200 AND 28299");
        jdbcTemplate.update("DELETE FROM attendance_overtime WHERE applicant_id BETWEEN 28200 AND 28299");
        jdbcTemplate.update("DELETE FROM attendance_leave_ledger WHERE user_id BETWEEN 28200 AND 28299");
        jdbcTemplate.update("DELETE FROM attendance_group_member WHERE user_id BETWEEN 28200 AND 28299");
        jdbcTemplate.update("DELETE FROM attendance_group WHERE id = ?", GROUP_ID);
        jdbcTemplate.update("DELETE FROM attendance_shift WHERE id = ?", SHIFT_ID);
        jdbcTemplate.update("DELETE FROM attendance_holiday WHERE name LIKE 't282%'");
        jdbcTemplate.update("DELETE FROM attendance_leave_type WHERE id BETWEEN 28200 AND 28299");
        jdbcTemplate.update("DELETE FROM oa_process_record WHERE process_id IN "
                + "(SELECT id FROM oa_process WHERE business_type IN "
                + "('attendance.leave','attendance.overtime'))");
        jdbcTemplate.update("DELETE FROM oa_process_cc WHERE process_id IN "
                + "(SELECT id FROM oa_process WHERE business_type IN "
                + "('attendance.leave','attendance.overtime'))");
        jdbcTemplate.update("DELETE FROM oa_process_add_sign_task WHERE process_id IN "
                + "(SELECT id FROM oa_process WHERE business_type IN "
                + "('attendance.leave','attendance.overtime'))");
        jdbcTemplate.update("DELETE FROM oa_process WHERE business_type IN "
                + "('attendance.leave','attendance.overtime')");
        jdbcTemplate.update("DELETE FROM oa_business_template WHERE business_type IN "
                + "('attendance.leave','attendance.overtime')");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 28200 AND 28299");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 28200 AND 28299");
        // 预置「调休」是加班入账目标；某些用例会临时停用它，逐测恢复。
        jdbcTemplate.update("UPDATE attendance_leave_type SET status = 1 "
                + "WHERE overtime_credit = 1 AND name = '调休'");
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
