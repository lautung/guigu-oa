package com.lautung.oa.process.attachment;

import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class S3AttachmentObjectStore implements AttachmentObjectStore {

    private final S3Client client;
    private final S3Presigner presigner;
    private final AttachmentS3Properties properties;

    public S3AttachmentObjectStore(
            S3Client attachmentS3Client,
            S3Presigner attachmentS3Presigner,
            AttachmentS3Properties properties) {
        this.client = attachmentS3Client;
        this.presigner = attachmentS3Presigner;
        this.properties = properties;
    }

    @Override
    public String presignUpload(String key, String contentType, Duration ttl) {
        var put = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .build();
        var request = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build();
        return presigner.presignPutObject(request).url().toExternalForm();
    }

    @Override
    public StoredObject head(String key) {
        var result = client.headObject(HeadObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
        return new StoredObject(result.contentLength(), result.contentType());
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(properties.getBucket())
                .sourceKey(sourceKey)
                .destinationBucket(properties.getBucket())
                .destinationKey(targetKey)
                .build());
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
    }

    @Override
    public String presignDownload(String key, String fileName, Duration ttl) {
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
        var get = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .responseContentDisposition(contentDisposition)
                .build();
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build();
        return presigner.presignGetObject(request).url().toExternalForm();
    }
}
