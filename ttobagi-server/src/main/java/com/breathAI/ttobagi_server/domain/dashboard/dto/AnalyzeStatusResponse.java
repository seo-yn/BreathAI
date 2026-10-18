package com.breathAI.ttobagi_server.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 분석 진행 상태 조회 응답
public class AnalyzeStatusResponse {
    private String uploadId;
    private Long analysisId;
    private String status;
    private Integer progress;
    private String message;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC"
    )
    private LocalDateTime updatedAt;
}