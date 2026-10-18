package com.breathAI.ttobagi_server.domain.dashboard.repository;

import com.breathAI.ttobagi_server.domain.dashboard.entity.UsageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 사용량 통계 리포지토리
public interface UsageStatRepository extends JpaRepository<UsageStat, Long> {

    Optional<UsageStat> findByStatDate_StatDate(LocalDate statDate);

    @Query("SELECT u FROM UsageStat u JOIN FETCH u.statDate " +
           "WHERE u.statDate.statDate BETWEEN :startDate AND :endDate " +
           "ORDER BY u.statDate.statDate ASC")
    List<UsageStat> findByStatDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT u FROM UsageStat u JOIN FETCH u.statDate " +
           "WHERE u.statDate.statYear = :year AND u.statDate.statMonth = :month " +
           "ORDER BY u.statDate.statDate ASC")
     List<UsageStat> findByYearAndMonth(
             @Param("year") int year, 
             @Param("month") int month);

    Optional<UsageStat> findTopByOrderByStatDate_StatDateDesc();
}