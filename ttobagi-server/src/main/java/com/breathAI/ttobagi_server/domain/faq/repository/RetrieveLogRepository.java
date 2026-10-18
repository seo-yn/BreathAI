package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.RetrieveLog;
import com.breathAI.ttobagi_server.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query; 
import org.springframework.data.repository.query.Param;


import java.util.List;

// FAQ 검색 로그 리포지토리
public interface RetrieveLogRepository extends JpaRepository<RetrieveLog, Long> {

    // 특정 분석 작업 기반 조회
    List<RetrieveLog> findByAnalysisJobAnalysisId(Long analysisId);

    // 특정 사용자 기반 조회
    List<RetrieveLog> findByUserUserId(Long userId);

    // 특정 FAQ 문항이 반환된 이력 조회
    List<RetrieveLog> findByReturnedFaqSeqNum(Integer returnedFaqSeqNum);

    // 회원 탈퇴 시 사용자 참조 해제
    @Modifying
    @Query("UPDATE RetrieveLog r SET r.user = null WHERE r.user = :user")
    void clearUser(@Param("user") User user);

}