package com.atguigu.process.approval;

import com.atguigu.process.ProcessEngineApproval;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ApprovalEngine 的 Flowable 适配器。Flowable 调用仍集中在 {@link ProcessEngineApproval}。
 */
@Component
public class FlowableApprovalEngine implements ApprovalEngine {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;

    public FlowableApprovalEngine(
            RuntimeService runtimeService, RepositoryService repositoryService, TaskService taskService) {
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
    public void rejectToEnd(String taskId) {
        ProcessEngineApproval.rejectToEnd(repositoryService, taskService, taskId);
    }

    @Override
    public List<CurrentTask> currentTasks(String processInstanceId) {
        return ProcessEngineApproval.currentTasks(taskService, processInstanceId).stream()
                .map(task -> new CurrentTask(task.getId(), task.getAssignee()))
                .toList();
    }
}
