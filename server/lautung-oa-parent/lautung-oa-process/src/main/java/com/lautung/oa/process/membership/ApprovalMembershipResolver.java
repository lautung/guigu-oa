package com.lautung.oa.process.membership;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 审批成员资格的唯一解析点（ADR 0011，ADR 0068 修订）。关系来自三个证据源：
 * 引擎（当前任务 assignee/候选人）、账本（历史处理人、转办经手人）与加签任务表
 * （加签人关系与前加签挂起标记）。process 模块不直接依赖 auth 的 service——
 * 引擎与账本证据都按本包的端口形状进来（MembershipEvidence / MembershipLedgerEvidence）。
 */
@Service
public class ApprovalMembershipResolver {

    private final MembershipEvidence evidence;
    private final MembershipLedgerEvidence ledgerEvidence;
    private final List<ProcessMembershipExtension> extensions;

    public ApprovalMembershipResolver(
            MembershipEvidence evidence,
            MembershipLedgerEvidence ledgerEvidence,
            List<ProcessMembershipExtension> extensions) {
        this.evidence = evidence;
        this.ledgerEvidence = ledgerEvidence;
        this.extensions = extensions == null ? List.of() : extensions;
    }

    public ApprovalMembership resolve(SysUser user, Process process) {
        if (user == null || user.getId() == null || process == null) {
            return ApprovalMembership.empty();
        }

        boolean applicant = user.getId().equals(process.getUserId());
        String username = user.getUsername();
        boolean hasUsername = StringUtils.hasText(username);

        Set<String> engineTaskIds = new LinkedHashSet<>();
        if (hasUsername && StringUtils.hasText(process.getProcessInstanceId())) {
            List<MembershipEvidence.CurrentTask> currentTasks =
                    evidence.currentTasks(process.getProcessInstanceId());
            if (currentTasks != null) {
                for (MembershipEvidence.CurrentTask task : currentTasks) {
                    if (task == null || !StringUtils.hasText(task.taskId())) {
                        continue;
                    }
                    // assignee 或候选用户都能处理（ADR 0069：处理动作本身认领）。
                    if (username.equals(task.assignee()) || task.candidates().contains(username)) {
                        engineTaskIds.add(task.taskId());
                    }
                }
            }
        }

        // 加签关系与挂起判定（#288）：同一次解析只读一份未决加签任务。
        List<MembershipLedgerEvidence.SignTaskSnapshot> signTasks =
                ledgerEvidence.pendingSignTasks(process.getId());
        Set<String> signTaskIds = new LinkedHashSet<>();
        Set<String> batchedEngineTaskIds = new LinkedHashSet<>();
        for (MembershipLedgerEvidence.SignTaskSnapshot signTask : signTasks) {
            if (signTask == null) {
                continue;
            }
            if (user.getId().equals(signTask.assigneeId()) && StringUtils.hasText(signTask.signTaskId())) {
                signTaskIds.add(signTask.signTaskId());
            }
            if (StringUtils.hasText(signTask.engineTaskId())) {
                batchedEngineTaskIds.add(signTask.engineTaskId());
            }
        }
        Set<String> suspendedTaskIds = new LinkedHashSet<>(engineTaskIds);
        suspendedTaskIds.retainAll(batchedEngineTaskIds);

        boolean historicApprover = ledgerEvidence.hasDecisionBy(process.getId(), user.getId());
        boolean transferHandler = ledgerEvidence.hasTransferBy(process.getId(), user.getId());
        boolean ccMember = extensions.stream()
                .filter(Objects::nonNull)
                .anyMatch(extension -> extension.isMember(user, process));

        return new ApprovalMembership(
                applicant, engineTaskIds, suspendedTaskIds, signTaskIds,
                historicApprover, ccMember, transferHandler);
    }
}
