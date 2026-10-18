package com.breathAI.ttobagi_server.domain.dashboard.service;

import com.breathAI.ttobagi_server.domain.dashboard.repository.UploadFileRepository;
import com.breathAI.ttobagi_server.global.storage.ObjectStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;
import java.util.Locale;

// 업로드 원본을 Object Storage에 보관하고 위치를 업로드 기록에 남긴다
// 원본 보관은 부가 기능이라 실패해도 분석 흐름에는 영향을 주지 않는다
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadArchiveService {

    private final ObjectProvider<S3Client> s3Client;
    private final ObjectStorageProperties properties;
    private final UploadFileRepository uploadFileRepository;

    @Async
    public void archive(String uploadId, byte[] content, String originalFileName) {
        S3Client client = s3Client.getIfAvailable();
        if (client == null) {
            return;
        }
        String key = objectKey(uploadId, originalFileName, LocalDate.now());
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.uploadBucket())
                            .key(key)
                            .build(),
                    RequestBody.fromBytes(content));
            uploadFileRepository.updateFilePath(uploadId, "s3://" + properties.uploadBucket() + "/" + key);
            log.info("업로드 원본 보관 완료 uploadId: {}, key: {}", uploadId, key);
        } catch (Exception e) {
            log.warn("업로드 원본 보관 실패, 분석은 그대로 진행 uploadId: {}", uploadId, e);
        }
    }

    // 파일 이름의 한글·공백 문제를 피하려고 업로드 ID와 확장자만 쓴다 (원래 이름은 DB에 있다)
    static String objectKey(String uploadId, String originalFileName, LocalDate date) {
        String name = originalFileName == null ? "" : originalFileName;
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
        return String.format("uploads/%d/%02d/%s%s", date.getYear(), date.getMonthValue(), uploadId, extension);
    }
}
