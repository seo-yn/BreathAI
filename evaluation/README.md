# BreathAI 성능 평가 준비

## 2026-10-17: 테스트 코드 인계

최신 실행 경로와 결과 저장 위치는 [RESTART_HANDOFF.md](RESTART_HANDOFF.md)를
참고한다. 아래 v2/v3는 실패 원인을 보존한 과거 진단 경로이며 권장 Judge가 아니다.
두 후속 로컬 Judge도 대조 검증에 실패했다. 최신 표는 Codex의 직접 평가로,
검증된 독립 Judge 또는 사람 평가 결과로 해석하지 않는다.

이번 추가 코드는 `evaluation/`과 `tests/`에 한정된다. 최초 체크아웃 커밋
`24ece94` 이후 `services/` 운영 파이프라인의 변경은 없다.

## 2026-10-12: 항목별 평가 v3

현재 후속 경로는 [CHECKLIST_V3.md](CHECKLIST_V3.md)를 따른다. 점수를 먼저
생성하지 않고 사실성·공손함·관련성·실행 안내·필수정보를 검사하며 근거 부족을
unknown으로 구분한다. 결정적 연산 설정과 캐시 없는 대조 시험을 적용했다.
공손함에는 모든 판정값을 동등하게 허용하는 출력 문법을 적용한다.

익명 사람 평가 양식과 질문 검토 초안은 `results/checklist-v3-001`에 있다.
후속 자동 채점 진단은 `results/checklist-v3-004`에 저장한다. 사람 라벨과의
일치도가 검증되기 전까지 자동 점수를 최종 성능 우열로 해석하지 않는다.
진단 모드에서는 대조군 실패 후에도 나머지 대조군을 수집하지만 전체 재채점은
차단한다. 모델·프롬프트가 다른 기록을 섞어 재개하지 않는다.

```powershell
python -m evaluation.run_checklist_judge --output evaluation/results/checklist-v3-004 --diagnostic-controls --resume-controls --controls-only --budget-seconds 1200
python -m evaluation.summarize_checklist evaluation/results/checklist-v3-004
```

## 2026-10-12: 안정화 채점 경로 v2

이전 집계 도구와 별도로 `rejudge_saved.py`가 고정된 생성 답변을 실제 로컬
모델로 재채점한다. NFKC·공백 정규화와 입력 해시 캐시로 동일 입력의 점수를
재사용하며, 근거 인용·수치·자리표시자 검사와 대조군 검증을 적용한다.
자세한 기준·적용 범위·제한은 [JUDGE_V2.md](JUDGE_V2.md)에 있다.

```powershell
python -m unittest tests.test_stable_judge tests.test_evaluation
python -m evaluation.rejudge_saved --controls-only
python -m evaluation.rejudge_saved
```

출력은 `evaluation/results/rejudge-v2-001`에 저장한다. 원점수와 규칙으로
조정한 점수를 분리한다. 기존 질문·답변·검색 임계값은 바꾸지 않는다.
사람 정답 라벨이 없는 자동 예비 평가이며 운영 Refine 자체 채점기는 별도다.
아래는 기존 집계 명세와 2026-10-11 당시의 준비 기록이다.

현재 제공하는 것은 **저장된 평가 결과의 검증·집계 도구**이다. 생성 모델,
Cross-Encoder, Judge를 실행하는 도구나 전체 파이프라인 성능 측정 완료본은 아니다.
외부 패키지 없이 Python 표준 라이브러리로 실행한다. 운영 DB에는 접근하지 않는다.

## 현재 확인된 상태 (2026-10-11)

6번 통합 검증의 최신 결과와 실행 전 보완 항목은
[통합 검증 기록](../docs/pipeline-integration-validation.md)을 참고한다.

- 현재 develop에 `services/retriever.py`와 KB 관련 코드가 있다.
- Clustering, Question Synthesis, Answer Generation, Refine 실행 소스는 로컬
  `hyeon36/ai-services` 및 `Ai_server` 브랜치에서 확인했다.
  확인한 `hyeon36/ai-services` 커밋은 `78a4005ef11af19733a3b4d2d6f24ca9640b4c07`이다.
- `feat/evaluation/pipeline-integration/AI`에 위 소스의 실행기와 직접/간접 의존
  모듈 9개를 원본 그대로 가져왔다: `__init__`, `preprocess`, `clustering`,
  `faq_generator`, `answer_regenerator`, `answer_evaluator`, `answer_refiner`,
  `oos_refresher`, `run_pipeline`. 기존 FAQ/검색/DB 코드는 보존했다.
  Python 구문 검증과 단위 테스트 27개가 통과했다. 전체 모듈 import와 모델 추론은
  아직 검증하지 않았다. 현재 실행기는 운영용 dataset/logs 경로를 사용하므로
  평가용 경로 분리 및 누락 설정 보완 후 실행해야 한다.
- 해당 소스는 외부 추론 서버 대신 Hugging Face 모델을 직접 로딩한다.
  Generator는 `Qwen/Qwen2.5-7B-Instruct`이며 기존 Judge도 같은 모델이다.
- 기존 `answer_evaluator.e2_score`는 질문-생성 답변 쌍을 평가한다.
  이번 명세의 질문-검색 FAQ CE와 다른 지표다. 기존 Judge 프롬프트에도 검색 근거가
  전달되지 않아 명세를 적용하려면 평가 경로를 수정해야 한다.
- 생성기에 필요한 `dataset/answer_templates.json`은 현재 작업 폴더와 확인한
  브랜치/로컬 Git 이력에서 원본을 찾지 못해 `evaluation-reconstructed-v1`으로 신규 작성했다.
  기존 발표의 프롬프트를 재현한 것은 아니다.
- 기존 KB·Bronze·Gold 단위 테스트 23개가 통과했다. 실제 모델 성능 검증과는 별개다.
- 실제 성능 평가에는 파이프라인 소스, 모델 실행 환경, 독립 Judge,
  고정 KB 및 사람이 확인한 테스트 질문/정답 매핑이 필요하다.

## 실험 규칙

### 최신 검색 코드 연결 검증

- `services/retrieval_contract.py`에서 `faqRetrieval`을 검증한다. FAQ 후보의
  클러스터 ID/표준 질문과 검색 결과가 다르거나 결과가 누락/중복되면 모델 로딩 전에 실패한다.
  정상 검색의 빈 결과는 허용한다. FAQ가 아닌 태그는 검색 없이 처리한다.
- 검색기의 `candidateType`, `qType`, `category`, `similarQuestions`, `matchedFaqs`를
  답변 후보에 연결한다. 기존 KB와 검색 구현은 변경하지 않았다.
- 생성/Refine 공통 프롬프트는 최대 5개 근거를 사용한다. 기존 score threshold 0.05는
  유지하므로 점수가 낮은 근거는 제외된다. 검색 Top-5와 실제 사용 근거는 다를 수 있다.
- 통합 실행기의 최종 후보에는 `retrievalEvidence`로 검색 질문, 전체 검색 결과,
  실제 사용 근거, 임계값, 최대 개수를 보관한다. Refine이 질문을 변경하면
  `questionChangedAfterRetrieval=true`로 표시한다. 현재 Refine은 기존 근거를 재사용하며
  질문 변경 후 자동 재검색을 구현한 것은 아니다.
- 단위/연결 테스트 34개 통과. 프롬프트 빌더는 실제 함수를 사용하고 통합 실행 루프의
  모델 추론과 평가 단계는 모의 처리했다. 실제 모델 기반 E2E 성능 검증은 아직 수행하지 않았다.

1. 모델 비교(Llama3, Qwen2.5, Ours)와 Ablation(Cluster+Synth, +RAG, +Refine)은
   별도 실행 폴더로 관리한다. 정확한 모델 ID/리비전, seed, temperature, max_tokens,
   모델/단계별 프롬프트 및 해시, 패키지 버전, 장치, Top-20/Top-5 설정을 보관한다.
2. 동일 실험에서는 입력·KB·평가 모델·Judge 프롬프트를 고정한다.
   Ablation은 동일 클러스터와 표준 질문을 재사용하고 RAG/Refine만 변경한다.
   RAG와 Refine은 입력 구조가 다르므로 단계별 프롬프트를 각각 고정한다.
3. Generator와 Judge는 다른 모델을 사용한다. 집계 도구는 모델의 실제 동일성이나
   실행 설정의 공정성을 검증하지 않으므로 실행 어댑터 연결 시 이를 검증해야 한다.
4. RAG 없는 조건도 **평가용** 검색 근거를 준비해 동일 Judge로 채점한다.
   이 검색 결과는 baseline 답변 생성에는 전달하지 않는다.
5. 평가 전 KB 스냅샷을 고정한다. 원본별 ID 충돌을 피하기 위해 고유 `chunk_id`를
   평가용 문자열 `id`로 매핑하고 사람이 정답 ID를 확인한다.
   KB의 유사질문을 그대로 테스트로 사용하면 누출 가능성이 있으므로 별도 표시한다.

## 입력 계약

`evaluation/dataset/kb_snapshot.json`: `id`, `text`가 있는 객체 배열.
`evaluation/dataset/test_queries.jsonl`: 한 줄에 다음 객체 하나.

```json
{"id":"TEST_001","input_logs":["평가용 질문"],"ground_truth_faq_id":"counselling_info:123","ground_truth_category":"분류"}
```

위 ID는 형식 예시이며 실제 정답이 아니다. 정답 미확정은 `null`로 둔다.
이 버전은 단일 정답만 지원한다. 다중 정답과 KB에 정답이 없는 사례는 별도 설계가 필요하다.

`evaluation/outputs/<experiment>.jsonl`: 모든 샘플×시스템 조합에 대해 한 행.

```json
{"test_id":"TEST_001","system":"rag","status":"ok","standard_question":"표준 질문","answer":"생성된 답변","retrieved":[{"id":"counselling_info:123","cross_encoder":0.8}],"judge":{"accuracy":4,"politeness":4,"actionability":4,"essential":4,"reason":"각 항목의 근거"}}
```

위 수치도 형식 예시이며 측정 결과가 아니다. 실패는 누락하지 않고 다음처럼 기록한다.

```json
{"test_id":"TEST_001","system":"rag","status":"failed","error":"timeout"}
```

실행 시 `judge_prompt.txt`의 필드에 실제 질문, 스냅샷 근거 본문, 답변을 넣는다.
Judge 응답 원문과 모델 메타데이터도 실행 산출물에 보관한다.

## 집계 실행

저장소 루트에서 실행한다. 결과 폴더는 새 경로여야 한다.

```powershell
python -m evaluation.aggregate_results --queries evaluation/dataset/test_queries.jsonl --kb evaluation/dataset/kb_snapshot.json --outputs evaluation/outputs/ablation.jsonl --systems cluster_synth rag refine --result-dir evaluation/results/ablation-001
```

- `raw_scores.csv`: 샘플별 7개 지표와 실행 상태.
- `summary.csv`: 시스템/지표별 평균, 표본 표준편차(ddof=1), 유효 표본 수,
  전체 표본 수, 실패 수·실패율. n<2의 표준편차는 빈 값.
- `inputs.json`: 입력 경로와 SHA-256. 집계 재현을 위한 기록이며 실행 설정 검증은 아니다.

Recall@5와 MRR@5는 정답 매핑이 있는 성공 샘플만 대상으로 한다.
정답이 5위 밖이면 0점이며, 전체 순위 MRR과 구분한다.
CE는 sigmoid 점수(0~1)의 Top-5 문서 평균을 먼저 구하고 샘플 간 평균/SD를 계산한다.
기존 발표의 CE 집계 방식이 확인되지 않았으므로 기존 수치와 직접 동등하다고 해석하지 않는다.
CE는 관련성 지표이며 정확도 확률은 아니다.

실패는 품질 점수 0으로 바꾸지 않는다. 품질 평균은 성공 샘플에 조건부이므로
반드시 실패율과 유효 n을 함께 보고한다. 시스템별 성공 집합이 다르면 공통 성공
샘플 비교를 추가해야 한다. 이 도구는 공통 성공 집합 비교를 자동 생성하지 않는다.
총 처리시간·샘플별 추론시간·GPU peak memory는 실제 실행 어댑터에서 별도로 측정해야 한다.

## 검증

실제 입력을 이용한 격리 smoke 실행(로컬에 모델 전체 파일이 있어야 함):

```powershell
.\ttobagi-AI\.venv\Scripts\python.exe -m evaluation.smoke_pipeline --run-dir evaluation/results/smoke-002 --clusters 5
```

매번 새 결과 폴더를 사용한다. 기존 클러스터 중 노이즈가 아니고 크기가 5 이상인 것을
ID 순으로 선택한다. 클러스터링을 재실행하거나 정식 평가 표본을 선정하는 작업은 아니다.
KB는 원천 파일로 고정하며 운영 DB는 읽지 않는다. 모델 다운로드는 비활성화한다.
중간/최종 산출물과 로그는 해당 실행 폴더에만 기록한다.

2026-10-11 `smoke-001`: 실제 5개 클러스터(로그 80건), 파일 FAQ 1,093건 준비 및
전체 모듈 import 성공. Qwen 로딩 중 `OfflineModeIsEnabled`로 실패했다.
로컬 캐시에는 Qwen 설정/토크나이저만 확인됐으며 모델 가중치는 없었다.
생성 답변은 없고 검색·Refine 단계도 미실행이다. 기록은 실행 폴더의 `run_report.json`에 있다.

```powershell
python -m unittest discover -s tests -v
```

Windows sandbox의 기본 임시 폴더 정리 권한 오류가 발생하면 저장소의
`tmp/evaluation-tests`를 만들고 해당 절대 경로로 `TEMP`와 `TMP`를 지정해 실행한다.
평가 데이터·출력·결과는 Git에서 제외해 운영 로그와 실험 산출물이 커밋되지 않게 한다.
