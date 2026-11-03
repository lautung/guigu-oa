package com.lautung.oa.notice.image;

import com.lautung.oa.storage.StorageS3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 公告图片的 S3 实现（ADR 0055）：复用 {@code lautung-oa-storage} 装配的私有附件桶，
 * 对象键由 notice 模块自己生成（{@code notices/<uuid>}），不新增桶、不改桶策略。
 * 上传经服务端转存，不使用预签名直传。
 */
@Component
public class S3NoticeImageStore implements NoticeImageStore {

    private final S3Client s3Client;
    private final StorageS3Properties properties;

    public S3NoticeImageStore(S3Client attachmentS3Client, StorageS3Properties properties) {
        this.s3Client = attachmentS3Client;
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String objectKey) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .build())
                .asByteArray();
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
    }
}
