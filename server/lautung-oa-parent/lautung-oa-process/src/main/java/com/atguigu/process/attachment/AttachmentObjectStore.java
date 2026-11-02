package com.atguigu.process.attachment;

import java.time.Duration;

public interface AttachmentObjectStore {

    String presignUpload(String key, String contentType, Duration ttl);

    StoredObject head(String key);

    void copy(String sourceKey, String targetKey);

    void delete(String key);

    String presignDownload(String key, String fileName, Duration ttl);

    record StoredObject(long sizeBytes, String contentType) {
    }
}
