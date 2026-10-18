package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.Cluster;
import com.breathAI.ttobagi_server.domain.faq.entity.ClusterLogMap;
import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.faq.entity.ClusterLogMap.LogType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 클러스터-로그 매핑 리포지토리
public interface ClusterLogMapRepository extends JpaRepository<ClusterLogMap, Long> {

    // 기본 조회
    List<ClusterLogMap> findByCluster(Cluster cluster);

    // ID 기반 조회
    List<ClusterLogMap> findByClusterClusterId(Long clusterId);

    // 타입별 필터링
    List<ClusterLogMap> findByLogType(LogType logType);

    // 복합 조건 조회
    List<ClusterLogMap> findByClusterAndLogType(Cluster cluster, LogType logType);

    // 특정 군집에 속한 모든 매핑 삭제
    @Modifying
    @Transactional
    void deleteByClusterIn(List<Cluster> clusters);

    // 분석 단위로 전체 로그 조회
    List<ClusterLogMap> findAllByCluster_AnalysisJob(AnalysisJob analysisJob);

    // 분석 단위 로그 유형별 건수 집계
    long countByCluster_AnalysisJobAndLogType(AnalysisJob analysisJob, LogType logType);
}