-- Team-provided schema. Requires bronze_ingest_batch first.
CREATE TABLE IF NOT EXISTS bronze_unanswered_learning (
    seq_num INT NOT NULL COMMENT '엑셀 번호 컬럼',
    domain VARCHAR(50) NOT NULL DEFAULT 'seoulmetro' COMMENT '도메인. 예: seoulmetro',
    answer_request VARCHAR(255) NULL COMMENT '답변요청 컬럼. 현재 데이터에서는 대부분 NULL',
    user_question TEXT NOT NULL COMMENT '사용자 질문 원문. 숫자형 입력도 문자열로 저장',
    is_valid TINYINT NOT NULL COMMENT '질의 유효/무효. 1=유효, 0=무효',
    question_type VARCHAR(100) NULL COMMENT '질문 유형 라벨',
    core_keywords TEXT NULL COMMENT '질문 핵심 키워드',
    answer_direction TEXT NULL COMMENT '답변 생성 방향 검토',
    match_probability DECIMAL(8,6) NOT NULL DEFAULT 0.000000 COMMENT '답변확률. 0~1 사이 비율 값',
    learn_state VARCHAR(50) NULL COMMENT '학습 상태. 예: 학습대기',
    occurred_at DATETIME NOT NULL COMMENT '엑셀 날짜 컬럼의 로그 발생 시각',
    raw_json JSON NULL COMMENT '엑셀 원본 행 전체 JSON',
    _ingest_batch_id VARCHAR(36) NOT NULL,
    _ingested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    _source_table VARCHAR(100) NOT NULL DEFAULT 'unanswered_learning',
    _source_file VARCHAR(255) NULL,
    _row_hash VARCHAR(64) NOT NULL COMMENT '중복 적재 방지용 행 해시',
    PRIMARY KEY (_ingest_batch_id, seq_num),
    KEY idx_bronze_unanswered_learning_occurred_at (occurred_at),
    KEY idx_bronze_unanswered_learning_is_valid (is_valid),
    KEY idx_bronze_unanswered_learning_question_type (question_type),
    KEY idx_bronze_unanswered_learning_learn_state (learn_state),
    KEY idx_bronze_unanswered_learning_row_hash (_row_hash),
    CONSTRAINT fk_unanswered_learning_batch
        FOREIGN KEY (_ingest_batch_id) REFERENCES bronze_ingest_batch(ingest_batch_id)
) COMMENT='또타24 미답변 분석 엑셀 원본 학습 데이터';
