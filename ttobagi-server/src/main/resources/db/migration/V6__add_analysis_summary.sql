-- AI 콜백이 보내는 업로드 파일별 요약값을 그대로 보관한다
-- 업로드 파일(분석 입력) 범위의 값이며 챗봇 서비스 전체 통계가 아니다
-- 값을 보내지 않은 예전 분석은 비워 두고, 조회 시 기존 방식으로 계산한다

ALTER TABLE `gold_analysis_job`
  ADD COLUMN `total_log_count` int DEFAULT NULL COMMENT '업로드 입력 전체 행 수',
  ADD COLUMN `correct_count` int DEFAULT NULL COMMENT '정상 답변 건수',
  ADD COLUMN `low_quality_count` int DEFAULT NULL COMMENT '저품질 답변 건수',
  ADD COLUMN `unanswer_count` int DEFAULT NULL COMMENT '미답변 건수',
  ADD COLUMN `current_unanswer_rate` decimal(5,2) DEFAULT NULL COMMENT '개선 전 미답변율(%)',
  ADD COLUMN `predicted_accuracy_gain` decimal(5,2) DEFAULT NULL COMMENT '예상 정확도 향상치(%)',
  ADD COLUMN `resolved_count_by_ai` int DEFAULT NULL COMMENT 'AI로 해결 가능한 미답변 건수';

-- 업로드 파일별 날짜 통계. 서비스 전체 사용량(gold_usage_stat)과 섞지 않는다
CREATE TABLE `gold_analysis_daily_stat` (
  `daily_stat_id` bigint NOT NULL AUTO_INCREMENT,
  `analysis_id` bigint NOT NULL,
  `stat_date` date NOT NULL COMMENT '통계 날짜',
  `total_cnt` int DEFAULT NULL COMMENT '전체 건수 (예전 형식 콜백은 없음)',
  `answer_cnt` int DEFAULT NULL COMMENT '정상 답변 건수 (예전 형식 콜백은 없음)',
  `low_quality_count` int NOT NULL DEFAULT 0 COMMENT '저품질 답변 건수',
  `unanswer_cnt` int NOT NULL DEFAULT 0 COMMENT '미답변 건수',
  PRIMARY KEY (`daily_stat_id`),
  UNIQUE KEY `uq_analysis_daily_stat` (`analysis_id`, `stat_date`),
  CONSTRAINT `fk_daily_stat_analysis_id` FOREIGN KEY (`analysis_id`) REFERENCES `gold_analysis_job` (`analysis_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='업로드 파일(분석)별 날짜 통계';
