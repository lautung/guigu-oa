package com.lautung.oa.process.membership;

import java.util.List;

/**
 * 判定审批成员资格所需的引擎证据。生产上由 ApprovalEngine 的 Flowable 适配器实现，测试实现直接给结果。
 * 历史处理人不再读引擎历史（ADR 0068 修订 ADR 0011：改由账本动作记录供给，
 * 见 {@link MembershipLedgerEvidence}）。
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
