package com.lautung.oa.auth.profile;

import com.lautung.oa.storage.StorageS3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 头像的 S3 实现（#254）：复用 {@code lautung-oa-storage} 装配的私有附件桶，
 * 对象键由 {@link ProfileAvatarPaths} 生成（{@code avatar/{userId}}），固定键覆盖式写入，
 * 不新增桶、不改桶策略、不经浏览器直传。
 */
@Component
public class S3ProfileAvatarStore implements ProfileAvatarStore {

    private final S3Client s3Client;
    private final StorageS3Properties properties;

    public S3ProfileAvatarStore(S3Client attachmentS3Client, StorageS3Properties properties) {
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
}
