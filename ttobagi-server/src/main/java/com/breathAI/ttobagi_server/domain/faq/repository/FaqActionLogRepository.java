package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.FaqActionLog;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqActionLog.Action;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate;
import com.breathAI.ttobagi_server.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query; 
import org.springframework.data.repository.query.Param;

import java.util.List;

// FAQ 후보 검토 이력 리포지토리
public interface FaqActionLogRepository extends JpaRepository<FaqActionLog, Long> {

    // 객체 기반 조회
    List<FaqActionLog> findByCandidate(FaqCandidate candidate);

    // ID 기반 조회 + 최신순 정렬
    List<FaqActionLog> findByCandidateCandidateIdOrderByCreatedAtDesc(Long candidateId);

    // 특정 액션별 조회 + 최신순 정렬
    List<FaqActionLog> findByActionOrderByCreatedAtDesc(Action action);

    // 회원 탈퇴 시 사용자 참조 해제
    @Modifying
    @Query("UPDATE FaqActionLog f SET f.actedBy = null WHERE f.actedBy = :user")
    void clearActedBy(@Param("user") User user);
}