package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.faq.entity.PerformanceComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

// 미답변율 개선 전후 비교 리포지토리
public interface PerformanceComparisonRepository extends JpaRepository<PerformanceComparison, Long> {

    // 특정 분석 작업 기반 조회
    Optional<PerformanceComparison> findByAnalysisJob(AnalysisJob analysisJob);
    Optional<PerformanceComparison> findByAnalysisJob_AnalysisId(Long analysisId);

    // 날짜 기반 조회
    @Query("SELECT pc FROM PerformanceComparison pc " +
           "WHERE pc.usageStat.statDate.statDate = :statDate")
    Optional<PerformanceComparison> findByTargetDate(@Param("statDate") LocalDate statDate);

    // 가장 최신 성능 비교 결과 단건 조회
    Optional<PerformanceComparison> findTopByOrderByAnalysisJob_AnalysisIdDesc();

    // 실제 반영 후 결과가 아직 업데이트되지 않은 항목들 조회
    @Query("SELECT pc FROM PerformanceComparison pc WHERE pc.actualUnanswerCnt IS NULL")
    java.util.List<PerformanceComparison> findPendingEvaluationResults();

}