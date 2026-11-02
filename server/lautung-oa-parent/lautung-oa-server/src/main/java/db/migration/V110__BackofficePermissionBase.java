package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * 权限底座（ADR 0056，#221）：组织表、{@code sys_role.data_scope}、菜单 0/1/2、
 * 审批两棵树迁入系统管理、现有 6 页按钮点与 super_admin 种子。
 *
 * <p>为什么是 Java 迁移而不是 SQL：MySQL 8 与 H2 都不支持可移植的
 * {@code ADD/DROP COLUMN IF [NOT] EXISTS}，而这张票要求空库与既有库重复执行都不重复写。
 * 建表用 {@code CREATE TABLE IF NOT EXISTS}；加列/删列先查元数据；菜单行的移动靠
 * {@code WHERE 旧值} 判定，第二次执行自然不再命中；按钮/角色/授予种子在
 * {@code db/seed/backoffice_permission_v110.sql} 里用 {@code WHERE NOT EXISTS} 保持幂等。
 */
public class V110__BackofficePermissionBase extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/backoffice_permission_v110.sql";

    private static final List<String> TABLES = List.of(
            """
            CREATE TABLE IF NOT EXISTS sys_dept (
              id BIGINT NOT NULL AUTO_INCREMENT,
              parent_id BIGINT DEFAULT '0',
              ancestors VARCHAR(500) DEFAULT NULL,
              name VARCHAR(50) DEFAULT NULL,
              leader VARCHAR(50) DEFAULT NULL,
              sort_value INT DEFAULT NULL,
              status TINYINT DEFAULT '1',
              create_time DATETIME DEFAULT NULL,
              update_time DATETIME DEFAULT NULL,
              is_deleted TINYINT NOT NULL DEFAULT '0',
              PRIMARY KEY (id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sys_post (
              id BIGINT NOT NULL AUTO_INCREMENT,
              post_code VARCHAR(50) DEFAULT NULL,
              name VARCHAR(50) DEFAULT NULL,
              sort_value INT DEFAULT NULL,
              status TINYINT DEFAULT '1',
              create_time DATETIME DEFAULT NULL,
              update_time DATETIME DEFAULT NULL,
              is_deleted TINYINT NOT NULL DEFAULT '0',
              PRIMARY KEY (id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sys_user_post (
              id BIGINT NOT NULL AUTO_INCREMENT,
              user_id BIGINT DEFAULT NULL,
              post_id BIGINT DEFAULT NULL,
              create_time DATETIME DEFAULT NULL,
              update_time DATETIME DEFAULT NULL,
              is_deleted TINYINT NOT NULL DEFAULT '0',
              PRIMARY KEY (id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sys_role_dept (
              id BIGINT NOT NULL AUTO_INCREMENT,
              role_id BIGINT DEFAULT NULL,
              dept_id BIGINT DEFAULT NULL,
              create_time DATETIME DEFAULT NULL,
              update_time DATETIME DEFAULT NULL,
              is_deleted TINYINT NOT NULL DEFAULT '0',
              PRIMARY KEY (id)
            )
            """);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addColumnIfMissing(connection, "sys_role", "data_scope", "TINYINT DEFAULT 5");
        dropColumnIfExists(connection, "sys_user", "post_id");
        for (String ddl : TABLES) {
            execute(connection, ddl);
        }
        migrateProcessMenus(connection);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_RESOURCE));
    }

    private void migrateProcessMenus(Connection connection) throws SQLException {
        Long systemRootId = selectId(connection,
                "SELECT id FROM sys_menu WHERE path = '/system' AND parent_id = 0 AND is_deleted = 0");
        if (systemRootId != null) {
            update(connection,
                    "UPDATE sys_menu SET parent_id = ?, path = 'processSet', name = '审批设置', sort_value = 9 "
                            + "WHERE path = '/processSet' AND parent_id = 0 AND is_deleted = 0",
                    systemRootId);
            update(connection,
                    "UPDATE sys_menu SET parent_id = ?, path = 'processMgr', name = '审批管理', sort_value = 10 "
                            + "WHERE path = '/processMgr' AND parent_id = 0 AND is_deleted = 0",
                    systemRootId);
        }

        Long processSetId = selectId(connection,
                "SELECT id FROM sys_menu WHERE path = 'processSet' AND is_deleted = 0");
        if (processSetId != null) {
            update(connection,
                    "UPDATE sys_menu SET path = '/system/processSet/processType' "
                            + "WHERE parent_id = ? AND path = 'processType' AND is_deleted = 0",
                    processSetId);
            update(connection,
                    "UPDATE sys_menu SET path = '/system/processSet/processTemplate' "
                            + "WHERE parent_id = ? AND path = 'processTemplate' AND is_deleted = 0",
                    processSetId);
        }

        Long processTemplateId = selectId(connection,
                "SELECT id FROM sys_menu WHERE path = '/system/processSet/processTemplate' AND is_deleted = 0");
        if (processTemplateId != null) {
            update(connection,
                    "UPDATE sys_menu SET path = '/system/processSet/templateSet' "
                            + "WHERE parent_id = ? AND path = 'templateSet' AND is_deleted = 0",
                    processTemplateId);
        }

        Long processMgrId = selectId(connection,
                "SELECT id FROM sys_menu WHERE path = 'processMgr' AND is_deleted = 0");
        if (processMgrId != null) {
            update(connection,
                    "UPDATE sys_menu SET path = '/system/processMgr/process' "
                            + "WHERE parent_id = ? AND path = 'process' AND is_deleted = 0",
                    processMgrId);
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

    private Long selectId(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getLong(1) : null;
        }
    }

    private void update(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
