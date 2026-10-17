package com.breathAI.ttobagi_server.global.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

// AI 서버 호출 담당 유틸
@Slf4j
@Component
@RequiredArgsConstructor
public class WebClientUtil {

    // AI 서버는 파일을 받으면 바로 접수 응답을 주므로, 이 시간 안에 응답이 없으면 실패로 본다
    // 최대 50MB 파일 전송 시간을 고려해 넉넉히 잡았다
    private static final Duration PIPELINE_REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final WebClient webClient;

    // 업로드 파일을 AI 서버로 전달하여 분석 파이프라인 시작
    // 결과는 응답이 아닌 별도 콜백으로 수신하고, 요청 자체가 실패하면 onFailure를 실행한다
    @Async
    public void sendPipeline(
            String uploadId,
            Long analysisId,
            byte[] fileBytes,
            String fileName,
            Boolean isMaskingEnabled,
            Boolean isTranslationEnabled,
            String periodStartDate,
            String periodEndDate,
            Runnable onFailure) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("uploadId", uploadId);
            body.add("analysisId", analysisId.toString());
            body.add("isMaskingEnabled", isMaskingEnabled != null ? isMaskingEnabled.toString() : "true");
            body.add("isTranslationEnabled", isTranslationEnabled != null ? isTranslationEnabled.toString() : "false");
            body.add("periodStartDate", periodStartDate != null ? periodStartDate : "");
            body.add("periodEndDate", periodEndDate != null ? periodEndDate : "");
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });

            // 연결 실패, 4xx·5xx 응답, 시간 초과를 모두 실패로 처리한다
            webClient.post()
                    .uri("/pipeline")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(PIPELINE_REQUEST_TIMEOUT)
                    .subscribe(
                            response -> log.info("AI 서버 파이프라인 요청 접수 analysisId: {}, status: {}",
                                    analysisId, response.getStatusCode()),
                            e -> {
                                log.error("AI 서버 파이프라인 요청 실패 analysisId: {}, uploadId: {}", analysisId, uploadId, e);
                                onFailure.run();
                            });

        } catch (Exception e) {
            log.error("AI 서버 파이프라인 요청 실패 analysisId: {}, uploadId: {}", analysisId, uploadId, e);
            onFailure.run();
        }
    }
}
