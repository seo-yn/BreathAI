package com.breathAI.ttobagi_server.domain.dashboard.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.dashboard.entity.UnansweredStat;
import com.breathAI.ttobagi_server.global.enums.UnansweredReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 미답변 사유별 집계 리포지토리
public interface UnansweredStatRepository extends JpaRepository<UnansweredStat, Long> {

    List<UnansweredStat> findByAnalysisJob_AnalysisId(Long analysisId);

    List<UnansweredStat> findByAnalysisJob(AnalysisJob analysisJob);

    List<UnansweredStat> findByAnalysisJob_AnalysisIdAndReason(Long analysisId, UnansweredReason reason);

    @Query("SELECT SUM(u.unanswerCount) FROM UnansweredStat u WHERE u.analysisJob.analysisId = :analysisId")
    Long sumUnanswerCountByAnalysisId(@Param("analysisId") Long analysisId);

}
