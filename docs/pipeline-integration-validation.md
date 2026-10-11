# AI 파이프라인 통합 검증 (2026-10-11)

최신 상태: 사용자 요청으로 이 컴퓨터에서 추가 설치했던 accelerate/psutil과
`tmp/models`의 BGE 두 모델 및 다운로드 기록을 제거했다. 코드·템플릿·실험 데이터는
보존했으며 다른 컴퓨터에서 실행 환경과 모델을 다시 준비해야 한다.
아래 설치 완료 기록은 제거 전의 검증 이력이다.

## 후속 환경·템플릿 보완

- 샌드박스 밖에서 기존 프로젝트 `.venv`를 실행하면 Python 경로 경고 없이
  `services.run_pipeline`, `services.retriever`, `services.clustering` 실제 import가
  22.83초에 완료됐다. 따라서 앞선 샌드박스 시간 초과를 환경 파손으로 간주하지 않는다.
- 프로젝트 가상환경에 accelerate 1.15.0 및 의존 psutil 7.2.2 설치 완료.
  추가 설치 명세는 `evaluation/requirements-extra.txt`에 기록했다.
- `dataset/answer_templates.json`을 신규 작성했다. 원본 복구가 아닌
  `evaluation-reconstructed-v1`이며 FAQ/EMERGENCY/OUT_OF_SCOPE/REJECT와
  범위 외 4개 하위 유형을 포함한다. 검증되지 않은 전화번호·운영시간·정책은 넣지 않았다.
- 아래 '실행 전 보완 사항'은 최초 점검 이력이다. 템플릿 누락, accelerate 미설치,
  실제 import 미완료 항목은 위 후속 작업으로 해결됐다.
- 실험 경로 분리, 중간 산출물 생성, 독립 Judge 연결과 실제 모델 실행은 여전히 남아 있다.
- 실제 모듈로 4개 태그의 생성/Refine 프롬프트와 범위 외 4개 하위 유형 프롬프트
  생성 성공. 모델 추론은 호출하지 않았다. `pip check` 의존성 충돌 없음.
- 설치 후 프로젝트 `.venv`에서 단위 테스트 34개 통과(0.541초).
  기존 테스트 실행 중 sqlite 연결 미종료 ResourceWarning이 1건 출력됐으며 실패는 없었다.

대상: `feat/evaluation/pipeline-integration/AI` 작업 폴더.
6번 검증은 코드·환경·데이터 계약 점검이다. 실제 모델 추론이나 AI 품질 평가를 수행한 결과가 아니다.

## 통과한 점검

- `services/*.py` 전체 Python 구문 분석 성공.
- 단위/모의 연결 테스트 34개 통과. Codex 번들 Python에서 실행했고,
  Windows 임시 폴더 권한 문제를 피하려고 TEMP/TMP를 저장소 `tmp/evaluation-tests`로 지정했다.
- 최신 `retriever.py`, `faq_kb.py`는 HEAD 대비 변경 없음.
- 원천 미응답 CSV와 전처리 CSV 모두 1,103행이며 필요한 컬럼 존재.
- 클러스터링의 최소 정규화 길이 2자 조건 적용 후 1,029행.
  이 집합의 `(logId, normalizedForClustering)`과 기존 `clusters.csv`의
  `(logId, text)`가 중복 횟수까지 일치한다.
- 76개 클러스터 요약의 크기가 `clusters.csv`의 실제 그룹별 건수와 일치한다.
  이것은 파일 간 일관성 점검이며 임베딩/클러스터링을 재실행한 검증은 아니다.
- 실제 파일 기반 KB 로더 실행 성공: 병합 FAQ 1,093건, 고유 chunk_id 1,093개.
  원천 일반상담 1,060건, counselling 1,067건 중 counselling 단독 33건.
  DB 연결과 임베딩/검색 모델 실행은 수행하지 않았다.
- 프로젝트 `.venv`에서 전처리, 클러스터링, 질문 생성 모듈의 실제 import 성공.

## 실행 전 보완 사항

검색기와 통합 실행기의 실제 import 검사는 별도 프로세스에서 오프라인 환경변수로
수행했으나 45초 제한 내에 완료되지 않았다. 첫 검색기 import 완료 출력도 없었으므로
답변 모듈들의 실제 import는 검증되지 않았다. 시간 초과만으로 원인을 특정할 수 없으며,
단위 테스트의 모의 import와 구분한다.

1. `dataset/answer_templates.json` 없음: 입력 설정이므로 복구 또는 새 작성 필요.
2. `accelerate` 없음: 현재 LLM 로더의 `device_map='auto'` 실행 전 설치 필요.
3. 프로젝트 `.venv` 실행 시 원래 Python313 경로를 찾지 못했다는 경고가 출력된다.
   명령 일부는 실행되지만 실제 모델 실행 전 인터프리터 경로와 환경을 정리해야 한다.
4. 이전 환경 점검에서 PyTorch 2.9.1+cpu, CUDA 미사용 확인.
   실제 7B 추론 속도와 메모리는 아직 측정하지 않았다.
5. `faq_candidates.json`, `faq_retrieval.json` 없음: 원천 입력의 누락이 아니라
   질문 생성/검색을 실행해야 생성되는 중간 산출물이다. 답변 단계만 단독 실행할 수 없다.
6. 실행기는 현재 dataset/logs에 기록하므로 실험용 경로를 분리한 뒤 소량 실행한다.
7. 독립 Judge와 근거 기반 채점은 별도 평가 연결이 필요하다.
   현재 내부 Judge는 생성 모델을 재사용한다.

테스트 통과를 전체 실행 준비 완료나 실제 모델 성능 통과로 해석하지 않는다.
