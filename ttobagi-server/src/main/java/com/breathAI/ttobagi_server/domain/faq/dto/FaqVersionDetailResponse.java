package com.breathAI.ttobagi_server.domain.faq.dto;

import com.breathAI.ttobagi_server.domain.faq.entity.FaqEditHistory.EditType;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqVersion.VersionType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
// FAQ 버전 상세 조회 응답, 이 버전에 묶인 변경 목록 포함
public class FaqVersionDetailResponse {
    private Long versionId;
    private String versionName;
    private VersionType versionType;
    private Integer totalFaqCount;
    private Integer createdCount;
    private Integer expandedCount;
    private Integer manualCount;
    private Integer deletedCount;
    private Long previousVersionId;

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

    private List<ChangeItem> changes;

    @Getter
    @Builder
    // 버전에 묶인 변경 단건
    public static class ChangeItem {
        private Long historyId;
        private Long faqId;
        private EditType editType;
        private Integer qType;
        private String standardQuestion;
    }
}
