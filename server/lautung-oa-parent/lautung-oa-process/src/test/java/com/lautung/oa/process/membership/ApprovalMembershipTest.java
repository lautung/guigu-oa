package com.lautung.oa.process.membership;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.membership.MembershipEvidence.CurrentTask;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalMembershipTest {

    private static final ProcessMembershipExtension CC_READER =
            (user, process) -> "cc-reader".equals(user.getUsername());

    @Test
    void applicantCanReadAndUploadButCannotAct() {
        ApprovalMembership membership = resolver(new FakeEvidence(
                List.of(new CurrentTask("task-other", "someone-else")), false))
                .resolve(user(2L, "alice"), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.canAct("task-other"));
    }

    @Test
    void currentApproverCanReadUploadAndActOnlyOnOwnTask() {
        ApprovalMembership membership = resolver(new FakeEvidence(
                List.of(new CurrentTask("task-a", "zhangsan"), new CurrentTask("task-b", "lisi")), false))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertTrue(membership.canAct("task-a"));
        assertFalse(membership.canAct("task-b"));
        assertFalse(membership.canAct(""));
        assertFalse(membership.canAct(null));
        assertFalse(membership.isPastApprover());
    }

    @Test
    void pastApproverCanReadButCannotUploadOrAct() {
        ApprovalMembership membership = resolver(new FakeEvidence(List.of(), true))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isPastApprover());
        assertTrue(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void ccRecipientCanReadOnly() {
        ApprovalMembership membership = resolver(new FakeEvidence(List.of(), false), CC_READER)
                .resolve(user(6L, "cc-reader"), process(2L, "instance-1"));

        assertTrue(membership.isCc());
        assertTrue(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void applicantWhoIsAlsoCcKeepsBothRelations() {
        ApprovalMembership membership = resolver(new FakeEvidence(List.of(), false), CC_READER)
                .resolve(user(2L, "cc-reader"), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.isCc());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
    }

    @Test
    void outsiderHasNoMembership() {
        ApprovalMembership membership = resolver(new FakeEvidence(List.of(), false))
                .resolve(user(5L, "eve"), process(2L, "instance-1"));

        assertFalse(membership.canRead());
        assertFalse(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
        assertFalse(membership.isApplicant());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
        assertFalse(membership.isCc());
    }

    @Test
    void nullUserProcessOrUserIdReturnsEmptyMembership() {
        ApprovalMembershipResolver resolver = resolver(new FakeEvidence(List.of(), true));

        assertFalse(resolver.resolve(null, process(2L, "instance-1")).canRead());
        assertFalse(resolver.resolve(user(2L, "alice"), null).canRead());
        assertFalse(resolver.resolve(user(null, "alice"), process(2L, "instance-1")).canRead());
    }

    @Test
    void blankUsernameKeepsApplicantButDropsApproverRelations() {
        FakeEvidence evidence = new FakeEvidence(
                List.of(new CurrentTask("task-a", "  ")), true);

        ApprovalMembership membership = resolver(evidence)
                .resolve(user(2L, "  "), process(2L, "instance-1"));

        assertTrue(membership.isApplicant());
        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
        assertEquals(0, evidence.currentTaskCalls);
        assertEquals(0, evidence.historicCalls);
    }

    @Test
    void applicantWithoutEngineInstanceCanReadAndUploadButCannotAct() {
        ApprovalMembership membership = resolver(new FakeEvidence(List.of(), false))
                .resolve(user(2L, "alice"), process(2L, ""));

        assertTrue(membership.canRead());
        assertTrue(membership.canUpload());
        assertFalse(membership.canAct("task-a"));
    }

    @Test
    void currentApproverWithHistoricTaskIsNotPastApprover() {
        ApprovalMembership membership = resolver(new FakeEvidence(
                List.of(new CurrentTask("task-a", "zhangsan")), true))
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertTrue(membership.isCurrentApprover());
        assertFalse(membership.isPastApprover());
    }

    @Test
    void resolveReadsEvidenceOnceAndCollapsesDuplicateTaskIds() {
        FakeEvidence evidence = new FakeEvidence(
                List.of(new CurrentTask("task-a", "zhangsan"), new CurrentTask("task-a", "zhangsan")), true);

        ApprovalMembership membership = resolver(evidence)
                .resolve(user(3L, "zhangsan"), process(2L, "instance-1"));

        assertEquals(1, evidence.currentTaskCalls);
        assertEquals(1, evidence.historicCalls);
        assertEquals(Set.of("task-a"), membership.currentTaskIds());
    }

    @Test
    void nullExtensionsAreIgnored() {
        ApprovalMembershipResolver resolver = new ApprovalMembershipResolver(
                new FakeEvidence(List.of(), false), java.util.Arrays.asList(CC_READER, null));

        assertTrue(resolver.resolve(user(6L, "cc-reader"), process(2L, "instance-1")).isCc());
        assertFalse(resolver.resolve(user(5L, "eve"), process(2L, "instance-1")).isCc());
    }

    private static ApprovalMembershipResolver resolver(
            MembershipEvidence evidence, ProcessMembershipExtension... extensions) {
        return new ApprovalMembershipResolver(evidence, List.of(extensions));
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

    private static final class FakeEvidence implements MembershipEvidence {

        private final List<CurrentTask> tasks;
        private final boolean historic;
        private int currentTaskCalls;
        private int historicCalls;

        private FakeEvidence(List<CurrentTask> tasks, boolean historic) {
            this.tasks = tasks;
            this.historic = historic;
        }

        @Override
        public List<CurrentTask> currentTasks(String processInstanceId) {
            currentTaskCalls++;
            return processInstanceId == null || processInstanceId.isBlank() ? List.of() : tasks;
        }

        @Override
        public boolean hasHistoricAssignee(String processInstanceId, String username) {
            historicCalls++;
            return historic
                    && processInstanceId != null && !processInstanceId.isBlank()
                    && username != null && !username.isBlank();
        }
    }
}
