package com.breathAI.ttobagi_server.domain.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 비밀번호 재설정 메일 발송 요청
@Getter
@NoArgsConstructor
public class PasswordResetRequest {

    @NotBlank(message = "이메일은 필수 입력 값입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+-]+@seoulmetro\\.co\\.kr$",
        message = "서울교통공사 이메일만 가능합니다."
    )
    private String email;
    
}
