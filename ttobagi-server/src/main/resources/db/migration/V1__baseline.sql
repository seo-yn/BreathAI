-- Gold 계층 스키마 baseline
-- 이미 테이블이 있는 DB에서도 안전하게 실행되도록 IF NOT EXISTS 로 작성한다
-- Bronze 계층은 AI 서버 소유라 포함하지 않는다

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `gold_analysis_job` (
  `analysis_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `error_message` text DEFAULT NULL COMMENT '실패 시 에러 로그',
  `file_name` varchar(255) NOT NULL COMMENT '업로드 당시 파일명 참조',
  `finished_at` datetime DEFAULT NULL COMMENT '분석 종료 시각',
  `is_masking_enabled` bit(1) DEFAULT b'0',
  `is_translation_enabled` bit(1) DEFAULT b'0',
  `period_end_date` date DEFAULT NULL COMMENT '분석 대상 종료일',
  `period_start_date` date DEFAULT NULL COMMENT '분석 대상 시작일',
  `started_at` datetime DEFAULT NULL COMMENT '분석 시작 시각',
  `status` varchar(20) NOT NULL COMMENT 'PENDING, PREPROCESSING, ANALYZING, COMPLETED, FAIL',
  `upload_id` varchar(36) NOT NULL COMMENT '파일 업로드 식별 UUID',
  PRIMARY KEY (`analysis_id`),
  KEY `fk_analysis_upload_id` (`upload_id`),
  CONSTRAINT `fk_analysis_upload_id` FOREIGN KEY (`upload_id`) REFERENCES `gold_upload_file` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='데이터 분석 작업 이력 및 상태';

CREATE TABLE IF NOT EXISTS `gold_cluster` (
  `cluster_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cluster_label` int(11) NOT NULL COMMENT 'HDBSCAN 번호 (-1=노이즈)',
  `cluster_name` varchar(100) DEFAULT NULL COMMENT '클러스터 이름 (예: 냉난방 온도 조절)',
  `cluster_size` int(11) DEFAULT NULL COMMENT '해당 클러스터에 포함된 로그 총 개수',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `top_keywords` json DEFAULT NULL COMMENT 'TF-IDF Top5 키워드 배열',
  `analysis_id` bigint(20) NOT NULL,
  PRIMARY KEY (`cluster_id`),
  UNIQUE KEY `uq_analysis_label` (`analysis_id`,`cluster_label`),
  CONSTRAINT `fk_cluster_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='클러스터링 결과 (HDBSCAN)';

CREATE TABLE IF NOT EXISTS `gold_cluster_log_map` (
  `map_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `log_text` text DEFAULT NULL COMMENT '사용자 실제 입력 문구 복사본',
  `log_type` varchar(20) NOT NULL COMMENT 'CORRECT / LOW_QUALITY / UNANSWER',
  `source_log_seq_num` int(11) NOT NULL COMMENT 'Bronze chatting_log.seq_num',
  `umap_x` float DEFAULT NULL COMMENT 'UMAP x 좌표',
  `umap_y` float DEFAULT NULL COMMENT 'UMAP y 좌표',
  `unanswered_reason` varchar(50) DEFAULT NULL COMMENT '미답변 사유 (Enum 매핑)',
  `cluster_id` bigint(20) NOT NULL,
  PRIMARY KEY (`map_id`),
  UNIQUE KEY `uq_cluster_log` (`cluster_id`,`source_log_seq_num`),
  CONSTRAINT `fk_map_cluster_id` FOREIGN KEY (`cluster_id`) REFERENCES `gold_cluster` (`cluster_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='클러스터-로그 매핑';

CREATE TABLE IF NOT EXISTS `gold_faq` (
  `faq_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer` longtext NOT NULL COMMENT '답변',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `is_active` tinyint(4) NOT NULL DEFAULT 1 COMMENT '활성 여부',
  `keywords` json DEFAULT NULL COMMENT '["유실물", "분실물"]',
  `q_type` int(11) DEFAULT NULL COMMENT '카테고리 코드',
  `qa_cnt` int(11) NOT NULL DEFAULT 0 COMMENT '누적 질의 수',
  `question` text NOT NULL COMMENT '질문',
  `source_seq_num` int(11) DEFAULT NULL COMMENT 'bronze_counselling_info.seq_num 참조 (기존 FAQ 연동용)',
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `candidate_id` bigint(20) DEFAULT NULL COMMENT '후보 식별 ID',
  `created_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`faq_id`),
  KEY `fk_faq_candidate_id` (`candidate_id`),
  KEY `fk_faq_created_by` (`created_by`),
  CONSTRAINT `fk_faq_candidate_id` FOREIGN KEY (`candidate_id`) REFERENCES `gold_faq_candidate` (`candidate_id`),
  CONSTRAINT `fk_faq_created_by` FOREIGN KEY (`created_by`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='실제 운영 FAQ';

CREATE TABLE IF NOT EXISTS `gold_faq_action_log` (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` varchar(20) NOT NULL COMMENT 'ACCEPT / REJECT / APPLY / REVERT',
  `after_status` varchar(20) DEFAULT NULL COMMENT '변경 후 상태',
  `before_status` varchar(20) DEFAULT NULL COMMENT '변경 전 상태',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `note` text DEFAULT NULL COMMENT '반려 사유 등 추가 참고사항',
  `acted_by` bigint(20) DEFAULT NULL,
  `candidate_id` bigint(20) NOT NULL COMMENT '후보 식별 ID',
  PRIMARY KEY (`log_id`),
  KEY `fk_action_user_id` (`acted_by`),
  KEY `fk_action_candidate_id` (`candidate_id`),
  CONSTRAINT `fk_action_candidate_id` FOREIGN KEY (`candidate_id`) REFERENCES `gold_faq_candidate` (`candidate_id`),
  CONSTRAINT `fk_action_user_id` FOREIGN KEY (`acted_by`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FAQ 후보 검토/반영 이력 (Audit Log)';

CREATE TABLE IF NOT EXISTS `gold_faq_candidate` (
  `candidate_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '후보 식별 ID',
  `answer_draft` text DEFAULT NULL COMMENT '생성된 답변 초안',
  `candidate_type` varchar(20) NOT NULL COMMENT '신규(NEW) 또는 확장(EXPAND)',
  `created_at` datetime(6) NOT NULL,
  `occurrence_count` int(11) DEFAULT 0 COMMENT '유사 질문 출현 횟수',
  `representative_keywords` json DEFAULT NULL COMMENT '대표 키워드 배열',
  `review_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACCEPTED, REJECTED, APPLIED',
  `standard_question` text NOT NULL COMMENT '생성된 표준 질문',
  `updated_at` datetime(6) NOT NULL,
  `analysis_id` bigint(20) NOT NULL,
  `cluster_id` bigint(20) DEFAULT NULL,
  `reviewed_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`candidate_id`),
  KEY `fk_candidate_analysis_id` (`analysis_id`),
  KEY `fk_candidate_cluster_id` (`cluster_id`),
  KEY `fk_candidate_user_id` (`reviewed_by`),
  CONSTRAINT `fk_candidate_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`),
  CONSTRAINT `fk_candidate_cluster_id` FOREIGN KEY (`cluster_id`) REFERENCES `gold_cluster` (`cluster_id`),
  CONSTRAINT `fk_candidate_user_id` FOREIGN KEY (`reviewed_by`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `gold_faq_candidate_match` (
  `match_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `match_score` decimal(5,4) NOT NULL COMMENT '매칭 유사도 점수',
  `matched_faq_seq_num` int(11) NOT NULL COMMENT 'counselling_info.seq_num (Bronze 참조)',
  `candidate_id` bigint(20) NOT NULL COMMENT '후보 식별 ID',
  PRIMARY KEY (`match_id`),
  UNIQUE KEY `uq_candidate_faq` (`candidate_id`,`matched_faq_seq_num`),
  CONSTRAINT `fk_match_candidate_id` FOREIGN KEY (`candidate_id`) REFERENCES `gold_faq_candidate` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FAQ 후보-기존 FAQ 매칭 결과';

CREATE TABLE IF NOT EXISTS `gold_faq_edit_history` (
  `history_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `after_answer` longtext DEFAULT NULL COMMENT '수정 후 답변',
  `after_question` text DEFAULT NULL COMMENT '수정 후 질문',
  `before_answer` longtext DEFAULT NULL COMMENT '수정 전 답변',
  `before_question` text DEFAULT NULL COMMENT '수정 전 질문',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `edit_reason` text DEFAULT NULL COMMENT '수정 사유',
  `analysis_id` bigint(20) DEFAULT NULL COMMENT '연관 분석 ID (수동 수정 시 NULL 가능)',
  `edited_by` bigint(20) DEFAULT NULL COMMENT '수정한 관리자 ID (탈퇴 시 NULL)',
  `faq_id` bigint(20) NOT NULL,
  PRIMARY KEY (`history_id`),
  KEY `fk_edit_history_analysis_id` (`analysis_id`),
  KEY `fk_edit_history_user_id` (`edited_by`),
  KEY `fk_edit_history_faq_id` (`faq_id`),
  CONSTRAINT `fk_edit_history_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`),
  CONSTRAINT `fk_edit_history_faq_id` FOREIGN KEY (`faq_id`) REFERENCES `gold_faq` (`faq_id`),
  CONSTRAINT `fk_edit_history_user_id` FOREIGN KEY (`edited_by`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='현행 FAQ 직접 수정 이력';

CREATE TABLE IF NOT EXISTS `gold_faq_snapshot` (
  `snapshot_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `ci_answer0` longtext DEFAULT NULL COMMENT '스냅샷 시점 답변',
  `ci_question` text DEFAULT NULL COMMENT '스냅샷 시점 질문',
  `q_type` int(11) DEFAULT NULL COMMENT '질문 유형 코드',
  `qa_cnt` int(11) DEFAULT NULL COMMENT '조회수/매칭수',
  `snapshotted_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `source_seq_num` int(11) NOT NULL COMMENT 'counselling_info.seq_num (Bronze)',
  `analysis_id` bigint(20) NOT NULL,
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uq_analysis_faq` (`analysis_id`,`source_seq_num`),
  CONSTRAINT `fk_snapshot_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='분석 시점의 FAQ 원본 데이터 스냅샷';

CREATE TABLE IF NOT EXISTS `gold_password_reset_token` (
  `token_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `expiry_date` datetime(6) NOT NULL,
  `is_used` tinyint(1) NOT NULL DEFAULT 0,
  `token` varchar(255) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `UK11duprkc21pxn0sgcj0r9ai9j` (`token`),
  KEY `fk_token_user_id` (`user_id`),
  CONSTRAINT `fk_token_user_id` FOREIGN KEY (`user_id`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `gold_performance_candidate_map` (
  `map_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `candidate_id` bigint(20) NOT NULL COMMENT '후보 식별 ID',
  `comparison_id` bigint(20) NOT NULL,
  PRIMARY KEY (`map_id`),
  UNIQUE KEY `uq_comparison_candidate` (`comparison_id`,`candidate_id`),
  KEY `fk_perf_map_candidate_id` (`candidate_id`),
  CONSTRAINT `fk_perf_map_candidate_id` FOREIGN KEY (`candidate_id`) REFERENCES `gold_faq_candidate` (`candidate_id`),
  CONSTRAINT `fk_perf_map_comparison_id` FOREIGN KEY (`comparison_id`) REFERENCES `gold_performance_comparison` (`comparison_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='성능 비교-FAQ 후보 매핑 (어느 후보가 반영되어 성능이 개선됐는지 추적)';

CREATE TABLE IF NOT EXISTS `gold_performance_comparison` (
  `comparison_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accuracy_gain` decimal(5,2) DEFAULT NULL COMMENT '예상 정확도 향상치(%)',
  `actual_unanswer_cnt` int(11) DEFAULT NULL COMMENT '실제 반영 후 미답변 건수',
  `after_unanswer_cnt` int(11) DEFAULT NULL COMMENT '개선 후 예상 미답변 건수',
  `after_unanswer_rate` decimal(5,2) DEFAULT NULL COMMENT '개선 후 예상 미답변율(%)',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `eval_threshold` decimal(4,3) DEFAULT NULL COMMENT '적용 임계값 (예: 0.75)',
  `false_positive_rate` decimal(4,3) DEFAULT NULL COMMENT '오답 발생률',
  `resolved_count_by_ai` int(11) DEFAULT NULL COMMENT 'AI를 통해 해결된 미답변 로그 수',
  `analysis_id` bigint(20) NOT NULL,
  `before_stat_date` date NOT NULL COMMENT '비교 기준이 되는 통계 날짜',
  PRIMARY KEY (`comparison_id`),
  UNIQUE KEY `uq_analysis` (`analysis_id`),
  KEY `fk_perf_comp_usage_stat` (`before_stat_date`),
  CONSTRAINT `fk_perf_comp_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`),
  CONSTRAINT `fk_perf_comp_usage_stat` FOREIGN KEY (`before_stat_date`) REFERENCES `gold_usage_stat` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='미답변율 개선 전·후 비교 (FAQ 후보 반영 효과 측정)';

CREATE TABLE IF NOT EXISTS `gold_retrieve_log` (
  `retrieve_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `query_text` text NOT NULL COMMENT '사용자 검색어',
  `rerank_score` decimal(5,4) DEFAULT NULL COMMENT 'BGE-Reranker 최종 유사도 점수',
  `retrieved_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `returned_faq_seq_num` int(11) DEFAULT NULL COMMENT '반환된 FAQ seq_num (Bronze counselling_info 참조)',
  `analysis_id` bigint(20) DEFAULT NULL COMMENT '검색 기반 분석 ID (FAQ DB 버전 추적)',
  `user_id` bigint(20) DEFAULT NULL COMMENT '검색한 사용자 (비로그인 시 NULL)',
  PRIMARY KEY (`retrieve_id`),
  KEY `fk_retrieve_analysis_id` (`analysis_id`),
  KEY `fk_retrieve_user_id` (`user_id`),
  CONSTRAINT `fk_retrieve_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`),
  CONSTRAINT `fk_retrieve_user_id` FOREIGN KEY (`user_id`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='실시간 FAQ 검색 로그 (/retrieve)';

CREATE TABLE IF NOT EXISTS `gold_stat_date` (
  `stat_date` date NOT NULL COMMENT '통계 기준 날짜 (PK)',
  `stat_month` int(11) NOT NULL COMMENT '월 (추출값)',
  `stat_year` int(11) NOT NULL COMMENT '연도 (추출값)',
  PRIMARY KEY (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='통계 기준 날짜 마스터 테이블';

CREATE TABLE IF NOT EXISTS `gold_surge_detection` (
  `surge_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `increased_rate` decimal(7,2) DEFAULT NULL COMMENT '이전 대비 증가율 (예: 150.0)',
  `is_new` tinyint(4) NOT NULL DEFAULT 0 COMMENT '신규 유형 여부',
  `keyword` varchar(100) NOT NULL COMMENT '급증 키워드',
  `keyword_count` int(11) NOT NULL DEFAULT 0 COMMENT '키워드 출현 횟수',
  `analysis_id` bigint(20) NOT NULL,
  `prev_analysis_id` bigint(20) DEFAULT NULL COMMENT '비교 기준 이전 분석 ID',
  `related_cluster_id` bigint(20) DEFAULT NULL COMMENT '연관 클러스터 ID',
  PRIMARY KEY (`surge_id`),
  UNIQUE KEY `uq_analysis_keyword` (`analysis_id`,`keyword`),
  KEY `fk_surge_prev_analysis_id` (`prev_analysis_id`),
  KEY `fk_surge_cluster_id` (`related_cluster_id`),
  CONSTRAINT `fk_surge_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`),
  CONSTRAINT `fk_surge_cluster_id` FOREIGN KEY (`related_cluster_id`) REFERENCES `gold_cluster` (`cluster_id`),
  CONSTRAINT `fk_surge_prev_analysis_id` FOREIGN KEY (`prev_analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='급증 질문 탐지 결과';

CREATE TABLE IF NOT EXISTS `gold_surge_stat` (
  `surge_stat_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `count` int(11) NOT NULL DEFAULT 0 COMMENT '발생 건수',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `period_type` varchar(10) NOT NULL COMMENT 'BASE(기준기간) / PREV(비교기간)',
  `stat_date` date NOT NULL COMMENT '통계 기준 날짜 (PK)',
  `surge_id` bigint(20) NOT NULL,
  PRIMARY KEY (`surge_stat_id`),
  UNIQUE KEY `uq_surge_date_type` (`surge_id`,`stat_date`,`period_type`),
  KEY `fk_surge_stat_date` (`stat_date`),
  CONSTRAINT `fk_surge_stat_date` FOREIGN KEY (`stat_date`) REFERENCES `gold_stat_date` (`stat_date`),
  CONSTRAINT `fk_surge_stat_surge_id` FOREIGN KEY (`surge_id`) REFERENCES `gold_surge_detection` (`surge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='급증 탐지 기간별 건수';

CREATE TABLE IF NOT EXISTS `gold_synonym_candidate` (
  `synonym_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `synonym_text` varchar(100) NOT NULL COMMENT '유사어 문구',
  `synonym_type` varchar(20) DEFAULT NULL COMMENT 'TYPO / ABBR / SYNONYM',
  `candidate_id` bigint(20) NOT NULL COMMENT '후보 식별 ID',
  PRIMARY KEY (`synonym_id`),
  UNIQUE KEY `uq_candidate_synonym` (`candidate_id`,`synonym_text`),
  CONSTRAINT `fk_synonym_candidate_id` FOREIGN KEY (`candidate_id`) REFERENCES `gold_faq_candidate` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='유사어/동의어 후보';

CREATE TABLE IF NOT EXISTS `gold_unanswer_stat` (
  `stat_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `current_unanswer_rate` decimal(5,2) DEFAULT NULL COMMENT '분석 결과 적용 후 예상 미답변율 (%)',
  `reason` enum('ETC','FOREIGN_LANGUAGE','POLICY_RESTRICTION','UNREGISTERED_KEYWORD') NOT NULL,
  `unanswer_count` int(11) NOT NULL DEFAULT 0 COMMENT '해당 사유별 집계 건수',
  `analysis_id` bigint(20) NOT NULL,
  PRIMARY KEY (`stat_id`),
  KEY `fk_unanswer_stat_analysis_id` (`analysis_id`),
  CONSTRAINT `fk_unanswer_stat_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `gold_upload_file` (
  `upload_id` varchar(36) NOT NULL COMMENT '파일 업로드 식별 UUID',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `error_message` text DEFAULT NULL COMMENT '적재 실패 시 사유',
  `file_hash` varchar(64) DEFAULT NULL COMMENT '파일 SHA-256 해시값 (중복 업로드 방지)',
  `file_name` varchar(255) NOT NULL COMMENT '서버 저장용 고유 파일명 (uuid_원본파일명)',
  `file_path` varchar(500) NOT NULL COMMENT '파일 저장 경로',
  `file_size` bigint(20) DEFAULT NULL COMMENT '파일 크기 (byte)',
  `original_file_name` varchar(255) NOT NULL COMMENT '사용자가 올린 실제 파일명',
  `row_count` int(11) DEFAULT NULL COMMENT '엑셀 행 수 (데이터 개수)',
  `status` varchar(20) NOT NULL COMMENT 'UPLOADED, INGESTED, FAIL',
  `uploaded_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`upload_id`),
  UNIQUE KEY `uq_upload_file_name` (`file_name`),
  KEY `fk_upload_user_id` (`uploaded_by`),
  CONSTRAINT `fk_upload_user_id` FOREIGN KEY (`uploaded_by`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='업로드 파일 메타 정보';

CREATE TABLE IF NOT EXISTS `gold_usage_stat` (
  `stat_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer_cnt` int(11) NOT NULL DEFAULT 0 COMMENT '정상 답변 건수',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `low_quality_count` int(11) NOT NULL DEFAULT 0 COMMENT '저품질 답변 건수',
  `total_cnt` int(11) NOT NULL DEFAULT 0 COMMENT '총 인입 건수',
  `unanswer_cnt` int(11) NOT NULL DEFAULT 0 COMMENT '미답변 건수',
  `stat_date` date NOT NULL COMMENT '통계 기준 날짜 (PK)',
  PRIMARY KEY (`stat_id`),
  UNIQUE KEY `UK7usp4t02i9970p7714kha9fj` (`stat_date`),
  CONSTRAINT `fk_usage_stat_date` FOREIGN KEY (`stat_date`) REFERENCES `gold_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='일별 챗봇 사용량 통계';

CREATE TABLE IF NOT EXISTS `gold_user` (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK3i58p26ig618kj13anutpkq9d` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



SET FOREIGN_KEY_CHECKS = 1;
