package com.breathAI.ttobagi_server.domain.faq.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import com.breathAI.ttobagi_server.global.enums.UnansweredReason;

import java.time.LocalDateTime;

// 클러스터와 원본 로그 매핑
@Entity
@Getter
@Table(
    name = "gold_cluster_log_map",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_cluster_log",
            columnNames = {"cluster_id", "source_log_seq_num"}
        )
    },
    comment = "클러스터-로그 매핑"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClusterLogMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_id")
    private Long mapId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id", nullable = false, foreignKey = @ForeignKey(name = "fk_map_cluster_id"))
    private Cluster cluster;

    @Column(name = "source_log_seq_num", nullable = false, columnDefinition = "INT COMMENT 'Bronze chatting_log.seq_num'")
    private Integer sourceLogSeqNum;

    @Column(name = "log_type", nullable = false, length = 20, columnDefinition = "VARCHAR(20) COMMENT 'CORRECT / LOW_QUALITY / UNANSWER'")
    @Enumerated(EnumType.STRING)
    private LogType logType;

    @Column(name = "unanswered_reason", length = 50, columnDefinition = "VARCHAR(50) COMMENT '미답변 사유 (Enum 매핑)'")
    @Enumerated(EnumType.STRING)
    private UnansweredReason unansweredReason;

    @Column(name = "umap_x", columnDefinition = "FLOAT COMMENT 'UMAP x 좌표'")
    private Float umapX;
    
    @Column(name = "umap_y", columnDefinition = "FLOAT COMMENT 'UMAP y 좌표'")
    private Float umapY;

    @Column(name = "log_text", columnDefinition = "TEXT COMMENT '사용자 실제 입력 문구 복사본'")
    private String logText;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public enum LogType {
        CORRECT, LOW_QUALITY, UNANSWER
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Builder
    public ClusterLogMap(Cluster cluster, Integer sourceLogSeqNum, LogType logType,
                         UnansweredReason unansweredReason, Float umapX, Float umapY, String logText) {
        this.cluster = cluster;
        this.sourceLogSeqNum = sourceLogSeqNum;
        this.logType = logType;
        this.unansweredReason = unansweredReason;
        this.umapX = umapX;
        this.umapY = umapY;
        this.logText = logText;
    }
}