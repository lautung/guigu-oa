package com.lautung.oa.process.approval;

/**
 * 审批人规则的来源类型（ADR 0069）：每节点单选一种。BPMN 写死用户名不是规则类型——
 * 没有规则行的节点沿用 BPMN assignee。
 */
public enum ApproverSourceType {

    /** 发起人所在部门的直接负责人（sys_dept.leader_user_id，只认启用；不逐级向上；自审保留）。 */
    DEPT_LEADER("部门负责人"),

    /** 指定角色的启用成员集合，剔除申请人。 */
    ROLE("角色"),

    /** 指定岗位的启用成员集合，剔除申请人。 */
    POST("岗位");

    private final String label;

    ApproverSourceType(String label) {
        this.label = label;
    }

    /** 对外文案里的来源名（「第 N 步审批人无法解析（部门负责人）」）。 */
    public String label() {
        return label;
    }

    /** 数据库里的 source_type 字符串转枚举；未知值返回 null（调用方拒绝）。 */
    public static ApproverSourceType of(String sourceType) {
        if (sourceType == null) {
            return null;
        }
        for (ApproverSourceType type : values()) {
            if (type.name().equals(sourceType)) {
                return type;
            }
        }
        return null;
    }
}
