package com.lautung.oa.notice.image;

import com.lautung.oa.storage.image.ImageContentTypes;

import java.util.Set;

/**
 * 公告图片的类型判定（ADR 0055）：只收 jpeg/png/gif/webp，同时看声明的 Content-Type 与
 * 文件头魔数；SVG 无论声明什么类型都拒绝。Content-Type 归一与魔数识别在
 * {@link ImageContentTypes}，这里只保留本域的允许集与上限。
 */
public final class NoticeImageTypes {

    public static final long MAX_BYTES = 5L * 1024L * 1024L;

    public static final String JPEG = ImageContentTypes.JPEG;
    public static final String PNG = ImageContentTypes.PNG;
    public static final String GIF = ImageContentTypes.GIF;
    public static final String WEBP = ImageContentTypes.WEBP;

    private static final Set<String> ALLOWED = Set.of(JPEG, PNG, GIF, WEBP);

    private NoticeImageTypes() {
    }

    /** 去掉参数并转小写；空值返回空串。 */
    public static String normalize(String contentType) {
        return ImageContentTypes.normalize(contentType);
    }

    public static boolean isAllowed(String normalizedContentType) {
        return ALLOWED.contains(normalizedContentType);
    }

    /**
     * 文件头魔数识别：返回四种允许类型之一，识别不了（含 SVG/XML）返回 {@code null}。
     */
    public static String detect(byte[] content) {
        return ImageContentTypes.detect(content);
    }
}
