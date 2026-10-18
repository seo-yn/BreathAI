-- FAQ 카테고리 및 유사 질문 컬럼 추가
-- ddl-auto=update 로 이미 컬럼이 생긴 DB를 고려해 IF NOT EXISTS 로 작성한다

ALTER TABLE `gold_faq`
  ADD COLUMN IF NOT EXISTS `category` varchar(100) DEFAULT NULL COMMENT '일반상담 카테고리';

ALTER TABLE `gold_faq_candidate`
  ADD COLUMN IF NOT EXISTS `q_type` int(11) DEFAULT NULL COMMENT 'counselling_info 카테고리 번호; 미매칭 시 null',
  ADD COLUMN IF NOT EXISTS `category` varchar(100) DEFAULT NULL COMMENT '일반상담 카테고리; 미매칭 시 null',
  ADD COLUMN IF NOT EXISTS `similar_questions` json DEFAULT NULL COMMENT '클러스터 내 실제 사용자 질문';
