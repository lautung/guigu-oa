package com.atguigu.process.membership;

import java.util.List;

/**
 * 判定审批成员资格所需的引擎证据。生产实现查 Flowable，测试实现直接给结果。
 */
public interface MembershipEvidence {

    List<CurrentTask> currentTasks(String processInstanceId);

    boolean hasHistoricAssignee(String processInstanceId, String username);

    record CurrentTask(String taskId, String assignee) {
    }
}
