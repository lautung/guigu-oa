package com.lautung.oa.attendance;

import db.migration.V139__AttendanceSchemaAndMenu;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static com.lautung.oa.auth.LoginAuthContractTest.InMemoryRedisConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #278 的迁移合同：V139 在 H2 上跑真迁移建出九表与全部索引、菜单（/business 与考勤管理两级目录/五页/八按钮）
 * 与 super_admin 补授、假期类型五项预置；迁移重复执行幂等。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_migration;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Import(InMemoryRedisConfig.class)
class AttendanceMigrationContractTest {

    private static final List<String> ATTENDANCE_TABLES = List.of(
            "attendance_shift",
            "attendance_group",
            "attendance_group_member",
            "attendance_holiday",
            "attendance_punch",
            "attendance_leave_type",
            "attendance_leave_ledger",
            "attendance_leave",
            "attendance_overtime");

    private static final List<String> UNIQUE_INDEXES = List.of(
            "uk_attendance_member_user",
            "uk_attendance_holiday_date");

    private static final List<String> ORDINARY_INDEXES = List.of(
            "idx_attendance_group_shift",
            "idx_attendance_member_group",
            "idx_attendance_punch_user_date",
            "idx_attendance_punch_date",
            "idx_attendance_ledger_user_type",
            "idx_attendance_ledger_business",
            "idx_attendance_leave_applicant_status",
            "idx_attendance_leave_dates",
            "idx_attendance_overtime_applicant_status",
            "idx_attendance_overtime_date");

    private static final List<String> ATTENDANCE_BUTTONS = List.of(
            "attendance:group:add", "attendance:group:edit", "attendance:group:remove",
            "attendance:record:repair",
            "attendance:type:add", "attendance:type:edit", "attendance:type:remove",
            "attendance:ledger:grant");

    private static final List<String> ATTENDANCE_PAGES = List.of(
            "/business/attendance/group",
            "/business/attendance/record",
            "/business/attendance/repair",
            "/business/attendance/leave",
            "/business/attendance/stat");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void v139BuildsNineTablesIndexesMenusAndLeaveTypesIdempotently() throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '139' AND success = TRUE"));

        for (String table : ATTENDANCE_TABLES) {
            assertTrue(tableExists(table), "缺表: " + table);
        }
        for (String index : UNIQUE_INDEXES) {
            assertTrue(indexExists(index), "缺唯一索引: " + index);
        }
        for (String index : ORDINARY_INDEXES) {
            assertTrue(indexExists(index), "缺索引: " + index);
        }

        // 成员与节假日是硬删表，不带 is_deleted；其余七表按公共字段约定带 is_deleted。
        assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE table_name = 'ATTENDANCE_GROUP_MEMBER' AND column_name = 'IS_DELETED'"));
        assertEquals(0, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE table_name = 'ATTENDANCE_HOLIDAY' AND column_name = 'IS_DELETED'"));
        for (String table : List.of("attendance_shift", "attendance_group", "attendance_punch",
                "attendance_leave_type", "attendance_leave_ledger", "attendance_leave",
                "attendance_overtime")) {
            assertEquals(1, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE table_name = '" + table.toUpperCase() + "' "
                    + "AND column_name = 'IS_DELETED'"), table + " 缺 is_deleted");
        }

        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu "
                + "WHERE path='/business' AND parent_id=0 AND type=0 AND is_deleted=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu d ON d.id=c.parent_id "
                + "WHERE c.path='/business/attendance' AND c.type=0 AND c.is_deleted=0 "
                + "AND d.path='/business' AND d.type=0"));
        for (String page : ATTENDANCE_PAGES) {
            assertEquals(1, count("SELECT COUNT(*) FROM sys_menu c JOIN sys_menu d ON d.id=c.parent_id "
                    + "WHERE c.path='" + page + "' AND c.type=1 AND c.is_deleted=0 "
                    + "AND d.path='/business/attendance' AND d.type=0"), "缺页面: " + page);
        }
        assertEquals(8, count("SELECT COUNT(*) FROM sys_menu b WHERE b.type=2 AND b.is_deleted=0 "
                + "AND b.perms IN ('attendance:group:add','attendance:group:edit','attendance:group:remove',"
                + "'attendance:record:repair','attendance:type:add','attendance:type:edit',"
                + "'attendance:type:remove','attendance:ledger:grant')"));
        for (String button : ATTENDANCE_BUTTONS) {
            assertEquals(1, count("SELECT COUNT(*) FROM sys_menu b WHERE b.perms='" + button
                    + "' AND b.type=2 AND b.is_deleted=0"), "缺按钮点: " + button);
        }

        assertEquals(2, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id "
                + "JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='super_admin' AND rm.is_deleted=0 "
                + "AND m.type=0 AND m.path IN ('/business','/business/attendance')"));
        assertEquals(5, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id "
                + "JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='super_admin' AND rm.is_deleted=0 "
                + "AND m.type=1 AND m.path IN ('/business/attendance/group','/business/attendance/record',"
                + "'/business/attendance/repair','/business/attendance/leave','/business/attendance/stat')"));
        assertEquals(8, count("SELECT COUNT(*) FROM sys_role_menu rm JOIN sys_role r ON r.id=rm.role_id "
                + "JOIN sys_menu m ON m.id=rm.menu_id WHERE r.role_code='super_admin' AND rm.is_deleted=0 "
                + "AND m.type=2 AND m.perms IN ('attendance:group:add','attendance:group:edit',"
                + "'attendance:group:remove','attendance:record:repair','attendance:type:add',"
                + "'attendance:type:edit','attendance:type:remove','attendance:ledger:grant')"));

        // 假期类型五项预置：年假/调休要余额，调休是加班入账目标（全局唯一）。
        assertEquals(5, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE name IN ('年假','事假','病假','调休','婚丧产假') AND status=1 AND is_deleted=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE name='年假' AND balance_required=1 AND overtime_credit=0"));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE name='调休' AND balance_required=1 AND overtime_credit=1"));
        assertEquals(3, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE name IN ('事假','病假','婚丧产假') AND balance_required=0 AND overtime_credit=0"));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_leave_type "
                + "WHERE overtime_credit=1 AND name <> '调休'"));

        replayMigrationIsIdempotent();
    }

    private void replayMigrationIsIdempotent() throws Exception {
        int menus = count("SELECT COUNT(*) FROM sys_menu");
        int grants = count("SELECT COUNT(*) FROM sys_role_menu WHERE is_deleted=0");
        int types = count("SELECT COUNT(*) FROM attendance_leave_type");
        int indexes = count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE index_name LIKE 'uk_attendance_%' OR index_name LIKE 'idx_attendance_%'");
        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V139__AttendanceSchemaAndMenu().migrate(context);
            new V139__AttendanceSchemaAndMenu().migrate(context);
        }
        assertEquals(menus, count("SELECT COUNT(*) FROM sys_menu"));
        assertEquals(grants, count("SELECT COUNT(*) FROM sys_role_menu WHERE is_deleted=0"));
        assertEquals(types, count("SELECT COUNT(*) FROM attendance_leave_type"));
        assertEquals(indexes, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE index_name LIKE 'uk_attendance_%' OR index_name LIKE 'idx_attendance_%'"));
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(
                     connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (tables.next()) {
                if (table.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean indexExists(String indexName) throws SQLException {
        // IndexInfo 是按表枚举的：逐一查九表，按索引名命中。
        for (String table : ATTENDANCE_TABLES) {
            try (Connection connection = dataSource.getConnection();
                 ResultSet indexes = connection.getMetaData().getIndexInfo(
                         connection.getCatalog(), null, table, false, false)) {
                if (matchIndex(indexes, indexName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchIndex(ResultSet indexes, String indexName) throws SQLException {
        while (indexes.next()) {
            String name = indexes.getString("INDEX_NAME");
            if (name != null && name.equalsIgnoreCase(indexName)) {
                return true;
            }
        }
        return false;
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
