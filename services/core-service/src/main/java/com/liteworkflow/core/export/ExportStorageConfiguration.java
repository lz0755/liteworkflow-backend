package com.liteworkflow.core.export;

import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.S3ObjectStorage;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
public class ExportStorageConfiguration {

    @Bean
    S3Client exportS3Client(CoreExportProperties properties) {
        CoreExportProperties.S3 config = properties.getS3();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle())
                        .build());
        URI endpoint = config.getEndpoint();
        if (endpoint != null && !endpoint.toString().isBlank()) builder.endpointOverride(endpoint);
        if (hasStaticCredentials(config)) {
            builder.credentialsProvider(staticCredentials(config));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    S3Presigner exportS3Presigner(CoreExportProperties properties) {
        CoreExportProperties.S3 config = properties.getS3();
        var builder = S3Presigner.builder()
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle())
                        .build());
        URI endpoint = config.getEndpoint();
        if (endpoint != null && !endpoint.toString().isBlank()) builder.endpointOverride(endpoint);
        if (hasStaticCredentials(config)) {
            builder.credentialsProvider(staticCredentials(config));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    ObjectStorage exportObjectStorage(
            S3Client exportS3Client,
            S3Presigner exportS3Presigner,
            CoreExportProperties properties) {
        return new S3ObjectStorage(exportS3Client, exportS3Presigner, properties.getS3().getBucket());
    }

    private boolean hasStaticCredentials(CoreExportProperties.S3 config) {
        if (config.getAccessKey() == null || config.getAccessKey().isBlank()) return false;
        if (config.getSecretKey() == null || config.getSecretKey().isBlank()) {
            throw new IllegalArgumentException("S3 secret key is required with an access key");
        }
        return true;
    }

    private StaticCredentialsProvider staticCredentials(CoreExportProperties.S3 config) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                config.getAccessKey(), config.getSecretKey()));
    }
}
