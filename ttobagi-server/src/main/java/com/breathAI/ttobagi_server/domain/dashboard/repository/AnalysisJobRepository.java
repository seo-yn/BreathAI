package com.breathAI.ttobagi_server.domain.dashboard.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.dashboard.entity.UploadFile;
import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

// 분석 작업 리포지토리, 조회 시 업로드 파일을 함께 로딩
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, Long> {
    
    @EntityGraph(attributePaths = {"uploadFile"})
    Optional<AnalysisJob> findByUploadFileUploadId(String uploadId);

    @EntityGraph(attributePaths = {"uploadFile"})
    Page<AnalysisJob> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"uploadFile"})
    Optional<AnalysisJob> findFirstByStatusOrderByCreatedAtDesc(Status status);

    @EntityGraph(attributePaths = {"uploadFile"})
    Optional<AnalysisJob> findFirstByStatusOrderByFinishedAtDesc(Status status);

    @EntityGraph(attributePaths = {"uploadFile"})
    Page<AnalysisJob> findByStatusOrderByCreatedAtDesc(Status status, Pageable pageable);

    @EntityGraph(attributePaths = {"uploadFile"})
    @Query("SELECT a FROM AnalysisJob a WHERE a.status = :status " +
        "AND a.periodStartDate >= :startDate AND a.periodEndDate <= :endDate " +
        "ORDER BY a.finishedAt DESC")
    List<AnalysisJob> findByStatusAndPeriod(
            @Param("status") Status status,
            @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    Optional<AnalysisJob> findByAnalysisId(Long analysisId);

    Optional<AnalysisJob> findByUploadFileAndPeriodStartDateAndPeriodEndDate(
        UploadFile uploadFile, LocalDate periodStartDate, LocalDate periodEndDate);
    
        Optional<AnalysisJob> findFirstByUploadFileOrderByCreatedAtDesc(UploadFile uploadFile);

    boolean existsByStatusIn(List<Status> statuses);
}