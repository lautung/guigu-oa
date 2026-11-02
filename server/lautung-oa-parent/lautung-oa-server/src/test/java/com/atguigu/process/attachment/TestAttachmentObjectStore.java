package com.atguigu.process.attachment;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared in-memory object store for attachment and approval contract tests.
 */
public class TestAttachmentObjectStore implements AttachmentObjectStore {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private int downloadGrantCount;
    private Duration lastDownloadTtl;
    private int copyAttempts;
    private int failOnCopyAttempt = -1;

    @Override
    public String presignUpload(String key, String contentType, Duration ttl) {
        return "https://objects.test/upload/" + key;
    }

    @Override
    public StoredObject head(String key) {
        StoredObject storedObject = objects.get(key);
        if (storedObject == null) {
            throw new IllegalStateException("Object does not exist");
        }
        return storedObject;
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        copyAttempts++;
        if (copyAttempts == failOnCopyAttempt) {
            failOnCopyAttempt = -1;
            throw new IllegalStateException("Object copy failed for test");
        }
        objects.put(targetKey, head(sourceKey));
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    @Override
    public String presignDownload(String key, String fileName, Duration ttl) {
        downloadGrantCount++;
        lastDownloadTtl = ttl;
        return "https://objects.test/download/" + key.substring(key.lastIndexOf('/') + 1);
    }

    public void put(String key, long size, String contentType) {
        objects.put(key, new StoredObject(size, contentType));
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    public int downloadGrantCount() {
        return downloadGrantCount;
    }

    public Duration lastDownloadTtl() {
        return lastDownloadTtl;
    }

    public void failOnCopyAttempt(int attempt) {
        failOnCopyAttempt = attempt;
    }

    public int copyAttempts() {
        return copyAttempts;
    }

    public void clear() {
        objects.clear();
        downloadGrantCount = 0;
        lastDownloadTtl = null;
        copyAttempts = 0;
        failOnCopyAttempt = -1;
    }
}
