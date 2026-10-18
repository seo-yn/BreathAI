package com.breathAI.ttobagi_server.global.exception;

import lombok.Getter;

// 비즈니스 로직에서 발생시키는 예외
@Getter
public class CustomException extends RuntimeException {
    
    // 응답 상태와 메시지를 담은 에러 코드
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
