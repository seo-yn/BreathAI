# 백엔드 API 추가/수정 요청서

최초 작성: 2026-10-10 · 갱신: 2026-10-11 (develop `f32afdc` 기준)
작성: 프론트엔드 · 대상: `ttobagi-server`, 일부는 AI 서버(FastAPI) 동반 수정 필요

프론트엔드 신규 요구사항 4건에 대한 요청서입니다. **10/4~10/10 PR 8건이 머지되면서
처음 요청했던 내용 대부분이 이미 해결됐습니다.** 아래는 남은 항목만 정리한 것입니다.

---

## 처리 완료 (확인했습니다, 감사합니다)

| 최초 요청 | 처리된 PR / 커밋 |
|---|---|
| EXPAND 후보 반영 시 기존 FAQ를 갱신 | `b4ea9bb` (PR #25) + `FaqApplyResponse.editType` 추가 |
| FAQ 목록에 `versionId` / `category` / `keyword` 필터 | PR #25 |
| FAQ 버전 목록·상세 조회 | PR #30 |
| FAQ 파일 다운로드 | PR #32 (CSV, `byte[]` + Content-Disposition) |
| 스냅샷 적재 (버전 데이터가 쌓이지 않던 문제) | PR #30 (`FaqVersion` / `FaqVersionItem` 도입) |
| 미구현 라우트가 500으로 응답되던 문제 | PR #34 (`NoResourceFoundException` 처리) |
| 비밀번호 재설정 6자리 코드 | PR #36 |
| `FaqCandidateItem`에 `matchedFaqs` 추가 | PR #25 (`matchedFaqId`, `question`까지 내려와서 중복 검토가 훨씬 쉬워졌습니다) |

프론트는 위 변경에 맞춰 전부 수정했습니다. 다운로드는 CSV 바이너리 응답용 경로를 따로
만들었고(`src/api/client.js`의 `requestFile`), FAQ 목록·이력은 서버 페이징/필터를 쓰도록 바꿨습니다.

---

## ① 기존 학습데이터 조회 API (아직 필요)

**요구사항**: "기존 학습데이터를 화면에서 볼 수 있게"

대상은 또타24 챗봇이 현재 학습하고 있는 **질문-답변 쌍 약 2000건**(`bronze_counselling_info`)입니다.
`TrainingDataListResponse`가 DTO 목록에는 있는데 API 명세서 표에는 행 자체가 없고, 백엔드에도
엔드포인트가 없습니다. PR #37(Bronze 원본 적재)이 적재는 다루지만 조회 API는 아닙니다.
관련 이슈로 #28(기존 FAQ 적재 자동화)이 열려 있습니다.

2000건 규모라 **서버 페이징과 서버 검색이 필요합니다.**

### `GET /api/v1/faq/training-data`

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | int | X | 0 | 0-base |
| `size` | int | X | 20 | |
| `keyword` | string | X | - | 질문 + 답변 부분 일치 |

Token: Yes · 권한: `hasAnyRole('USER','ADMIN')` (조회라서 USER도 허용 부탁드립니다)

```json
{
  "success": true,
  "message": "요청에 성공하였습니다.",
  "status": 200,
  "data": {
    "totalCount": 2041,
    "totalPages": 103,
    "currentPage": 0,
    "size": 20,
    "trainingData": [
      {
        "seqNum": 1043,
        "standardQuestion": "유실물은 어디서 찾나요?",
        "answer": "유실물센터는 ...",
        "qType": 3,
        "category": "유실물",
        "qaCnt": 812,
        "linkedFaqId": 128
      }
    ]
  }
}
```

- `seqNum`: `counselling_info.seq_num`. 프론트에서 행 식별자로 씁니다.
- `linkedFaqId`: 이 학습데이터를 `sourceSeqNum`으로 참조하는 `gold_faq.faq_id`. 없으면 null.
  어떤 기존 데이터가 이미 현행화됐는지 화면에서 구분하는 데 씁니다.
- 질문 필드명은 다른 응답과 맞춰 `standardQuestion`으로 적었습니다. 다르면 알려주세요.

프론트 화면은 `src/pages/TrainingDataPage.jsx`에 있고 사이드바 "기존 학습데이터"로 들어갑니다.
지금은 404를 받아 "준비 중"으로 보입니다.

---

## ② 군집화 파라미터 조정 (백엔드 + AI 서버, 아직 필요)

**요구사항**: "클러스터 개수 등등 조정할 수 있게"

HDBSCAN은 k-means처럼 클러스터 개수를 직접 지정할 수 없습니다. `min_cluster_size`를 올리면
클러스터가 적고 커지고, 내리면 많고 작아집니다. 그래서 화면에는 "클러스터 개수"가 아니라
**최소 클러스터 크기 / 최소 샘플 수** 두 입력으로 만들었습니다.

### `POST /api/v1/dashboard/analyze/upload` 파라미터 추가

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `minClusterSize` | int | X | AI 서버 기본값 | HDBSCAN `min_cluster_size` |
| `minSamples` | int | X | AI 서버 기본값 | HDBSCAN `min_samples` |

프론트는 이미 이 두 값을 multipart로 보내고 있습니다(`src/api/dashboard.js`). 빈 값은 아예
보내지 않으니 `required = false`로 받으시면 됩니다.

변경이 필요한 지점:

1. `DashboardController.uploadAndStartAnalysis()` — `@RequestParam` 2개 추가
2. `WebClientUtil.sendPipeline()` — AI 서버 `/pipeline` 호출 바디에 전달
3. `AnalysisJob` — 어떤 파라미터로 돌린 분석인지 재현 가능하게 컬럼 추가 (권장)
4. AI 서버 — `/pipeline`에서 두 값을 받아 HDBSCAN에 넘기고, 미전달 시 기존 기본값 유지

프론트 화면에는 이 항목에 "백엔드 반영 대기" 뱃지를 달아뒀습니다. 서버가 받기 시작하면
그 뱃지만 떼면 됩니다.

---

## ③ ApiResponse에 에러 식별 코드 노출 (권장)

`ErrorCode`에는 `A-008`, `A-009`, `A-010` 같은 식별 코드가 있고 주석에 "클라이언트 분기용
식별 코드"라고 적혀 있는데, `ApiResponse`에 `code` 필드가 없어서 **응답에 실리지 않습니다.**

그래서 프론트가 비밀번호 재설정 실패를 구분할 때 메시지 문자열을 검사하고 있습니다
(`src/pages/ResetPage.jsx`의 `needsNewCode()` — 429이거나 400 + 메시지에 "만료"/"이미 사용" 포함).
문구가 한 글자라도 바뀌면 이 분기가 깨집니다.

```json
{ "success": false, "message": "인증 코드가 만료되었습니다. ...", "code": "A-009", "status": 400 }
```

`ApiResponse.error(ErrorCode)`에서 `errorCode.getCode()`를 같이 담아주시면, 프론트를
코드 기반 분기로 바꾸겠습니다. 기존 응답에 필드가 추가되는 것뿐이라 호환성 문제는 없습니다.

---

## ④ FAQ 변경 이력의 `faqId` 필터 (선택)

`GET /api/v1/faq/history`가 전역 이력만 조회하고 `faqId` 필터를 받지 않습니다. 그래서
FAQ 단건의 변경 이력을 보여주는 UI는 포기하고, **전역 "변경 이력" 탭**으로 만들었습니다
(`editType` 필터 + 페이징, 각 행에 `faqId` 표시). 지금 구조로도 쓸 수 있습니다.

다만 "이 FAQ가 어떻게 바뀌어 왔는지"를 FAQ 상세에서 보여주려면 `faqId` 필터가 필요합니다.
여유 있을 때 `@RequestParam(required = false) Long faqId` 추가를 검토해주세요.

---

## ⑤ 후보 반려 API (선택, 우선순위 낮음)

승인은 `POST /api/v1/faq/apply`가 있는데 **반려에 해당하는 엔드포인트가 없습니다.**
`ReviewStatus.REJECTED`와 `FaqActionLog`가 있는데 이를 기록할 통로가 없습니다.

지금 프론트의 "반려"는 화면 상태만 바꿔서 새로고침하면 사라집니다. 검토자가 같은 후보를
두 번 판단해야 하는 상황입니다.

```
PATCH /api/v1/faq/recommendations/{candidateId}/reject
Request:  { "reason": "운영 정책과 맞지 않음" }   // optional
Response: ApiResponse<Void>
```

---

## 명세서 자체 수정 요청

프론트는 **실제 백엔드 구현을 기준**으로 맞춰져 있습니다. 명세서 쪽이 아직 다른 항목입니다.

| 기능 | 명세서 | 실제 구현 (develop) |
|---|---|---|
| FAQ 수정 이력 | `/api/v1/faq/history?page=&editType=` | 동일 ✅ (10/4에 맞춰졌습니다) |
| 데이터 분석 결과 조회 | `/api/v1/dashboard/analyze/result` | 동일 ✅ |
| 분석 결과 날짜 | `startDate=2026-04-01` | 동일 ✅ (`ISO.DATE`) |
| 엑셀 다운로드 | `ApiResponse<ExcelDownloadResponse>` | **CSV `byte[]` 직접 응답** — 명세서 갱신 필요 |
| 비밀번호 재설정 확정 | `{ token, newPassword }` | **`{ email, token, newPassword }`** — 명세서 갱신 필요 |
| FAQ 질문 필드 | `question` | **`standardQuestion`** — DTO 문서 갱신 필요 |

### 미답변 필드명 안내

`AnalyzeResultResponse`의 미답변 관련 JSON 키가 `unanswered*` → `unanswer*`로 바뀌었습니다
(`unanswerAnalysis`, `samplingStatus.unanswer`, `currentUnanswerRate`, `trend[].unanswerCount`).
프론트는 이에 맞춰 수정했습니다. 다만 Java 타입명은 `UnansweredAnalysis`로 남아 있어
혼동 소지가 있으니, 타입명도 맞추시거나 주석을 남겨두시면 좋겠습니다.

또 `AnalyzeResultResponse.SurgeDetection`에는 AI 콜백에 있는 `increasedRate`와 `keywordCount`가
빠져 있습니다. 급증 키워드를 화면에 띄우려면 이 두 값이 필요합니다.

---

## 이슈 정리 부탁

PR이 머지됐는데 이슈가 열린 채로 남아 있는 것들입니다 — #11, #15, #29, #31, #33.
실제로 작업이 남은 건 #28과 #35뿐입니다.
