package com.atguigu.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_flyway_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
class FlywayMigrationContractTest {

    private static final List<String> BUSINESS_TABLES = List.of(
            "sys_user",
            "sys_role",
            "sys_menu",
            "sys_role_menu",
            "sys_user_role",
            "oa_process_type",
            "oa_process_template",
            "oa_process",
            "oa_process_record");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesBusinessSchemaAndSeedsWhileFlowableCreatesEngineSchema() throws SQLException {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = 'admin' AND id = 1"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_user WHERE username = 'user'"));
        assertEquals(1, count("SELECT COUNT(*) FROM sys_menu WHERE path = '/system' AND parent_id = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM sys_menu WHERE type = 2"));
        assertEquals(1, count("SELECT COUNT(*) FROM oa_process_template WHERE name = '请假申请' AND status = 1"));
        assertEquals(0, count("SELECT COUNT(*) FROM oa_process_template WHERE name LIKE '%草稿%'"));

        List<String> tableNames = tableNames();
        BUSINESS_TABLES.forEach(table -> assertTrue(
                tableNames.stream().anyMatch(table::equalsIgnoreCase),
                "Flyway should create business table " + table));
        assertTrue(tableNames.stream().anyMatch("ACT_GE_PROPERTY"::equalsIgnoreCase),
                "Flowable should create its engine tables independently of Flyway");
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private List<String> tableNames() throws SQLException {
        List<String> names = new ArrayList<>();
        try (var connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (tableName != null) {
                    names.add(tableName.toUpperCase(Locale.ROOT));
                }
            }
        }
        return names;
    }
}
