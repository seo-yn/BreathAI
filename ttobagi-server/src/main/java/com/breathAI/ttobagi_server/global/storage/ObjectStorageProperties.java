package com.breathAI.ttobagi_server.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

// NCP Object Storage(S3 호환) 설정 (환경변수 TTOBAGI_STORAGE_*)
@ConfigurationProperties(prefix = "storage")
public record ObjectStorageProperties(
        boolean enabled,
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String uploadBucket) {
}
