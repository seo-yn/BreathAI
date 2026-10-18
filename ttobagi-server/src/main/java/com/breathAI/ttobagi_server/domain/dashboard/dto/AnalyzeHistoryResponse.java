package com.breathAI.ttobagi_server.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.List;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 분석 이력 목록 응답
public class AnalyzeHistoryResponse {
    private List<HistoryItem> history;
    private long totalCount;
    private int totalPages;
    private int currentPage;

    @Getter
    @Builder
    public static class HistoryItem {
        private Long analysisId;
        private String fileName;
        private String status;
        private String period;

        @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
            timezone = "UTC"
        )
        private LocalDateTime submittedAt;
    }
}
