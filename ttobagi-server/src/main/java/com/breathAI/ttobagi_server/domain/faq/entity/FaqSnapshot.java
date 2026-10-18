package com.breathAI.ttobagi_server.domain.faq.entity;

import com.breathAI.ttobagi_server.domain.dashboard.entity.AnalysisJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// 분석 시점의 FAQ 스냅샷, 개선 전후 비교용
@Entity
@Getter
@Table(
    name = "gold_faq_snapshot",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_analysis_faq",
            columnNames = {"analysis_id", "source_seq_num"}
        )
    },
    comment = "분석 시점의 FAQ 원본 데이터 스냅샷"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqSnapshot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false, foreignKey = @ForeignKey(name = "fk_snapshot_analysis_id"))
    private AnalysisJob analysisJob;

    @Column(name = "source_seq_num", nullable = false, 
            columnDefinition = "INT COMMENT 'counselling_info.seq_num (Bronze)'")
    private Integer sourceSeqNum;

    @Column(name = "q_type", columnDefinition = "INT COMMENT '질문 유형 코드'")
    private Integer qType;

    @Column(name = "ci_question", columnDefinition = "TEXT COMMENT '스냅샷 시점 질문'")
    private String ciQuestion;

    @Column(name = "ci_answer0", columnDefinition = "LONGTEXT COMMENT '스냅샷 시점 답변'")
    private String ciAnswer0;

    @Column(name = "qa_cnt", columnDefinition = "INT COMMENT '조회수/매칭수'")
    private Integer qaCnt;

    @Column(name = "snapshotted_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime snapshottedAt;

    @PrePersist
    protected void onCreate() {
        this.snapshottedAt = LocalDateTime.now();
    }

    @Builder
    public FaqSnapshot(AnalysisJob analysisJob, Integer sourceSeqNum,
                       Integer qType, String ciQuestion, String ciAnswer0, Integer qaCnt) {
        this.analysisJob = analysisJob;
        this.sourceSeqNum = sourceSeqNum;
        this.qType = qType;
        this.ciQuestion = ciQuestion;
        this.ciAnswer0 = ciAnswer0;
        this.qaCnt = qaCnt;
    }

}
