package com.breathAI.ttobagi_server.domain.faq.entity;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 실시간 FAQ 검색 로그
@Entity
@Getter
@Table(name = "gold_retrieve_log", comment = "실시간 FAQ 검색 로그 (/retrieve)")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetrieveLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "retrieve_id")
    private Long retrieveId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", foreignKey = @ForeignKey(name = "fk_retrieve_analysis_id"),
                columnDefinition = "BIGINT COMMENT '검색 기반 분석 ID (FAQ DB 버전 추적)'")
    private AnalysisJob analysisJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_retrieve_user_id"),
                columnDefinition = "BIGINT COMMENT '검색한 사용자 (비로그인 시 NULL)'")
    private User user;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT COMMENT '사용자 검색어'")
    private String queryText;

    @Column(name = "returned_faq_seq_num", 
            columnDefinition = "INT COMMENT '반환된 FAQ seq_num (Bronze counselling_info 참조)'")
    private Integer returnedFaqSeqNum;

    @Column(name = "rerank_score", precision = 5, scale = 4, 
            columnDefinition = "DECIMAL(5,4) COMMENT 'BGE-Reranker 최종 유사도 점수'")
    private BigDecimal rerankScore;

    @Column(name = "retrieved_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime retrievedAt;

    @PrePersist
    protected void onCreate() {
        this.retrievedAt = LocalDateTime.now();
    }

    @Builder
    public RetrieveLog(AnalysisJob analysisJob, User user, String queryText,
                       Integer returnedFaqSeqNum, BigDecimal rerankScore) {
        this.analysisJob = analysisJob;
        this.user = user;
        this.queryText = queryText;
        this.returnedFaqSeqNum = returnedFaqSeqNum;
        this.rerankScore = rerankScore;
    }
    
}
