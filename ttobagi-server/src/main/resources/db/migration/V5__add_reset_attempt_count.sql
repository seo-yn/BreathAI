-- 비밀번호 재설정 인증 코드를 6자리로 줄이면서 입력 실패 횟수를 기록한다
-- 한도에 도달한 코드는 더 쓸 수 없고 다시 발급받아야 한다

ALTER TABLE `gold_password_reset_token`
  ADD COLUMN `attempt_count` int NOT NULL DEFAULT 0 COMMENT '인증 코드 입력 실패 횟수';
