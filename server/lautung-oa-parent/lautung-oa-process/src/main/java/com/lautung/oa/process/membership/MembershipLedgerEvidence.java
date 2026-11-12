package com.lautung.oa.process.membership;

/**
 * 判定审批成员资格所需的账本证据（#288，ADR 0068 修订 ADR 0011，ADR 0076）。历史处理人
 * 改为「账本里留下通过/拒绝的人」，转办经手人由 TRANSFER 动作推导，加签发起人由 ADD_SIGN
 * 动作推导——都不读引擎历史（引擎 setAssignee 会覆盖历史 ASSIGNEE_，账本才是动作事实）。
 * 加签任务事实（挂起判定与加签人关系）已收进 {@code PendingWorklist.handlers}，本端口不再
 * 读加签任务表。生产实现由账本服务供给，测试实现直接给结果。
 */
public interface MembershipLedgerEvidence {

    /** 历史处理人证据：该用户对该审批留下过 APPROVE/REJECT 动作记录。 */
    boolean hasDecisionBy(Long processId, Long userId);

    /** 转办经手人证据：该用户对该审批发起过 TRANSFER 动作记录。 */
    boolean hasTransferBy(Long processId, Long userId);

    /** 加签发起人证据：该用户对该审批发起过 ADD_SIGN 动作记录（前加签被拒后仍只读，ADR 0011 修订）。 */
    boolean hasAddSignBy(Long processId, Long userId);
}
