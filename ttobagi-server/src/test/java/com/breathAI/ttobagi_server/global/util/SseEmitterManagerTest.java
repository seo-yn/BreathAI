package com.breathAI.ttobagi_server.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterManagerTest {

    @Test
    @DisplayName("SSE 연결은 오래 걸리는 분석을 끝까지 받도록 8시간 유지된다")
    void emitterLastsEightHours() {
        SseEmitter emitter = new SseEmitterManager().create(1L);

        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofHours(8).toMillis());
    }
}
