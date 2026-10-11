package com.breathAI.ttobagi_server.domain.faq.dto;

import com.breathAI.ttobagi_server.domain.faq.entity.FaqVersion.VersionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
// FAQ 버전 목록 조회 응답, 페이징 정보 포함
public class FaqVersionListResponse {
    private long totalCount;
    private List<VersionItem> versions;
    private int totalPages;
    private int currentPage;
    private int size;

    @Getter
    @Builder
    // 버전 단건 요약
    public static class VersionItem {
        private Long versionId;
        private String versionName;
        private VersionType versionType;
        private Integer totalFaqCount;
        private Integer createdCount;
        private Integer expandedCount;

        // boolean이면 응답에 latest가 함께 나가므로 Boolean으로 둔다
        @JsonProperty("isLatest")
        private Boolean isLatest;

        private String createdBy;

        @JsonFormat(
            shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
            timezone = "UTC"
        )
        private LocalDateTime createdAt;
    }
}
