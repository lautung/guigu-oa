package com.atguigu.process;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessEngineCutoverTest {

    @Test
    public void currentTasksTreatUnknownProcessInstanceIdAsEmpty() {
        ProcessEngine engine = inMemoryEngine("jdbc:h2:mem:cutover-orphan-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try {
            assertTrue(ProcessEngineApproval.currentTasks(
                    engine.getTaskService(), "pre-cutover-instance-id").isEmpty());
        } finally {
            engine.close();
        }
    }

    @Test
    public void dropEngineTablesThenRestartRebuildsSchemaWithEmptyPendingAndProcessed() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:cutover-drop-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        ProcessEngine before = inMemoryEngine(jdbcUrl);
        try {
            deployTwoStepZip(before);
            before.getRuntimeService().startProcessInstanceByKey("approvalTwoStep", "12");
            Task first = ProcessEngineApproval.pendingTasks(before.getTaskService(), "zhangsan", 0, 10).get(0);
            ProcessEngineApproval.complete(before.getTaskService(), first.getId());
            assertEquals(1, ProcessEngineApproval.processedTasks(
                    before.getHistoryService(), "zhangsan", 0, 10).size());
        } finally {
            before.close();
        }

        List<String> dropped = ProcessCutoverSupport.engineTablesToDrop(jdbcTableNames(jdbcUrl));
        assertFalse(dropped.isEmpty());
        jdbcDropEngineTables(jdbcUrl, dropped);
        assertTrue(ProcessCutoverSupport.engineTablesToDrop(jdbcTableNames(jdbcUrl)).isEmpty());

        ProcessEngine after = inMemoryEngine(jdbcUrl);
        try {
            List<String> rebuilt = ProcessCutoverSupport.engineTablesToDrop(jdbcTableNames(jdbcUrl));
            assertTrue(rebuilt.stream().anyMatch(name -> name.toUpperCase(Locale.ROOT).startsWith("ACT_")));
            assertTrue(rebuilt.stream().anyMatch(name -> name.toUpperCase(Locale.ROOT).startsWith("FLW_")));
            assertTrue(ProcessEngineApproval.pendingTasks(
                    after.getTaskService(), "zhangsan", 0, 10).isEmpty());
            assertEquals(0L, ProcessEngineApproval.pendingCount(after.getTaskService(), "zhangsan"));
            assertTrue(ProcessEngineApproval.processedTasks(
                    after.getHistoryService(), "zhangsan", 0, 10).isEmpty());
            assertEquals(0L, ProcessEngineApproval.processedCount(after.getHistoryService(), "zhangsan"));
        } finally {
            after.close();
        }
    }

    @Test
    public void afterZipRedeployNewInstanceCanFlowAndProcessedOnlyShowsPostCutoverHistory() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:cutover-history-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        ProcessEngine before = inMemoryEngine(jdbcUrl);
        try {
            deployTwoStepZip(before);
            ProcessInstance oldInstance = before.getRuntimeService()
                    .startProcessInstanceByKey("approvalTwoStep", "12");
            Task oldFirst = ProcessEngineApproval.pendingTasks(before.getTaskService(), "zhangsan", 0, 10).get(0);
            ProcessEngineApproval.complete(before.getTaskService(), oldFirst.getId());
            Task oldSecond = ProcessEngineApproval.pendingTasks(before.getTaskService(), "lisi", 0, 10).get(0);
            ProcessEngineApproval.complete(before.getTaskService(), oldSecond.getId());
            assertNotNull(oldInstance.getId());
        } finally {
            before.close();
        }

        jdbcDropEngineTables(jdbcUrl, ProcessCutoverSupport.engineTablesToDrop(jdbcTableNames(jdbcUrl)));

        ProcessEngine after = inMemoryEngine(jdbcUrl);
        try {
            deployTwoStepZip(after);
            ProcessInstance started = after.getRuntimeService()
                    .startProcessInstanceByKey("approvalTwoStep", "99");
            assertNotNull(started.getId());

            Task first = ProcessEngineApproval.pendingTasks(after.getTaskService(), "zhangsan", 0, 10).get(0);
            ProcessEngineApproval.complete(after.getTaskService(), first.getId());
            Task second = ProcessEngineApproval.pendingTasks(after.getTaskService(), "lisi", 0, 10).get(0);
            ProcessEngineApproval.complete(after.getTaskService(), second.getId());

            assertTrue(ProcessEngineApproval.pendingTasks(
                    after.getTaskService(), "zhangsan", 0, 10).isEmpty());
            assertTrue(ProcessEngineApproval.pendingTasks(
                    after.getTaskService(), "lisi", 0, 10).isEmpty());
            assertEquals(1, ProcessEngineApproval.processedTasks(
                    after.getHistoryService(), "zhangsan", 0, 10).size());
            assertEquals(1, ProcessEngineApproval.processedTasks(
                    after.getHistoryService(), "lisi", 0, 10).size());
            assertEquals(started.getId(), ProcessEngineApproval.processedTasks(
                    after.getHistoryService(), "zhangsan", 0, 10).get(0).getProcessInstanceId());
        } finally {
            after.close();
        }
    }

    private static ProcessEngine inMemoryEngine(String jdbcUrl) {
        return ProcessEngineConfiguration
                .createStandaloneInMemProcessEngineConfiguration()
                .setHistory("full")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setJdbcUrl(jdbcUrl)
                .buildProcessEngine();
    }

    private static void deployTwoStepZip(ProcessEngine engine) throws IOException {
        byte[] zipBytes = zipClasspathResource("process/approval-two-step.bpmn20.xml");
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            engine.getRepositoryService()
                    .createDeployment()
                    .addZipInputStream(zipInputStream)
                    .name("processes/approval-two-step.zip")
                    .deploy();
        }
    }

    private static byte[] zipClasspathResource(String resource) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream input = ProcessEngineCutoverTest.class.getClassLoader().getResourceAsStream(resource);
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            assertNotNull(input);
            zip.putNextEntry(new ZipEntry(resource.substring(resource.lastIndexOf('/') + 1)));
            input.transferTo(zip);
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    private static List<String> jdbcTableNames(String jdbcUrl) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_TYPE='BASE TABLE'")) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1));
            }
        }
        return names;
    }

    private static void jdbcDropEngineTables(String jdbcUrl, List<String> tableNames) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String tableName : tableNames) {
                statement.execute("DROP TABLE IF EXISTS \"" + tableName.replace("\"", "") + "\" CASCADE");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
