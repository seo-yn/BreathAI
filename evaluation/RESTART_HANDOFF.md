# 10표본 실험 코드와 로컬 산출물 인계

## 변경 범위

최초 체크아웃 `24ece94` 이후 운영 `services/` 및 데이터 생성 프롬프트는
수정하지 않았다. 대신 평가 전용 실행기에서 고정 질문, 3B 생성 모델,
RAG 초안 수정과 1회 자기 비평/Refine을 구성했다. 이 실험 흐름은 운영
파이프라인의 조건부 best-of-N 또는 전처리부터의 종단간 실행과 같지 않다.
따라서 평가 코드 추가도 변경 사항이지만, 서비스 성능 개선을 구현한 변경은 아니다.

## 결과 위치와 업로드 제외

- `evaluation/results/restart-10-20261014/`: 최신 10질문 × 5조건의 답변·검색·CE·실행 기록.
- 그 아래 `codex-direct-review/`: 직접 평정 `assessments.json`, 집계 `summary.json`,
  비교표 `results.md`, `results.html`, `comparison_tables.png`.
- 그 아래 `glider/`: 두 번째 로컬 Judge의 대조 검증 및 중단된 실행 기록.
- `evaluation/results/presentation-10-001/`: 이전 10표본 실험.
- `evaluation/results/checklist-*`, `rejudge-*`: 이전 Judge 진단.
- `evaluation/outputs/`: 초기 집계 도구용 JSONL 입력 경로. 최신 답변은 이곳이 아니라 results에 있다.
- `tmp/`: 모델, 캐시, 바이너리, 환경 준비 스크립트와 평가용 KB/임베딩.

`evaluation/.gitignore`는 dataset/outputs/results 내용을 제외하고 루트
`.gitignore`는 tmp, 가상환경, 로그와 원천 상담 파일을 제외한다.
이 커밋은 코드·평가 지침·테스트만 포함하며 원본 상담 로그, 모델 및 생성
답변/점수/표 이미지를 포함하지 않는다. 다른 PC에서는 이 코드만 clone해도
같은 모델과 로컬 실행 자산이 자동 설치되는 것은 아니다.

## 실행 자산

이 노트북 작업 루트는 `D:\projects\Capstone\BreathAI`다. 모든 설치·캐시는 D를 사용했다.
초기 준비/다운로드는 로컬 `tmp/` 보조 스크립트로 수행했으며 일반 설치기로 배포하지 않는다.
실행 전 아래 파일을 별도로 준비해야 한다. 없으면 아래 명령을 실행하지 않는다.

- `tmp/evaluation-assets/kb_snapshot.json`, `kb_embeddings.npy`, `retrieval_models.json`.
  KB는 source별 `chunk_id`를 가진 FAQ이고 임베딩은 KB와 같은 행 순서의 BGE-M3 1024차원이다.
  retrieval_models는 `model_id`, 로컬 `path`를 가진 객체 배열이다.
- `dataset/clusters.csv`, `dataset/cluster_keywords.json`: 기존 군집 및 키워드.
- 결과 폴더의 `runtime_manifest.json`: `server`(llama-server 실행 파일 경로),
  `files` 배열. 각 항목은 `role`(llama/qwen/judge), `path`, 모델 리비전·정밀도·해시.
  GPU 레이어 후보와 context_length는 선택 필드다. 실행기 구현은 restart_llama_runtime.py 참고.
- Llama 3.2 3B Instruct F16, Qwen2.5 3B Instruct FP16 GGUF 및 llama.cpp CUDA 실행 파일.
- Python의 torch, transformers, sentence-transformers, numpy. 표 이미지 생성에는 matplotlib 필요.

준비 후 저장소 루트에서 실행한다. 기존 결과 폴더는 재개/덮어쓰기에 사용되므로
원본 결과를 별도 보관하고 실행해야 한다. 이 경로들은 실험 당시 날짜로 고정되어 있다.

```powershell
python -m evaluation.prepare_restart_benchmark
python -m evaluation.restart_retrieval
python -m evaluation.run_restart_benchmark --stage llama
python -m evaluation.run_restart_benchmark --stage qwen
```

`restart_cross_encoder`는 judged_answers.json을 입력으로 사용하므로 생성만 끝낸
단계에는 바로 실행할 수 없다. 최초 실행은 로컬 Judge 단계 이후 CE를 측정했다.
현재 저장된 raw_records.json에는 50개 답변의 실제 CE가 포함되어 있다.

## 평가의 상태와 제한

M-Prometheus-7B Q4_K_M, GLIDER Q4_K_M 모두 기본 타당성 대조 검증에 실패했다.
`run_restart_benchmark --stage judge`, `rejudge_restart_glider` 및 과거 v2/v3는
진단을 보존한 코드다. 검증 실패에도 진단 점수를 수집할 수 있으므로, 실행 완료가
Judge 타당성 검증 통과를 뜻하지 않는다. 자동 평가 표를 최종 품질 결과로 사용하지 않는다.

`report_codex_direct_review.py`는 현재 대화에서 작성해 로컬에 저장한
assessments.json을 읽어 표를 재생성한다. 새 AI 평가를 실행하는 프로그램이 아니다.
점수는 1~5점 사실성·공손함·실행 가능성·필수정보 평정이고 정답률이 아니다.
평가자는 모델 이름을 본 단일 Codex이며 사람 일치도와 독립 재현성은 미검증이다.
스크립트에는 개별 생성 답변과 직접 평정 점수를 하드코딩하지 않았다.

```powershell
python -m evaluation.report_codex_direct_review
```

`prepare_external_judge_bundle.py`는 검토용 요청 본문·비용 추정만 준비한다.
실제 외부 API 호출·키 설정·결제 코드는 없고 유료 API는 사용하지 않았다.

10개 질문은 목적 표집이고 같은 질문을 5조건에 사용했다. 전체 운영 분포나
군집/질문 생성 품질을 대표하지 않는다. Llama 2개 출력의 토큰 제한도 그대로 포함했다.

## 경량 검증

```powershell
python -m unittest discover -s tests -v
```

모델 추론을 다시 실행하는 테스트가 아니다. 로컬 benchmark가 없는 환경에서는
해당 파일에 의존하는 1개 테스트만 skip한다. 단위 검사 통과가 Judge의 평가 타당성을
입증하지 않는다. 과거 실패한 Judge의 규칙 테스트도 실행 기록 보존 목적으로 포함한다.
