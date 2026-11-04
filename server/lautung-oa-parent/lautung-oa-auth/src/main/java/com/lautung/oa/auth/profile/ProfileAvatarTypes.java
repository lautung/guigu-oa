package com.lautung.oa.auth.profile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 头像的类型判定（#254）：只收 jpeg/png/webp，同时看声明的 Content-Type 与文件头魔数；
 * 上限 2MB。判定收敛在这里，service 只消费结论（与公告图片的 NoticeImageTypes 同形状，
 * 但允许集与上限不同，不共用一份常量）。
 */
public final class ProfileAvatarTypes {

    public static final long MAX_BYTES = 2L * 1024L * 1024L;

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";

    private static final Set<String> ALLOWED = Set.of(JPEG, PNG, WEBP);

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ProfileAvatarTypes() {
    }

    /** 去掉参数并转小写；空值返回空串。 */
    public static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String base = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowed(String normalizedContentType) {
        return ALLOWED.contains(normalizedContentType);
    }

    /**
     * 文件头魔数识别：返回三种允许类型之一，识别不了（含 SVG/XML/GIF）返回 {@code null}。
     */
    public static String detect(byte[] content) {
        if (content == null || content.length < 4) {
            return null;
        }
        if (isJpeg(content)) {
            return JPEG;
        }
        if (isPng(content)) {
            return PNG;
        }
        if (isWebp(content)) {
            return WEBP;
        }
        return null;
    }

    private static boolean isJpeg(byte[] content) {
        return content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] content) {
        if (content.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (content[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWebp(byte[] content) {
        if (content.length < 12) {
            return false;
        }
        String riff = new String(content, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(content, 8, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WEBP".equals(webp);
    }
}
