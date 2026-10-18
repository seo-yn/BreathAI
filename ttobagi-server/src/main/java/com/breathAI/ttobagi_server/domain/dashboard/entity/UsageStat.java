package com.breathAI.ttobagi_server.domain.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// 일별 챗봇 사용량 통계
@Entity
@Getter
@Table(
    name = "gold_usage_stat",
    comment = "일별 챗봇 사용량 통계"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UsageStat {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stat_id")
    private Long statId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "stat_date",
        referencedColumnName = "stat_date",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_usage_stat_date")
    )
private StatDate statDate;

    @Column(name = "total_cnt", nullable = false, columnDefinition = "INT DEFAULT 0 COMMENT '총 인입 건수'")
    private Integer totalCnt;

    @Column(name = "answer_cnt", nullable = false, columnDefinition = "INT DEFAULT 0 COMMENT '정상 답변 건수'")
    private Integer answerCnt;

    @Column(name = "low_quality_count", nullable = false, columnDefinition = "INT DEFAULT 0 COMMENT '저품질 답변 건수'")
    private Integer lowQualityCount;

    @Column(name = "unanswer_cnt", nullable = false, columnDefinition = "INT DEFAULT 0 COMMENT '미답변 건수'")
    private Integer unanswerCnt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.totalCnt == null) this.totalCnt = 0;
        if (this.answerCnt == null) this.answerCnt = 0;
        if (this.lowQualityCount == null) this.lowQualityCount = 0;
        if (this.unanswerCnt == null) this.unanswerCnt = 0;
    }

    @Builder
    public UsageStat(StatDate statDate, Integer totalCnt, Integer answerCnt,
                     Integer lowQualityCount, Integer unanswerCnt) {
        this.statDate = statDate;
        this.totalCnt = totalCnt;
        this.answerCnt = answerCnt;
        this.lowQualityCount = lowQualityCount;
        this.unanswerCnt = unanswerCnt;
    }

    // 집계값 갱신
    public void update(Integer totalCnt, Integer answerCnt,
                       Integer lowQualityCount, Integer unanswerCnt) {
        this.totalCnt = totalCnt;
        this.answerCnt = answerCnt;
        this.lowQualityCount = lowQualityCount;
        this.unanswerCnt = unanswerCnt;
    }

}
