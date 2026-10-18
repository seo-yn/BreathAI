package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.faq.entity.SurgeDetection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 급증 질문 탐지 리포지토리
public interface SurgeDetectionRepository extends JpaRepository<SurgeDetection, Long> {

    // 특정 분석 작업 기반 조회
    List<SurgeDetection> findByAnalysisJob(AnalysisJob analysisJob);
    List<SurgeDetection> findByAnalysisJobAnalysisId(Long analysisId);

    // 신규 급증 키워드 전체 조회
    List<SurgeDetection> findByIsNewTrue();

    // 특정 분석 내에서 신규 키워드만 필터링
    List<SurgeDetection> findByAnalysisJobAnalysisIdAndIsNewTrue(Long analysisId);

    // 키워드 검색
    List<SurgeDetection> findByKeywordContaining(String keyword);
}