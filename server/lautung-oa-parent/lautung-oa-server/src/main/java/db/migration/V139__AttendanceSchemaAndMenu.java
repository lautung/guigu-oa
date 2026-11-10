package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 考勤底座（#278，ADR 0073）：九张考勤表 + 索引 + 考勤菜单（目录/五页/按钮点）
 * + 假期类型五项预置。
 *
 * <p>建表用 {@code CREATE TABLE IF NOT EXISTS} 已是幂等；MySQL 8 不支持
 * {@code CREATE INDEX IF NOT EXISTS}，索引先查元数据再建（与 V110/V130 同一纪律）。
 * 成员与节假日两表是硬删关系/配置，不带 {@code is_deleted}（ADR 0073）。
 * 菜单/按钮/假期类型行是纯 DML，放 {@code db/seed/attendance_menu_v139.sql}，
 * 全部由 {@code NOT EXISTS} 守住。打卡/流水/单据/统计四张表本票只落结构，
 * 业务实现归后续票（#279/#280/#282）。
 */
public class V139__AttendanceSchemaAndMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/attendance_menu_v139.sql";

    private static final String SHIFT_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_shift (
              id BIGINT NOT NULL AUTO_INCREMENT,
              name VARCHAR(50) NOT NULL,
              start_time TIME NOT NULL,
              end_time TIME NOT NULL,
              lunch_start TIME DEFAULT NULL,
              lunch_end TIME DEFAULT NULL,
              grace_minutes INT NOT NULL DEFAULT 0,
              remark VARCHAR(255) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String GROUP_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_group (
              id BIGINT NOT NULL AUTO_INCREMENT,
              name VARCHAR(50) NOT NULL,
              shift_id BIGINT NOT NULL,
              workdays VARCHAR(16) NOT NULL,
              remark VARCHAR(255) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String GROUP_MEMBER_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_group_member (
              id BIGINT NOT NULL AUTO_INCREMENT,
              group_id BIGINT NOT NULL,
              user_id BIGINT NOT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              PRIMARY KEY (id)
            )
            """;

    private static final String HOLIDAY_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_holiday (
              id BIGINT NOT NULL AUTO_INCREMENT,
              holiday_date DATE NOT NULL,
              name VARCHAR(50) NOT NULL,
              day_type TINYINT NOT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              PRIMARY KEY (id)
            )
            """;

    private static final String PUNCH_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_punch (
              id BIGINT NOT NULL AUTO_INCREMENT,
              user_id BIGINT NOT NULL,
              punch_date DATE NOT NULL,
              slot TINYINT NOT NULL,
              punch_time DATETIME NOT NULL,
              source TINYINT NOT NULL,
              operator_user_id BIGINT DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String LEAVE_TYPE_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_leave_type (
              id BIGINT NOT NULL AUTO_INCREMENT,
              name VARCHAR(50) NOT NULL,
              balance_required TINYINT NOT NULL DEFAULT 0,
              overtime_credit TINYINT NOT NULL DEFAULT 0,
              status TINYINT NOT NULL DEFAULT 1,
              sort_no INT NOT NULL DEFAULT 0,
              remark VARCHAR(255) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String LEAVE_LEDGER_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_leave_ledger (
              id BIGINT NOT NULL AUTO_INCREMENT,
              user_id BIGINT NOT NULL,
              leave_type_id BIGINT NOT NULL,
              entry_type TINYINT NOT NULL,
              change_hours DECIMAL(10,2) NOT NULL,
              business_type VARCHAR(64) DEFAULT NULL,
              business_id BIGINT DEFAULT NULL,
              operator_user_id BIGINT DEFAULT NULL,
              remark VARCHAR(255) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String LEAVE_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_leave (
              id BIGINT NOT NULL AUTO_INCREMENT,
              applicant_id BIGINT NOT NULL,
              leave_type_id BIGINT NOT NULL,
              start_date DATE NOT NULL,
              end_date DATE NOT NULL,
              days INT NOT NULL,
              hours DECIMAL(10,2) NOT NULL,
              reason VARCHAR(255) DEFAULT NULL,
              status TINYINT NOT NULL DEFAULT 0,
              submit_time DATETIME DEFAULT NULL,
              finish_time DATETIME DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String OVERTIME_TABLE = """
            CREATE TABLE IF NOT EXISTS attendance_overtime (
              id BIGINT NOT NULL AUTO_INCREMENT,
              applicant_id BIGINT NOT NULL,
              overtime_date DATE NOT NULL,
              start_time TIME NOT NULL,
              end_time TIME NOT NULL,
              hours DECIMAL(10,2) NOT NULL,
              reason VARCHAR(255) DEFAULT NULL,
              status TINYINT NOT NULL DEFAULT 0,
              submit_time DATETIME DEFAULT NULL,
              finish_time DATETIME DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    /** 索引名 → 建索引语句（表 ADR 0073 表结构草案，只加不改）。 */
    private static final String[][] INDEXES = {
            {"idx_attendance_group_shift",
                    "CREATE INDEX idx_attendance_group_shift ON attendance_group (shift_id)"},
            {"uk_attendance_member_user",
                    "CREATE UNIQUE INDEX uk_attendance_member_user "
                            + "ON attendance_group_member (user_id)"},
            {"idx_attendance_member_group",
                    "CREATE INDEX idx_attendance_member_group ON attendance_group_member (group_id)"},
            {"uk_attendance_holiday_date",
                    "CREATE UNIQUE INDEX uk_attendance_holiday_date ON attendance_holiday (holiday_date)"},
            {"idx_attendance_punch_user_date",
                    "CREATE INDEX idx_attendance_punch_user_date ON attendance_punch (user_id, punch_date)"},
            {"idx_attendance_punch_date",
                    "CREATE INDEX idx_attendance_punch_date ON attendance_punch (punch_date)"},
            {"idx_attendance_ledger_user_type",
                    "CREATE INDEX idx_attendance_ledger_user_type "
                            + "ON attendance_leave_ledger (user_id, leave_type_id)"},
            {"idx_attendance_ledger_business",
                    "CREATE INDEX idx_attendance_ledger_business "
                            + "ON attendance_leave_ledger (business_type, business_id)"},
            {"idx_attendance_leave_applicant_status",
                    "CREATE INDEX idx_attendance_leave_applicant_status "
                            + "ON attendance_leave (applicant_id, status)"},
            {"idx_attendance_leave_dates",
                    "CREATE INDEX idx_attendance_leave_dates ON attendance_leave (start_date, end_date)"},
            {"idx_attendance_overtime_applicant_status",
                    "CREATE INDEX idx_attendance_overtime_applicant_status "
                            + "ON attendance_overtime (applicant_id, status)"},
            {"idx_attendance_overtime_date",
                    "CREATE INDEX idx_attendance_overtime_date ON attendance_overtime (overtime_date)"},
    };

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute(SHIFT_TABLE);
            statement.execute(GROUP_TABLE);
            statement.execute(GROUP_MEMBER_TABLE);
            statement.execute(HOLIDAY_TABLE);
            statement.execute(PUNCH_TABLE);
            statement.execute(LEAVE_TYPE_TABLE);
            statement.execute(LEAVE_LEDGER_TABLE);
            statement.execute(LEAVE_TABLE);
            statement.execute(OVERTIME_TABLE);
        }
        for (String[] index : INDEXES) {
            if (!hasIndex(connection, index[0])) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(index[1]);
                }
            }
        }
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_RESOURCE));
    }

    /**
     * H2 的索引在元数据里挂在表上，MySQL 挂在 catalog 上；按索引名全局找一遍即可
     * （索引名全库唯一，与 V130 的按表查法等价且少一次表名传参）。
     */
    private boolean hasIndex(Connection connection, String indexName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String[] tables = {"attendance_shift", "attendance_group", "attendance_group_member",
                "attendance_holiday", "attendance_punch", "attendance_leave_type",
                "attendance_leave_ledger", "attendance_leave", "attendance_overtime"};
        for (String table : tables) {
            try (ResultSet indexes = metaData.getIndexInfo(
                    connection.getCatalog(), null, table, false, false)) {
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name != null && name.equalsIgnoreCase(indexName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
