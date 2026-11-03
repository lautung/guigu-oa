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
import java.util.List;

/**
 * 通知公告（ADR 0055，#218）：三张表 + 索引 + 菜单页/按钮点 + super_admin 补授。
 *
 * <p>为什么是 Java 迁移：建表用 {@code CREATE TABLE IF NOT EXISTS} 已经幂等，但 MySQL 8
 * 不支持 {@code CREATE INDEX IF NOT EXISTS}，H2/MySQL 两边重复执行时索引会炸；这里先查
 * 元数据再建（与 V110 同一纪律）。菜单种子是纯 DML，放
 * {@code db/seed/notice_menu_v116.sql}，全部由 {@code NOT EXISTS} 守住。
 */
public class V116__NoticeSchemaAndMenu extends BaseJavaMigration {

    private static final String SEED_RESOURCE = "db/seed/notice_menu_v116.sql";

    private static final List<String> TABLES = List.of(
            """
            CREATE TABLE IF NOT EXISTS oa_notice (
              id BIGINT NOT NULL AUTO_INCREMENT,
              title VARCHAR(100) NOT NULL,
              type TINYINT NOT NULL DEFAULT 1,
              content MEDIUMTEXT,
              status TINYINT NOT NULL DEFAULT 0,
              publisher_id BIGINT DEFAULT NULL,
              publisher_name VARCHAR(50) DEFAULT NULL,
              publish_time DATETIME DEFAULT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS oa_notice_read (
              id BIGINT NOT NULL AUTO_INCREMENT,
              notice_id BIGINT NOT NULL,
              user_id BIGINT NOT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              is_deleted TINYINT NOT NULL DEFAULT 0,
              PRIMARY KEY (id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS oa_notice_image (
              id BIGINT NOT NULL AUTO_INCREMENT,
              notice_id BIGINT DEFAULT NULL,
              uploader_id BIGINT NOT NULL,
              object_key VARCHAR(255) NOT NULL,
              content_type VARCHAR(100) NOT NULL,
              size BIGINT NOT NULL,
              create_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              update_time DATETIME DEFAULT (CURRENT_TIMESTAMP),
              PRIMARY KEY (id)
            )
            """);

    private record IndexDefinition(String name, String table, String columns) {
    }

    private static final List<IndexDefinition> INDEXES = List.of(
            // feed / unread：已发布未删除按 publish_time DESC, id DESC 分页
            new IndexDefinition("idx_oa_notice_feed", "oa_notice", "status, is_deleted, publish_time, id"),
            // 管理列表：create_time DESC, id DESC
            new IndexDefinition("idx_oa_notice_create_time", "oa_notice", "create_time, id"),
            new IndexDefinition("uk_oa_notice_read_notice_user", "oa_notice_read", "notice_id, user_id"),
            new IndexDefinition("idx_oa_notice_read_user", "oa_notice_read", "user_id, is_deleted"),
            // 绑定重算（按公告找行）与孤儿清理（notice_id 空 + update_time 截止）
            new IndexDefinition("idx_oa_notice_image_notice", "oa_notice_image", "notice_id"),
            new IndexDefinition("idx_oa_notice_image_orphan", "oa_notice_image", "notice_id, update_time"),
            new IndexDefinition("idx_oa_notice_image_uploader", "oa_notice_image", "uploader_id"));

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        for (String ddl : TABLES) {
            execute(connection, ddl);
        }
        for (IndexDefinition index : INDEXES) {
            if (!hasIndex(connection, index.table(), index.name())) {
                String unique = index.name().startsWith("uk_") ? "UNIQUE " : "";
                execute(connection, "CREATE " + unique + "INDEX " + index.name()
                        + " ON " + index.table() + " (" + index.columns() + ")");
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

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
