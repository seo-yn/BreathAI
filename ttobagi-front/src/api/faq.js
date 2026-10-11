import { request } from "./client.js";

function toQueryString(params) {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") usp.set(key, value);
  });
  const qs = usp.toString();
  return qs ? `?${qs}` : "";
}

export const faqApi = {
  // GET /api/v1/faq?page=0&size=10&versionId=&category=&keyword=
  // 주의: 백엔드 FaqController는 현재 page/size만 바인딩한다. versionId/category/keyword는
  // 명세서엔 있지만 서버에서 무시되므로, 호출부는 이 값들이 걸러진 결과를 기대하면 안 된다.
  getList: ({ page = 0, size = 10, versionId, category, keyword } = {}) =>
    request("GET", `/api/v1/faq${toQueryString({ page, size, versionId, category, keyword })}`),

  // GET /api/v1/faq/{faqId}
  getOne: (faqId) => request("GET", `/api/v1/faq/${faqId}`),

  // GET /api/v1/faq/recommendations/{analysisId}
  getCandidates: (analysisId) => request("GET", `/api/v1/faq/recommendations/${analysisId}`),

  // POST /api/v1/faq/apply
  apply: (body) => request("POST", "/api/v1/faq/apply", { body }),

  // PATCH /api/v1/faq/{faqId}
  update: (faqId, body) => request("PATCH", `/api/v1/faq/${faqId}`, { body }),

  // DELETE /api/v1/faq/{faqId}
  remove: (faqId) => request("DELETE", `/api/v1/faq/${faqId}`),

  // GET /api/v1/faq/history/{faqId}
  // 참고: API 명세서(Notion)엔 파라미터 없는 전역 /faq/history?editType=... 로 적혀있지만,
  // 실제 백엔드 FaqController는 FAQ 단건 기준 /faq/history/{faqId}로 구현되어 있어 그쪽을 따름
  getHistory: (faqId) => request("GET", `/api/v1/faq/history/${faqId}`),

  // ── 아래는 API 명세서엔 있지만 백엔드에 아직 구현되지 않은 엔드포인트 ──
  // 백엔드 배포되면 별도 수정 없이 바로 동작함 (FaqVersionsPage에서 404를 "준비 중"으로 처리)

  // GET /api/v1/faq/versions?page=0&size=10
  getVersions: (page = 0, size = 10) => request("GET", `/api/v1/faq/versions?page=${page}&size=${size}`),

  // GET /api/v1/faq/versions/{versionId}
  getVersionDetail: (versionId) => request("GET", `/api/v1/faq/versions/${versionId}`),

  // GET /api/v1/faq/download?versionId=
  downloadExcel: (versionId) => request("GET", `/api/v1/faq/download${toQueryString({ versionId })}`),

  // GET /api/v1/faq/training-data?page=0&size=20&keyword=
  // 챗봇 기존 학습데이터(질문-답변 쌍 약 2000건, bronze_counselling_info) 조회.
  // DTO 목록에 TrainingDataListResponse만 있고 명세서 API 표엔 행 자체가 없어서,
  // 경로/파라미터는 docs/backend-api-requests.md 에 제안한 명세를 따른다.
  // 2000건 규모라 전체를 한 번에 받지 않고 서버 페이징 + 서버 검색을 전제로 한다.
  getTrainingData: ({ page = 0, size = 20, keyword } = {}) =>
    request("GET", `/api/v1/faq/training-data${toQueryString({ page, size, keyword })}`),
};
