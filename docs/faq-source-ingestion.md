# 최신 코드 기준 FAQ 원본 적재

2026-10-10, develop `56eaf46` 기준으로 검토했다.

## AI와 BE의 역할

- 최신 BE는 이미 EXPAND 후보의 최고 점수 매칭을 `source_seq_num`으로 조회하고,
  기존 FAQ 수정 및 `gold_faq_edit_history` 저장을 수행한다.
- 기존 API를 그대로 사용한다. 이전 작업에서 제안한 `targetFaqId`와 FE 변경은 필요 없다.
- BE Flyway V1~V4는 Gold 계층을 관리한다. Bronze는 AI 소유라는 현재 정책에 따라
  이 작업의 DDL은 `db/schema/bronze_faq_sources.sql`로 별도 관리한다.
- 기존 FAQ의 `gold_faq` 초기 등록은 BE 작업과 조율해야 한다. Bronze 적재만으로
  FAQ 목록이나 EXPAND 대상이 자동 생성되지는 않는다. 등록 시 `source_seq_num`을
  보존하고 미수정 복사본에 편집 이력을 생성하지 않아야 한다.
- BE API 변경은 복원하지 않았다. Gold 초기 등록은 공식 배치 스키마에 맞춘
  `services.seed_gold_faq`를 사용한다. 기존 운영자 수정은 덮어쓰지 않는다.

## 원본과 검증

| 파일 | 건수 | 용도 |
| --- | ---: | --- |
| dataset/cate_info.csv | 49 | 카테고리 마스터, CP949 |
| dataset/counselling_info.csv | 1,067 | FAQ 마스터, CP949 |
| dataset/일반상담.xlsx | 1,060 | AI 파일 KB, 유사질문1~10 포함 |

CSV 원본은 `cate_info_202604061822.csv`, `counselling_info_202604061820.csv`이다.
원본 파일은 로컬에서만 사용하고 Git에서 제외한다.
일반상담과 counselling_info를 기존 매칭 규칙으로 합치면 1,093건이며,
일반상담에 연결되지 않은 counselling_info는 33건이다.

적재기는 CSV 헤더, 필수 값, 정수 범위, VARCHAR 길이, seq_num 중복을 검사한다.
행의 내용을 SHA-256으로 기록하고, 입력 테이블/행 해시로 재현 가능한 배치 ID를 만든다.
같은 파일 세트를 재실행하면 저장된 순번/해시를 확인한 뒤 적재를 생략한다.
파일 세트나 내용이 달라지면 별도의 스냅샷 배치가 된다. 다른 배치의 같은 행을
전역적으로 제거하는 방식은 아니며 기존 데이터는 덮어쓰지 않는다.

## 실행 절차

저장소 루트와 활성 Python 환경에서 실행한다. DB 연결에는 PyMySQL이 필요하다.
AI KB 및 테스트에는 pandas와 openpyxl이 필요하다.

### 1. 파일만 검증

```powershell
python -m services.bronze_faq_ingest --cate dataset/cate_info.csv --counselling dataset/counselling_info.csv
```

원본 테이블별로 배치를 분리한다. 카테고리 배치는
`b5d2206e-8953-5bfe-8c48-265c80a11557`, FAQ 배치는
`e39e67cf-2c59-5732-becf-6e9e36d4cb19`이다.
이 단계는 DB에 연결하거나 쓰지 않는다.

### 2. DB 접속 및 기존 배치 테이블 확인

`ttobagi-server/.env`의 `TTOBAGI_DB_URL`, `TTOBAGI_DB_USERNAME`,
`TTOBAGI_DB_PASSWORD`를 사용한다. 또는 DB_HOST/DB_PORT/DB_USER/DB_PASSWORD/DB_NAME을
환경변수로 지정할 수 있다. 접속 정보는 Git에 저장하지 않는다.
최신 Docker Compose의 `TTOBAGI_DB_PORT`와 JDBC URL의 포트를 일치시킨다.

기존 DB에서 다음을 조회해 배치 메타데이터 규약을 확인한다.

```sql
SHOW CREATE TABLE bronze_ingest_batch;
SHOW COLUMNS FROM bronze_ingest_batch;
```

팀에서 전달받은 공식 정의는 `db/schema/bronze_ingest_batch.sql` 및
`db/schema/bronze_unanswered_learning.sql`에 저장했다.

현재 로컬 DB는 기존 독립 컨테이너 `ttobagi-mariadb`를 재사용한다.
Docker Desktop 실행 후 `docker start ttobagi-mariadb`로 시작한다.
기존 컨테이너는 Compose 관리 대상이 아니므로 같은 이름으로 새 컨테이너를
만들거나 볼륨을 초기화하지 않는다. 로컬 앱 접속 주소는 localhost:3307이다.
AI 검색기에도 같은 설정을 적용하려면 다음 환경변수를 지정한다.

```powershell
$env:TTOBAGI_DB_ENV_FILE = (Resolve-Path ttobagi-server/.env).Path
```

### 3. DDL 미리보기와 적용

```powershell
python -m services.bronze_faq_migrate --env-file ttobagi-server/.env --rename-unanswered-fk
python -m services.bronze_faq_migrate --env-file ttobagi-server/.env --rename-unanswered-fk --apply
```

첫 명령은 DB 메타데이터를 읽고 계획된 SQL만 출력한다.
두 번째 명령이 실제 테이블 생성과 외래키 변경을 수행한다.
`bronze_unanswered_learning`의 기존 참조 열과 ON UPDATE/ON DELETE를 보존하며,
이미 `fk_unanswered_learning_batch`이면 이름 변경은 생략한다.
테이블이 없으면 공식 DDL로 생성한다. 기존 테이블의 참조 구조가 예상과 다르면 중단한다.
외래키 변경이 필요 없으면 `--rename-unanswered-fk`를 생략할 수 있다.
기존 FAQ 원천 테이블은 IF NOT EXISTS로 구조가 갱신되지 않으므로 DDL을 비교해야 한다.
DDL에는 암묵적 커밋이 있어 전체 작업을 하나의 트랜잭션으로 롤백할 수 없다.

### 4. 배치 행과 원본 적재

```powershell
python -m services.bronze_faq_ingest --cate dataset/cate_info.csv --extracted-at 2026-04-06T18:22:00 --env-file ttobagi-server/.env --apply
python -m services.bronze_faq_ingest --counselling dataset/counselling_info.csv --extracted-at 2026-04-06T18:20:00 --env-file ttobagi-server/.env --apply
```

`--batch-metadata`로 추가 메타데이터 JSON 객체를 전달할 수 있다.
기본 source_system은 seoulmetro, load_type은 FULL이다. 원본명에
`_YYYYMMDDHHMM.csv`가 있으면 추출 시각을 읽는다. 단순 파일명에는 위와 같이
원본 추출 시각을 명시한다. 상태와 건수는 적재기가 계산한다.

InnoDB 테이블에서 배치 행을 먼저 등록한 후 원본을 적재하고 건수를 확인한다.
원본 적재에 실패하면 배치와 원본 쓰기를 함께 롤백한다.
성공 시 SUCCESS로 확정한다. 재실행 시 상태·원본 테이블·건수·행 해시가
모두 일치해야 생략하며, 불완전한 기존 배치를 성공으로 취급하지 않는다.
두 원본을 한 명령으로 입력해도 각각 별도 트랜잭션으로 적재한다.

### 5. Gold 초기 등록

최신 BE Flyway V1~V4 적용 후 실행한다. 첫 명령은 읽기 전용 검증이다.

```powershell
python -m services.seed_gold_faq --env-file ttobagi-server/.env --batch-id e39e67cf-2c59-5732-becf-6e9e36d4cb19 --category-batch-id b5d2206e-8953-5bfe-8c48-265c80a11557
python -m services.seed_gold_faq --env-file ttobagi-server/.env --batch-id e39e67cf-2c59-5732-becf-6e9e36d4cb19 --category-batch-id b5d2206e-8953-5bfe-8c48-265c80a11557 --apply
```

성공한 원본 배치만 사용한다. source_seq_num이 이미 있으면 운영자 수정·비활성 상태를
유지하고 건너뛴다. 신규 복사본에 수정 이력을 만들지 않는다.
빈 질문/답변, 중복 source_seq_num, 연결되지 않는 카테고리는 오류로 처리한다.

## 검증 범위

FAQ 조회 필터, 수정된 질문의 원본 대체, 유사질문 처리, CP949 검증,
재적재 생략, 실패 롤백, 외래키 이름/참조 규칙 보존을 Python 단위 테스트로 확인한다.

```powershell
python -m unittest discover -s tests -v
```

로컬 DB에 Bronze 49건/1,067건 적재, 외래키 이름 변경, Flyway V1~V4 적용은 완료했다.
Gold 등록과 후속 DB 검증 상태는 `docs/faq-development-status.md`에 기록한다.
