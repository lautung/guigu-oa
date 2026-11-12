package com.lautung.oa.process.membership;

import java.util.List;

/**
 * 审批引擎的读侧形状：当前任务的 assignee 与候选用户。生产上由 ApprovalEngine 的
 * Flowable 适配器实现；成员资格不再单独注入本接口，而是经 PendingWorklist.handlers
 * 读取同一份引擎事实（ADR 0076）。
 */
public interface MembershipEvidence {

    List<CurrentTask> currentTasks(String processInstanceId);

    /**
     * 引擎侧任务快照：assignee 与候选用户（ADR 0069 多人来源按候选用户下发）。
     * 候选用户为空列表表示没有候选（BPMN 写死 assignee 的任务）。
     */
    record CurrentTask(String taskId, String assignee, List<String> candidates) {

        public CurrentTask {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        /** 兼容构造：无候选用户（纯 assignee 任务）。 */
        public CurrentTask(String taskId, String assignee) {
            this(taskId, assignee, List.of());
        }
    }
}
