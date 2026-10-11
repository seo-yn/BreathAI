package com.breathAI.ttobagi_server.domain.faq.repository;

import com.breathAI.ttobagi_server.domain.faq.entity.Faq;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.breathAI.ttobagi_server.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query; 
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// 운영 FAQ 리포지토리
public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 활성 FAQ 목록 페이징
    Page<Faq> findByIsActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    // 활성 FAQ 수
    long countByIsActiveTrue();

    // 단건 조회 (활성만)
    Optional<Faq> findByFaqIdAndIsActiveTrue(Long faqId);

    // 후보 기반 조회 (apply 중복 방지)
    Optional<Faq> findByCandidate(FaqCandidate candidate);

    // 키워드 검색
    Page<Faq> findByIsActiveTrueAndQuestionContaining(String keyword, Pageable pageable);

    // 활성 FAQ 목록 검색
    // filterByQType이 false면 카테고리 조건을, keyword가 null이면 키워드 조건을 적용하지 않는다
    // 키워드는 질문, 답변, 키워드 목록에서 찾는다
    @Query("SELECT f FROM Faq f WHERE f.isActive = true "
            + "AND (:filterByQType = false OR f.qType IN :qTypes) "
            + "AND (:keyword IS NULL "
            + "     OR f.question LIKE CONCAT('%', :keyword, '%') "
            + "     OR f.answer LIKE CONCAT('%', :keyword, '%') "
            + "     OR f.keywords LIKE CONCAT('%', :keyword, '%'))")
    Page<Faq> search(@Param("filterByQType") boolean filterByQType,
                     @Param("qTypes") Collection<Integer> qTypes,
                     @Param("keyword") String keyword,
                     Pageable pageable);

    // 카테고리 표시명으로 카테고리 코드 조회
    // 카테고리 마스터는 AI 서버가 적재하는 Bronze 테이블이라 읽기만 한다
    @Query(value = "SELECT DISTINCT q_type FROM bronze_cate_info WHERE q_disp_name = :name",
            nativeQuery = true)
    List<Integer> findQTypesByCategoryName(@Param("name") String name);

    // 원본 FAQ 순번으로 활성 FAQ 단건 조회 (EXPAND 후보의 확장 대상)
    Optional<Faq> findFirstBySourceSeqNumAndIsActiveTrueOrderByFaqIdAsc(Integer sourceSeqNum);

    // 원본 FAQ 순번으로 조회 (후보의 유사 FAQ 연결용)
    List<Faq> findBySourceSeqNumIn(Collection<Integer> sourceSeqNums);

    // 회원 탈퇴 시 사용자 참조 해제
    @Modifying
    @Query("UPDATE Faq f SET f.createdBy = null WHERE f.createdBy = :user")
    void clearCreatedBy(@Param("user") User user);
}
