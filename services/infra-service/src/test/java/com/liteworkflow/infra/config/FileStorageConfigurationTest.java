package com.liteworkflow.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class FileStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FileStorageConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder);

    @Test
    void awsModeStartsWithNoEndpointStaticCredentialsOrPathStyleAccess() {
        contextRunner.withPropertyValues(
                        "liteworkflow.storage.s3.bucket=aws-contract-bucket",
                        "liteworkflow.storage.s3.region=ap-southeast-2",
                        "liteworkflow.storage.s3.endpoint=",
                        "liteworkflow.storage.s3.access-key=",
                        "liteworkflow.storage.s3.secret-key=",
                        "liteworkflow.storage.s3.path-style=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    FileStorageProperties properties = context.getBean(FileStorageProperties.class);
                    assertThat(properties.getS3().getBucket()).isEqualTo("aws-contract-bucket");
                    assertThat(properties.getS3().getRegion()).isEqualTo("ap-southeast-2");
                    assertThat(properties.getS3().getEndpoint()).isNull();
                    assertThat(properties.getS3().isPathStyle()).isFalse();
                });
    }
}
