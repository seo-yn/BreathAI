package com.breathAI.ttobagi_server.global.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

// Object Storage 클라이언트 설정, storage.enabled=true일 때만 만든다 (로컬·테스트에서는 꺼져 있음)
@Configuration
@ConditionalOnProperty(prefix = "storage", name = "enabled", havingValue = "true")
public class ObjectStorageConfig {

    @Bean
    public S3Client s3Client(ObjectStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                // S3 호환 서비스는 버킷을 주소 경로에 넣는 방식을 쓴다
                .forcePathStyle(true)
                // SDK 2.30부터 기본으로 붙는 추가 체크섬을 지원하지 않는 S3 호환 서비스가 있어 필요할 때만 쓴다
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }
}
