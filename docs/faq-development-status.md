# FAQ 후속 개발 및 검증

## 이번 보완

- 원본 테이블별 배치 생성, RUNNING에서 SUCCESS 확정 및 실제 건수 기록.
- 재실행 시 배치 상태/건수/원본명과 행 해시 검증.
- 성공한 카테고리·FAQ 배치를 명시적으로 선택하는 Gold 초기 등록.
- 기존 Gold FAQ 수정/비활성 상태 보존 및 미수정 복사본 이력 생성 방지.
- Gold 등록 시 카테고리 누락 검사.
- AI 검색기가 TTOBAGI_DB_ENV_FILE로 BE와 동일한 접속 설정을 사용하도록 지원.
- 공식 Bronze DDL과 현재 배치 ID에 맞춘 실행 문서 갱신.

## 후속 작업

1. BE 인증을 포함한 FAQ 목록 → 분석 후보 → EXPAND → 변경 이력 전체 API 통합 테스트.
2. source_seq_num이 없는 신규 Gold FAQ를 EXPAND 대상으로 식별하는 AI/BE 계약 설계.
   현재 매칭 계약은 counselling_info 순번을 사용하므로 faq_id를 별도로 전달하는 변경이 필요하다.
3. 비활성화한 원본 FAQ가 파일 KB에서 다시 검색되지 않도록 활성 상태 동기화 정책 확정.
   현재 팀원 요청의 SQL 필터는 Gold 행만 제외하며 파일 원본까지 제외하지 않는다.
4. 배포 DB 종류·포트 확정 후 배포 설정, 스키마 호환성 및 백업·복구 검증.
5. Docker Desktop의 반복되는 Windows 런타임 소켓 오류 해결.
   현재 런타임 폴더 백업으로 복구했지만 재시작 시 재발해 영구 해결로 간주하지 않는다.

2~4는 식별자/API 또는 운영 정책을 바꾸므로 관련 담당자와 계약 확정 후 진행한다.
이번 수정에서는 새 API 필드나 운영 정책을 임의로 추가하지 않는다.

## 2026-10-10 완료 검증

- 로컬 MariaDB 11.8.6, localhost:3307, 기존 컨테이너/볼륨 재사용.
- Bronze 카테고리 49건, FAQ 1,067건: 모든 원본 컬럼과 행 해시 DB 왕복 비교 통과.
- 두 배치 SUCCESS 및 재실행 시 already loaded 확인.
- 미답변 FK 이름 fk_unanswered_learning_batch 확인.
- Flyway baseline 0 및 V1~V4 성공 확인.
- gold_faq 1,067건 최초 등록, 재검증 시 추가 대상 0건.
- gold_faq_edit_history 0건 유지, 미수정 복사본의 AI DB 조회 결과 0건.
- 파일 KB 1,093건 로딩 확인.
- 실제 DB 트랜잭션에서 질문 수정/이력 생성 시 해당 FAQ만 조회되며 원천 순번으로 대체됨을 확인.
  검증 후 롤백하여 편집 이력이나 테스트 질문을 남기지 않았다.
- Python 단위 테스트 23개 통과. 전체 BE HTTP API 및 모델 추론 E2E는 별도 미검증.

초기 DB 백업은 Git 제외 경로 `tmp/ttobagi-before-faq-20261010-173849.sql`에 있다.
이번에 생성한 Gold/Bronze 데이터를 복구하려면 별도 최신 백업이 필요하다.
