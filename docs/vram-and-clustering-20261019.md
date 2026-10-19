# VRAM 절감 및 군집화 설정 (2026-10-19)

브랜치: `fix/pipeline/vram-clustering/AI` (최신 develop 기반). 관련 이슈: [#76](https://github.com/yujin110/BreathAI/issues/76).

- 통합 KB의 text/question/answer에서 HTML, img의 base64, script/style 내용을 제거한다. 원천 파일 및 DB를 변경하지 않는다. 정리 후 텍스트 없는 문서는 검색에서 제외한다.
- BGE-M3 문서 및 질문 임베딩 max_seq_length=512. reranker의 기존 512는 유지한다.
- 임베딩과 reranker 배치 기본값 4. OOM이면 TTOBAGI_RETRIEVAL_BATCH_SIZE=1로 낮춘다.
- 임베딩 인덱스 메타데이터에 길이 제한을 기록한다. 기존 캐시는 다음 실행에서 재생성된다.
- 인덱스 생성 모델 참조 해제와 단계 종료 후 gc.collect()/CUDA empty_cache()로 앞 단계의 모델 및 캐시 회수를 돕는다. 살아 있는 모델 자체의 메모리를 empty_cache가 해제하는 것은 아니다.

## 군집화 계약

HDBSCAN 기능은 기존부터 존재했지만 min_cluster_size=5/min_samples=2로 고정돼 있었다.
이제 AI POST /pipeline multipart에 minClusterSize (정수 >=2, 기본 5), minSamples (정수 >=1, 기본 2)를 전달하면 큐 메타데이터 → worker → 작업 환경변수 → HDBSCAN으로 적용된다.
설정이 다른 동일 analysisId 요청은 충돌로 처리한다. 백엔드는 해당 필드를 받아 AI 요청으로 전달하는 작업이 별도로 필요하다.
직접 clustering 모듈을 실행할 때는 TTOBAGI_MIN_CLUSTER_SIZE / TTOBAGI_MIN_SAMPLES 환경변수를 쓸 수 있다.
클러스터 개수는 직접 지정할 수 없고 최소 크기를 키워도 개수의 단조 감소가 보장되지는 않는다.

## 검증 범위

로컬 최신 develop 기반 브랜치 및 서버에서 각각 64개 단위 테스트를 실행해 63개 통과, 1개 선택적 HTTP 테스트를 건너뛰었다. KB 정리, API 설정 보존/worker 전달/유효성, 작업별 환경변수 전달을 검증했다. Python 구문 검사도 통과했다.

서버의 실제 BGE-M3 + reranker GPU 추론을 통과했다. 긴 한국어 문서 4개에 각 100,000자의 이미지 base64를 포함시킨 입력을 정리하고, 길이 512·배치 4로 인덱스 생성 → 캐시 재사용 → 질의 검색 → reranking을 실행했다. 모델이 함께 올라간 실행의 peak allocated는 **4.324 GiB**, 참조 해제·GC·캐시 정리 후 allocated는 **0.008 GiB**였다. 이는 해당 소량 검색 시험의 PyTorch allocated 값이며 전체 파이프라인이나 nvidia-smi 프로세스 메모리와 같지 않다.

전체 Qwen 생성/평가 파이프라인과 실제 운영 데이터의 OOM 해소는 아직 재검증하지 않았다. 512 토큰 이후 정보가 잘리므로 검색 품질은 전체 분석 결과와 함께 확인해야 한다.

## 서버 적용 기록

- 코드 커밋: `838f2cd783ad27c1948692085fc7be671dfd2fbd`
- 서버: `171.235.173.155:34034`, RTX 5090 32GB
- 적용 디렉터리: `/workspace/breathai-5613078`
- 실행 중 작업이 없음을 확인하고 API 접수를 중지한 뒤 적용했다. 기존 FAILED/DELIVERED 작업은 재실행하지 않았다.
- 백업: `/workspace/breathai-state/backups/vram-20261019T075233Z` (services, SQLite 백업, 실행 wrapper)
- 배포 묶음: `/workspace/vram-clustering-20261019.zip`, SHA-256 `1bbf0d88ff19496a3e75439f9bea654868652a4e02448659ec34038cff60a0e7`
- 수정한 Python 소스 7개의 SHA-256을 배포 manifest와 대조했다. 서버의 기존 reranker activation_fn 호환성 수정은 유지했다.
- HDBSCAN을 최소 크기 12·최소 샘플 수 4로 실제 실행해 설정 적용과 두 합성 군집 분리를 확인했다.
- API·worker 모두 Supervisor RUNNING, `/health` 정상 응답을 확인했다. 시험 종료 후 GPU 사용량은 nvidia-smi 기준 2 MiB였다.
- 기존 인증·DB·콜백 설정 및 LLM 메모리 예산은 유지했다.
- 서버 검증 로그: `/workspace/breathai-checks/vram-unit-tests.log`, `/workspace/breathai-checks/vram-gpu.log`, `/workspace/breathai-checks/vram-gpu-result.json`

롤백이 필요하면 신규 접수를 중지하고 실행 중인 작업이 없을 때 worker를 중지한 뒤 위 백업의 services를 복원한다. 이후 API와 worker를 시작하고 health를 확인한다. 배포 이후의 작업 이력을 보존해야 하므로 SQLite 백업은 무조건 덮어쓰지 않는다.
