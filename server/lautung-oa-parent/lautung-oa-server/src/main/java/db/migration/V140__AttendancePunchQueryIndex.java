package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 打卡记录页查询索引（#279，ADR 0073）：后台打卡记录按日期区间与来源筛选
 * （{@code punch_date BETWEEN ? AND ? AND source = ?}），V139 只有单列
 * {@code idx_attendance_punch_date}；补一条 {@code (punch_date, source)} 复合索引。
 *
 * <p>沿用仓库纪律：索引单独 {@code CREATE INDEX}（MySQL 8 与 H2 都支持，不用
 * {@code ALTER TABLE ... ADD INDEX}）；先查元数据再建，迁移重复执行幂等。
 */
public class V140__AttendancePunchQueryIndex extends BaseJavaMigration {

    private static final String TABLE = "attendance_punch";
    private static final String INDEX_NAME = "idx_attendance_punch_date_source";
    private static final String CREATE_INDEX = "CREATE INDEX " + INDEX_NAME
            + " ON " + TABLE + " (punch_date, source)";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasIndex(connection)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_INDEX);
            }
        }
    }

    private boolean hasIndex(Connection connection) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(
                connection.getCatalog(), null, TABLE, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && name.equalsIgnoreCase(INDEX_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }
}
