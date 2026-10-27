package com.atguigu.process.approval;

import java.util.List;
import java.util.Map;

/**
 * 审批写入对流程引擎的命令。本 interface 不暴露 Flowable 类型；实现负责把引擎任务映射成当前任务快照。
 */
public interface ApprovalEngine {

    String start(String processDefinitionId, String businessKey, Map<String, Object> variables);

    void complete(String taskId);

    void rejectToEnd(String taskId);

    List<CurrentTask> currentTasks(String processInstanceId);

    record CurrentTask(String taskId, String assignee) {
    }
}
