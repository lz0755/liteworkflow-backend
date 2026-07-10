package com.liteworkflow.common.file.storage;

import java.net.URI;
import java.time.Duration;

/** Provider-neutral storage contract implemented by SeaweedFS S3 or AWS S3 adapters. */
public interface ObjectStorage {

    ObjectMetadata put(PutObjectRequest request);

    ObjectContent get(String objectKey);

    ObjectMetadata metadata(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);

    URI presignGet(String objectKey, Duration validity);
}
