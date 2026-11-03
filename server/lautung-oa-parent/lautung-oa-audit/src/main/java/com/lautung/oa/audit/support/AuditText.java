package com.lautung.oa.audit.support;

/**
 * 审计字段的截断（ADR 0054）：{@code oper_param}/{@code error_msg} 2000 字符、
 * {@code result_message} 500 字符，超出部分丢弃并留下明确标记。
 *
 * <p>截断后可能不再是合法 JSON——这是故意的：库表列宽是第二道闸，宁可留下可识别的缺口，
 * 也不把 1MB 参数静默写进库。
 */
public final class AuditText {

    public static final String TRUNCATION_MARKER = "…[已截断]";

    private AuditText() {
    }

    public static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        int keep = limit - TRUNCATION_MARKER.length();
        if (keep <= 0) {
            return TRUNCATION_MARKER.substring(0, Math.max(0, limit));
        }
        return value.substring(0, keep) + TRUNCATION_MARKER;
    }
}
