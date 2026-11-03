package com.lautung.oa.framework.operlog;

/**
 * 操作日志的业务类型（ADR 0054）。{@code code} 是落库的稳定小整数，不随枚举顺序变化：
 * {@code sys_oper_log.business_type} 的列注释与新值都必须回到本枚举登记，库里不存标签。
 */
public enum BusinessType {

    CREATE(1, "新增"),
    UPDATE(2, "修改"),
    DELETE(3, "删除"),
    ASSIGN(4, "授权"),
    PUBLISH(5, "发布"),
    CLEAR(6, "清空"),
    UNLOCK(7, "解锁"),
    OTHER(0, "其他");

    private final int code;
    private final String label;

    BusinessType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
