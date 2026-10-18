-- 테스트 전용: 운영 코드가 카테고리 이름을 읽는 bronze_cate_info의 최소 복사본
-- 실제 테이블은 AI 담당이 관리한다 (db/schema/bronze_faq_sources.sql)
CREATE TABLE IF NOT EXISTS bronze_cate_info (
    seq_num INT NOT NULL,
    q_type INT NOT NULL,
    q_disp_name VARCHAR(255) NOT NULL,
    state TINYINT NOT NULL DEFAULT 1,
    -- 같은 코드가 여러 번 적재되면 가장 최근 이름을 고르는 데 쓴다
    _ingested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (seq_num)
);

INSERT INTO bronze_cate_info (seq_num, q_type, q_disp_name, state) VALUES
    (1, 172, '승차권/운임', 1),
    (2, 176, '영업/제도', 1),
    (3, 188, '열차운영관련', 1);
