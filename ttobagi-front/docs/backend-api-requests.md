# 백엔드 API 추가/수정 요청서

작성일: 2026-10-10 · 작성: 프론트엔드
대상: `ttobagi-server` (Spring), 일부는 AI 서버(FastAPI) 동반 수정 필요

프론트엔드에 신규 요구사항 4건이 들어왔습니다. 화면은 모두 붙여놨고, 아래 항목들은
서버가 준비되면 프론트 수정 없이 바로 동작합니다. 각 항목은 독립적이라 순서대로
처리하지 않아도 됩니다.

우선순위 제안: **① → ④ → ② → ③** (①은 지금 데이터가 잘못 쌓이고 있는 버그 성격)

---

## ① EXPAND 후보를 승인하면 기존 FAQ가 갱신되어야 함 (버그)

**요구사항**: "기존 학습데이터를 input으로 사용하고, 신규 FAQ가 생성되면 이를 반영"

**현재 동작**: `FaqService.applyFaqCandidate()`가 `candidateType`을 보지 않고 항상
`Faq.builder()`로 `gold_faq`에 **새 행을 INSERT**합니다. 또 `sourceSeqNum`을 채우지 않습니다.

그래서 AI가 `candidateType = EXPAND`(기존 FAQ의 키워드 확장)로 내려준 후보를 승인하면,
기존 항목이 갱신되는 대신 거의 같은 질문의 FAQ가 하나 더 생깁니다. 운영 FAQ에 중복이
누적되는 구조입니다.

**요청**:

1. `candidateType == EXPAND`이고 해당 후보의 `FaqCandidateMatch` 중 `matchScore`가 가장 높은
   항목이 임계값 이상이면, 새 FAQ를 만들지 말고 **그 `matchedFaqSeqNum`에 해당하는 기존
   FAQ를 갱신**해주세요. 갱신 내용은 `finalQuestion` / `finalAnswer` / `keywords`입니다.
2. `candidateType == NEW`로 새로 만들 때는 `Faq.sourceSeqNum`에 매칭된 원본
   `counselling_info.seq_num`을 넣어주세요 (없으면 null). 지금은 항상 비어 있어서
   신규 FAQ와 기존 학습데이터의 연결이 끊깁니다.
3. 갱신인 경우 `FaqEditHistory`에 `editType`을 남겨주시면, 프론트의 FAQ 수정 이력 화면에서
   "AI 반영"과 "수동 수정"을 구분해 보여줄 수 있습니다.

**응답 변경 제안**: `FaqApplyResponse`에 분기 결과를 알려주는 필드를 추가해주세요.
프론트가 "신규 등록됨" / "기존 FAQ 갱신됨"을 구분해 안내하려고 합니다.

```json
{
  "appliedFaqId": 128,
  "applyType": "UPDATED",        // CREATED | UPDATED
  "updatedFaqSeqNum": 1043       // UPDATED일 때만, 갱신한 기존 학습데이터 seq_num
}
```

**참고**: 매칭 자체는 이미 잘 동작합니다. AI 콜백의 `faqCandidates[].matchedFaqs[]`를
`DashboardService`가 `FaqCandidateMatch`에 저장하고 있고, 프론트는 이 값을 받아
후보 카드에 "기존 FAQ 확장 / 유사 N건 · 최대 87%" 뱃지로 표시하도록 붙여놨습니다.

> 다만 `matchedFaqs`가 `FileAnalyzeResultResponse.faqCandidates`에만 있고
> `FaqCandidateListResponse.FaqCandidateItem`에는 없어서, 프론트가 추천 목록과 분석 결과를
> 두 번 호출해 `candidateId`로 이어붙이고 있습니다. `FaqCandidateItem`에
> `matchedFaqs`를 추가해주시면 호출 하나를 줄일 수 있습니다. (선택)

---

## ② 기존 학습데이터 조회 API (신규)

**요구사항**: "기존 학습데이터를 화면에서 볼 수 있게"

대상 데이터는 또타24 챗봇이 현재 학습하고 있는 **질문-답변 쌍 약 2000건**
(`bronze_counselling_info`)입니다. `TrainingDataListResponse`가 DTO 목록에는 있는데
API 명세서 표에는 해당 행 자체가 없고, 백엔드에도 엔티티/리포지토리/엔드포인트가 없습니다.

2000건 규모라 **서버 페이징과 서버 검색이 필요합니다.** 전체를 내려받아 프론트에서
거르는 방식은 쓰지 않았습니다.

### `GET /api/v1/faq/training-data`

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | int | X | 0 | 0-base |
| `size` | int | X | 20 | |
| `keyword` | string | X | - | 질문 + 답변 부분 일치 |

Token: Yes

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
        "question": "유실물은 어디서 찾나요?",
        "answer": "유실물센터는 ...",
        "qType": 3,
        "qaCnt": 812,
        "linkedFaqId": 128
      }
    ]
  }
}
```

- `seqNum`: `counselling_info.seq_num`. 프론트에서 행 식별자로 씁니다.
- `qaCnt`: 누적 질의 수. 없으면 생략해도 되고, 프론트는 있으면 뱃지로 보여줍니다.
- `linkedFaqId`: 이 학습데이터를 `sourceSeqNum`으로 참조하는 `gold_faq.faq_id`.
  없으면 null. 어떤 기존 데이터가 이미 현행화됐는지 화면에서 구분하는 데 씁니다.
  (①-2가 선행되어야 의미 있는 값이 됩니다)

프론트 화면은 `src/pages/TrainingDataPage.jsx`에 이미 있고, 사이드바 "기존 학습데이터"로
들어갑니다. 지금은 호출이 실패해서 "준비 중"으로 보입니다.

### 함께 부탁드리는 작은 수정

`FaqListResponse.FaqItem`에 **`qaCnt`를 추가**해주세요. 프론트 FAQ 리스트에서 누적 질의 수를
보여주려 했는데 이 필드가 `FaqDetailResponse`에만 있어서, 목록에서는 쓸 수 없어 일단
등록일 표시로 대체해뒀습니다.

---

## ③ 군집화 파라미터 조정 (백엔드 + AI 서버)

**요구사항**: "클러스터 개수 등등 조정할 수 있게"

HDBSCAN은 k-means처럼 클러스터 개수를 직접 지정할 수 없습니다. 대신 `min_cluster_size`를
올리면 클러스터가 적고 커지고, 내리면 많고 작아집니다. 그래서 화면에는 "클러스터 개수"가
아니라 **최소 클러스터 크기 / 최소 샘플 수** 두 입력으로 만들었습니다.

### `POST /api/v1/dashboard/analyze/upload` 파라미터 추가

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `minClusterSize` | int | X | AI 서버 기본값 | HDBSCAN `min_cluster_size` |
| `minSamples` | int | X | AI 서버 기본값 | HDBSCAN `min_samples` |

프론트는 이미 이 두 값을 multipart로 보내고 있습니다 (`src/api/dashboard.js`).
빈 값은 아예 보내지 않으니 `required = false`로 받으시면 됩니다.

변경이 필요한 지점:

1. `DashboardController.uploadAndStartAnalysis()` — `@RequestParam` 2개 추가
2. `WebClientUtil.sendPipeline()` — AI 서버 `/pipeline` 호출 바디에 전달
3. `AnalysisJob` — 어떤 파라미터로 돌린 분석인지 재현 가능하게 컬럼 추가 (권장)
4. AI 서버 — `/pipeline`에서 두 값을 받아 HDBSCAN에 넘기고, 미전달 시 기존 기본값 유지

프론트 화면에는 이 항목에 "백엔드 반영 대기" 뱃지를 달아뒀습니다. 서버가 받기 시작하면
그 뱃지만 떼면 됩니다.

### 참고: 현재 전송되지 않던 옵션들

명세서 `NewAnalyzeRequest`에 있는데 프론트가 안 보내고 있던
`periodStartDate` / `periodEndDate` / `isMaskingEnabled` / `isTranslationEnabled`는
이번에 UI를 붙여 모두 전송하도록 했습니다. 서버 수정은 필요 없습니다.

다만 `DashboardService`가 `LocalDate.parse(periodStartDate)`를 포맷터 없이 호출하므로
**빈 문자열이 오면 `DateTimeParseException` → 500**이 납니다. 프론트는 미입력 시 아예
파라미터를 보내지 않도록 처리했지만, 서버에서도 빈 문자열을 null로 보정해주시면
더 안전합니다.

---

## ④ FAQ 버전 관리 + 엑셀 다운로드

**요구사항**: "버전 관리, 엑셀 다운로드 기능"

API 명세서에 3개 엔드포인트가 이미 정의돼 있고 프론트 화면
(`src/pages/FaqVersionsPage.jsx`)도 완성돼 있습니다. 백엔드만 구현되면 동작합니다.

- `GET /api/v1/faq/versions?page=0&size=10`
- `GET /api/v1/faq/versions/{versionId}`
- `GET /api/v1/faq/download?versionId=2`

### 선행 작업: 스냅샷이 적재되지 않고 있습니다

`gold_faq_snapshot` 테이블과 `FaqSnapshot` 엔티티, 그리고 페이징 쿼리까지 갖춘
`FaqSnapshotRepository`가 이미 있습니다. 그런데 **이 리포지토리를 호출하는 코드가
프로젝트 어디에도 없어서 스냅샷이 한 건도 쌓이지 않습니다.** 버전 관리의 원본 데이터가
없는 상태라, 엔드포인트보다 이쪽이 먼저입니다.

분석 시작 시점(`uploadAndStartAnalysis`) 또는 콜백 수신 시점에 그때의 FAQ 상태를
`analysisId` 기준으로 저장해주세요. `uq_analysis_faq(analysis_id, source_seq_num)`
유니크 제약이 이미 걸려 있습니다.

### 버전의 정의를 확정해주세요

프론트는 **`analysisId` = 하나의 버전**으로 가정했습니다 (스냅샷 테이블이 `analysis_id`로
묶여 있으므로). 다른 정의를 쓰실 거면 알려주세요 — `versionId`와 `analysisId`의 관계만
확정되면 됩니다.

### 응답 필드명 확정 요청

명세서에 `FaqVersionListResponse` / `FaqVersionDetailListResponse` /
`ExcelDownloadResponse`의 **필드 정의가 없습니다.** 지금 프론트는 흔한 이름 몇 가지를
방어적으로 시도하는 임시 코드입니다. 아래 형태로 괜찮으시면 확정 부탁드리고,
다르면 알려주시면 프론트를 맞추겠습니다.

```json
// GET /api/v1/faq/versions
{
  "data": {
    "totalCount": 7, "totalPages": 1, "currentPage": 0,
    "versions": [
      { "versionId": 3, "analysisId": 12, "faqCount": 2104,
        "createdAt": "2026-10-04T09:51:07Z", "createdBy": "user@seoulmetro.co.kr" }
    ]
  }
}

// GET /api/v1/faq/versions/{versionId}
{
  "data": {
    "versionId": 3,
    "faqList": [ { "faqId": 128, "question": "...", "answer": "...", "keywords": ["유실물"] } ]
  }
}

// GET /api/v1/faq/download?versionId=2
{ "data": { "downloadUrl": "https://.../faq_v2.xlsx", "fileName": "faq_v2.xlsx", "expiresAt": "..." } }
```

엑셀 다운로드는 **JSON으로 URL을 돌려주는 방식**으로 가정했습니다 (명세서 응답 타입이
`ApiResponse<ExcelDownloadResponse>`라서). 파일 바이트를 직접 내려주실 거면
`Content-Disposition` 방식으로 바꿔야 하니 미리 알려주세요 — 프론트 처리가 완전히 달라집니다.

### FAQ 목록의 `versionId` 필터

명세서에는 `GET /api/v1/faq?versionId=3&category=열차운영&keyword=냉방`으로 적혀 있지만
`FaqController`는 `page` / `size`만 바인딩합니다. 버전 관리가 들어오면 `versionId`
필터가 필요해지고, 2000건 규모에서는 `keyword` 서버 검색도 필요합니다.
(현재 프론트 검색은 받아온 페이지 안에서만 걸러내고, 플레이스홀더에 그 범위를 명시해뒀습니다)

---

## ⑤ 후보 반려 API (신규, 우선순위 낮음)

승인은 `POST /api/v1/faq/apply`가 있는데 **반려에 해당하는 엔드포인트가 명세서에도
백엔드에도 없습니다.** `ReviewStatus`에 `REJECTED`가 있고 `FaqActionLog`도 있는데
이를 기록할 통로가 없습니다.

지금 프론트의 "반려"는 화면 상태만 바꿔서 새로고침하면 사라집니다. 검토자가 두 번
판단해야 하는 상황이라, 아래 정도로 추가해주시면 좋겠습니다.

```
PATCH /api/v1/faq/recommendations/{candidateId}/reject
Request:  { "reason": "운영 정책과 맞지 않음" }   // reason은 optional
Response: ApiResponse<Void>
```

---

## 명세서 자체 수정 요청

프론트는 **실제 백엔드 구현을 기준**으로 맞춰져 있습니다. 명세서 쪽이 틀린 항목입니다.

| 기능 | 명세서 | 실제 구현 |
|---|---|---|
| 데이터 분석 결과 조회 | `/api/v1/dashboard/analyze/result` | `/api/v1/dashboard/analyze/results` (복수) |
| FAQ 수정 이력 조회 | `/api/v1/faq/history?page=&editType=` | `/api/v1/faq/history/{faqId}` (FAQ 단건 기준) |
| 데이터 분석 결과 조회 날짜 | `startDate=2026-04-01` | `@DateTimeFormat("yyyyMMdd")` → `20260401` |

추가로 **DTO 내부 필드명 불일치**가 하나 있습니다. 지금은 매핑 코드가 양쪽을 알고 있어서
동작하지만, 한 군데만 놓치면 조용히 null이 되는 구조라 공유드립니다.

| | `AnalysisCallbackRequest` (AI → 서버) | `AnalyzeResultResponse` (서버 → 프론트) |
|---|---|---|
| 미답변 분석 | `unanswerAnalysis` | `unansweredAnalysis` |
| 미답변율 | `currentUnanswerRate` | `currentUnansweredRate` |
| 미답변 수 | `unanswerCount` | `unansweredCount` |
| 샘플링 상태 | `unanswer` | `unanswered` |

또 `AnalyzeResultResponse.SurgeDetection`에는 콜백에 있는 `increasedRate`와 `keywordCount`가
빠져 있습니다. 급증 키워드를 화면에 띄우려면 이 두 값이 필요합니다.

---

## 문의

이 문서의 가정(특히 ④의 버전 정의와 엑셀 다운로드 방식)이 실제 설계와 다르면
프론트를 맞추겠습니다. 응답 필드명만 확정해주시면 나머지는 프론트에서 처리합니다.
