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

단위 테스트로 KB 정리 및 API 설정 보존/worker 전달/유효성 검사를 확인한다. 실제 서버 GPU 추론과 VRAM peak 측정은 수행하지 않았다. 512 토큰 이후 정보가 잘리므로 검색 품질은 서버 재실행 결과와 함께 확인해야 한다.
