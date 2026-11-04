package com.lautung.oa.auth.profile;

/**
 * 头像的对象存储端口（#254）：默认实现是 {@link S3ProfileAvatarStore}（现有私有桶的
 * {@code avatar/{userId}} 固定键），测试用内存实现替换，不需要起 MinIO。
 */
public interface ProfileAvatarStore {

    void put(String objectKey, byte[] content, String contentType);

    /** 读不到时抛运行时异常或返回 {@code null}，由 service 统一收成「头像不存在」。 */
    byte[] get(String objectKey);
}
