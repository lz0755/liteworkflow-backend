package com.liteworkflow.infra.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("liteworkflow.storage")
public class FileStorageProperties {

    private final S3 s3 = new S3();
    private final Limits limits = new Limits();
    private String coreServiceUrl = "http://localhost:8082";
    private String internalToken = "change-me-internal-token";
    private boolean schedulingEnabled = true;

    public S3 getS3() { return s3; }
    public Limits getLimits() { return limits; }
    public String getCoreServiceUrl() { return coreServiceUrl; }
    public void setCoreServiceUrl(String value) { this.coreServiceUrl = value; }
    public String getInternalToken() { return internalToken; }
    public void setInternalToken(String value) { this.internalToken = value; }
    public boolean isSchedulingEnabled() { return schedulingEnabled; }
    public void setSchedulingEnabled(boolean value) { this.schedulingEnabled = value; }

    public static class S3 {
        private String bucket = "liteworkflow";
        private String region = "us-east-1";
        private URI endpoint;
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = true;

        public String getBucket() { return bucket; }
        public void setBucket(String value) { this.bucket = value; }
        public String getRegion() { return region; }
        public void setRegion(String value) { this.region = value; }
        public URI getEndpoint() { return endpoint; }
        public void setEndpoint(URI value) { this.endpoint = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String value) { this.accessKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { this.secretKey = value; }
        public boolean isPathStyle() { return pathStyle; }
        public void setPathStyle(boolean value) { this.pathStyle = value; }
    }

    public static class Limits {
        private long avatarBytes = 5L * 1024 * 1024;
        private long iconBytes = 5L * 1024 * 1024;
        private long attachmentBytes = 25L * 1024 * 1024;
        private long documentBytes = 50L * 1024 * 1024;

        public long getAvatarBytes() { return avatarBytes; }
        public void setAvatarBytes(long value) { this.avatarBytes = positive(value); }
        public long getIconBytes() { return iconBytes; }
        public void setIconBytes(long value) { this.iconBytes = positive(value); }
        public long getAttachmentBytes() { return attachmentBytes; }
        public void setAttachmentBytes(long value) { this.attachmentBytes = positive(value); }
        public long getDocumentBytes() { return documentBytes; }
        public void setDocumentBytes(long value) { this.documentBytes = positive(value); }
        private long positive(long value) {
            if (value < 1) throw new IllegalArgumentException("file size limits must be positive");
            return value;
        }
    }
}
