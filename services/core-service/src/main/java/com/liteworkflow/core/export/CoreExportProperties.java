package com.liteworkflow.core.export;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("liteworkflow.export")
public class CoreExportProperties {

    private int batchSize = 500;
    private Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"));
    private final Consumer consumer = new Consumer();
    private final S3 s3 = new S3();

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) {
        if (value < 1 || value > 5000) throw new IllegalArgumentException("batchSize must be between 1 and 5000");
        batchSize = value;
    }
    public Path getTempDirectory() { return tempDirectory; }
    public void setTempDirectory(Path value) {
        if (value == null) throw new IllegalArgumentException("tempDirectory must not be null");
        tempDirectory = value;
    }
    public Consumer getConsumer() { return consumer; }
    public S3 getS3() { return s3; }

    public static class Consumer {
        private int maxAttempts = 5;
        private Duration initialInterval = Duration.ofMillis(500);
        private double multiplier = 2.0;
        private Duration maxInterval = Duration.ofSeconds(5);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int value) {
            if (value < 1) throw new IllegalArgumentException("consumer.maxAttempts must be positive");
            maxAttempts = value;
        }
        public Duration getInitialInterval() { return initialInterval; }
        public void setInitialInterval(Duration value) { initialInterval = positive(value, "initialInterval"); }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double value) {
            if (value < 1.0) throw new IllegalArgumentException("consumer.multiplier must be at least 1");
            multiplier = value;
        }
        public Duration getMaxInterval() { return maxInterval; }
        public void setMaxInterval(Duration value) { maxInterval = positive(value, "maxInterval"); }

        private Duration positive(Duration value, String name) {
            if (value == null || value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }

    public static class S3 {
        private String bucket = "liteworkflow";
        private String region = "us-east-1";
        private URI endpoint;
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;

        public String getBucket() { return bucket; }
        public void setBucket(String value) { bucket = value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { region = value; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI value) { endpoint = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String value) { accessKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { secretKey = value; }
        public boolean isPathStyle() { return pathStyle; }
        public void setPathStyle(boolean value) { pathStyle = value; }
    }
}
