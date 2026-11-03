package com.lautung.oa.security.login;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 登录日志的 UA 解析表（ADR 0054）：只留浏览器与操作系统两列，<b>不存 UA 原串</b>。
 *
 * <p>顺序即优先级：Edge 的 UA 同时含 {@code Chrome} 与 {@code Safari}，Android 的 UA 同时含
 * {@code Linux}，所以先认更具体的标记。表外一律回退 {@link #UNKNOWN}，不做尽力猜测。
 */
public final class UserAgentParser {

    public static final String UNKNOWN = "Unknown";

    private static final List<Entry> BROWSERS = List.of(
            new Entry("Edg/", "Edge"),
            new Entry("Firefox/", "Firefox"),
            new Entry("FxiOS/", "Firefox"),
            new Entry("Chrome/", "Chrome"),
            new Entry("CriOS/", "Chrome"),
            new Entry("Chromium/", "Chrome"),
            new Entry("Safari/", "Safari"));

    private static final List<Entry> OPERATING_SYSTEMS = List.of(
            new Entry("Android", "Android"),
            new Entry("iPhone", "iOS"),
            new Entry("iPad", "iOS"),
            new Entry("iPod", "iOS"),
            new Entry("Windows", "Windows"),
            new Entry("Macintosh", "macOS"),
            new Entry("Mac OS X", "macOS"),
            new Entry("Linux", "Linux"));

    private UserAgentParser() {
    }

    public static String browser(String userAgent) {
        return match(BROWSERS, userAgent);
    }

    public static String os(String userAgent) {
        return match(OPERATING_SYSTEMS, userAgent);
    }

    private static String match(List<Entry> table, String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        for (Entry entry : table) {
            if (entry.token().matcher(userAgent).find()) {
                return entry.label();
            }
        }
        return UNKNOWN;
    }

    private record Entry(Pattern token, String label) {

        private Entry(String literal, String label) {
            this(Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE), label);
        }
    }
}
