package com.lautung.oa.process.approval;

import com.lautung.oa.framework.page.PageWindow;
import com.lautung.oa.process.ProcessEngineApproval;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ApprovalEngine 的 Flowable 适配器。写入与读取证据都走 {@link ProcessEngineApproval}，
 * 后者仍是唯一的 Flowable 调用点。
 */
@Component
public class FlowableApprovalEngine implements ApprovalEngine {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;

    public FlowableApprovalEngine(
            RuntimeService runtimeService,
            RepositoryService repositoryService,
            TaskService taskService) {
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.taskService = taskService;
    }

    @Override
    public String start(String processDefinitionId, String businessKey, Map<String, Object> variables) {
        return ProcessEngineApproval.start(runtimeService, processDefinitionId, businessKey, variables);
    }

    @Override
    public void complete(String taskId) {
        ProcessEngineApproval.complete(taskService, taskId);
    }

    @Override
    public void claim(String taskId, String username) {
        ProcessEngineApproval.claim(taskService, taskId, username);
    }

    @Override
    public void rejectToEnd(String taskId) {
        ProcessEngineApproval.rejectToEnd(repositoryService, taskService, taskId);
    }

    @Override
    public void cancel(String processInstanceId, String reason) {
        ProcessEngineApproval.cancel(runtimeService, processInstanceId, reason);
    }

    @Override
    public void reassign(String taskId, String fromUsername, String toUsername) {
        ProcessEngineApproval.reassign(taskService, taskId, fromUsername, toUsername);
    }

    @Override
    public List<CurrentTask> currentTasks(String processInstanceId) {
        return ProcessEngineApproval.currentTasks(taskService, processInstanceId).stream()
                .map(task -> new CurrentTask(
                        task.getId(),
                        task.getAssignee(),
                        ProcessEngineApproval.candidateUsers(taskService, task.getId())))
                .toList();
    }

    @Override
    public List<PendingTask> pendingTasks(String username, int firstResult, int maxResults) {
        List<Task> tasks = ProcessEngineApproval.pendingTasks(taskService, username, firstResult, maxResults);
        // businessKey 按实例 id 集合一次取回（#181）：端口签名不变，批量化收在实现内部。
        // 深页窗口（firstResult + size）可能超过 Flowable 集合查询上限（PageWindow.MAX_LIMIT），
        // 按上限分片回查；分片只影响查询次数，不影响行集合与顺序。
        Set<String> instanceIds = tasks.stream()
                .map(Task::getProcessInstanceId)
                .collect(Collectors.toSet());
        List<String> idList = new ArrayList<>(instanceIds);
        Map<String, String> businessKeys = new HashMap<>();
        int batchSize = (int) PageWindow.MAX_LIMIT;
        for (int start = 0; start < idList.size(); start += batchSize) {
            int end = Math.min(idList.size(), start + batchSize);
            businessKeys.putAll(ProcessEngineApproval.businessKeys(
                    runtimeService, Set.copyOf(idList.subList(start, end))));
        }
        return tasks.stream()
                .map(task -> new PendingTask(
                        task.getId(),
                        task.getProcessInstanceId(),
                        businessKeys.get(task.getProcessInstanceId()),
                        task.getAssignee(),
                        task.getCreateTime()))
                .toList();
    }

    @Override
    public long pendingCount(String username) {
        return ProcessEngineApproval.pendingCount(taskService, username);
    }
}
