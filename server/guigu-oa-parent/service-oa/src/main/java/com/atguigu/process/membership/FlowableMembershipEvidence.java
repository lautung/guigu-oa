package com.atguigu.process.membership;

import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class FlowableMembershipEvidence implements MembershipEvidence {

    private final TaskService taskService;
    private final HistoryService historyService;

    public FlowableMembershipEvidence(TaskService taskService, HistoryService historyService) {
        this.taskService = taskService;
        this.historyService = historyService;
    }

    @Override
    public List<CurrentTask> currentTasks(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            return List.of();
        }
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list()
                .stream()
                .map(task -> new CurrentTask(task.getId(), task.getAssignee()))
                .toList();
    }

    @Override
    public boolean hasHistoricAssignee(String processInstanceId, String username) {
        if (!StringUtils.hasText(processInstanceId) || !StringUtils.hasText(username)) {
            return false;
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(username)
                .count() > 0;
    }
}
