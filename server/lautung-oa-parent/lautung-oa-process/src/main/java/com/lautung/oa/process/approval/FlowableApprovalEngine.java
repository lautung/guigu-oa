package com.lautung.oa.process.approval;

import com.lautung.oa.process.ProcessEngineApproval;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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
        Map<String, String> businessKeys = ProcessEngineApproval.businessKeys(
                runtimeService,
                tasks.stream().map(Task::getProcessInstanceId).collect(Collectors.toSet()));
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
