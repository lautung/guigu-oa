package com.lautung.oa.storage.image;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 图片类型判定的共享实现（#254 头像 / #218 公告图片）：Content-Type 归一与文件头魔数
 * 只在这里写一次，各域自带允许集合与大小上限。
 *
 * <p>{@link #detect(byte[])} 识别全部四种已知类型；调用方必须再用自己的允许集合过滤，
 * 例如头像只收 jpeg/png/webp，公告图片多收 gif。SVG/XML 等识别不出的一律返回 {@code null}。
 */
public final class ImageContentTypes {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageContentTypes() {
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

    /** 文件头魔数识别：返回四种已知类型之一，识别不了（含 SVG/XML）返回 {@code null}。 */
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
        if (isGif(content)) {
            return GIF;
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

    private static boolean isGif(byte[] content) {
        if (content.length < 6) {
            return false;
        }
        String header = new String(content, 0, 6, StandardCharsets.US_ASCII);
        return "GIF87a".equals(header) || "GIF89a".equals(header);
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
