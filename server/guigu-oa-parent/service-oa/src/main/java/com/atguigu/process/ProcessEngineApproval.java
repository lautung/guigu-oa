package com.atguigu.process;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flowable 8 待处理 / 已处理查询与审批完成动作。
 */
public final class ProcessEngineApproval {

    private ProcessEngineApproval() {
    }

    public static List<Task> pendingTasks(TaskService taskService, String username, int firstResult, int maxResults) {
        // 主排序=任务创建时间倒序；同刻由任务 id 决胜，保证翻页稳定（tiebreaker 只求确定，不另设语义）。
        return taskService.createTaskQuery()
                .taskAssignee(username)
                .orderByTaskCreateTime()
                .desc()
                .orderByTaskId()
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
                .orderByTaskId()
                .desc()
                .listPage(firstResult, maxResults);
    }

    public static long processedCount(HistoryService historyService, String username) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(username)
                .finished()
                .count();
    }

    /**
     * 按实例 id 集合一次取回 businessKey（#181 消除逐任务查询）：实例缺失或无业务键的
     * 不进映射，调用方按 null 处理。Flowable 8 的集合查询有数量上限，这里上限是页大小（≤500）。
     */
    public static Map<String, String> businessKeys(
            RuntimeService runtimeService, Set<String> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return Map.of();
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list().stream()
                .filter(instance -> StringUtils.hasText(instance.getBusinessKey()))
                .collect(Collectors.toMap(
                        ProcessInstance::getId, ProcessInstance::getBusinessKey, (first, second) -> first));
    }

    public static boolean hasHistoricAssignee(
            HistoryService historyService, String processInstanceId, String username) {
        if (!StringUtils.hasText(processInstanceId) || !StringUtils.hasText(username)) {
            return false;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(username)
                .count() > 0;
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
