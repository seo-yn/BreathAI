import { request, getStoredTokens, BASE_URL } from "./client.js";

export const dashApi = {
  // GET /api/v1/dashboard/usage?year=2026&month=4
  getUsage: (year, month) => request("GET", `/api/v1/dashboard/usage?year=${year}&month=${month}`),

  // POST /api/v1/dashboard/analyze/upload (업로드 + 분석 통합)
  // 기간은 백엔드가 LocalDate.parse()로 포맷터 없이 파싱하므로 ISO(yyyy-MM-dd)여야 하고,
  // 빈 문자열을 보내면 parse("")가 터져 500이 되므로 미입력 항목은 아예 append하지 않는다.
  // clustering* 파라미터는 아직 백엔드 uploadAndStartAnalysis가 받지 않아 무시된다
  // (docs/backend-api-requests.md 참고) — 백엔드가 추가되면 그대로 전달된다.
  uploadAndAnalyze: (file, opts = {}) => {
    const { periodStartDate, periodEndDate, isMaskingEnabled, isTranslationEnabled, clustering } = opts;

    const formData = new FormData();
    formData.append("file", file);
    if (periodStartDate) formData.append("periodStartDate", periodStartDate);
    if (periodEndDate) formData.append("periodEndDate", periodEndDate);
    if (isMaskingEnabled !== undefined) formData.append("isMaskingEnabled", String(isMaskingEnabled));
    if (isTranslationEnabled !== undefined) formData.append("isTranslationEnabled", String(isTranslationEnabled));
    Object.entries(clustering || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") formData.append(key, String(value));
    });

    return request("POST", "/api/v1/dashboard/analyze/upload", { body: formData, isFormData: true });
  },

  // GET /api/v1/dashboard/analyze/{analysisId}
  getAnalysisStatus: (analysisId) => request("GET", `/api/v1/dashboard/analyze/${analysisId}`),

  // GET /api/v1/dashboard/analyze/{analysisId}/result
  getFileAnalyzeResult: (analysisId) => request("GET", `/api/v1/dashboard/analyze/${analysisId}/result`),

  // GET /api/v1/dashboard/analyze/results?startDate=yyyyMMdd&endDate=yyyyMMdd
  // 참고: API 명세서(Notion)에는 단수 /analyze/result로 적혀있지만, 실제 백엔드 DashboardController는
  // 복수형 /analyze/results로 구현되어 있어 그쪽을 따름 — 백엔드팀 확인 필요
  // 날짜는 백엔드가 @DateTimeFormat(pattern="yyyyMMdd")로 파싱하므로 "2026-04-01" 같은 형식은 400을 유발한다.
  // 미지정(기간 없음) 시 최신 분석을 반환하도록 백엔드가 처리하므로, 빈 값은 아예 쿼리에서 뺀다.
  getAnalyzeResult: (startDate, endDate) => {
    const params = new URLSearchParams();
    if (startDate) params.set("startDate", startDate);
    if (endDate) params.set("endDate", endDate);
    const qs = params.toString();
    return request("GET", `/api/v1/dashboard/analyze/results${qs ? `?${qs}` : ""}`);
  },

  // GET /api/v1/dashboard/analyze/history
  getAnalysisHistory: (page = 0, size = 10) => request("GET", `/api/v1/dashboard/analyze/history?page=${page}&size=${size}`),

  // GET /api/v1/dashboard/analyze/stream/{analysisId} (SSE)
  // 이 백엔드의 JwtAuthenticationFilter는 Authorization 헤더만 읽고 쿼리 파라미터 토큰은
  // 전혀 확인하지 않는다. EventSource는 커스텀 헤더를 보낼 수 없어 이 엔드포인트를 인증할
  // 방법이 없으므로(항상 익명 → 403), fetch + ReadableStream으로 SSE를 직접 파싱해
  // 실제 Authorization 헤더를 싣는다. 백엔드는 SseEmitter.event().name("status")로
  // 이름 있는 이벤트만 보낸다.
  streamAnalysisStatus: (analysisId, onMessage, onError) => {
    const { accessToken } = getStoredTokens();
    const controller = new AbortController();

    (async () => {
      try {
        const res = await fetch(`${BASE_URL}/api/v1/dashboard/analyze/stream/${analysisId}`, {
          headers: { Authorization: `Bearer ${accessToken || ""}`, Accept: "text/event-stream" },
          signal: controller.signal,
        });
        if (!res.ok || !res.body) throw new Error("SSE 연결에 실패했습니다.");

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";

        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });

          let sepIndex;
          while ((sepIndex = buffer.indexOf("\n\n")) !== -1) {
            const rawEvent = buffer.slice(0, sepIndex);
            buffer = buffer.slice(sepIndex + 2);

            let eventName = "message";
            const dataLines = [];
            for (const line of rawEvent.split("\n")) {
              if (line.startsWith("event:")) eventName = line.slice(6).trim();
              else if (line.startsWith("data:")) dataLines.push(line.slice(5).trim());
            }
            if (eventName === "status" && dataLines.length) onMessage(dataLines.join("\n"));
          }
        }
      } catch (err) {
        if (err.name !== "AbortError") onError?.();
      }
    })();

    // EventSource와 동일하게 close()로 정리할 수 있게 유지
    return { close: () => controller.abort() };
  },
};
