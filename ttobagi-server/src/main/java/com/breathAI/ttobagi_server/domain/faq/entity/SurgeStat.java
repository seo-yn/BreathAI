package com.breathAI.ttobagi_server.domain.faq.entity;

import com.breathAI.ttobagi_server.domain.dashboard.entity.StatDate;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// 급증 탐지 기간별 건수
@Entity
@Getter
@Table(
    name = "gold_surge_stat",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_surge_date_type",
            columnNames = {"surge_id", "stat_date", "period_type"}
        )
    },
    comment = "급증 탐지 기간별 건수"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurgeStat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "surge_stat_id")
    private Long surgeStatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surge_id", nullable = false, foreignKey = @ForeignKey(name = "fk_surge_stat_surge_id"))
    private SurgeDetection surgeDetection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stat_date", referencedColumnName = "stat_date", nullable = false, 
                foreignKey = @ForeignKey(name = "fk_surge_stat_date"))
    private StatDate statDate;

    @Column(
        name = "period_type", 
        nullable = false, 
        length = 10, 
        columnDefinition = "VARCHAR(10) NOT NULL COMMENT 'BASE(기준기간) / PREV(비교기간)'"
    )
    @Enumerated(EnumType.STRING)
    private PeriodType periodType;

    @Column(name = "count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '발생 건수'")
    @ColumnDefault("0")
    private Integer count; 

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public enum PeriodType {
        BASE, PREV
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.count == null) this.count = 0;
    }

    @Builder
    public SurgeStat(SurgeDetection surgeDetection, StatDate statDate,
                     PeriodType periodType, Integer count) {
        this.surgeDetection = surgeDetection;
        this.statDate = statDate;
        this.periodType = periodType;
        this.count = count;
    }

}
