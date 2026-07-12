package com.liteworkflow.core.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class ExportStorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void awsModeStartsWithNoEndpointStaticCredentialsOrPathStyleAccess() {
        contextRunner.withPropertyValues(
                        "liteworkflow.export.s3.bucket=aws-contract-bucket",
                        "liteworkflow.export.s3.region=ap-southeast-2",
                        "liteworkflow.export.s3.endpoint=",
                        "liteworkflow.export.s3.access-key=",
                        "liteworkflow.export.s3.secret-key=",
                        "liteworkflow.export.s3.path-style=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    CoreExportProperties properties = context.getBean(CoreExportProperties.class);
                    assertThat(properties.getS3().getBucket()).isEqualTo("aws-contract-bucket");
                    assertThat(properties.getS3().getRegion()).isEqualTo("ap-southeast-2");
                    assertThat(properties.getS3().getEndpoint()).isNull();
                    assertThat(properties.getS3().isPathStyle()).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CoreExportProperties.class)
    @Import(ExportStorageConfiguration.class)
    static class TestConfiguration {
    }
}
