package com.lautung.oa.model.attendance;

import java.util.List;

/**
 * 业务单据状态档（#282，ADR 0073；#294 升为枚举，ADR 0075）：请假单与加班单共用
 * 0/1/2/-1/-2，与 ADR 0071/0072 同形状。
 *
 * <p>库内仍存 int（实体与 DDL 不变），映射只发生在适配器的边界（{@link #fromCode}）；
 * 可编辑/可提交与 CAS 的 IN 列表由本枚举唯一回答。提交态（草稿/拒绝/撤回）可改可重提；
 * 审批中不可删改；通过后锁定不可重提。
 */
public enum AttendanceApplyStatus {

    /** 草稿：新建未提交。 */
    DRAFT(0),

    /** 审批中：已发起，单据在途（余额预计以此为准）。 */
    SUBMITTED(1),

    /** 通过：审批终态，请假已扣减 / 加班已入账。 */
    APPROVED(2),

    /** 拒绝：可改后重提（单据号不变）。 */
    REJECTED(-1),

    /** 撤回：可改后重提（单据号不变）。 */
    WITHDRAWN(-2);

    private static final List<Integer> SUBMITTABLE_CODES =
            List.of(DRAFT.code, REJECTED.code, WITHDRAWN.code);

    private final int code;

    AttendanceApplyStatus(int code) {
        this.code = code;
    }

    /** 库内档位（实体与 DDL 的 int 值）。 */
    public int code() {
        return code;
    }

    /** 库内档位 → 枚举；未知值（含 null）返回 {@code null}，由调用方按「状态不合法」处理。 */
    public static AttendanceApplyStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (AttendanceApplyStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /** 草稿/拒绝/撤回 → 可以编辑、删除或提交（审批中与通过都不行）。 */
    public boolean isSubmittable() {
        return this == DRAFT || this == REJECTED || this == WITHDRAWN;
    }

    /** 编辑/删除与提交同档：审批中不可删单，通过后锁定。 */
    public boolean isEditable() {
        return isSubmittable();
    }

    /** 可编辑/可提交的库内档位（CAS 的 IN 列表唯一来源）。 */
    public static List<Integer> submittableCodes() {
        return SUBMITTABLE_CODES;
    }
}
