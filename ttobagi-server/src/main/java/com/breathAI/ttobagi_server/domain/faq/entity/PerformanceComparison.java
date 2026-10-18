package com.breathAI.ttobagi_server.domain.faq.entity;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import com.breathAI.ttobagi_server.domain.dashboard.entity.UsageStat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 미답변율 개선 전후 비교
@Entity
@Getter
@Table(
    name = "gold_performance_comparison",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_analysis", 
            columnNames = {"analysis_id"}
        )
    },
    comment = "미답변율 개선 전·후 비교 (FAQ 후보 반영 효과 측정)"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceComparison {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comparison_id")
    private Long comparisonId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false, foreignKey = @ForeignKey(name = "fk_perf_comp_analysis_id"))
    private AnalysisJob analysisJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "before_stat_date", 
        referencedColumnName = "stat_date",
        nullable = false, 
        foreignKey = @ForeignKey(name = "fk_perf_comp_usage_stat"),
        columnDefinition = "DATE NOT NULL COMMENT '비교 기준이 되는 통계 날짜'"
    )
    private UsageStat usageStat;

    @Column(name = "after_unanswer_cnt", columnDefinition = "INT COMMENT '개선 후 예상 미답변 건수'")
    private Integer afterUnanswerCnt;

    @Column(name = "after_unanswer_rate", precision = 5, scale = 2, 
            columnDefinition = "DECIMAL(5,2) COMMENT '개선 후 예상 미답변율(%)'")
    private BigDecimal afterUnanswerRate;

    @Column(name = "accuracy_gain", precision = 5, scale = 2, 
            columnDefinition = "DECIMAL(5,2) COMMENT '예상 정확도 향상치(%)'")
    private BigDecimal accuracyGain;

    @Column(name = "false_positive_rate", precision = 4, scale = 3, 
            columnDefinition = "DECIMAL(4,3) COMMENT '오답 발생률'")
    private BigDecimal falsePositiveRate;

    @Column(name = "eval_threshold", precision = 4, scale = 3, 
            columnDefinition = "DECIMAL(4,3) COMMENT '적용 임계값 (예: 0.75)'")
    private BigDecimal evalThreshold;

    @Column(name = "actual_unanswer_cnt", columnDefinition = "INT COMMENT '실제 반영 후 미답변 건수'")
    private Integer actualUnanswerCnt;

    @Column(name = "resolved_count_by_ai", columnDefinition = "INT COMMENT 'AI를 통해 해결된 미답변 로그 수'")
    private Integer resolvedCountByAi;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Builder
    public PerformanceComparison(AnalysisJob analysisJob, UsageStat usageStat,
                                 Integer afterUnanswerCnt, BigDecimal afterUnanswerRate,
                                 BigDecimal accuracyGain, BigDecimal falsePositiveRate,
                                 BigDecimal evalThreshold, Integer resolvedCountByAi) {
        this.analysisJob = analysisJob;
        this.usageStat = usageStat;
        this.afterUnanswerCnt = afterUnanswerCnt;
        this.afterUnanswerRate = afterUnanswerRate;
        this.accuracyGain = accuracyGain;
        this.falsePositiveRate = falsePositiveRate;
        this.evalThreshold = evalThreshold;
        this.resolvedCountByAi = resolvedCountByAi;
    }

    // 실제 반영 후 측정된 미답변 건수 갱신
    public void updateActualUnanswerCnt(Integer actualUnanswerCnt) {
        this.actualUnanswerCnt = actualUnanswerCnt;
    }

}
