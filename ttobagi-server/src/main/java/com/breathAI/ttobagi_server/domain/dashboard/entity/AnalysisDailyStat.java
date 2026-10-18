package com.breathAI.ttobagi_server.domain.dashboard.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 업로드 파일(분석)별 날짜 통계, 서비스 전체 사용량과 섞지 않는다
@Entity
@Getter
@Table(name = "gold_analysis_daily_stat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisDailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_stat_id")
    private Long dailyStatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_daily_stat_analysis_id"))
    private AnalysisJob analysisJob;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    // 예전 형식 콜백(trend만 있음)은 전체·정상 건수가 없어 비워 둔다
    @Column(name = "total_cnt")
    private Integer totalCnt;

    @Column(name = "answer_cnt")
    private Integer answerCnt;

    @Column(name = "low_quality_count", nullable = false)
    private Integer lowQualityCount;

    @Column(name = "unanswer_cnt", nullable = false)
    private Integer unanswerCnt;

    @Builder
    public AnalysisDailyStat(AnalysisJob analysisJob, LocalDate statDate, Integer totalCnt, Integer answerCnt,
                             Integer lowQualityCount, Integer unanswerCnt) {
        this.analysisJob = analysisJob;
        this.statDate = statDate;
        this.totalCnt = totalCnt;
        this.answerCnt = answerCnt;
        this.lowQualityCount = lowQualityCount != null ? lowQualityCount : 0;
        this.unanswerCnt = unanswerCnt != null ? unanswerCnt : 0;
    }
}
