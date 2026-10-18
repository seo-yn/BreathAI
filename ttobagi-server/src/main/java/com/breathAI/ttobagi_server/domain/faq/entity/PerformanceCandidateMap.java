package com.breathAI.ttobagi_server.domain.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// 성능 비교와 FAQ 후보 매핑, 개선 기여 후보 추적용
@Entity
@Getter
@Table(
    name = "gold_performance_candidate_map",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_comparison_candidate",
            columnNames = {"comparison_id", "candidate_id"}
        )
    },
    comment = "성능 비교-FAQ 후보 매핑 (어느 후보가 반영되어 성능이 개선됐는지 추적)"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceCandidateMap {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_id")
    private Long mapId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comparison_id", nullable = false, foreignKey = @ForeignKey(name = "fk_perf_map_comparison_id"))
    private PerformanceComparison comparison;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false, foreignKey = @ForeignKey(name = "fk_perf_map_candidate_id"))
    private FaqCandidate candidate;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public PerformanceCandidateMap(PerformanceComparison comparison, FaqCandidate candidate) {
        this.comparison = comparison;
        this.candidate = candidate;
    }

}
