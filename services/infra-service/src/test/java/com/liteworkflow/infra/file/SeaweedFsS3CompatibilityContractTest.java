package com.liteworkflow.infra.file;

import java.net.URI;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "SEAWEEDFS_CONTRACT_ENABLED", matches = "true")
class SeaweedFsS3CompatibilityContractTest extends AbstractS3CompatibilityContractTest {
    protected String bucket() { return env("SEAWEEDFS_BUCKET", "liteworkflow"); }
    protected String region() { return "us-east-1"; }
    protected URI endpoint() { return URI.create(env("SEAWEEDFS_ENDPOINT", "http://localhost:8333")); }
    protected boolean pathStyle() { return true; }
    protected String accessKey() { return required("SEAWEEDFS_ACCESS_KEY"); }
    protected String secretKey() { return required("SEAWEEDFS_SECRET_KEY"); }
    private String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }
    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
