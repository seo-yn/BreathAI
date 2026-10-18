package com.breathAI.ttobagi_server.domain.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// 유사어 및 동의어 후보
@Entity
@Getter
@Table(
    name = "gold_synonym_candidate",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_candidate_synonym",
            columnNames = {"candidate_id", "synonym_text"}
        )
    },
    comment = "유사어/동의어 후보"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SynonymCandidate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "synonym_id")
    private Long synonymId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false, foreignKey = @ForeignKey(name = "fk_synonym_candidate_id"))
    private FaqCandidate candidate;

    @Column(name = "synonym_text", nullable = false, length = 100, columnDefinition = "VARCHAR(100) COMMENT '유사어 문구'")
    private String synonymText;

    @Column(name = "synonym_type", length = 20, columnDefinition = "VARCHAR(20) COMMENT 'TYPO / ABBR / SYNONYM'")
    @Enumerated(EnumType.STRING)
    private SynonymType synonymType;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public enum SynonymType {
        TYPO, ABBR, SYNONYM
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public SynonymCandidate(FaqCandidate candidate, String synonymText, SynonymType synonymType) {
        this.candidate = candidate;
        this.synonymText = synonymText;
        this.synonymType = synonymType;
    }
    
} 
