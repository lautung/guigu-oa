package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 登录日志的事件维度（#253，ADR 0062）：{@code sys_login_log} 加 {@code event VARCHAR(20) NOT NULL
 * DEFAULT 'login'}，既有的登录行与现有五参数写入路径都落在 {@code login}；注册写 {@code register}。
 *
 * <p>MySQL 8 没有 {@code ADD COLUMN IF NOT EXISTS}，H2/MySQL 合同测试又会重放迁移，所以先查
 * {@link DatabaseMetaData} 再决定是否加列（与 V110/V127 同一纪律）。默认值本身会回填既有行，
 * 这里再做一次 NULL/空串兜底，保证手工造的老库也不会留下没有事件维度的行。
 */
public class V131__SysLoginLogEvent extends BaseJavaMigration {

    private static final String TABLE = "sys_login_log";
    private static final String COLUMN = "event";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, TABLE, COLUMN)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + COLUMN
                        + " VARCHAR(20) NOT NULL DEFAULT 'login'");
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE " + TABLE + " SET " + COLUMN + " = 'login'"
                    + " WHERE " + COLUMN + " IS NULL OR " + COLUMN + " = ''");
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, null, null)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
