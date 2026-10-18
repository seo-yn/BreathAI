package com.breathAI.ttobagi_server.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Builder;
import java.time.LocalDateTime;

// 회원 정보 조회 응답
@Getter
@Builder
public class UserInfoResponse {
    
    private Long userId;

    private String email;

    private String role;

    // 원시 타입이면 Lombok이 isActive() 게터를 만들어 Jackson이 active 속성을 추가로 노출한다.
    // 래퍼 타입을 써서 getIsActive() 게터가 생성되도록 하여 isActive 하나로 통일한다
    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC"
    )
    private LocalDateTime createdAt;

}
