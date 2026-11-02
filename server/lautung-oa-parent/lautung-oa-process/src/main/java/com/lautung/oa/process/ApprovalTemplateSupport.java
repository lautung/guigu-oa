package com.lautung.oa.process;

/**
 * 审批模板未发布 / 已发布：能力判断只在这里认状态数字。
 */
public final class ApprovalTemplateSupport {

    public static final int 未发布 = 0;
    public static final int 已发布 = 1;

    private ApprovalTemplateSupport() {
    }

    public static boolean canInitiate(Integer status) {
        return status != null && status == 已发布;
    }

    public static boolean canWrite(Integer status) {
        return !canInitiate(status);
    }
}
