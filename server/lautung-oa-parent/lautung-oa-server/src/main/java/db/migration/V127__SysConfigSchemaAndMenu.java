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
 * 参数设置（#252，ADR 0062）：{@code sys_config} 表 + 唯一键 + 页面/按钮种子 +
 * 首批注册开关 {@code sys.account.registerUser=false}。
 *
 * <p>建表用 {@code CREATE TABLE IF NOT EXISTS} 已是幂等；MySQL 8 不支持
 * {@code CREATE INDEX IF NOT EXISTS}，唯一索引先查元数据再建（与 V110/V116 同一纪律）。
 * 菜单/按钮/参数行是纯 DML，放 {@code db/seed/sys_config_menu_v127.sql}，全部由
 * {@code NOT EXISTS} 守住。
 */
public class V127__SysConfigSchemaAndMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/sys_config_menu_v127.sql";

    private static final String TABLE = """
            CREATE TABLE IF NOT EXISTS sys_config (
              id BIGINT NOT NULL AUTO_INCREMENT,
              config_key VARCHAR(100) NOT NULL,
              config_value VARCHAR(500) DEFAULT NULL,
              config_name VARCHAR(100) NOT NULL,
              value_type VARCHAR(20) NOT NULL DEFAULT 'string',
              builtin TINYINT NOT NULL DEFAULT 0,
              remark VARCHAR(500) DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """;

    private static final String UNIQUE_INDEX = "uk_sys_config_key";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute(TABLE);
        }
        if (!hasIndex(connection, "sys_config", UNIQUE_INDEX)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE UNIQUE INDEX " + UNIQUE_INDEX
                        + " ON sys_config (config_key)");
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
