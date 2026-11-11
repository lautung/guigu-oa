package com.lautung.oa.attendance.application;

/**
 * 单据写请求的文本规范化（#294，ADR 0075）：两个适配器共用这一份，避免第四次复制。
 */
public final class ApplyTexts {

    private ApplyTexts() {
    }

    /** 去首尾空白；空白串折叠为 {@code null}（事由等可空字段的统一口径）。 */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
