package com.lautung.oa.process.membership;

import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.approval.ApprovalEngine;
import com.lautung.oa.process.membership.MembershipEvidence.CurrentTask;
import com.lautung.oa.process.pending.PendingSignTaskEvidence;
import com.lautung.oa.process.pending.PendingSignTaskEvidence.SignTaskSnapshot;
import com.lautung.oa.process.pending.PendingWorklist;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 成员资格六关系矩阵（#288，ADR 0068 修订 ADR 0011）的单测面：
 * 申请人/当前处理人（引擎 assignee、候选人、加签任务 assignee，挂起带不可动作标记）/
 * 历史处理人（账本通过或拒绝）/抄送人/转办经手人/加签发起人 × 读/补传/动作三档。
 * 引擎与加签事实走真实 {@link PendingWorklist}（假 ApprovalEngine + 假加签证据），
 * 账本证据用假实现；不启动 Spring，也不解析姓名（#299）。
 */
class ApprovalMembershipTest {

    private static final ProcessMembershipExtension CC_READER =
            (user, process) -> "cc-reader".equals(user.getUsername());

    private final SysUserService userService = mock(SysUserService.class);

    /** canAct 热路径只读处理人事实：解析成员资格不得为姓名投影查用户。 */
    @AfterEach
    void resolveNeverQueriesUsers() {
        verifyNoInteractions(userService);
    }

    @Test
    void applicantCanReadAndUploadButCannotAct() {
        ApprovalMembership membership = resolver(
                world(List.of(new CurrentTask("task-other", "someone-else")), false, false, List.of()))
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
        ApprovalMembership membership = resolver(world(
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
        ApprovalMembership membership = resolver(world(
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
        ApprovalMembership membership = resolver(world(
                List.of(new CurrentTask("task-a", "zhangsan")),
                false, false,
                List.of(signTask(9001, "task-a", "BEFORE", 4L))))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertTrue(membership.isSuspended());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload(), "挂起中的原处理人可补传");
        assertFalse(membership.canAct("task-a"), "挂起任务不可动作");
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void pendingBatchesSuspendTheirEngineTaskRegardlessOfBeforeOrAfterType() {
        // 挂起集合 = 本人引擎任务 ∩ 全部未决加签批次的 engineTaskId（BEFORE 与 AFTER 都算）：
        // BEFORE 的原处理人在等加签人；AFTER 的原处理人已记账但引擎任务未完成，同样不可动作。
        ApprovalMembership membership = resolver(world(
                List.of(new CurrentTask("task-a", "zhangsan"),
                        new CurrentTask("task-b", "zhangsan"),
                        new CurrentTask("task-c", "zhangsan")),
                false, false,
                List.of(signTask(9001, "task-a", "BEFORE", 4L),
                        signTask(9002, "task-b", "AFTER", 5L))))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertEquals(Set.of("task-a", "task-b", "task-c"), membership.engineTaskIds());
        assertEquals(Set.of("task-a", "task-b"), membership.suspendedTaskIds());
        assertTrue(membership.isCurrentApprover());
        assertTrue(membership.isSuspended());
        assertTrue(membership.canUpload());
        assertFalse(membership.canAct("task-a"), "BEFORE 批次挂起");
        assertFalse(membership.canAct("task-b"), "AFTER 批次的原处理人也不可动作");
        assertTrue(membership.canAct("task-c"), "没有批次的引擎任务仍可动作");
        assertTrue(membership.hasActionableTask());
    }

    @Test
    void addSignAssigneeActsOnSignTaskIdButCannotActOnEngineTask() {
        // 加签人（#288）：对加签任务 id 可动作；对配对引擎任务不可动作（加签人不能再加签/转办）。
        ApprovalMembership membership = resolver(world(
                List.of(new CurrentTask("task-a", "zhangsan")),
                false, false,
                List.of(signTask(9001, "task-a", "BEFORE", 4L))))
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
    void addSignRowWithoutEngineTaskIdIsInvisibleLikeTheOldEvidencePort() {
        // 旧的会计证据在 adapter 层丢掉 engineTaskId 为空的行；handlers 是原始事实，
        // 成员资格继续按同一可见面解读，避免权限面因迁移扩大。
        ApprovalMembership membership = resolver(world(
                List.of(new CurrentTask("task-a", "bob")),
                false, false,
                List.of(signTask(9001, " ", "BEFORE", 4L))))
                .resolve(user(4L, "lisi"), process(2L, "instance-1"));

        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isSuspended());
        assertFalse(membership.canAct("9001"));
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void ledgerDecisionsDrivePastApproverRelation() {
        ApprovalMembership membership = resolver(
                world(List.of(), true, false, List.of()))
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
                world(List.of(), false, true, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isTransferHandler());
        assertTrue(membership.canRead(), "转办经手人只读可见");
        assertFalse(membership.canUpload(), "转办经手人不能补传");
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void addSignInitiatorKeepsReadOnlyAfterRejection() {
        // 前加签被拒后引擎任务与加签任务都收场：ADD_SIGN 账本行让发起人保留只读（ADR 0011 修订）。
        ApprovalMembership membership = resolver(
                world(List.of(), false, false, true, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isAddSignInitiator());
        assertTrue(membership.canRead(), "加签发起人只读可见");
        assertFalse(membership.canUpload(), "加签发起人不能补传");
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.hasActionableTask());
    }

    @Test
    void ccRecipientCanReadOnly() {
        ApprovalMembership membership = resolver(
                world(List.of(), false, false, List.of()), CC_READER)
                .resolve(user(6L, "cc-reader"), process(2L, "instance-1"));

        assertTrue(membership.isCc());
        assertTrue(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void applicantWhoIsAlsoCcKeepsBothRelations() {
        ApprovalMembership membership = resolver(
                world(List.of(), false, false, List.of()), CC_READER)
                .resolve(user(2L, "cc-reader"), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.isCc());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
    }

    @Test
    void outsiderHasNoMembership() {
        ApprovalMembership membership = resolver(
                world(List.of(), false, false, List.of()))
                .resolve(user(5L, "eve"), process(2L, "instance-1"));

        assertFalse(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.isApplicant());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
        assertFalse(membership.isCc());
        assertFalse(membership.isTransferHandler());
        assertFalse(membership.isAddSignInitiator());
    }

    @Test
    void nullUserProcessOrUserIdReturnsEmptyMembership() {
        ApprovalMembershipResolver resolver = resolver(world(List.of(), true, true, List.of()));

        assertFalse(resolver.resolve(null, process(2L, "instance-1")).canRead());
        assertFalse(resolver.resolve(user(2L, "alice"), null).canRead());
        assertFalse(resolver.resolve(user(null, "alice"), process(2L, "instance-1")).canRead());
    }

    @Test
    void blankUsernameKeepsApplicantButDropsEngineApproverRelations() {
        World world = world(
                List.of(new CurrentTask("task-a", "  ")), false, false,
                List.of(signTask(9001, "task-a", "AFTER", 3L)));

        ApprovalMembership membership = resolver(world)
                .resolve(user(2L, "  "), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
        assertFalse(membership.isTransferHandler());
        assertFalse(membership.canAct("9001"), "加签关系按 assignee_id 判定，不属于别人");
        assertEquals(1, world.engine.currentTaskCalls, "引擎事实由 PendingWorklist 统一读，不因用户名为空跳过");
        assertEquals(1, world.signEvidence.pendingForProcessCalls, "挂起判定只按审批读一次");
        assertEquals(1, world.signEvidence.pendingByEngineTaskIdsCalls);
    }

    @Test
    void applicantWithoutEngineInstanceCanReadAndUploadButCannotAct() {
        ApprovalMembership membership = resolver(
                world(List.of(), false, false, List.of()))
                .resolve(user(2L, "alice"), process(2L, ""));

        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void currentApproverWithLedgerDecisionIsNotPastApprover() {
        ApprovalMembership membership = resolver(world(
                List.of(new CurrentTask("task-a", "zhangsan")), true, false, List.of()))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
    }

    @Test
    void resolveReadsEvidenceOnceAndCollapsesDuplicateTaskIds() {
        World world = world(
                List.of(new CurrentTask("task-a", "zhangsan"), new CurrentTask("task-a", "zhangsan")),
                true, false, List.of());

        ApprovalMembership membership = resolver(world)
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertEquals(1, world.engine.currentTaskCalls);
        assertEquals(1, world.signEvidence.pendingForProcessCalls);
        assertEquals(1, world.signEvidence.pendingByEngineTaskIdsCalls);
        assertEquals(1, world.ledger.decisionCalls);
        assertEquals(1, world.ledger.transferCalls);
        assertEquals(1, world.ledger.addSignCalls);
        assertEquals(Set.of("task-a"), membership.engineTaskIds());
    }

    @Test
    void nullExtensionsAreIgnored() {
        ApprovalMembershipResolver resolver = resolver(
                world(List.of(), false, false, List.of()),
                Arrays.asList(CC_READER, null));

        assertTrue(resolver.resolve(user(6L, "cc-reader"), process(2L, "instance-1")).isCc());
        assertFalse(resolver.resolve(user(5L, "eve"), process(2L, "instance-1")).isCc());
    }

    private ApprovalMembershipResolver resolver(World world) {
        return resolver(world, List.of());
    }

    private ApprovalMembershipResolver resolver(
            World world, ProcessMembershipExtension... extensions) {
        return resolver(world, List.of(extensions));
    }

    private ApprovalMembershipResolver resolver(
            World world, List<ProcessMembershipExtension> extensions) {
        return new ApprovalMembershipResolver(
                new PendingWorklist(world.engine, world.signEvidence, null, null, userService),
                world.ledger,
                extensions);
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

    private static World world(
            List<CurrentTask> tasks, boolean decided, boolean transferred,
            List<SignTaskSnapshot> signTasks) {
        return world(tasks, decided, transferred, false, signTasks);
    }

    private static World world(
            List<CurrentTask> tasks, boolean decided, boolean transferred, boolean addSigned,
            List<SignTaskSnapshot> signTasks) {
        World world = new World();
        world.engine.tasks.addAll(tasks);
        world.ledger.decided = decided;
        world.ledger.transferred = transferred;
        world.ledger.addSigned = addSigned;
        world.signEvidence.pending.addAll(signTasks);
        return world;
    }

    private static SignTaskSnapshot signTask(
            long id, String engineTaskId, String signType, Long assigneeId) {
        return new SignTaskSnapshot(id, 100L, engineTaskId, signType, assigneeId, new Date(0));
    }

    /** 一次解析的三份证据：真实 PendingWorklist 由前两份假实现驱动，账本证据独立假实现。 */
    private static final class World {

        private final FakeApprovalEngine engine = new FakeApprovalEngine();
        private final FakePendingSignTaskEvidence signEvidence = new FakePendingSignTaskEvidence();
        private final FakeLedgerEvidence ledger = new FakeLedgerEvidence();
    }

    /** 引擎假实现：只支持成员资格会用到的 currentTasks，写侧与其他读侧一律不支持。 */
    private static final class FakeApprovalEngine implements ApprovalEngine {

        private final List<CurrentTask> tasks = new ArrayList<>();
        private int currentTaskCalls;

        @Override
        public List<CurrentTask> currentTasks(String processInstanceId) {
            currentTaskCalls++;
            if (processInstanceId == null || processInstanceId.isBlank()) {
                return List.of();
            }
            return List.copyOf(tasks);
        }

        @Override
        public String start(String processDefinitionId, String businessKey, Map<String, Object> variables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void complete(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void claim(String taskId, String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rejectToEnd(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel(String processInstanceId, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reassign(String taskId, String fromUsername, String toUsername) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingTask> pendingTasks(String username, int firstResult, int maxResults) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long pendingCount(String username) {
            throw new UnsupportedOperationException();
        }
    }

    /** 加签证据假实现：成员资格只用按审批取未决快照与按引擎任务批查两条读法。 */
    private static final class FakePendingSignTaskEvidence implements PendingSignTaskEvidence {

        private final List<SignTaskSnapshot> pending = new ArrayList<>();
        private int pendingForProcessCalls;
        private int pendingByEngineTaskIdsCalls;

        @Override
        public List<SignTaskSnapshot> pendingForProcess(Long processId) {
            pendingForProcessCalls++;
            return pending.stream()
                    .filter(task -> Objects.equals(task.processId(), processId))
                    .toList();
        }

        @Override
        public List<SignTaskSnapshot> pendingByEngineTaskIds(List<String> engineTaskIds) {
            pendingByEngineTaskIdsCalls++;
            return pending.stream()
                    .filter(task -> engineTaskIds.contains(task.engineTaskId()))
                    .toList();
        }

        @Override
        public List<SignTaskSnapshot> pendingForUser(Long assigneeId, long offset, long limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countPendingForUser(Long assigneeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SignTaskSnapshot> pendingByType(String signType) {
            throw new UnsupportedOperationException();
        }
    }

    /** 账本证据假实现：只回答历史处理人、转办经手人、加签发起人。 */
    private static final class FakeLedgerEvidence implements MembershipLedgerEvidence {

        private boolean decided;
        private boolean transferred;
        private boolean addSigned;
        private int decisionCalls;
        private int transferCalls;
        private int addSignCalls;

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
        public boolean hasAddSignBy(Long processId, Long userId) {
            addSignCalls++;
            return addSigned && processId != null && userId != null;
        }
    }
}
