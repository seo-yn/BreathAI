package com.breathAI.ttobagi_server.domain.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;

import java.util.List;
import java.util.ArrayList;

import java.time.LocalDateTime;

// 시스템 사용자 계정
@Entity
@Getter
@Table(name = "gold_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    // 로그인 식별자, 서울교통공사 도메인만 허용
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    // BCrypt 해시값
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // 권한 구분 (USER, ADMIN)
    @Column(name = "role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 탈퇴 시 함께 삭제되는 비밀번호 재설정 토큰 목록
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PasswordResetToken> passwordResetTokens = new ArrayList<>();

    // 최초 저장 시 생성일시 및 기본값 설정
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (role == null) role = Role.USER;
        isActive = true;
    }

    // 수정 시 갱신일시 반영
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Role {
        USER, ADMIN
    }

    @Builder
    public User(String email, String password, Role role) {
        this.email = email;
        this.password = password;
        this.role = role;
    }

    // 비밀번호 변경 (암호화된 값 전달 필요)
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // 권한 변경
    public void updateRole(Role role) {
        this.role = role;
    }
    
}
