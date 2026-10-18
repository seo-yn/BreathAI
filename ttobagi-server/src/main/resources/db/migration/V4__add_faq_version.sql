-- FAQ 버전 관리
-- 버전은 엑셀 업로드·다운로드 시점에 만들어지며, 직전 버전 이후의 변경 이력을 묶는다

CREATE TABLE `gold_faq_version` (
  `version_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `version_name` varchar(100) NOT NULL COMMENT '버전 이름 (v번호_날짜)',
  `version_type` varchar(20) NOT NULL COMMENT 'UPLOAD, DOWNLOAD',
  `total_faq_count` int(11) NOT NULL COMMENT '버전 생성 시점의 활성 FAQ 수',
  `created_count` int(11) NOT NULL DEFAULT 0 COMMENT '신규 등록 건수',
  `expanded_count` int(11) NOT NULL DEFAULT 0 COMMENT '확장 건수',
  `manual_count` int(11) NOT NULL DEFAULT 0 COMMENT '직접 수정 건수',
  `deleted_count` int(11) NOT NULL DEFAULT 0 COMMENT '삭제 건수',
  `previous_version_id` bigint(20) DEFAULT NULL COMMENT '직전 버전 ID',
  `created_by` bigint(20) DEFAULT NULL COMMENT '버전을 만든 사용자 ID (탈퇴 시 NULL)',
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`version_id`),
  KEY `fk_faq_version_previous_id` (`previous_version_id`),
  KEY `fk_faq_version_created_by` (`created_by`),
  CONSTRAINT `fk_faq_version_previous_id` FOREIGN KEY (`previous_version_id`) REFERENCES `gold_faq_version` (`version_id`),
  CONSTRAINT `fk_faq_version_created_by` FOREIGN KEY (`created_by`) REFERENCES `gold_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FAQ 버전';

-- 버전 생성 시점의 FAQ 전체 사본
-- 지난 버전의 목록 조회와 다운로드에 사용한다
CREATE TABLE `gold_faq_version_item` (
  `item_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `version_id` bigint(20) NOT NULL,
  `faq_id` bigint(20) NOT NULL COMMENT '원본 FAQ ID',
  `q_type` int(11) DEFAULT NULL COMMENT '카테고리 코드',
  `category` varchar(100) DEFAULT NULL COMMENT '일반상담 카테고리',
  `question` text NOT NULL COMMENT '질문',
  `answer` longtext NOT NULL COMMENT '답변',
  `keywords` json DEFAULT NULL COMMENT '키워드 배열',
  `faq_created_at` datetime(6) NOT NULL COMMENT '원본 FAQ 등록 시각',
  PRIMARY KEY (`item_id`),
  UNIQUE KEY `uq_version_item_faq` (`version_id`, `faq_id`),
  KEY `idx_version_item_q_type` (`version_id`, `q_type`),
  CONSTRAINT `fk_version_item_version_id` FOREIGN KEY (`version_id`) REFERENCES `gold_faq_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='버전별 FAQ 사본';

-- 변경 이력이 어느 버전에 묶였는지 기록한다. 아직 버전으로 묶이지 않은 이력은 NULL
ALTER TABLE `gold_faq_edit_history`
  ADD COLUMN `version_id` bigint(20) DEFAULT NULL COMMENT '이 변경이 포함된 버전 ID (미확정 시 NULL)',
  ADD KEY `fk_edit_history_version_id` (`version_id`),
  ADD CONSTRAINT `fk_edit_history_version_id` FOREIGN KEY (`version_id`) REFERENCES `gold_faq_version` (`version_id`);
