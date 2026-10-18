package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate;
import com.breathAI.ttobagi_server.domain.faq.entity.PerformanceCandidateMap;
import com.breathAI.ttobagi_server.domain.faq.entity.PerformanceComparison;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 성능 비교-FAQ 후보 매핑 리포지토리
public interface PerformanceCandidateMapRepository extends JpaRepository<PerformanceCandidateMap, Long> {

    // 특정 성능 비교 결과에 매핑된 모든 정보 조회
    List<PerformanceCandidateMap> findByComparison(PerformanceComparison comparison);
    List<PerformanceCandidateMap> findByComparisonComparisonId(Long comparisonId);

    // 특정 FAQ 후보가 포함된 모든 성능 비교 정보 조회
    List<PerformanceCandidateMap> findByCandidate(FaqCandidate candidate);

    // 후보 ID로 직접 조회
    List<PerformanceCandidateMap> findByCandidateCandidateId(Long candidateId);

    // 특정 비교 결과에 후보가 포함되어 있는지 확인
    boolean existsByComparisonComparisonIdAndCandidateCandidateId(Long comparisonId, Long candidateId);
}