package com.lautung.oa.attendance;

import db.migration.V140__AttendancePunchQueryIndex;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.lautung.oa.auth.LoginAuthContractTest.InMemoryRedisConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #279 的迁移合同：V140 在 H2 上真迁移建出打卡记录查询索引
 * {@code idx_attendance_punch_date_source (punch_date, source)}，重复执行幂等。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_punch_migration;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Import(InMemoryRedisConfig.class)
class AttendancePunchMigrationContractTest {

    private static final String INDEX_NAME = "idx_attendance_punch_date_source";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void v140AddsPunchDateSourceIndexIdempotently() throws Exception {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history "
                + "WHERE version = '140' AND success = TRUE"));

        Set<String> columns = indexColumns(INDEX_NAME);
        assertTrue(columns.contains("PUNCH_DATE"), "索引缺 punch_date 列: " + columns);
        assertTrue(columns.contains("SOURCE"), "索引缺 source 列: " + columns);

        int indexes = count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE index_name = '" + INDEX_NAME + "'");
        try (Connection connection = dataSource.getConnection()) {
            Context context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V140__AttendancePunchQueryIndex().migrate(context);
            new V140__AttendancePunchQueryIndex().migrate(context);
        }
        assertEquals(indexes, count("SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE index_name = '" + INDEX_NAME + "'"));
    }

    private Set<String> indexColumns(String indexName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet indexes = metaData.getIndexInfo(
                    connection.getCatalog(), null, "attendance_punch", false, false)) {
                while (indexes.next()) {
                    String name = indexes.getString("INDEX_NAME");
                    if (name != null && name.equalsIgnoreCase(indexName)) {
                        columns.add(indexes.getString("COLUMN_NAME").toUpperCase());
                    }
                }
            }
        }
        return columns;
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
