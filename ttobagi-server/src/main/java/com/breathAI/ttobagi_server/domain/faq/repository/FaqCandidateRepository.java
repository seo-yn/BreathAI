package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate.CandidateType;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import com.breathAI.ttobagi_server.domain.auth.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// AI 생성 FAQ 후보 리포지토리
public interface FaqCandidateRepository extends JpaRepository<FaqCandidate, Long> {

    // 특정 분석 결과에 속한 후보 전체 조회
    List<FaqCandidate> findByAnalysisJob(AnalysisJob analysisJob);
    List<FaqCandidate> findByAnalysisJobAnalysisId(Long analysisId);

    // 상태별 필터링
    List<FaqCandidate> findByReviewStatus(ReviewStatus reviewStatus);

    // 분석 ID + 상태 조회 필터링
    List<FaqCandidate> findByAnalysisJobAnalysisIdAndReviewStatus(Long analysisId, ReviewStatus reviewStatus);

    // 유형별 필터링
    List<FaqCandidate> findByCandidateType(CandidateType candidateType);

    // 질문 텍스트 키워드 검색
    List<FaqCandidate> findByStandardQuestionContaining(String keyword);

    // 특정 군집 기반 조회
    List<FaqCandidate> findByClusterClusterId(Long clusterId);

    // 회원 탈퇴 시 사용자 참조 해제
    @Modifying
    @Query("UPDATE FaqCandidate f SET f.reviewedBy = null WHERE f.reviewedBy = :user")
    void clearReviewedBy(@Param("user") User user);

}