package com.lautung.oa.process.membership;

import java.util.List;

/**
 * 判定审批成员资格所需的账本与加签任务证据（#288，ADR 0068 修订 ADR 0011）。
 * 历史处理人改为「账本里留下通过/拒绝的人」，转办经手人由 TRANSFER 动作推导，
 * 挂起标记与加签人关系来自加签任务表——三者都不读引擎历史（引擎 setAssignee 会
 * 覆盖历史 ASSIGNEE_，账本才是动作事实）。生产实现由账本服务与加签任务表供给，
 * 测试实现直接给结果。
 */
public interface MembershipLedgerEvidence {

    /** 历史处理人证据：该用户对该审批留下过 APPROVE/REJECT 动作记录。 */
    boolean hasDecisionBy(Long processId, Long userId);

    /** 转办经手人证据：该用户对该审批发起过 TRANSFER 动作记录。 */
    boolean hasTransferBy(Long processId, Long userId);

    /** 该审批的未决（PENDING）加签任务快照：挂起判定与加签人关系的唯一证据。 */
    List<SignTaskSnapshot> pendingSignTasks(Long processId);

    record SignTaskSnapshot(String signTaskId, String engineTaskId, String signType, Long assigneeId) {
    }
}
