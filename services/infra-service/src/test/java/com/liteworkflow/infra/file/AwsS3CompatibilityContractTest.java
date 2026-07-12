package com.liteworkflow.infra.file;

import java.net.URI;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_S3_CONTRACT_ENABLED", matches = "true")
class AwsS3CompatibilityContractTest extends AbstractS3CompatibilityContractTest {
    protected String bucket() { return required("AWS_S3_CONTRACT_BUCKET"); }
    protected String region() { return System.getenv().getOrDefault("AWS_REGION", "us-east-1"); }
    protected URI endpoint() { return null; }
    protected boolean pathStyle() { return false; }
    protected String accessKey() { return ""; }
    protected String secretKey() { return ""; }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
