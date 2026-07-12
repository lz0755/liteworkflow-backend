package com.liteworkflow.infra.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.liteworkflow.common.file.storage.ObjectStorage;
import com.liteworkflow.common.file.storage.PutObjectRequest;
import com.liteworkflow.common.file.storage.S3ObjectStorage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** The same basic contract is executed unchanged against SeaweedFS and AWS S3. */
abstract class AbstractS3CompatibilityContractTest {
    protected abstract String bucket();
    protected abstract String region();
    protected abstract URI endpoint();
    protected abstract boolean pathStyle();
    protected abstract String accessKey();
    protected abstract String secretKey();

    @Test
    void supportsPortablePutHeadGetExistsDeleteBehavior() throws Exception {
        var clientBuilder = S3Client.builder().region(Region.of(region()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle()).build());
        var presignerBuilder = S3Presigner.builder().region(Region.of(region()));
        if (endpoint() != null) {
            clientBuilder.endpointOverride(endpoint());
            presignerBuilder.endpointOverride(endpoint());
        }
        if (accessKey() == null || accessKey().isBlank()) {
            clientBuilder.credentialsProvider(DefaultCredentialsProvider.create());
            presignerBuilder.credentialsProvider(DefaultCredentialsProvider.create());
        } else {
            var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey(), secretKey()));
            clientBuilder.credentialsProvider(credentials);
            presignerBuilder.credentialsProvider(credentials);
        }
        try (S3Client client = clientBuilder.build(); S3Presigner presigner = presignerBuilder.build()) {
            ObjectStorage storage = new S3ObjectStorage(client, presigner, bucket());
            String key = "contract-tests/" + UUID.randomUUID() + "/sample.txt";
            byte[] body = "s3-compatible".getBytes(StandardCharsets.UTF_8);
            try {
                storage.put(new PutObjectRequest(key, new ByteArrayInputStream(body), body.length,
                        "text/plain", Map.of("contract", "m8")));
                assertThat(storage.exists(key)).isTrue();
                assertThat(storage.metadata(key).contentLength()).isEqualTo(body.length);
                try (var content = storage.get(key)) {
                    assertThat(content.content().readAllBytes()).isEqualTo(body);
                }
            } finally {
                storage.delete(key);
            }
            assertThat(storage.exists(key)).isFalse();
        }
    }
}
