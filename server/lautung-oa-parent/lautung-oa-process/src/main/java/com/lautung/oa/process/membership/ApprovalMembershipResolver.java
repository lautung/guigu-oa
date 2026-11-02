package com.lautung.oa.process.membership;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ApprovalMembershipResolver {

    private final MembershipEvidence evidence;
    private final List<ProcessMembershipExtension> extensions;

    public ApprovalMembershipResolver(
            MembershipEvidence evidence,
            List<ProcessMembershipExtension> extensions) {
        this.evidence = evidence;
        this.extensions = extensions == null ? List.of() : extensions;
    }

    public ApprovalMembership resolve(SysUser user, Process process) {
        if (user == null || user.getId() == null || process == null) {
            return ApprovalMembership.empty();
        }

        boolean applicant = user.getId().equals(process.getUserId());
        String username = user.getUsername();
        boolean hasUsername = StringUtils.hasText(username);

        Set<String> currentTaskIds = new LinkedHashSet<>();
        if (hasUsername) {
            List<MembershipEvidence.CurrentTask> currentTasks =
                    evidence.currentTasks(process.getProcessInstanceId());
            if (currentTasks != null) {
                for (MembershipEvidence.CurrentTask task : currentTasks) {
                    if (task != null && StringUtils.hasText(task.taskId())
                            && username.equals(task.assignee())) {
                        currentTaskIds.add(task.taskId());
                    }
                }
            }
        }

        boolean historicApprover = hasUsername
                && evidence.hasHistoricAssignee(process.getProcessInstanceId(), username);
        boolean ccMember = extensions.stream()
                .filter(Objects::nonNull)
                .anyMatch(extension -> extension.isMember(user, process));

        return new ApprovalMembership(applicant, currentTaskIds, historicApprover, ccMember);
    }
}
