-- Team-provided schema for one source table per batch.
CREATE TABLE IF NOT EXISTS bronze_ingest_batch (
    ingest_batch_id VARCHAR(36) NOT NULL COMMENT '배치 실행 UUID',
    source_system VARCHAR(50) NOT NULL COMMENT '원본 시스템명 (seoulmetro)',
    source_table VARCHAR(100) NOT NULL COMMENT '원본 테이블명',
    source_object VARCHAR(255) NULL COMMENT '원본 파일명',
    extracted_at DATETIME NOT NULL COMMENT '원본 추출 시각',
    ingested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    load_type VARCHAR(20) NOT NULL COMMENT 'FULL / INCREMENTAL',
    retention_days INT NOT NULL DEFAULT 365,
    row_count INT NULL,
    batch_status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAIL',
    PRIMARY KEY (ingest_batch_id)
);
