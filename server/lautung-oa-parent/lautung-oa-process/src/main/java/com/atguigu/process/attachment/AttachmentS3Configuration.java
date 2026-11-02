package com.atguigu.process.attachment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AttachmentS3Properties.class)
public class AttachmentS3Configuration {

    @Bean(destroyMethod = "close")
    public S3Client attachmentS3Client(AttachmentS3Properties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner attachmentS3Presigner(AttachmentS3Properties properties) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        String endpoint = StringUtils.hasText(properties.getPublicEndpoint())
                ? properties.getPublicEndpoint()
                : properties.getEndpoint();
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials(AttachmentS3Properties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }
}
