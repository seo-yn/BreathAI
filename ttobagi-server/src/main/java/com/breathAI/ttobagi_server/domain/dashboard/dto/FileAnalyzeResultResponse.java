package com.breathAI.ttobagi_server.domain.dashboard.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// 업로드 파일 단위 분석 결과 응답
public class FileAnalyzeResultResponse {

    private Long analysisId;
    private String fileName;
    private String status;
    private String periodStartDate;
    private String periodEndDate;
    private AnalyzeResultResponse.SystemStatus systemStatus;
    private AnalyzeResultResponse.PerformanceMetrics performanceMetrics;
    private List<AnalyzeResultResponse.TrendItem> trend;
    private List<AnalyzeResultResponse.BurstKeyword> burstKeywords;
    private AnalyzeResultResponse.UnansweredAnalysis unanswerAnalysis;
    private AnalyzeResultResponse.ClusteringView clusteringView;
    private List<AnalyzeResultResponse.FaqCandidate> faqCandidates;
    private AnalyzeResultResponse.Evaluation evaluation;
    private List<AnalyzeResultResponse.SurgeDetection> surgeDetection;
}