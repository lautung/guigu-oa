package com.atguigu.process;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Flowable 8 待处理 / 已处理查询与审批完成动作。
 */
public final class ProcessEngineApproval {

    private ProcessEngineApproval() {
    }

    public static List<Task> pendingTasks(TaskService taskService, String username, int firstResult, int maxResults) {
        return taskService.createTaskQuery()
                .taskAssignee(username)
                .orderByTaskCreateTime()
                .desc()
                .listPage(firstResult, maxResults);
    }

    public static long pendingCount(TaskService taskService, String username) {
        return taskService.createTaskQuery()
                .taskAssignee(username)
                .count();
    }

    public static List<HistoricTaskInstance> processedTasks(
            HistoryService historyService, String username, int firstResult, int maxResults) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(username)
                .finished()
                .orderByTaskCreateTime()
                .desc()
                .listPage(firstResult, maxResults);
    }

    public static long processedCount(HistoryService historyService, String username) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(username)
                .finished()
                .count();
    }

    public static String start(
            RuntimeService runtimeService,
            String processDefinitionId,
            String businessKey,
            Map<String, Object> variables) {
        return runtimeService.startProcessInstanceById(processDefinitionId, businessKey, variables).getId();
    }

    public static void complete(TaskService taskService, String taskId) {
        taskService.complete(taskId);
    }

    public static List<Task> currentTasks(TaskService taskService, String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return Collections.emptyList();
        }
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    }

    public static void rejectToEnd(RepositoryService repositoryService, TaskService taskService, String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        BpmnModel bpmnModel = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        List<EndEvent> endEventList = bpmnModel.getMainProcess().findFlowElementsOfType(EndEvent.class);
        if (CollectionUtils.isEmpty(endEventList)) {
            return;
        }
        FlowNode endFlowNode = endEventList.get(0);
        FlowNode currentFlowNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(task.getTaskDefinitionKey());

        List<SequenceFlow> originalOutgoing = new ArrayList<>(currentFlowNode.getOutgoingFlows());
        currentFlowNode.getOutgoingFlows().clear();

        SequenceFlow toEnd = new SequenceFlow();
        toEnd.setId("rejectToEnd");
        toEnd.setSourceFlowElement(currentFlowNode);
        toEnd.setTargetFlowElement(endFlowNode);
        List<SequenceFlow> redirected = new ArrayList<>();
        redirected.add(toEnd);
        currentFlowNode.setOutgoingFlows(redirected);

        try {
            taskService.complete(task.getId());
        } finally {
            currentFlowNode.setOutgoingFlows(originalOutgoing);
        }
    }
}
