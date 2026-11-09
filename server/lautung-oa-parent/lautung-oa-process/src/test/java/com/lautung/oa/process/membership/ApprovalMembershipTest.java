package com.lautung.oa.process.membership;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.membership.MembershipEvidence.CurrentTask;
import com.lautung.oa.process.membership.MembershipLedgerEvidence.SignTaskSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成员资格五关系矩阵（#288，ADR 0068 修订 ADR 0011）的单测面：
 * 申请人/当前处理人（引擎 assignee、候选人、加签任务 assignee，挂起带不可动作标记）/
 * 历史处理人（账本通过或拒绝）/抄送人/转办经手人 × 读/补传/动作三档。
 */
class ApprovalMembershipTest {

    private static final ProcessMembershipExtension CC_READER =
            (user, process) -> "cc-reader".equals(user.getUsername());

    @Test
    void applicantCanReadAndUploadButCannotAct() {
        ApprovalMembership membership = resolver(
                evidence(List.of(new CurrentTask("task-other", "someone-else")), false, false, List.of()))
                .resolve(user(2L, "alice"), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.canAct("task-other"));
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void engineApproverCanReadUploadAndActOnlyOnOwnTask() {
        ApprovalMembership membership = resolver(evidence(
                List.of(new CurrentTask("task-a", "zhangsan"), new CurrentTask("task-b", "lisi")),
                false, false, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertTrue(membership.canAct("task-a"));
        assertFalse(membership.canAct("task-b"));
        assertFalse(membership.canAct(""));
        assertFalse(membership.canAct(null));
        assertFalse(membership.isPastApprover());
        assertTrue(membership.hasActionableTask());
    }

    @Test
    void candidateIsCurrentApproverWhoActsByClaiming() {
        ApprovalMembership membership = resolver(evidence(
                List.of(new CurrentTask("task-a", null, List.of("zhangsan"))), false, false, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertTrue(membership.canAct("task-a"));
    }

    @Test
    void suspendedEngineApproverKeepsReadUploadButLosesAct() {
        // 前加签挂起（#288）：引擎任务上有未决批次 → 任务带不可动作标记，仍是当前处理人。
        ApprovalMembership membership = resolver(evidence(
                List.of(new CurrentTask("task-a", "zhangsan")),
                false, false,
                List.of(new SignTaskSnapshot("9001", "task-a", "BEFORE", 4L))))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertTrue(membership.isSuspended());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload(), "挂起中的原处理人可补传");
        assertFalse(membership.canAct("task-a"), "挂起任务不可动作");
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void addSignAssigneeActsOnSignTaskIdButCannotActOnEngineTask() {
        // 加签人（#288）：对加签任务 id 可动作；对配对引擎任务不可动作（加签人不能再加签/转办）。
        ApprovalMembership membership = resolver(evidence(
                List.of(new CurrentTask("task-a", "zhangsan")),
                false, false,
                List.of(new SignTaskSnapshot("9001", "task-a", "BEFORE", 4L))))
                .resolve(user(4L, "lisi"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertFalse(membership.isSuspended(), "加签人自己不挂起");
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertTrue(membership.canAct("9001"), "加签任务 id 可动作");
        assertFalse(membership.canAct("task-a"), "配对引擎任务不是加签人的动作面");
        assertTrue(membership.hasActionableTask());
    }

    @Test
    void ledgerDecisionsDrivePastApproverRelation() {
        ApprovalMembership membership = resolver(
                evidence(List.of(), true, false, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isPastApprover());
        assertTrue(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void transferHandlerCanReadButCannotUploadOrAct() {
        ApprovalMembership membership = resolver(
                evidence(List.of(), false, true, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isTransferHandler());
        assertTrue(membership.canRead(), "转办经手人只读可见");
        assertFalse(membership.canUpload(), "转办经手人不能补传");
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void ccRecipientCanReadOnly() {
        ApprovalMembership membership = resolver(
                evidence(List.of(), false, false, List.of()), CC_READER)
                .resolve(user(6L, "cc-reader"), process(2L, "instance-1"));

        assertTrue(membership.isCc());
        assertTrue(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void applicantWhoIsAlsoCcKeepsBothRelations() {
        ApprovalMembership membership = resolver(
                evidence(List.of(), false, false, List.of()), CC_READER)
                .resolve(user(2L, "cc-reader"), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.isCc());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
    }

    @Test
    void outsiderHasNoMembership() {
        ApprovalMembership membership = resolver(
                evidence(List.of(), false, false, List.of()))
                .resolve(user(5L, "eve"), process(2L, "instance-1"));

        assertFalse(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.isApplicant());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
        assertFalse(membership.isCc());
        assertFalse(membership.isTransferHandler());
    }

    @Test
    void nullUserProcessOrUserIdReturnsEmptyMembership() {
        ApprovalMembershipResolver resolver = resolver(evidence(List.of(), true, true, List.of()));

        assertFalse(resolver.resolve(null, process(2L, "instance-1")).canRead());
        assertFalse(resolver.resolve(user(2L, "alice"), null).canRead());
        assertFalse(resolver.resolve(user(null, "alice"), process(2L, "instance-1")).canRead());
    }

    @Test
    void blankUsernameKeepsApplicantButDropsEngineApproverRelations() {
        FakeEvidence evidence = evidence(
                List.of(new CurrentTask("task-a", "  ")), false, false,
                List.of(new SignTaskSnapshot("9001", "task-a", "AFTER", 3L)));

        ApprovalMembership membership = resolver(evidence)
                .resolve(user(2L, "  "), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
        assertFalse(membership.isTransferHandler());
        assertFalse(membership.canAct("9001"), "加签关系按 assignee_id 判定，不属于别人");
        assertEquals(0, evidence.currentTaskCalls);
        assertEquals(1, evidence.pendingSignTaskCalls, "挂起判定不依赖用户名，只按审批读一次");
    }

    @Test
    void applicantWithoutEngineInstanceCanReadAndUploadButCannotAct() {
        ApprovalMembership membership = resolver(
                evidence(List.of(), false, false, List.of()))
                .resolve(user(2L, "alice"), process(2L, ""));

        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void currentApproverWithLedgerDecisionIsNotPastApprover() {
        ApprovalMembership membership = resolver(evidence(
                List.of(new CurrentTask("task-a", "zhangsan")), true, false, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
    }

    @Test
    void resolveReadsEvidenceOnceAndCollapsesDuplicateTaskIds() {
        FakeEvidence evidence = evidence(
                List.of(new CurrentTask("task-a", "zhangsan"), new CurrentTask("task-a", "zhangsan")),
                true, false, List.of());

        ApprovalMembership membership = resolver(evidence)
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertEquals(1, evidence.currentTaskCalls);
        assertEquals(1, evidence.decisionCalls);
        assertEquals(1, evidence.transferCalls);
        assertEquals(1, evidence.pendingSignTaskCalls);
        assertEquals(Set.of("task-a"), membership.engineTaskIds());
    }

    @Test
    void nullExtensionsAreIgnored() {
        ApprovalMembershipResolver resolver = new ApprovalMembershipResolver(
                evidence(List.of(), false, false, List.of()),
                evidence(List.of(), false, false, List.of()),
                java.util.Arrays.asList(CC_READER, null));

        assertTrue(resolver.resolve(user(6L, "cc-reader"), process(2L, "instance-1")).isCc());
        assertFalse(resolver.resolve(user(5L, "eve"), process(2L, "instance-1")).isCc());
    }

    private static ApprovalMembershipResolver resolver(FakeEvidence evidence) {
        return new ApprovalMembershipResolver(evidence, evidence, List.of());
    }

    private static ApprovalMembershipResolver resolver(
            FakeEvidence evidence, ProcessMembershipExtension... extensions) {
        return new ApprovalMembershipResolver(evidence, evidence, List.of(extensions));
    }

    private static SysUser user(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static Process process(Long userId, String processInstanceId) {
        Process process = new Process();
        process.setId(100L);
        process.setUserId(userId);
        process.setProcessInstanceId(processInstanceId);
        return process;
    }

    private static FakeEvidence evidence(
            List<CurrentTask> tasks, boolean decided, boolean transferred,
            List<SignTaskSnapshot> signTasks) {
        return new FakeEvidence(tasks, decided, transferred, signTasks);
    }

    private static final class FakeEvidence implements MembershipEvidence, MembershipLedgerEvidence {

        private final List<CurrentTask> tasks;
        private final boolean decided;
        private final boolean transferred;
        private final List<SignTaskSnapshot> signTasks;
        private int currentTaskCalls;
        private int decisionCalls;
        private int transferCalls;
        private int pendingSignTaskCalls;

        private FakeEvidence(
                List<CurrentTask> tasks, boolean decided, boolean transferred,
                List<SignTaskSnapshot> signTasks) {
            this.tasks = tasks;
            this.decided = decided;
            this.transferred = transferred;
            this.signTasks = signTasks;
        }

        @Override
        public List<CurrentTask> currentTasks(String processInstanceId) {
            currentTaskCalls++;
            return processInstanceId == null || processInstanceId.isBlank() ? List.of() : tasks;
        }

        @Override
        public boolean hasDecisionBy(Long processId, Long userId) {
            decisionCalls++;
            return decided && processId != null && userId != null;
        }

        @Override
        public boolean hasTransferBy(Long processId, Long userId) {
            transferCalls++;
            return transferred && processId != null && userId != null;
        }

        @Override
        public List<SignTaskSnapshot> pendingSignTasks(Long processId) {
            pendingSignTaskCalls++;
            return processId == null ? List.of() : signTasks;
        }
    }
}
