package com.liteworkflow.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("liteworkflow.ai.rag")
public class RagProperties {

    private boolean enabled = true;
    @NotBlank private String embeddingModel;
    @NotNull @Min(1) private Integer embeddingDimensions;
    private boolean startupValidation = true;
    @Min(1) @Max(100) private int topK = 8;
    @DecimalMin("0.0") @DecimalMax("1.0") private double similarityThreshold = 0.72;
    @Min(500) @Max(800) private int documentChunkTokens = 650;
    @Min(80) @Max(120) private int documentChunkOverlapTokens = 100;
    @Min(1) private int maxDocumentChunks = 10_000;
    @Min(1) private int maxExtractedCharacters = 5_000_000;
    @Min(1) private long maxDocumentBytes = 52_428_800;
    @Min(1) private int indexMaxAttempts = 3;
    @NotNull private Duration indexRetryInitialInterval = Duration.ofMillis(500);
    @NotNull private Duration indexProcessingLease = Duration.ofMinutes(5);
    @Valid private final S3 s3 = new S3();

    @AssertTrue(message = "document chunk overlap must be smaller than document chunk size")
    public boolean isChunkOverlapValid() {
        return documentChunkOverlapTokens < documentChunkTokens;
    }

    @AssertTrue(message = "embedding model and dimensions are required when RAG is enabled")
    public boolean isEmbeddingConfigurationComplete() {
        return !enabled || (resolved(embeddingModel) && embeddingDimensions != null && embeddingDimensions > 0);
    }

    @AssertTrue(message = "index processing lease must be positive")
    public boolean isIndexProcessingLeaseValid() {
        return indexProcessingLease != null && !indexProcessingLease.isZero() && !indexProcessingLease.isNegative();
    }

    private static boolean resolved(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.strip().toLowerCase();
        return !normalized.contains("${") && !normalized.startsWith("replace_me")
                && !normalized.startsWith("replace-me");
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(Integer embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
    public boolean isStartupValidation() { return startupValidation; }
    public void setStartupValidation(boolean startupValidation) { this.startupValidation = startupValidation; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public int getDocumentChunkTokens() { return documentChunkTokens; }
    public void setDocumentChunkTokens(int value) { this.documentChunkTokens = value; }
    public int getDocumentChunkOverlapTokens() { return documentChunkOverlapTokens; }
    public void setDocumentChunkOverlapTokens(int value) { this.documentChunkOverlapTokens = value; }
    public int getMaxDocumentChunks() { return maxDocumentChunks; }
    public void setMaxDocumentChunks(int maxDocumentChunks) { this.maxDocumentChunks = maxDocumentChunks; }
    public int getMaxExtractedCharacters() { return maxExtractedCharacters; }
    public void setMaxExtractedCharacters(int value) { this.maxExtractedCharacters = value; }
    public long getMaxDocumentBytes() { return maxDocumentBytes; }
    public void setMaxDocumentBytes(long value) { this.maxDocumentBytes = value; }
    public int getIndexMaxAttempts() { return indexMaxAttempts; }
    public void setIndexMaxAttempts(int indexMaxAttempts) { this.indexMaxAttempts = indexMaxAttempts; }
    public Duration getIndexRetryInitialInterval() { return indexRetryInitialInterval; }
    public void setIndexRetryInitialInterval(Duration value) { this.indexRetryInitialInterval = value; }
    public Duration getIndexProcessingLease() { return indexProcessingLease; }
    public void setIndexProcessingLease(Duration value) { this.indexProcessingLease = value; }
    public S3 getS3() { return s3; }

    public static class S3 {
        @NotBlank private String bucket = "liteworkflow";
        @NotBlank private String region = "us-east-1";
        private URI endpoint;
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;

        @AssertTrue(message = "S3 secret key is required with an access key")
        public boolean isCredentialsComplete() {
            return accessKey == null || accessKey.isBlank() || (secretKey != null && !secretKey.isBlank());
        }

        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public boolean isPathStyle() { return pathStyle; }
        public void setPathStyle(boolean pathStyle) { this.pathStyle = pathStyle; }
    }
}
