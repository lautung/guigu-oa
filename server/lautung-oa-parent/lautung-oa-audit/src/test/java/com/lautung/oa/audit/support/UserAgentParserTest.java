package com.lautung.oa.audit.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UA 解析表（ADR 0054）：只存浏览器与操作系统两列，不存 UA 原串。表要覆盖主流组合，
 * 且认不出时必须回退到 {@code Unknown} 而不是把整串塞进列里。
 */
class UserAgentParserTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";
    private static final String EDGE_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
    private static final String FIREFOX_LINUX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0";
    private static final String SAFARI_MAC =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "Version/17.0 Safari/605.1.15";
    private static final String CHROME_IOS =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                    + "CriOS/120.0.6099.119 Mobile/15E148 Safari/604.1";
    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    @Test
    void parsesTheMainstreamBrowserAndOsPairs() {
        assertParsed(CHROME_WINDOWS, "Chrome", "Windows");
        assertParsed(EDGE_WINDOWS, "Edge", "Windows");
        assertParsed(FIREFOX_LINUX, "Firefox", "Linux");
        assertParsed(SAFARI_MAC, "Safari", "macOS");
        assertParsed(CHROME_IOS, "Chrome", "iOS");
        assertParsed(CHROME_ANDROID, "Chrome", "Android");
    }

    @Test
    void unknownOrMissingAgentsFallBackWithoutLeakingTheRawString() {
        assertParsed("curl/8.4.0", UserAgentParser.UNKNOWN, UserAgentParser.UNKNOWN);
        assertParsed("", UserAgentParser.UNKNOWN, UserAgentParser.UNKNOWN);
        assertParsed(null, UserAgentParser.UNKNOWN, UserAgentParser.UNKNOWN);
    }

    private static void assertParsed(String userAgent, String browser, String os) {
        assertEquals(browser, UserAgentParser.browser(userAgent), "browser of " + userAgent);
        assertEquals(os, UserAgentParser.os(userAgent), "os of " + userAgent);
    }
}
