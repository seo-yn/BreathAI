package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.faq.entity.Cluster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

// 클러스터링 결과 리포지토리
public interface ClusterRepository extends JpaRepository<Cluster, Long> {

    // 기본 조회
    List<Cluster> findByAnalysisJob(AnalysisJob analysisJob);
    List<Cluster> findByAnalysisJobAnalysisId(Long analysisId);

    // 특정 라벨 조회
    Optional<Cluster> findByAnalysisJobAndClusterLabel(AnalysisJob analysisJob, Integer clusterLabel);

    // 클러스터 이름으로 검색
    List<Cluster> findByAnalysisJobAndClusterNameContaining(AnalysisJob analysisJob, String clusterName);

}