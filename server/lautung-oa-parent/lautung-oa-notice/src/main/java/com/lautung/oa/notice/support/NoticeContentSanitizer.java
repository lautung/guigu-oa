package com.lautung.oa.notice.support;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公告正文的唯一消毒点（ADR 0055）：写入时用 jsoup {@code Safelist} 洗一次，库里的就是干净
 * HTML；读侧不再二次消毒。规则逐条对齐 ADR：
 *
 * <ul>
 *   <li>标签只留 {@code p br h1 h2 h3 h4 strong em u s blockquote ol ul li pre code a img}；</li>
 *   <li>属性只留 {@code a[href,title,target,rel]} 与 {@code img[src,alt,title]}，
 *       {@code style/class/id/on*} 一律剥掉；</li>
 *   <li>链接协议只收 {@code http/https/mailto}，落库前强制 {@code target="_blank"} 与
 *       {@code rel="noopener noreferrer"}；</li>
 *   <li>图片 {@code src} 只接受同源 {@code /admin/notices/images/{id}}，外链图、{@code data:}
 *       与 SVG 直接连元素一起剥掉。</li>
 * </ul>
 *
 * <p>图片不在 Safelist 的协议表里判：相对路径会被 jsoup 的协议检查误杀；这里放开属性、
 * 清洗后按同源正则收紧，唯一判据仍然只有一处。
 */
@Component
public class NoticeContentSanitizer {

    /** 正文里允许出现的唯一图片地址形状（与 {@code GET /admin/notices/images/{id}} 对应）。 */
    private static final Pattern LOCAL_IMAGE_PATH = Pattern.compile("^/admin/notices/images/(\\d+)$");

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "h1", "h2", "h3", "h4", "strong", "em", "u", "s",
                    "blockquote", "ol", "ul", "li", "pre", "code", "a", "img")
            .addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("img", "src", "alt", "title")
            .addProtocols("a", "href", "http", "https", "mailto");

    /**
     * 消毒并返回可落库的正文片段；{@code null}/空白正文归一成空串（草稿允许空正文）。
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String cleaned = Jsoup.clean(html, "", SAFELIST, compactOutput());
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings(compactOutput());

        for (Element image : document.select("img")) {
            if (!LOCAL_IMAGE_PATH.matcher(image.attr("src")).matches()) {
                image.remove();
            }
        }
        for (Element link : document.select("a[href]")) {
            link.attr("target", "_blank");
            link.attr("rel", "noopener noreferrer");
        }
        return document.body().html();
    }

    /**
     * 从已消毒正文里取出图片 id：保存/更新时按它重算绑定（新出现的绑定、消失的解除绑定）。
     * 入参必须是 {@link #sanitize(String)} 的输出，未消毒的 HTML 不经过本方法。
     */
    public Set<Long> imageIds(String sanitizedHtml) {
        Set<Long> ids = new LinkedHashSet<>();
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return ids;
        }
        for (Element image : Jsoup.parseBodyFragment(sanitizedHtml).select("img[src]")) {
            Matcher matcher = LOCAL_IMAGE_PATH.matcher(image.attr("src"));
            if (matcher.matches()) {
                ids.add(Long.valueOf(matcher.group(1)));
            }
        }
        return ids;
    }

    /**
     * 消毒输出保持紧凑：jsoup 默认的 pretty print 会往文本里塞换行与缩进，正文落库前不该
     * 被重排。每次新建，避免共享可变对象。
     */
    private Document.OutputSettings compactOutput() {
        return new Document.OutputSettings().prettyPrint(false);
    }
}
