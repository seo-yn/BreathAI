package com.breathAI.ttobagi_server.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 파일 업로드 및 분석 시작 응답
public class NewAnalyzeResponse {
    private Long analysisId;
    private String uploadId;
    private String fileName;
    private String status;
    private String message;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC"
    )
    private LocalDateTime submittedAt;
}