package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.SurgeDetection;
import com.breathAI.ttobagi_server.domain.faq.entity.SurgeStat;
import com.breathAI.ttobagi_server.domain.faq.entity.SurgeStat.PeriodType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDate;

// 급증 탐지 기간별 통계 리포지토리
public interface SurgeStatRepository extends JpaRepository<SurgeStat, Long> {

    // 특정 탐지 결과에 대한 통계 전체 조회
    List<SurgeStat> findBySurgeDetection(SurgeDetection surgeDetection);
    List<SurgeStat> findBySurgeDetectionSurgeId(Long surgeId);

    // 기간 유형별 필터링
    List<SurgeStat> findByPeriodType(PeriodType periodType);

    // 특정 날짜의 급증 통계 조회
    List<SurgeStat> findByStatDateStatDate(LocalDate statDate);

    // 특정 분석 회차에 속한 모든 급증 통계 데이터 추출
    List<SurgeStat> findBySurgeDetectionAnalysisJobAnalysisId(Long analysisId);

}