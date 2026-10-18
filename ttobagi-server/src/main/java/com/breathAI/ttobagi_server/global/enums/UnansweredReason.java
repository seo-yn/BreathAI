package com.breathAI.ttobagi_server.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 챗봇 미답변 사유 구분
@Getter
@RequiredArgsConstructor
public enum UnansweredReason {
    UNREGISTERED_KEYWORD("키워드 미등록"),
    FOREIGN_LANGUAGE("외국어 질의"),
    POLICY_RESTRICTION("보안/정책상 답변 불가"),
    ETC("기타");

    // AI 서버 및 API 응답에서 사용하는 한글 표기
    private final String description;

    // 한글 사유 문구를 enum으로 변환 (미일치 시 ETC 반환)
    public static UnansweredReason fromDescription(String description) {
        for (UnansweredReason reason : values()) {
            if (reason.description.equals(description)) {
                return reason;
            }
        }
        return ETC;
    }
}
