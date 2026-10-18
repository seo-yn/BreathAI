package com.breathAI.ttobagi_server.domain.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// FAQ 후보와 기존 FAQ 매칭 결과
@Entity
@Getter
@Table(
    name = "gold_faq_candidate_match",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_candidate_faq",
            columnNames = {"candidate_id", "matched_faq_seq_num"}
        )
    },
    comment = "FAQ 후보-기존 FAQ 매칭 결과"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqCandidateMatch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long matchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false, foreignKey = @ForeignKey(name = "fk_match_candidate_id"))
    private FaqCandidate candidate;

    @Column(name = "matched_faq_seq_num", nullable = false, 
            columnDefinition = "INT COMMENT 'counselling_info.seq_num (Bronze 참조)'")
    private Integer matchedFaqSeqNum;

    @Column(name = "match_score", nullable = false, precision = 5, scale = 4, 
            columnDefinition = "DECIMAL(5,4) COMMENT '매칭 유사도 점수'")
    private BigDecimal matchScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Builder
    public FaqCandidateMatch(FaqCandidate candidate, Integer matchedFaqSeqNum, BigDecimal matchScore) {
        this.candidate = candidate;
        this.matchedFaqSeqNum = matchedFaqSeqNum;
        this.matchScore = matchScore;
    }

}
