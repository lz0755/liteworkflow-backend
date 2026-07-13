package com.liteworkflow.ai.config;

import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.S3ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RagProperties.class)
@ConditionalOnProperty(prefix = "liteworkflow.ai.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagConfiguration {

    @Bean
    S3Client ragS3Client(RagProperties properties) {
        RagProperties.S3 config = properties.getS3();
        var builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle()).build());
        if (config.getEndpoint() != null) builder.endpointOverride(config.getEndpoint());
        if (hasStaticCredentials(config)) builder.credentialsProvider(credentials(config));
        else builder.credentialsProvider(DefaultCredentialsProvider.create());
        return builder.build();
    }

    @Bean
    S3Presigner ragS3Presigner(RagProperties properties) {
        RagProperties.S3 config = properties.getS3();
        var builder = S3Presigner.builder()
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle()).build());
        if (config.getEndpoint() != null) builder.endpointOverride(config.getEndpoint());
        if (hasStaticCredentials(config)) builder.credentialsProvider(credentials(config));
        else builder.credentialsProvider(DefaultCredentialsProvider.create());
        return builder.build();
    }

    @Bean
    ObjectStorage ragObjectStorage(
            S3Client ragS3Client, S3Presigner ragS3Presigner, RagProperties properties) {
        return new S3ObjectStorage(ragS3Client, ragS3Presigner, properties.getS3().getBucket());
    }

    private static boolean hasStaticCredentials(RagProperties.S3 config) {
        return config.getAccessKey() != null && !config.getAccessKey().isBlank();
    }

    private static StaticCredentialsProvider credentials(RagProperties.S3 config) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
    }
}
