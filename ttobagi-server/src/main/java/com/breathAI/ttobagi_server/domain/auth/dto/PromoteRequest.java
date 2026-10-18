package com.breathAI.ttobagi_server.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리자 권한 승격 요청
@Getter
@NoArgsConstructor
public class PromoteRequest {
    
    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+-]+@seoulmetro\\.co\\.kr$",
        message = "서울교통공사 이메일만 가능합니다."
    )
    private String email;

    @NotBlank(message = "관리자 인증 코드는 필수입니다.")
    private String adminCode;
    
}
