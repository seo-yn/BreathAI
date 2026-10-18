package com.breathAI.ttobagi_server.global.security;

import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// AI 서버 콜백의 Bearer 토큰 검증
@Component
public class AiCallbackTokenVerifier {

    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public AiCallbackTokenVerifier(@Value("${ai.callback-token}") String callbackToken) {
        if (callbackToken == null || callbackToken.isBlank()) {
            throw new IllegalStateException("TTOBAGI_CALLBACK_TOKEN이 설정되지 않았습니다.");
        }
        this.expectedToken = callbackToken.getBytes(StandardCharsets.UTF_8);
    }

    // 토큰이 없거나 다르면 401
    public void verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        byte[] actualToken = authorizationHeader.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        // 비교 시간으로 토큰을 추측하지 못하게 일정 시간 비교를 쓴다
        if (!MessageDigest.isEqual(expectedToken, actualToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }
}
