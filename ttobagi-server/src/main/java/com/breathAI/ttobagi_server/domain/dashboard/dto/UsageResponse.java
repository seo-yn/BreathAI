package com.breathAI.ttobagi_server.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 챗봇 사용량 조회 응답, 월별 합계와 일별 추이
public class UsageResponse {
    private long totalUsageCount;
    private double increaseRate;
    private List<DailyUsage> dailyUsage;

    @Getter
    @Builder
    public static class DailyUsage {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate date; 
        private int count;
    }
}