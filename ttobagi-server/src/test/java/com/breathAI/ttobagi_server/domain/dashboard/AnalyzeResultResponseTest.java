package com.breathAI.ttobagi_server.domain.dashboard;

import com.breathAI.ttobagi_server.domain.dashboard.dto.AnalyzeResultResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 분석 결과 응답의 필드 이름 검증
class AnalyzeResultResponseTest {

    @Test
    @DisplayName("급증 감지의 신규 여부는 new가 아니라 isNew로 나간다")
    void surgeDetectionExposesIsNew() {
        AnalyzeResultResponse.SurgeDetection surge = AnalyzeResultResponse.SurgeDetection.builder()
                .keyword("환불")
                .isNew(true)
                .build();

        JsonNode json = new ObjectMapper().valueToTree(surge);

        assertTrue(json.get("isNew").asBoolean(), json.toString());
        assertFalse(json.has("new"), json.toString());
    }
}
