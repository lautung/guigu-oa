package com.lautung.oa.auth.profile;

/**
 * 头像读出的字节与 Content-Type（#254）：类型按魔数识别，不信任对象元数据，
 * 响应带 {@code X-Content-Type-Options: nosniff}。
 */
public record ProfileAvatarContent(byte[] content, String contentType) {
}
