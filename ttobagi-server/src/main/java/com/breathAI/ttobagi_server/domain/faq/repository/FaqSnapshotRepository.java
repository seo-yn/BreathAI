package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

// FAQ 스냅샷 리포지토리
public interface FaqSnapshotRepository extends JpaRepository<FaqSnapshot, Long> {

    // 특정 분석 작업에 포함된 전체 스냅샷 조회 (분석 결과 렌더링용)
    List<FaqSnapshot> findByAnalysisJob(AnalysisJob analysisJob);

    // 분석 ID를 직접 이용한 조회 (JOIN 최적화)
    List<FaqSnapshot> findByAnalysisJob_AnalysisId(Long analysisId);

    // 분석 ID 기준 페이징 조회 (FAQ 리스트용)
    Page<FaqSnapshot> findByAnalysisJob_AnalysisId(Long analysisId, Pageable pageable);

    // 특정 분석 시점의 특정 문항 상태 조회 (복합 UK 조건 충족)
    Optional<FaqSnapshot> findByAnalysisJob_AnalysisIdAndSourceSeqNum(Long analysisId, Integer sourceSeqNum);

    // 과거 버전 내 키워드 검색
    List<FaqSnapshot> findByAnalysisJob_AnalysisIdAndCiQuestionContaining(Long analysisId, String keyword);

    // apply 후 신규 sourceSeqNum 채번용
    @Query("SELECT COALESCE(MAX(s.sourceSeqNum), 0) FROM FaqSnapshot s")
    Integer findMaxSourceSeqNum();
}