package com.liteworkflow.common.file.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3-compatible implementation deliberately limited to the common operations supported by
 * SeaweedFS S3 Gateway and AWS S3.
 */
public final class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.presigner = Objects.requireNonNull(presigner, "presigner must not be null");
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket must not be blank");
        }
        this.bucket = bucket;
    }

    @Override
    public ObjectMetadata put(PutObjectRequest request) {
        try {
            PutObjectResponse response = client.putObject(builder -> builder
                            .bucket(bucket)
                            .key(request.objectKey())
                            .contentLength(request.contentLength())
                            .contentType(request.contentType())
                            .metadata(request.metadata()),
                    RequestBody.fromInputStream(request.content(), request.contentLength()));
            return new ObjectMetadata(
                    request.objectKey(), request.contentLength(), request.contentType(), response.eTag(), null,
                    request.metadata());
        } catch (RuntimeException exception) {
            throw failure("put", request.objectKey(), exception);
        }
    }

    @Override
    public ObjectContent get(String objectKey) {
        String safeKey = ObjectKeys.requireSafe(objectKey);
        try {
            ResponseInputStream<GetObjectResponse> content = client.getObject(GetObjectRequest.builder()
                    .bucket(bucket).key(safeKey).build());
            GetObjectResponse response = content.response();
            return new ObjectContent(toMetadata(safeKey, response.contentLength(), response.contentType(),
                    response.eTag(), response.lastModified(), response.metadata()), content);
        } catch (RuntimeException exception) {
            throw failure("get", safeKey, exception);
        }
    }

    @Override
    public ObjectMetadata metadata(String objectKey) {
        String safeKey = ObjectKeys.requireSafe(objectKey);
        try {
            HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket).key(safeKey).build());
            return toMetadata(safeKey, response.contentLength(), response.contentType(), response.eTag(),
                    response.lastModified(), response.metadata());
        } catch (RuntimeException exception) {
            throw failure("head", safeKey, exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        String safeKey = ObjectKeys.requireSafe(objectKey);
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(safeKey).build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw failure("head", safeKey, exception);
        } catch (RuntimeException exception) {
            throw failure("head", safeKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        String safeKey = ObjectKeys.requireSafe(objectKey);
        try {
            client.deleteObject(builder -> builder.bucket(bucket).key(safeKey));
        } catch (RuntimeException exception) {
            throw failure("delete", safeKey, exception);
        }
    }

    @Override
    public URI presignGet(String objectKey, Duration validity) {
        String safeKey = ObjectKeys.requireSafe(objectKey);
        if (validity == null || validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("validity must be positive");
        }
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(validity)
                            .getObjectRequest(builder -> builder.bucket(bucket).key(safeKey))
                            .build())
                    .url().toURI();
        } catch (Exception exception) {
            throw failure("presign", safeKey, exception);
        }
    }

    private ObjectMetadata toMetadata(
            String key, Long length, String type, String etag, java.time.Instant modified, Map<String, String> metadata) {
        return new ObjectMetadata(key, length == null ? 0 : length,
                type == null ? "application/octet-stream" : type, etag, modified, metadata);
    }

    private ObjectStorageException failure(String operation, String key, Exception cause) {
        return new ObjectStorageException("S3 " + operation + " failed for object " + key, cause);
    }
}
