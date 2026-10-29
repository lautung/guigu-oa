package com.atguigu.demo;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoMigrationContractTest {

    private static final String URL = "jdbc:h2:mem:guigu_oa_demo_migration;DB_CLOSE_DELAY=-1;"
            + "DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;"
            + "INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP";

    @Test
    void demoMigrationsSeedTheApprovalTemplateUnpublishedAndLeavePublishingToTheSeeder() throws SQLException {
        Flyway.configure()
                .dataSource(URL, "sa", "")
                .locations("classpath:db/migration", "classpath:db/demo-migration")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(URL, "sa", "");
             Statement statement = connection.createStatement()) {
            assertEquals(0, intValue(statement.executeQuery(
                    "SELECT status FROM oa_process_template WHERE process_definition_key = 'demoApproval' "
                            + "AND is_deleted = 0")));
            assertEquals(0, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_process_template_publication")));
            assertEquals(3, intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM sys_user WHERE username IN ('alice', 'bob', 'carol') AND is_deleted = 0")));
            assertTrue(intValue(statement.executeQuery(
                    "SELECT COUNT(*) FROM oa_process_cc_rule r JOIN sys_user u ON u.id = r.user_id "
                            + "WHERE r.process_definition_key = 'demoApproval' AND u.username = 'carol' "
                            + "AND r.is_deleted = 0")) > 0);
        }
    }

    private static int intValue(ResultSet result) throws SQLException {
        result.next();
        return result.getInt(1);
    }
}
