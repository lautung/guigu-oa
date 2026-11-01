package com.atguigu.process;

import com.atguigu.common.page.PageWindow;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessEngineApprovalTest {

    private ProcessEngine processEngine;

    @BeforeEach
    public void startEngine() {
        processEngine = ProcessEngineConfiguration
                .createStandaloneInMemProcessEngineConfiguration()
                .setHistory("full")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setJdbcUrl("jdbc:h2:mem:approval-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1")
                .buildProcessEngine();
        processEngine.getRepositoryService()
                .createDeployment()
                .addClasspathResource("process/approval-two-step.bpmn20.xml")
                .deploy();
    }

    @AfterEach
    public void stopEngine() {
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    public void pendingQueryReturnsTaskAssignedToCurrentUser() {
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey("approvalTwoStep", "12");

        List<Task> zhangsan = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10);
        List<Task> lisi = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "lisi", 0, 10);

        assertEquals(1, zhangsan.size());
        assertEquals(instance.getId(), zhangsan.get(0).getProcessInstanceId());
        assertEquals("12", processEngine.getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId(zhangsan.get(0).getProcessInstanceId())
                .singleResult()
                .getBusinessKey());
        assertTrue(lisi.isEmpty());
        assertEquals(1L, ProcessEngineApproval.pendingCount(processEngine.getTaskService(), "zhangsan"));
    }

    @Test
    public void completeMovesTaskFromPendingToNextAssignee() {
        processEngine.getRuntimeService().startProcessInstanceByKey("approvalTwoStep", "12");
        Task pending = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).get(0);

        ProcessEngineApproval.complete(processEngine.getTaskService(), pending.getId());

        assertTrue(ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).isEmpty());
        assertEquals(1, ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "lisi", 0, 10).size());
    }

    @Test
    public void rejectRewiresCurrentNodeToEndAndLeavesLaterInstancesIntact() {
        ProcessInstance rejected = processEngine.getRuntimeService()
                .startProcessInstanceByKey("approvalTwoStep", "12");
        Task first = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).get(0);

        ProcessEngineApproval.rejectToEnd(
                processEngine.getRepositoryService(),
                processEngine.getTaskService(),
                first.getId());

        assertTrue(ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).isEmpty());
        assertTrue(ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "lisi", 0, 10).isEmpty());
        assertNotNull(processEngine.getHistoryService()
                .createHistoricProcessInstanceQuery()
                .processInstanceId(rejected.getId())
                .singleResult()
                .getEndTime());

        processEngine.getRuntimeService().startProcessInstanceByKey("approvalTwoStep", "13");
        Task nextFirst = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).get(0);
        ProcessEngineApproval.complete(processEngine.getTaskService(), nextFirst.getId());
        assertEquals(1, ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "lisi", 0, 10).size());
    }

    @Test
    public void currentTasksTreatBlankProcessInstanceIdAsEmpty() {
        assertTrue(ProcessEngineApproval.currentTasks(processEngine.getTaskService(), null).isEmpty());
        assertTrue(ProcessEngineApproval.currentTasks(processEngine.getTaskService(), "").isEmpty());
    }

    @Test
    public void businessKeysResolveRunningInstancesInOneBatch() {
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey("approvalTwoStep", "12");

        Map<String, String> keys = ProcessEngineApproval.businessKeys(
                processEngine.getRuntimeService(),
                Set.of(instance.getId(), "missing"));

        assertEquals(Map.of(instance.getId(), "12"), keys,
                "存在的实例按 id 映射 businessKey，缺失实例不进映射");
        assertTrue(ProcessEngineApproval.businessKeys(
                processEngine.getRuntimeService(), Set.of()).isEmpty());
        assertTrue(ProcessEngineApproval.businessKeys(processEngine.getRuntimeService(), null).isEmpty());
    }

    @Test
    public void businessKeysAcceptsExactlyThePageWindowLimitAndRejectsBeyondIt() {
        Set<String> fullPage = new HashSet<>();
        for (int i = 0; i < (int) PageWindow.MAX_LIMIT; i++) {
            fullPage.add("missing-" + i);
        }

        // 恰好 MAX_LIMIT 放行：整页回查集合是唯一合法调用形态，缺失实例不进映射。
        assertTrue(ProcessEngineApproval.businessKeys(
                processEngine.getRuntimeService(), fullPage).isEmpty(),
                "恰好一页上限的集合必须放行");

        fullPage.add("over-limit");
        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> ProcessEngineApproval.businessKeys(processEngine.getRuntimeService(), fullPage));
        assertTrue(rejected.getMessage().contains(String.valueOf(PageWindow.MAX_LIMIT)),
                "拒绝消息必须说明上限来源（PageWindow.MAX_LIMIT）");
    }

    @Test
    public void historicAssigneeCountsCurrentAndFinishedTasksButNotOtherUsers() {
        ProcessInstance instance = processEngine.getRuntimeService()
                .startProcessInstanceByKey("approvalTwoStep", "13");

        assertTrue(ProcessEngineApproval.hasHistoricAssignee(
                processEngine.getHistoryService(), instance.getId(), "zhangsan"));
        assertFalse(ProcessEngineApproval.hasHistoricAssignee(
                processEngine.getHistoryService(), instance.getId(), "lisi"));

        Task first = ProcessEngineApproval.pendingTasks(
                processEngine.getTaskService(), "zhangsan", 0, 10).get(0);
        ProcessEngineApproval.complete(processEngine.getTaskService(), first.getId());

        assertTrue(ProcessEngineApproval.hasHistoricAssignee(
                processEngine.getHistoryService(), instance.getId(), "zhangsan"));
        assertTrue(ProcessEngineApproval.hasHistoricAssignee(
                processEngine.getHistoryService(), instance.getId(), "lisi"));
        assertFalse(ProcessEngineApproval.hasHistoricAssignee(
                processEngine.getHistoryService(), null, "zhangsan"));
        assertFalse(ProcessEngineApproval.hasHistoricAssignee(
                processEngine.getHistoryService(), instance.getId(), " "));
    }
}
