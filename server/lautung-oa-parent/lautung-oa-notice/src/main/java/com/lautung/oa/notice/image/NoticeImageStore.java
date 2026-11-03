package com.lautung.oa.notice.image;

/**
 * 公告图片的对象存储端口（ADR 0055）：默认实现是 {@link S3NoticeImageStore}（现有私有桶的
 * {@code notices/} 前缀），测试用内存实现替换，不需要起 MinIO。
 */
public interface NoticeImageStore {

    void put(String objectKey, byte[] content, String contentType);

    /** 读不到时抛运行时异常或返回 {@code null}，由 service 统一收成「图片不存在」。 */
    byte[] get(String objectKey);

    void delete(String objectKey);
}
