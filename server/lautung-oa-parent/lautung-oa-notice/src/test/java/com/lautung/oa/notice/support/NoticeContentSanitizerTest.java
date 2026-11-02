package com.lautung.oa.notice.support;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 写入时消毒的白名单逐条（ADR 0055）：库里的正文是唯一事实，读侧不再二次消毒，
 * 所以每种绕过面都必须在本类里有名称、有断言。
 */
class NoticeContentSanitizerTest {

    private final NoticeContentSanitizer sanitizer = new NoticeContentSanitizer();

    @Test
    void stripsScriptsAndKeepsAllowedMarkup() {
        String result = sanitizer.sanitize(
                "<script>alert(1)</script><p>正文</p><h1>标题</h1><strong>粗</strong><em>斜</em>"
                        + "<ul><li>一</li></ul><blockquote>引</blockquote><pre><code>x</code></pre>");

        assertFalse(result.contains("<script"), result);
        assertFalse(result.toLowerCase().contains("alert(1)"), result);
        assertTrue(result.contains("<p>正文</p>"), result);
        assertTrue(result.contains("<h1>标题</h1>"), result);
        assertTrue(result.contains("<strong>粗</strong>"), result);
        assertTrue(result.contains("<blockquote>引</blockquote>"), result);
        assertTrue(result.contains("<pre><code>x</code></pre>"), result);
    }

    @Test
    void stripsEveryEventHandlerAttribute() {
        String result = sanitizer.sanitize(
                "<p onclick=\"alert(1)\">a</p>"
                        + "<img src=\"/admin/notices/images/7\" onerror=\"alert(2)\">"
                        + "<a href=\"https://example.com\" onmouseover=\"alert(3)\">b</a>");

        assertFalse(result.toLowerCase().contains("onclick"), result);
        assertFalse(result.toLowerCase().contains("onerror"), result);
        assertFalse(result.toLowerCase().contains("onmouseover"), result);
        assertTrue(result.contains("src=\"/admin/notices/images/7\""), result);
        assertTrue(result.contains("href=\"https://example.com\""), result);
    }

    @Test
    void stripsJavascriptAndRelativeHrefsButAllowsHttpHttpsMailto() {
        assertFalse(sanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>")
                .toLowerCase().contains("javascript:"));

        String relative = sanitizer.sanitize("<a href=\"/admin/system/notices\">x</a>");
        assertFalse(relative.contains("href"), relative);

        assertTrue(sanitizer.sanitize("<a href=\"http://example.com\">x</a>")
                .contains("href=\"http://example.com\""));
        assertTrue(sanitizer.sanitize("<a href=\"https://example.com\">x</a>")
                .contains("href=\"https://example.com\""));
        assertTrue(sanitizer.sanitize("<a href=\"mailto:oa@example.com\">x</a>")
                .contains("href=\"mailto:oa@example.com\""));
    }

    @Test
    void stripsExternalDataAndSvgImagesButKeepsLocalImagePaths() {
        assertFalse(sanitizer.sanitize("<img src=\"https://evil.test/a.png\">").contains("<img"),
                "外部图必须连元素一起剥掉");
        assertFalse(sanitizer.sanitize("<img src=\"//evil.test/a.png\">").contains("<img"));
        assertFalse(sanitizer.sanitize("<img src=\"data:image/png;base64,AAAA\">").contains("<img"));
        assertFalse(sanitizer.sanitize(
                "<img src=\"/admin/notices/images/1.svg\">").contains("<img"));
        assertFalse(sanitizer.sanitize(
                "<svg onload=\"alert(1)\"><circle/></svg>").contains("<svg"));

        String local = sanitizer.sanitize(
                "<img src=\"/admin/notices/images/12\" alt=\"截图\" title=\"图\">");
        assertTrue(local.contains("<img"), local);
        assertTrue(local.contains("src=\"/admin/notices/images/12\""), local);
        assertTrue(local.contains("alt=\"截图\""), local);
    }

    @Test
    void stripsStyleClassAndIdAndIframe() {
        String result = sanitizer.sanitize(
                "<p style=\"color:red\" class=\"lead\" id=\"top\">t</p>"
                        + "<iframe src=\"https://evil.test\"></iframe>");

        assertFalse(result.contains("style="), result);
        assertFalse(result.contains("class="), result);
        assertFalse(result.contains("id="), result);
        assertFalse(result.contains("iframe"), result);
        assertTrue(result.contains("<p>t</p>"), result);
    }

    @Test
    void forcesBlankTargetAndNoopenerOnLinks() {
        String result = sanitizer.sanitize(
                "<a href=\"https://example.com\" target=\"_self\" rel=\"nofollow\">x</a>");

        assertTrue(result.contains("target=\"_blank\""), result);
        assertTrue(result.contains("rel=\"noopener noreferrer\""), result);
        assertFalse(result.contains("_self"), result);
        assertFalse(result.contains("nofollow"), result);
    }

    @Test
    void extractsImageIdsFromSanitizedContent() {
        String content = sanitizer.sanitize(
                "<p><img src=\"/admin/notices/images/3\"><img src=\"/admin/notices/images/9\">"
                        + "<img src=\"https://evil.test/x.png\"></p>");

        assertEquals(Set.of(3L, 9L), sanitizer.imageIds(content));
    }

    @Test
    void blankContentBecomesEmptyString() {
        assertEquals("", sanitizer.sanitize(null));
        assertEquals("", sanitizer.sanitize(""));
        assertEquals("", sanitizer.sanitize("   "));
        assertEquals(Set.of(), sanitizer.imageIds(""));
    }
}
