package com.lautung.oa.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoMigrationContractTest {

    private static final String URL = "jdbc:h2:mem:guigu_oa_demo_migration;DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;"
            + "INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP";

    private static final String ATTENDANCE_SEED = "db/demo-migration/V141__demo_attendance_seed.sql";

    @Test
    void demoMigrationsSeedTheApprovalTemplateUnpublishedAndLeavePublishingToTheSeeder() throws SQLException {
        migrate();

        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            assertEquals(0, intValue(statement.executeQuery(
                    "SELECT status FROM oa_process_template WHERE process_definition_key = 'demoApproval' "
                            + "AND is_deleted = 0")));
            assertEquals(0, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_process_template_publication")));
            assertEquals(3, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_user WHERE username IN ('alice', 'bob', 'carol') AND is_deleted = 0")));
            assertTrue(intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_process_cc_rule r JOIN sys_user u ON u.id = r.user_id "
                            + "WHERE r.process_definition_key = 'demoApproval' AND u.username = 'carol' "
                            + "AND r.is_deleted = 0")) > 0);
        }
    }

    @Test
    void demoAttendanceSeedIsCompleteAndReplaySafe() throws SQLException {
        migrate();

        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            assertEquals(1, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_dept WHERE name = '演示部门' AND parent_id = 0 "
                            + "AND ancestors = CONCAT(',0,', id, ',') AND is_deleted = 0")));
            assertEquals(1, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_dept d JOIN sys_user bob ON bob.username = 'bob' "
                            + "WHERE d.name = '演示部门' AND d.leader_user_id = bob.id AND d.is_deleted = 0")));
            assertEquals(1, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_user u JOIN sys_dept d ON d.id = u.dept_id "
                            + "WHERE u.username = 'alice' AND d.name = '演示部门' AND u.is_deleted = 0")));

            assertEquals(1, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM attendance_shift WHERE name = '演示班次' "
                            + "AND start_time = '09:00:00' AND end_time = '18:00:00' "
                            + "AND lunch_start = '12:00:00' AND lunch_end = '13:00:00' "
                            + "AND is_deleted = 0")));
            assertEquals(1, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM attendance_group WHERE name = '演示考勤组' "
                            + "AND workdays = '1,2,3,4,5' AND is_deleted = 0")));
            assertEquals(1, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM attendance_group_member m "
                            + "JOIN attendance_group g ON g.id = m.group_id "
                            + "JOIN sys_user u ON u.id = m.user_id "
                            + "WHERE g.name = '演示考勤组' AND u.username = 'alice'")));

            assertTrue(intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM attendance_holiday WHERE day_type = 1 "
                            + "AND YEAR(holiday_date) = YEAR(CURRENT_DATE)")) >= 2);
            assertTrue(intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM attendance_holiday WHERE day_type = 2 "
                            + "AND YEAR(holiday_date) = YEAR(CURRENT_DATE)")) >= 1);

            assertTrue(intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM attendance_leave_ledger l "
                            + "JOIN sys_user u ON u.id = l.user_id "
                            + "JOIN attendance_leave_type t ON t.id = l.leave_type_id "
                            + "WHERE u.username = 'alice' AND t.name = '年假' AND l.entry_type = 1 "
                            + "AND l.change_hours = 40.00 AND l.is_deleted = 0")) >= 1);

            assertEquals(2, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_process_template WHERE process_definition_key IN "
                            + "('attendanceLeave', 'attendanceOvertime') AND status = 0 AND is_deleted = 0")));
            assertEquals(2, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_process_approver_rule r "
                            + "JOIN oa_process_template p ON p.id = r.process_template_id "
                            + "WHERE p.process_definition_key IN ('attendanceLeave', 'attendanceOvertime') "
                            + "AND r.task_definition_key = 'deptLeaderReview' "
                            + "AND r.source_type = 'DEPT_LEADER' AND r.is_deleted = 0")));
            assertEquals(2, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_business_template b "
                            + "JOIN oa_process_template p ON p.id = b.process_template_id "
                            + "WHERE b.business_type IN ('attendance.leave', 'attendance.overtime') "
                            + "AND p.process_definition_key IN ('attendanceLeave', 'attendanceOvertime') "
                            + "AND b.is_deleted = 0")));

            int[] before = seedCounts(statement);
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(ATTENDANCE_SEED));
            int[] after = seedCounts(statement);
            assertEquals(before[0], after[0], "重放不得新增部门/账号归属");
            assertEquals(before[1], after[1], "重放不得新增班次/考勤组/成员");
            assertEquals(before[2], after[2], "重放不得新增节假日");
            assertEquals(before[3], after[3], "重放不得新增发放流水");
            assertEquals(before[4], after[4], "重放不得新增模板/规则/映射");
        }
    }

    private static void migrate() {
        Flyway.configure()
                .dataSource(URL, "sa", "")
                .locations("classpath:db/migration", "classpath:db/demo-migration")
                .load()
                .migrate();
    }

    /** 部门归属 / 班次组员 / 节假日 / 发放流水 / 模板规则映射五组行数。 */
    private static int[] seedCounts(Statement statement) throws SQLException {
        return new int[]{
                intValue(statement.executeQuery(
                        "SELECT COUNT(*) FROM sys_dept WHERE name = '演示部门' AND is_deleted = 0")),
                intValue(statement.executeQuery(
                        "SELECT COUNT(*) FROM attendance_group_member m "
                                + "JOIN attendance_group g ON g.id = m.group_id "
                                + "WHERE g.name = '演示考勤组'")),
                intValue(statement.executeQuery("SELECT COUNT(*) FROM attendance_holiday")),
                intValue(statement.executeQuery(
                        "SELECT COUNT(*) FROM attendance_leave_ledger WHERE entry_type = 1")),
                intValue(statement.executeQuery(
                        "SELECT COUNT(*) FROM oa_process_template WHERE process_definition_key IN "
                                + "('attendanceLeave', 'attendanceOvertime')"))
                        + intValue(statement.executeQuery(
                        "SELECT COUNT(*) FROM oa_process_approver_rule"))
                        + intValue(statement.executeQuery(
                        "SELECT COUNT(*) FROM oa_business_template"))
        };
    }

    private static int intValue(ResultSet result) throws SQLException {
        result.next();
        return result.getInt(1);
    }
}
