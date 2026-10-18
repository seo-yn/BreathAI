package com.breathAI.ttobagi_server.domain.dashboard.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

// 업로드 파일(분석)별 날짜 통계 리포지토리
public interface AnalysisDailyStatRepository extends JpaRepository<AnalysisDailyStat, Long> {

    List<AnalysisDailyStat> findByAnalysisJob_AnalysisIdInOrderByStatDateAsc(Collection<Long> analysisIds);
}
