package com.breathAI.ttobagi_server.domain.auth.repository;

import com.breathAI.ttobagi_server.domain.auth.entity.PasswordResetToken;
import com.breathAI.ttobagi_server.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.LocalDateTime;

// 비밀번호 재설정 토큰 리포지토리
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // 사용자가 입력한 토큰 문자열로 조회
    Optional<PasswordResetToken> findByToken(String token);

    // 같은 코드가 이미 발급돼 있는지 확인
    boolean existsByToken(String token);

    // 사용자 기준 토큰 조회
    Optional<PasswordResetToken> findByUser(User user);

    // 재설정 재요청 시 기존 토큰 제거
    void deleteByUser(User user);

    // 만료된 토큰 일괄 정리
    void deleteAllByExpiryDateBefore(LocalDateTime now);
}
