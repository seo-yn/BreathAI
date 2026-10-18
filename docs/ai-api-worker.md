# AI 분석 API와 콜백

관련 이슈: [AI/BE 분석 요약 연동 #66](https://github.com/yujin110/BreathAI/issues/66).
브랜치: `feat/dashboard/analysis-callback/AI` (최신 develop 기반).
BE의 수신·저장 변경은 BE 담당 브랜치에서 별도로 진행한다. 이 AI 브랜치는 BE 파일을 변경하지 않는다.

## 실행

Python 3.11 환경에서 API 의존성은 `requirements-api.txt`, GPU 분석 의존성은
`requirements-runtime.txt`로 설치한다. 모델·FAQ 소스·DB 설정은 별도 준비한다.
실제 인증 토큰·DB 비밀번호·업로드·모델 캐시는 저장소에 넣지 않는다.
환경변수 이름은 `deployment.env.example`을 참고한다.

```bash
python -m uvicorn services.pipeline_api:create_app --factory --host 127.0.0.1 --port 18000
python -u -m services.pipeline_worker
```

API와 worker는 같은 영속 TTOBAGI_JOB_ROOT를 사용한다. GPU당 worker는 1개다.
TTOBAGI_BE_BASE_URL과 두 방향의 서로 다른 토큰을 설정한다.
CPU offload가 필요한 환경은 TTOBAGI_LLM_GPU_MEMORY/TTOBAGI_LLM_CPU_MEMORY를 명시한다.
기본 메모리 예산을 임의로 올리지 않는다.

## 입력과 처리

- Bearer 인증된 multipart `POST /pipeline`: file, uploadId, analysisId,
  isMaskingEnabled, isTranslationEnabled, periodStartDate, periodEndDate.
- BE가 XLSX를 CSV로 변환한다. AI는 UTF-8 CSV의 번호/질문/날짜 및 선택적 logType을 받는다.
- 번호는 업로드 내 유일한 INT, 날짜는 ISO 날짜/시각이어야 한다.
- 접수 후 202 반환. 같은 analysisId와 입력/options는 재실행하지 않으며 다른 입력이면 409.
- 작업별 폴더에서 전처리→군집화→FAQ→검색→답변/평가를 실행한다.
- 결과는 인증된 `POST /api/v1/dashboard/analyze/callback/{analysisId}`로 전송한다.
- PREPROCESSING/ANALYZING은 실제 단계 진입 시 자식 프로세스가 끝나기 전에 전송한다.
- 중간 상태는 별도 SQLite 테이블로 관리하며 최종 payload/delivery를 덮어쓰지 않는다.
- 중간 전송 실패는 분석을 중단하지 않는다. 응답 유실은 UNKNOWN으로 남기고 자동 재전송하지 않는다.
- 종료 후 중간 이벤트·중복·역순 전송을 막는다. standalone=true는 외부 콜백을 보내지 않는다.
- 상세 필드 의미와 BE 전달 계약은 [포인트·통계 문서](dashboard-callback-metrics.md)를 참고한다.

## 로컬 테스트

```bash
python -m unittest discover -s tests -v
```

테스트는 GPU/운영 DB 없이 가짜 추론·모의 BE 응답과 경량 실제 자식 프로세스를 사용한다.
TTOBAGI_TEST_HTTP=1이면 로컬 multipart HTTP 전송도 검증한다.
실제 Vast 전체 GPU 분석·운영 BE 수신은 이 결과와 구분해서 배포 후 확인한다.
