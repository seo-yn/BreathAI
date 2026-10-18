package com.breathAI.ttobagi_server.domain.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// 비밀번호 재설정 메일 발송 결과
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetResponse {
    
    private String email;
}
