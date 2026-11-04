package com.lautung.oa.auth.profile;

import com.lautung.oa.storage.image.ImageContentTypes;

import java.util.Set;

/**
 * 头像的类型判定（#254）：只收 jpeg/png/webp，同时看声明的 Content-Type 与文件头魔数；
 * 上限 2MB。Content-Type 归一与魔数识别在 {@link ImageContentTypes}，这里只保留本域的
 * 允许集与上限——公告图片多收 gif，但两域共用同一份判定实现。
 */
public final class ProfileAvatarTypes {

    public static final long MAX_BYTES = 2L * 1024L * 1024L;

    public static final String JPEG = ImageContentTypes.JPEG;
    public static final String PNG = ImageContentTypes.PNG;
    public static final String WEBP = ImageContentTypes.WEBP;

    private static final Set<String> ALLOWED = Set.of(JPEG, PNG, WEBP);

    private ProfileAvatarTypes() {
    }

    /** 去掉参数并转小写；空值返回空串。 */
    public static String normalize(String contentType) {
        return ImageContentTypes.normalize(contentType);
    }

    public static boolean isAllowed(String normalizedContentType) {
        return ALLOWED.contains(normalizedContentType);
    }

    /**
     * 文件头魔数识别：返回三种允许类型之一，识别不了（含 SVG/XML/GIF）返回 {@code null}。
     */
    public static String detect(byte[] content) {
        String detected = ImageContentTypes.detect(content);
        return detected != null && ALLOWED.contains(detected) ? detected : null;
    }
}
