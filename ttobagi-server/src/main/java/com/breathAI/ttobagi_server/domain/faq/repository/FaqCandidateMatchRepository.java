package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidateMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import java.math.BigDecimal;

// FAQ 후보-기존 FAQ 매칭 리포지토리
public interface FaqCandidateMatchRepository extends JpaRepository<FaqCandidateMatch, Long> {

    // 특정 후보의 모든 매칭 정보
    List<FaqCandidateMatch> findByCandidate(FaqCandidate candidate);

    // 분석 단위 유사 FAQ 매칭 전체 조회, 점수 높은 순
    List<FaqCandidateMatch> findByCandidate_AnalysisJob_AnalysisIdOrderByMatchScoreDesc(Long analysisId);

    // 점수 높은 순 정렬
    List<FaqCandidateMatch> findByCandidateCandidateIdOrderByMatchScoreDesc(Long candidateId);

    // 특정 FAQ 문항 기준 역조회
    List<FaqCandidateMatch> findByMatchedFaqSeqNum(Integer matchedFaqSeqNum);

    // 점수 임계치 기반 필터링
    List<FaqCandidateMatch> findByCandidateAndMatchScoreGreaterThanEqual(FaqCandidate candidate, BigDecimal threshold);

}