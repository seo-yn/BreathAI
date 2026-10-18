package com.breathAI.ttobagi_server.domain.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 토큰 재발급 요청
@Getter
@NoArgsConstructor
public class TokenRequest {
    private String refreshToken;
}