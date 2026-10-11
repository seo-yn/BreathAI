package com.breathAI.ttobagi_server.domain.faq.entity;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 급증 질문 탐지 결과
@Entity
@Getter
@Table(
    name = "gold_surge_detection",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_analysis_keyword",
            columnNames = {"analysis_id", "keyword"}
        )
    },
    comment = "급증 질문 탐지 결과"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurgeDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "surge_id")
    private Long surgeId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false, foreignKey = @ForeignKey(name = "fk_surge_analysis_id"))
    private AnalysisJob analysisJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_analysis_id", foreignKey = @ForeignKey(name = "fk_surge_prev_analysis_id"),
                columnDefinition = "BIGINT COMMENT '비교 기준 이전 분석 ID'")
    private AnalysisJob prevAnalysisJob;

    @Column(name = "keyword", nullable = false, length = 100, columnDefinition = "VARCHAR(100) COMMENT '급증 키워드'")
    private String keyword;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "is_new", nullable = false, columnDefinition = "TINYINT NOT NULL DEFAULT 0 COMMENT '신규 유형 여부'")
    private Boolean isNew;

    @Column(name = "increased_rate", precision = 7, scale = 2, 
            columnDefinition = "DECIMAL(7,2) COMMENT '이전 대비 증가율 (예: 150.0)'")
    private BigDecimal increasedRate;

    @Column(name = "keyword_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '키워드 출현 횟수'")
    private Integer keywordCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_cluster_id", foreignKey = @ForeignKey(name = "fk_surge_cluster_id"),
                columnDefinition = "BIGINT COMMENT '연관 클러스터 ID'")
    private Cluster relatedCluster;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isNew == null) this.isNew = false;
        if (this.keywordCount == null) this.keywordCount = 0;
    }

    @Builder
    public SurgeDetection(AnalysisJob analysisJob, AnalysisJob prevAnalysisJob, String keyword, 
                          Boolean isNew, BigDecimal increasedRate, Integer keywordCount, Cluster relatedCluster) {
        this.analysisJob = analysisJob;
        this.prevAnalysisJob = prevAnalysisJob;
        this.keyword = keyword;
        this.isNew = isNew;
        this.increasedRate = increasedRate;
        this.keywordCount = keywordCount;
        this.relatedCluster = relatedCluster;
    }

}
