package com.breathAI.ttobagi_server.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientUtilTest {

    @Test
    @DisplayName("AI가 입력 파일을 거절(422)하면 연결 실패와 다른 사유를 남긴다")
    void inputRejectionIsDistinguishedFromConnectionFailure() {
        var rejected = new WebClientResponseException(422, "Unprocessable Content", null, null, null);

        assertThat(WebClientUtil.failureReason(rejected)).contains("파일 내용").contains("날짜");
        assertThat(WebClientUtil.failureReason(new ConnectException("refused")))
                .isEqualTo("AI 서버에 분석을 요청하지 못했습니다.");
        assertThat(WebClientUtil.failureReason(new WebClientResponseException(500, "Server Error", null, null, null)))
                .isEqualTo("AI 서버에 분석을 요청하지 못했습니다.");
    }
}
