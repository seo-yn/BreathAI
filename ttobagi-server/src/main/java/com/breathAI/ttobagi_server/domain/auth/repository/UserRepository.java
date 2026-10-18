package com.breathAI.ttobagi_server.domain.auth.repository;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// 사용자 계정 조회 리포지토리
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 이메일로 사용자 조회, 로그인 및 인증 전반에서 사용
    Optional<User> findByEmail(String email);
    
    // 회원가입 시 이메일 중복 확인
    boolean existsByEmail(String email);
} 
