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
 * 字典管理（#251，ADR 0062）：{@code sys_dict_type} / {@code sys_dict_data} 两张表 +
 * 唯一索引 + 页面/按钮种子 + 首批四组字典数据（公告类型/公告状态/定时任务状态/登录日志状态）。
 *
 * <p>建表用 {@code CREATE TABLE IF NOT EXISTS} 已是幂等；MySQL 8 不支持
 * {@code CREATE INDEX IF NOT EXISTS}，唯一索引先查元数据再建（与 V110/V116/V127 同一纪律）。
 * 菜单/按钮/字典行是纯 DML，放 {@code db/seed/sys_dict_menu_v130.sql}，全部由
 * {@code NOT EXISTS} 守住。
 */
public class V130__SysDictSchemaAndMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/sys_dict_menu_v130.sql";

    private static final String TYPE_TABLE = """
            CREATE TABLE IF NOT EXISTS sys_dict_type (
              id BIGINT NOT NULL AUTO_INCREMENT,
              dict_name VARCHAR(100) NOT NULL,
              dict_type VARCHAR(100) NOT NULL,
              status TINYINT NOT NULL DEFAULT 1,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String DATA_TABLE = """
            CREATE TABLE IF NOT EXISTS sys_dict_data (
              id BIGINT NOT NULL AUTO_INCREMENT,
              dict_type VARCHAR(100) NOT NULL,
              dict_label VARCHAR(100) NOT NULL,
              dict_value VARCHAR(100) NOT NULL,
              sort_value INT NOT NULL DEFAULT 0,
              status TINYINT NOT NULL DEFAULT 1,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String TYPE_NAME_INDEX = "uk_sys_dict_type_name";

    private static final String TYPE_CODE_INDEX = "uk_sys_dict_type_type";

    private static final String DATA_VALUE_INDEX = "uk_sys_dict_data_type_value";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute(TYPE_TABLE);
            statement.execute(DATA_TABLE);
        }
        if (!hasIndex(connection, "sys_dict_type", TYPE_NAME_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE UNIQUE INDEX " + TYPE_NAME_INDEX
                        + " ON sys_dict_type (dict_name)");
            }
        }
        if (!hasIndex(connection, "sys_dict_type", TYPE_CODE_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE UNIQUE INDEX " + TYPE_CODE_INDEX
                        + " ON sys_dict_type (dict_type)");
            }
        }
        if (!hasIndex(connection, "sys_dict_data", DATA_VALUE_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE UNIQUE INDEX " + DATA_VALUE_INDEX
                        + " ON sys_dict_data (dict_type, dict_value)");
            }
        }
        ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_RESOURCE));
    }

    private boolean hasIndex(Connection connection, String table, String index) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(
                connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                String name = indexes.getString("INDEX_NAME");
                if (name != null && name.equalsIgnoreCase(index)) {
                    return true;
                }
            }
        }
        return false;
    }
}
