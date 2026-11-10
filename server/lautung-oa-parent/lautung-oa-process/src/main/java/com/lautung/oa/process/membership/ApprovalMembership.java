package com.lautung.oa.process.membership;

import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 某用户与某一次审批的关系，以及由关系派生的能力（ADR 0068 修订 ADR 0011，#288）。
 * 六种关系：申请人；当前处理人（引擎 assignee/候选人 {@code engineTaskIds}、加签任务
 * assignee {@code signTaskIds}；前加签挂起的引擎任务同时出现在 {@code suspendedTaskIds}，
 * 带「不可动作」标记）；历史处理人（账本里留下通过/拒绝的人）；抄送人；转办经手人；
 * 加签发起人（账本里留下 ADD_SIGN 的人，只读——前加签被拒后仍可回看）。
 * 能力由关系派生，调用方不再各自解释。
 */
public record ApprovalMembership(
        boolean applicant,
        Set<String> engineTaskIds,
        Set<String> suspendedTaskIds,
        Set<String> signTaskIds,
        boolean historicApprover,
        boolean ccMember,
        boolean transferHandler,
        boolean addSignInitiator) {

    public ApprovalMembership {
        engineTaskIds = engineTaskIds == null ? Set.of() : Set.copyOf(engineTaskIds);
        suspendedTaskIds = suspendedTaskIds == null ? Set.of() : Set.copyOf(suspendedTaskIds);
        signTaskIds = signTaskIds == null ? Set.of() : Set.copyOf(signTaskIds);
    }

    public static ApprovalMembership empty() {
        return new ApprovalMembership(false, Set.of(), Set.of(), Set.of(), false, false, false, false);
    }

    public boolean isApplicant() {
        return applicant;
    }

    /** 当前处理人：引擎任务（含挂起中的）或加签任务在手。 */
    public boolean isCurrentApprover() {
        return !engineTaskIds.isEmpty() || !signTaskIds.isEmpty();
    }

    public boolean isPastApprover() {
        return historicApprover && !isCurrentApprover();
    }

    public boolean isCc() {
        return ccMember;
    }

    public boolean isTransferHandler() {
        return transferHandler;
    }

    /** 加签发起人：只读；前加签被拒、引擎任务与加签任务都收场后仍可回看（ADR 0011 修订）。 */
    public boolean isAddSignInitiator() {
        return addSignInitiator;
    }

    /** 前加签挂起中的原处理人（或候选人）：仍是当前处理人，但不可动作。 */
    public boolean isSuspended() {
        return !suspendedTaskIds.isEmpty();
    }

    /** 六种关系皆可读（canRead，ADR 0068，ADR 0011 修订）。 */
    public boolean canRead() {
        return applicant || isCurrentApprover() || isPastApprover() || ccMember || transferHandler
                || addSignInitiator;
    }

    /** 只有申请人或当前处理人可补传（挂起中的原处理人仍是当前处理人）。 */
    public boolean canUpload() {
        return applicant || isCurrentApprover();
    }

    /**
     * canAct：当前处理人且未挂起。加签任务 id 直接可动作；引擎任务 id 要求没有未决
     * 加签批次压在上面（前加签挂起的原处理人、后加签已记账的原处理人都不可再动作）。
     */
    public boolean canAct(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return false;
        }
        if (signTaskIds.contains(taskId)) {
            return true;
        }
        return engineTaskIds.contains(taskId) && !suspendedTaskIds.contains(taskId);
    }

    /** 是否存在可动作的任务面（详情 isApprove 的判据：挂起行与已记账的原处理人为 false）。 */
    public boolean hasActionableTask() {
        if (!signTaskIds.isEmpty()) {
            return true;
        }
        return engineTaskIds.stream().anyMatch(taskId -> !suspendedTaskIds.contains(taskId));
    }
}
