package com.breathAI.ttobagi_server.domain.faq.entity;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// FAQ 후보 검토 및 반영 이력 (Audit Log)
@Entity
@Getter
@Table(name = "gold_faq_action_log", comment = "FAQ 후보 검토/반영 이력 (Audit Log)")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqActionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false, foreignKey = @ForeignKey(name = "fk_action_candidate_id"))
    private FaqCandidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acted_by", nullable = true, foreignKey = @ForeignKey(name = "fk_action_user_id"))
    private User actedBy;

    @Column(name = "action", nullable = false, length = 20, 
            columnDefinition = "VARCHAR(20) COMMENT 'ACCEPT / REJECT / APPLY / REVERT'")
    @Enumerated(EnumType.STRING)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_status", length = 20, columnDefinition = "VARCHAR(20) COMMENT '변경 전 상태'")
    private FaqCandidate.ReviewStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_status", length = 20, columnDefinition = "VARCHAR(20) COMMENT '변경 후 상태'")
    private FaqCandidate.ReviewStatus afterStatus;

    @Column(name = "note", columnDefinition = "TEXT COMMENT '반려 사유 등 추가 참고사항'")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public enum Action {
        ACCEPT, REJECT, APPLY, REVERT
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }


    @Builder
    public FaqActionLog(FaqCandidate candidate, User actedBy, Action action,
                        FaqCandidate.ReviewStatus beforeStatus, FaqCandidate.ReviewStatus afterStatus, String note) {
        this.candidate = candidate;
        this.actedBy = actedBy;
        this.action = action;
        this.beforeStatus = beforeStatus;
        this.afterStatus = afterStatus;
        this.note = note;
    }

}
