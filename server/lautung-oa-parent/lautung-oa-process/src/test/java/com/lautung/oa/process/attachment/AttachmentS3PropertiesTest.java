package com.lautung.oa.process.attachment;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentS3PropertiesTest {

    @Test
    void exposesDefaultsForLocalDevelopment() {
        AttachmentS3Properties properties = new AttachmentS3Properties();

        assertEquals("http://localhost:9000", properties.getEndpoint());
        assertEquals("http://localhost:9000", properties.getPublicEndpoint());
        assertEquals("us-east-1", properties.getRegion());
        assertEquals("guigu-oa-attachments", properties.getBucket());
        assertEquals("local-access-key", properties.getAccessKey());
        assertEquals("local-secret-key", properties.getSecretKey());
        assertEquals(Duration.ofMinutes(5), properties.getUploadUrlTtl());
        assertEquals(Duration.ofMinutes(15), properties.getDownloadUrlTtl());
        assertEquals(Duration.ofHours(24), properties.getPendingTtl());
        assertEquals(Duration.ofHours(1), properties.getCleanupInterval());
        assertEquals(50L * 1024L * 1024L, properties.getMaxFileSizeBytes());
    }

    @Test
    void bindsEveryConfiguredValue() {
        AttachmentS3Properties properties = new AttachmentS3Properties();

        properties.setEndpoint("http://s3.test");
        properties.setPublicEndpoint("https://public.s3.test");
        properties.setRegion("eu-west-1");
        properties.setBucket("bucket");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setUploadUrlTtl(Duration.ofMinutes(1));
        properties.setDownloadUrlTtl(Duration.ofMinutes(2));
        properties.setPendingTtl(Duration.ofHours(3));
        properties.setCleanupInterval(Duration.ofMinutes(4));
        properties.setMaxFileSizeBytes(1024L);

        assertEquals("http://s3.test", properties.getEndpoint());
        assertEquals("https://public.s3.test", properties.getPublicEndpoint());
        assertEquals("eu-west-1", properties.getRegion());
        assertEquals("bucket", properties.getBucket());
        assertEquals("access", properties.getAccessKey());
        assertEquals("secret", properties.getSecretKey());
        assertEquals(Duration.ofMinutes(1), properties.getUploadUrlTtl());
        assertEquals(Duration.ofMinutes(2), properties.getDownloadUrlTtl());
        assertEquals(Duration.ofHours(3), properties.getPendingTtl());
        assertEquals(Duration.ofMinutes(4), properties.getCleanupInterval());
        assertEquals(1024L, properties.getMaxFileSizeBytes());
    }
}
