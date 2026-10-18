package com.breathAI.ttobagi_server.domain.auth.dto;

import lombok.Getter;
import lombok.Builder;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

// 로그인 응답, 액세스·리프레시 토큰 및 만료 시각 반환
@Getter
@Builder
public class LoginResponse {

    private String tokenType;
    
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

}
