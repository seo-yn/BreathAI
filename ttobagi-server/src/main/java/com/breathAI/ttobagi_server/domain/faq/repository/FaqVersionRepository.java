package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// FAQ 버전 리포지토리
public interface FaqVersionRepository extends JpaRepository<FaqVersion, Long> {

    // 가장 최근 버전
    Optional<FaqVersion> findTopByOrderByVersionIdDesc();

    // 버전 목록 최신순 페이징
    @EntityGraph(attributePaths = {"createdBy"})
    Page<FaqVersion> findAllByOrderByVersionIdDesc(Pageable pageable);

    // 회원 탈퇴 시 사용자 참조 해제
    @Modifying
    @Query("UPDATE FaqVersion v SET v.createdBy = null WHERE v.createdBy = :user")
    void clearCreatedBy(@Param("user") User user);
}
