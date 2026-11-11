package com.lautung.oa.attendance;

import com.lautung.oa.attendance.approval.AttendanceLeaveApprovalCallback;
import com.lautung.oa.attendance.approval.AttendanceOvertimeApprovalCallback;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.process.business.BusinessApprovalOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static com.lautung.oa.auth.LoginAuthContractTest.InMemoryRedisConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #282 的审批回调端到端合同（ADR 0067/0073）：APPROVED → 2 + 请假扣减/加班入账流水，
 * REJECTED → -1、WITHDRAWN → -2 不写流水；回调加入调用方事务，异常时单据与流水一起回滚
 * （用 TransactionTemplate 模拟审批动作事务）。同值 no-op、异值冲突与 CAS 0 行重读是
 * 生命周期 module 的规则，由 #294 的 {@code DocumentLifecycleTest} 断言，本类不再重复。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_callback;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Import(InMemoryRedisConfig.class)
class AttendanceApprovalCallbackContractTest {

    private static final long APPLICANT = 28251L;
    private static final long TYPE_ID = 28251L;

    @Autowired
    private AttendanceLeaveApprovalCallback leaveCallback;

    @Autowired
    private AttendanceOvertimeApprovalCallback overtimeCallback;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        resetFixtures();
        jdbcTemplate.update("INSERT INTO attendance_leave_type (id, name, balance_required, "
                + "overtime_credit, status, sort_no, remark, create_time, update_time, is_deleted) "
                + "VALUES (?, 't282回调假', 1, 0, 1, 0, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                TYPE_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("UPDATE attendance_leave_type SET status = 1, overtime_credit = 1 "
                + "WHERE name = '调休'");
        resetFixtures();
    }

    @Test
    void approvedLeaveSettlesAndWritesOneNegativeDeduction() {
        long id = insertLeave("8.00");
        assertEquals("attendance.leave", leaveCallback.businessType());

        leaveCallback.onTerminal(id, BusinessApprovalOutcome.APPROVED);

        Map<String, Object> document = jdbcTemplate.queryForMap(
                "SELECT status, finish_time FROM attendance_leave WHERE id = ?", id);
        assertEquals(2, ((Number) document.get("status")).intValue());
        assertNotNull(document.get("finish_time"));

        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "SELECT user_id, leave_type_id, entry_type, change_hours, business_type, business_id "
                        + "FROM attendance_leave_ledger WHERE business_type = 'attendance.leave' "
                        + "AND business_id = ?", id);
        assertEquals(APPLICANT, ((Number) ledger.get("user_id")).longValue());
        assertEquals(TYPE_ID, ((Number) ledger.get("leave_type_id")).longValue());
        assertEquals(2, ((Number) ledger.get("entry_type")).intValue());
        assertEquals(0, new BigDecimal("-8.00").compareTo((BigDecimal) ledger.get("change_hours")));
        assertEquals("attendance.leave", ledger.get("business_type"));
    }

    @Test
    void rejectedAndWithdrawnOnlySettleStatus() {
        long rejected = insertLeave("4.00");
        leaveCallback.onTerminal(rejected, BusinessApprovalOutcome.REJECTED);
        assertEquals(-1, statusOf("attendance_leave", rejected));

        long withdrawn = insertLeave("4.00");
        leaveCallback.onTerminal(withdrawn, BusinessApprovalOutcome.WITHDRAWN);
        assertEquals(-2, statusOf("attendance_leave", withdrawn));

        assertEquals(0, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.leave'"));
    }

    @Test
    void callbackJoinsCallerTransactionAndRollsBackWithIt() {
        long id = insertLeave("8.00");

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            leaveCallback.onTerminal(id, BusinessApprovalOutcome.APPROVED);
            throw new IllegalStateException("动作回滚");
        }));

        assertEquals(1, statusOf("attendance_leave", id));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.leave' AND business_id = " + id));
    }

    @Test
    void approvedOvertimeWritesCreditToConfiguredOvertimeType() {
        Long creditTypeId = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_leave_type WHERE overtime_credit = 1 "
                        + "AND is_deleted = 0 ORDER BY id ASC LIMIT 1", Long.class);
        long id = insertOvertime("2.50");
        assertEquals("attendance.overtime", overtimeCallback.businessType());

        overtimeCallback.onTerminal(id, BusinessApprovalOutcome.APPROVED);

        assertEquals(2, statusOf("attendance_overtime", id));
        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "SELECT leave_type_id, entry_type, change_hours, business_type, business_id "
                        + "FROM attendance_leave_ledger WHERE business_type = 'attendance.overtime' "
                        + "AND business_id = ?", id);
        assertEquals(creditTypeId, ((Number) ledger.get("leave_type_id")).longValue());
        assertEquals(3, ((Number) ledger.get("entry_type")).intValue());
        assertEquals(0, new BigDecimal("2.50").compareTo((BigDecimal) ledger.get("change_hours")));
    }

    @Test
    void overtimeWithoutCreditTargetRollsBackTerminalWriteTogether() {
        jdbcTemplate.update("UPDATE attendance_leave_type SET overtime_credit = 0 "
                + "WHERE overtime_credit = 1");
        long id = insertOvertime("2.00");

        BusinessException missing = assertThrows(BusinessException.class,
                () -> transactions.executeWithoutResult(status ->
                        overtimeCallback.onTerminal(id, BusinessApprovalOutcome.APPROVED)));
        assertEquals(201, missing.getCode());
        assertEquals("未配置加班入账的调休类型", missing.getMessage());
        assertEquals(1, statusOf("attendance_overtime", id));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.overtime'"));
    }

    @Test
    void disabledOvertimeTargetStillAcceptsInFlightCredit() {
        jdbcTemplate.update("UPDATE attendance_leave_type SET status = 0 WHERE overtime_credit = 1");
        long id = insertOvertime("3.00");

        overtimeCallback.onTerminal(id, BusinessApprovalOutcome.APPROVED);

        assertEquals(2, statusOf("attendance_overtime", id));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_ledger "
                + "WHERE business_type = 'attendance.overtime' AND business_id = " + id));
    }

    private long insertLeave(String hours) {
        jdbcTemplate.update("INSERT INTO attendance_leave (applicant_id, leave_type_id, start_date, "
                        + "end_date, days, hours, reason, status, submit_time, create_time, update_time, "
                        + "is_deleted) VALUES (?, ?, ?, ?, 1, ?, 't282回调', 1, CURRENT_TIMESTAMP, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                APPLICANT, TYPE_ID, LocalDate.of(2026, 12, 7), LocalDate.of(2026, 12, 7), hours);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM attendance_leave "
                + "WHERE applicant_id = ?", Long.class, APPLICANT);
    }

    private long insertOvertime(String hours) {
        jdbcTemplate.update("INSERT INTO attendance_overtime (applicant_id, overtime_date, start_time, "
                        + "end_time, hours, reason, status, submit_time, create_time, update_time, "
                        + "is_deleted) VALUES (?, ?, '18:00:00', '20:00:00', ?, 't282回调', 1, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)",
                APPLICANT, LocalDate.of(2026, 12, 12), hours);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM attendance_overtime "
                + "WHERE applicant_id = ?", Long.class, APPLICANT);
    }

    private int statusOf(String table, long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_leave_ledger WHERE user_id = ?", APPLICANT);
        jdbcTemplate.update("DELETE FROM attendance_leave WHERE applicant_id = ?", APPLICANT);
        jdbcTemplate.update("DELETE FROM attendance_overtime WHERE applicant_id = ?", APPLICANT);
        jdbcTemplate.update("DELETE FROM attendance_leave_type WHERE id = ?", TYPE_ID);
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
