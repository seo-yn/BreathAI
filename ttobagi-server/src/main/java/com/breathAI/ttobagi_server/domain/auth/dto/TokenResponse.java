package com.breathAI.ttobagi_server.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

// 토큰 재발급 응답
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    
    private String accessToken;
    
    private String refreshToken;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC"
    )
    private LocalDateTime accessTokenExpiresAt;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC"
    )
    private LocalDateTime refreshTokenExpiresAt;
    
    @Builder.Default
    private String tokenType = "Bearer";
}