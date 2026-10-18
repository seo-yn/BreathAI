-- FAQ 변경 이력에 변경 유형과 키워드 전후 값 추가
-- 기존 이력은 모두 운영자 직접 수정이므로 edit_type 기본값을 MANUAL 로 둔다

ALTER TABLE `gold_faq_edit_history`
  ADD COLUMN IF NOT EXISTS `edit_type` varchar(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'CREATE, EXPAND, MANUAL, DELETE',
  ADD COLUMN IF NOT EXISTS `before_keywords` json DEFAULT NULL COMMENT '수정 전 키워드',
  ADD COLUMN IF NOT EXISTS `after_keywords` json DEFAULT NULL COMMENT '수정 후 키워드';
