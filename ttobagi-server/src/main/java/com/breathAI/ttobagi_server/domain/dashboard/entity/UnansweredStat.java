package com.breathAI.ttobagi_server.domain.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import com.breathAI.ttobagi_server.global.enums.UnansweredReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 분석 단위 미답변 사유별 집계
@Entity
@Getter
@Table(name = "gold_unanswer_stat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnansweredStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long statId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "analysis_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_unanswer_stat_analysis_id")
    )
    private AnalysisJob analysisJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private UnansweredReason reason;

    @Column(name = "current_unanswer_rate", precision = 5, scale = 2,
            columnDefinition = "DECIMAL(5,2) COMMENT '분석 결과 적용 후 예상 미답변율 (%)'")
    private BigDecimal currentUnanswerRate;

    @Column(name = "unanswer_count", nullable = false,
            columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '해당 사유별 집계 건수'")
    @ColumnDefault("0")
    private Integer unanswerCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.unanswerCount == null) this.unanswerCount = 0;
    }
    
    @Builder
    public UnansweredStat(AnalysisJob analysisJob, UnansweredReason reason,
                          BigDecimal currentUnanswerRate, Integer unanswerCount) {
        this.analysisJob = analysisJob;
        this.reason = reason;
        this.currentUnanswerRate = currentUnanswerRate;
        this.unanswerCount = unanswerCount != null ? unanswerCount : 0;
    }
}