package com.breathAI.ttobagi_server.domain.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 통계 기준 날짜 마스터
@Entity
@Getter
@Table(name = "gold_stat_date", comment = "통계 기준 날짜 마스터 테이블")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatDate {
    
    @Id
    @Column(name = "stat_date", nullable = false, columnDefinition = "DATE COMMENT '통계 기준 날짜 (PK)'")
    private LocalDate statDate;

    @Column(name = "stat_year", nullable = false, columnDefinition = "INT COMMENT '연도 (추출값)'")
    private Integer statYear;

    @Column(name = "stat_month", nullable = false, columnDefinition = "INT COMMENT '월 (추출값)'")
    private Integer statMonth;

    @Builder
    public StatDate(LocalDate statDate) {
        this.statDate = statDate;
        this.statYear = statDate.getYear();
        this.statMonth = statDate.getMonthValue();
    }
}