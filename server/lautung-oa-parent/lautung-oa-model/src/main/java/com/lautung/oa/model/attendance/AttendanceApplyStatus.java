package com.lautung.oa.model.attendance;

/**
 * 业务单据状态档（#282，ADR 0073）：请假单与加班单共用 0/1/2/-1/-2，
 * 与 ADR 0071/0072 同形状。
 *
 * <p>提交态（草稿/拒绝/撤回）可改可重提；审批中不可删改；通过后锁定不可重提。
 */
public final class AttendanceApplyStatus {

    /** 草稿：新建未提交。 */
    public static final int DRAFT = 0;

    /** 审批中：已发起，单据在途（余额预计以此为准）。 */
    public static final int SUBMITTED = 1;

    /** 通过：审批终态，请假已扣减 / 加班已入账。 */
    public static final int APPROVED = 2;

    /** 拒绝：可改后重提（单据号不变）。 */
    public static final int REJECTED = -1;

    /** 撤回：可改后重提（单据号不变）。 */
    public static final int WITHDRAWN = -2;

    private AttendanceApplyStatus() {
    }

    /** 草稿/拒绝/撤回 → 可以提交或编辑（审批中与通过都不行）。 */
    public static boolean isSubmittable(Integer status) {
        return status != null
                && (status == DRAFT || status == REJECTED || status == WITHDRAWN);
    }

    /** 编辑/删除与提交同档：审批中不可删单，通过后锁定。 */
    public static boolean isEditable(Integer status) {
        return isSubmittable(status);
    }
}
