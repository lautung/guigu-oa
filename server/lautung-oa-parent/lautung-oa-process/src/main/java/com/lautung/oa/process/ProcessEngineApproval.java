package com.lautung.oa.process;

import com.lautung.oa.framework.page.PageWindow;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Flowable 8 待处理查询与审批完成动作。已处理列表的读证据不在这里（#186，ADR 0049：
 * 已处理按账本动作记录供给），成员资格的历史处理人也改由账本供给（#288，ADR 0068 修订
 * ADR 0011）——引擎历史不再是成员资格证据。
 */
public final class ProcessEngineApproval {

    private ProcessEngineApproval() {
    }

    public static List<Task> pendingTasks(TaskService taskService, String username, int firstResult, int maxResults) {
        // 待处理第一/二源（#288，ADR 0068 修订 ADR 0045）：assignee 或候选人之一。
        // 主排序=任务创建时间倒序；同刻由任务 id 决胜，保证翻页稳定（tiebreaker 只求确定，不另设语义）。
        return taskService.createTaskQuery()
                .taskCandidateOrAssigned(username)
                .orderByTaskCreateTime()
                .desc()
                .orderByTaskId()
                .desc()
                .listPage(firstResult, maxResults);
    }

    public static long pendingCount(TaskService taskService, String username) {
        return taskService.createTaskQuery()
                .taskCandidateOrAssigned(username)
                .count();
    }

    /**
     * 按实例 id 集合一次取回 businessKey（#181 消除逐任务查询）：实例缺失或无业务键的
     * 不进映射，调用方按 null 处理。Flowable 8 的集合查询有数量上限，这里上限取一页窗口
     * （{@value PageWindow#MAX_LIMIT}，调用方只传整页回查集合），超限显式拒绝，
     * 不把「集合不大于页」留在注释级假设里。
     */
    public static Map<String, String> businessKeys(
            RuntimeService runtimeService, Set<String> processInstanceIds) {
        if (processInstanceIds == null || processInstanceIds.isEmpty()) {
            return Map.of();
        }
        if (processInstanceIds.size() > PageWindow.MAX_LIMIT) {
            throw new IllegalArgumentException(
                    "实例 id 集合大小 " + processInstanceIds.size()
                            + " 超出批量取回上限 " + PageWindow.MAX_LIMIT
                            + "（PageWindow.MAX_LIMIT，即一页窗口）：调用方只应传单页回查集合");
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list().stream()
                .filter(instance -> StringUtils.hasText(instance.getBusinessKey()))
                .collect(Collectors.toMap(
                        ProcessInstance::getId, ProcessInstance::getBusinessKey, (first, second) -> first));
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

    /**
     * 处理动作本身认领（ADR 0069）：任务尚无 assignee 时把当前处理人写为 assignee，
     * 已有 assignee（含并发对手刚认领）时不动——认领冲突统一在完成动作上显式失败。
     */
    public static void claim(TaskService taskService, String taskId, String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task != null && !StringUtils.hasText(task.getAssignee())) {
            taskService.claim(taskId, username);
        }
    }

    /** 任务的候选用户（ADR 0069 多人来源）；没有候选返回空列表。 */
    public static List<String> candidateUsers(TaskService taskService, String taskId) {
        return taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .map(IdentityLink::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    /** 撤回的引擎命令（ADR 0068）：删除运行时实例，历史明确落 {@code STATE_=cancelled}。 */
    public static void cancel(RuntimeService runtimeService, String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    /**
     * 转办的引擎命令（#288，ADR 0068）：任务永久改派给目标用户。转办人自己的候选身份
     * 一并移除——否则转办经手人会借候选身份继续出现在待处理并可处理，违背「只读」。
     * 其它候选人不动（他们从未处理，候选资格是节点配置）。引擎侧 setAssignee 会覆盖
     * 引擎历史的 ASSIGNEE_，这正是成员资格历史处理人改由账本供给的原因（ADR 0068）。
     */
    public static void reassign(
            TaskService taskService, String taskId, String fromUsername, String toUsername) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new FlowableObjectNotFoundException("无法找到任务 " + taskId, Task.class);
        }
        taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()))
                .filter(link -> fromUsername != null && fromUsername.equals(link.getUserId()))
                .forEach(link -> taskService.deleteUserIdentityLink(taskId, fromUsername, IdentityLinkType.CANDIDATE));
        taskService.setAssignee(taskId, toUsername);
    }

    public static List<Task> currentTasks(TaskService taskService, String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return Collections.emptyList();
        }
        return taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    }

    public static void rejectToEnd(RepositoryService repositoryService, TaskService taskService, String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            // 任务已被并发对手处理掉：显式命名失败，绝不在共享代码里 NPE。
            throw new FlowableObjectNotFoundException("无法找到任务 " + taskId, Task.class);
        }
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
