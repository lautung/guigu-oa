package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 部门负责人升级为用户引用（ADR 0065/0069，#285）：{@code sys_dept} 增
 * {@code leader_user_id}，按 {@code leader} 姓名「唯一匹配启用用户」回填；重名、
 * 无匹配或仅停用同名一律置空并记日志（负责人停用或删除视为无负责人，不猜测）；
 * 随后删除 {@code leader} 列。迁移不阻断启动。
 *
 * <p>为什么是 Java 迁移而不是 SQL：MySQL 8 与 H2 都不支持可移植的
 * {@code ADD/DROP COLUMN IF [NOT] EXISTS}；加列/删列先查元数据（与 V110 同一纪律），
 * 回填与删列都钉在 {@code leader} 列存在上，重放时整段跳过。
 */
public class V134__DeptLeaderUserRef extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V134__DeptLeaderUserRef.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addColumnIfMissing(connection, "sys_dept", "leader_user_id", "BIGINT DEFAULT NULL");
        if (!hasColumn(connection, "sys_dept", "leader")) {
            return;
        }
        backfillLeaderUserIds(connection);
        dropColumnIfExists(connection, "sys_dept", "leader");
    }

    /**
     * 按姓名唯一匹配启用用户回填：命中唯一启用用户才写 {@code leader_user_id}；
     * 重名与无匹配保持为空并记日志（姓名不是身份，宁可置空不可猜）。
     * 已删部门的行不参与回填。
     */
    private void backfillLeaderUserIds(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, leader FROM sys_dept WHERE leader IS NOT NULL AND is_deleted = 0");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                long deptId = rows.getLong(1);
                String leaderName = rows.getString(2);
                Long matchedUserId = matchUniqueEnabledUser(connection, leaderName);
                if (matchedUserId == null) {
                    log.warn("部门负责人迁移置空：姓名无法唯一匹配启用用户 deptId={} leader={}",
                            deptId, leaderName);
                    continue;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE sys_dept SET leader_user_id = ? WHERE id = ?")) {
                    update.setLong(1, matchedUserId);
                    update.setLong(2, deptId);
                    update.executeUpdate();
                }
            }
        }
    }

    private Long matchUniqueEnabledUser(Connection connection, String leaderName) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM sys_user WHERE name = ? AND status = 1 AND is_deleted = 0")) {
            query.setString(1, leaderName);
            try (ResultSet matches = query.executeQuery()) {
                if (!matches.next()) {
                    return null;
                }
                Long only = matches.getLong(1);
                if (matches.next()) {
                    return null;
                }
                return only;
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (!hasColumn(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void dropColumnIfExists(Connection connection, String table, String column) throws SQLException {
        if (hasColumn(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " DROP COLUMN " + column);
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

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
