package com.lautung.oa.process.membership;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.process.pending.HandlerEntry;
import com.lautung.oa.process.pending.PendingWorklist;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 审批成员资格的唯一解析点（ADR 0011，ADR 0068 修订，ADR 0076）。关系来自三个证据源：
 * 处理人事实（{@link PendingWorklist#handlers(Process)}：引擎当前任务 assignee/候选人 +
 * 未决加签任务）、账本（历史处理人、转办经手人、加签发起人）与抄送扩展。三源合成
 * 由待处理 module 拥有，本类只按 username/userId 解读原始事实；process 模块不直接
 * 依赖 auth 的 service——账本证据按本包的端口形状进来，引擎证据由 handlers 供给。
 */
@Service
public class ApprovalMembershipResolver {

    private final PendingWorklist pendingWorklist;
    private final MembershipLedgerEvidence ledgerEvidence;
    private final List<ProcessMembershipExtension> extensions;

    public ApprovalMembershipResolver(
            PendingWorklist pendingWorklist,
            MembershipLedgerEvidence ledgerEvidence,
            List<ProcessMembershipExtension> extensions) {
        this.pendingWorklist = pendingWorklist;
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
        Set<String> signTaskIds = new LinkedHashSet<>();
        Set<String> batchedEngineTaskIds = new LinkedHashSet<>();
        for (HandlerEntry handler : pendingWorklist.handlers(process)) {
            if (handler == null) {
                continue;
            }
            if (handler.source() == HandlerEntry.Source.ADD_SIGN) {
                // 旧加签证据在 adapter 层丢掉无 engineTaskId 的行；原始事实仍按同一可见面解读。
                if (!StringUtils.hasText(handler.engineTaskId())) {
                    continue;
                }
                if (user.getId().equals(handler.userId()) && StringUtils.hasText(handler.signTaskId())) {
                    signTaskIds.add(handler.signTaskId());
                }
                batchedEngineTaskIds.add(handler.engineTaskId());
            } else if (hasUsername && username.equals(handler.username())) {
                // assignee 或候选用户都能处理（ADR 0069：处理动作本身认领）。
                engineTaskIds.add(handler.engineTaskId());
            }
        }
        Set<String> suspendedTaskIds = new LinkedHashSet<>(engineTaskIds);
        suspendedTaskIds.retainAll(batchedEngineTaskIds);

        boolean historicApprover = ledgerEvidence.hasDecisionBy(process.getId(), user.getId());
        boolean transferHandler = ledgerEvidence.hasTransferBy(process.getId(), user.getId());
        boolean addSignInitiator = ledgerEvidence.hasAddSignBy(process.getId(), user.getId());
        boolean ccMember = extensions.stream()
                .filter(Objects::nonNull)
                .anyMatch(extension -> extension.isMember(user, process));

        return new ApprovalMembership(
                applicant, engineTaskIds, suspendedTaskIds, signTaskIds,
                historicApprover, ccMember, transferHandler, addSignInitiator);
    }
}
