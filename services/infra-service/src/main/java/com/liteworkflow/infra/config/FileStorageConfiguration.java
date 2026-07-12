package com.liteworkflow.infra.config;

import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.S3ObjectStorage;
import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {

    @Bean
    S3Client s3Client(FileStorageProperties properties) {
        var config = properties.getS3();
        var builder = S3Client.builder()
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle()).build());
        configure(builder, config);
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(FileStorageProperties properties) {
        var config = properties.getS3();
        var builder = S3Presigner.builder()
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyle()).build());
        if (config.getEndpoint() != null && !config.getEndpoint().toString().isBlank()) {
            builder.endpointOverride(config.getEndpoint());
        }
        if (hasStaticCredentials(config)) {
            builder.credentialsProvider(staticCredentials(config));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    ObjectStorage objectStorage(
            S3Client s3Client, S3Presigner presigner, FileStorageProperties properties) {
        return new S3ObjectStorage(s3Client, presigner, properties.getS3().getBucket());
    }

    @Bean
    RestClient coreRestClient(RestClient.Builder builder, FileStorageProperties properties) {
        return builder.baseUrl(properties.getCoreServiceUrl()).build();
    }

    private void configure(S3ClientBuilder builder, FileStorageProperties.S3 config) {
        URI endpoint = config.getEndpoint();
        if (endpoint != null && !endpoint.toString().isBlank()) {
            builder.endpointOverride(endpoint);
        }
        if (hasStaticCredentials(config)) {
            builder.credentialsProvider(staticCredentials(config));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
    }

    private boolean hasStaticCredentials(FileStorageProperties.S3 config) {
        if (config.getAccessKey() == null || config.getAccessKey().isBlank()) return false;
        if (config.getSecretKey() == null || config.getSecretKey().isBlank()) {
            throw new IllegalArgumentException("S3 secret key is required with an access key");
        }
        return true;
    }

    private StaticCredentialsProvider staticCredentials(FileStorageProperties.S3 config) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                config.getAccessKey(), config.getSecretKey()));
    }
}
