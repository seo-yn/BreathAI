package com.breathAI.ttobagi_server.global.util;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 분석 진행 상태 실시간 전송을 위한 SSE 연결 관리자
// 제약: 연결을 메모리에 보관하므로 다중 인스턴스 환경에서 알림 유실 가능
@Component
public class SseEmitterManager {

    // analysisId 기준 구독 중인 연결 목록
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // SSE 연결 생성 및 보관, 타임아웃 1시간
    public SseEmitter create(Long analysisId) {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L);
        emitters.put(analysisId, emitter);
        // 그 사이 다른 연결로 교체됐을 수 있으므로 자기 자신일 때만 제거한다
        emitter.onCompletion(() -> emitters.remove(analysisId, emitter));
        emitter.onTimeout(() -> emitters.remove(analysisId, emitter));
        return emitter;
    }

    // 클라이언트에 상태 변경 전송
    public void send(Long analysisId, String status, String message) {
        SseEmitter emitter = emitters.get(analysisId);
        if (emitter == null) return;
        send(emitter, analysisId, status, message);
    }

    // 지정한 연결에 상태 전송, 종료 상태면 연결을 닫는다
    public void send(SseEmitter emitter, Long analysisId, String status, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data("{\"status\":\"" + status + "\",\"message\":\"" + message + "\"}"));
            if ("COMPLETED".equals(status) || "FAIL".equals(status)) {
                emitter.complete();
            }
        } catch (Exception e) {
            emitters.remove(analysisId, emitter);
        }
    }
}
