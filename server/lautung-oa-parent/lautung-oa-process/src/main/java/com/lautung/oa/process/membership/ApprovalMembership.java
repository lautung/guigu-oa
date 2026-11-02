package com.lautung.oa.process.membership;

import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 某用户与某一次审批的关系，以及由关系派生的能力。
 * 关系来自申请人、当前处理人、历史处理人、抄送人；能力由关系派生，调用方不再各自解释。
 */
public record ApprovalMembership(
        boolean applicant,
        Set<String> currentTaskIds,
        boolean historicApprover,
        boolean ccMember) {

    public ApprovalMembership {
        currentTaskIds = currentTaskIds == null ? Set.of() : Set.copyOf(currentTaskIds);
    }

    public static ApprovalMembership empty() {
        return new ApprovalMembership(false, Set.of(), false, false);
    }

    public boolean isApplicant() {
        return applicant;
    }

    public boolean isCurrentApprover() {
        return !currentTaskIds.isEmpty();
    }

    public boolean isPastApprover() {
        return historicApprover && !isCurrentApprover();
    }

    public boolean isCc() {
        return ccMember;
    }

    public boolean canRead() {
        return applicant || isCurrentApprover() || isPastApprover() || ccMember;
    }

    public boolean canUpload() {
        return applicant || isCurrentApprover();
    }

    public boolean canAct(String taskId) {
        return StringUtils.hasText(taskId) && currentTaskIds.contains(taskId);
    }
}
