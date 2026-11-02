package com.lautung.oa.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 私有对象存储的配置（原 {@code com.lautung.oa.process.attachment.AttachmentS3Properties}，
 * #218 抽到共享 {@code lautung-oa-storage}）。
 *
 * <p>属性前缀保持 {@code lautung.oa.attachments}（ADR 0055：公告图片复用现有私有附件桶，
 * 不新增桶、不改桶策略）；进程启动参数、Compose 栈与文档都无需改名。附件的上传/下载
 * 预签名有效期、pending 清理节奏与 50MB 上限留在这里，是因为它们描述的是同一个桶；
 * 公告图片的 5MB 上限属于公告域的写入规则，由 notice 模块自己判。
 */
@ConfigurationProperties(prefix = "lautung.oa.attachments")
public class StorageS3Properties {

    private String endpoint = "http://localhost:9000";
    private String publicEndpoint = "http://localhost:9000";
    private String region = "us-east-1";
    private String bucket = "guigu-oa-attachments";
    private String accessKey = "local-access-key";
    private String secretKey = "local-secret-key";
    private Duration uploadUrlTtl = Duration.ofMinutes(5);
    private Duration downloadUrlTtl = Duration.ofMinutes(15);
    private Duration pendingTtl = Duration.ofHours(24);
    private Duration cleanupInterval = Duration.ofHours(1);
    private long maxFileSizeBytes = 50L * 1024L * 1024L;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPublicEndpoint() { return publicEndpoint; }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public Duration getUploadUrlTtl() { return uploadUrlTtl; }
    public void setUploadUrlTtl(Duration uploadUrlTtl) { this.uploadUrlTtl = uploadUrlTtl; }
    public Duration getDownloadUrlTtl() { return downloadUrlTtl; }
    public void setDownloadUrlTtl(Duration downloadUrlTtl) { this.downloadUrlTtl = downloadUrlTtl; }
    public Duration getPendingTtl() { return pendingTtl; }
    public void setPendingTtl(Duration pendingTtl) { this.pendingTtl = pendingTtl; }
    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
}
