package com.breathAI.ttobagi_server.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// 비밀번호 재설정 인증 토큰
@Entity
@Getter
@Table(name = "gold_password_reset_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_token_user_id"))
    private User user;

    // 메일로 발송되는 6자리 인증 코드 (영문 대소문자 + 숫자)
    @Column(name = "token", nullable = false, unique = true)
    private String token;

    // 틀린 코드를 입력한 횟수, 한도에 도달하면 이 코드는 더 쓸 수 없다
    @Column(name = "attempt_count", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0 COMMENT '인증 코드 입력 실패 횟수'")
    private int attemptCount = 0;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    // 재사용 방지용 사용 여부
    @Column(name = "is_used", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isUsed = false;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // 만료 시각은 생성 시점 기준으로 계산
    @Builder
    public PasswordResetToken(String token, User user, int expiryMinutes) {
        this.token = token;
        this.user = user;
        this.expiryDate = LocalDateTime.now().plusMinutes(expiryMinutes);
        this.isUsed = false;
        this.createdAt = LocalDateTime.now();
    }

    // 사용 처리, 동일 토큰 재사용 차단
    public void useToken() {
        this.isUsed = true;
    }

    // 만료 여부 확인
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }

    // 틀린 입력 1회 기록
    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    // 틀린 횟수가 한도에 도달했는지 확인
    public boolean isLocked(int maxAttempts) {
        return this.attemptCount >= maxAttempts;
    }

}
