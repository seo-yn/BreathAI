#!/bin/sh
# 매일 DB 덤프를 압축해 보관하고 14일 지난 것은 지운다
# .env에 Object Storage 키가 있으면 백업 버킷에도 올린다 (버킷 수명 주기로 30일 뒤 자동 삭제)
# /usr/local/bin/breathai-db-backup.sh 로 복사해 cron에 등록한다 (README 참고)
set -e
# 백업에는 실제 데이터가 들어 있으므로 root만 읽을 수 있게 만든다
umask 077
BACKUP_DIR=/var/backups/breathai
APP_DIR=/opt/breathai/ttobagi-server
mkdir -p "$BACKUP_DIR"
cd "$APP_DIR"
NAME="ttobagi_db_$(date +%Y%m%d_%H%M).sql.gz"
docker compose -f docker-compose.prod.yml exec -T mariadb \
  sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --triggers ttobagi_db' | gzip > "$BACKUP_DIR/$NAME"
find "$BACKUP_DIR" -name 'ttobagi_db_*.sql.gz' -mtime +14 -delete

getv() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
AWS_ACCESS_KEY_ID=$(getv TTOBAGI_STORAGE_ACCESS_KEY)
AWS_SECRET_ACCESS_KEY=$(getv TTOBAGI_STORAGE_SECRET_KEY)
BUCKET=$(getv TTOBAGI_STORAGE_BACKUP_BUCKET)
if [ -n "$AWS_ACCESS_KEY_ID" ] && [ -n "$AWS_SECRET_ACCESS_KEY" ] && [ -n "$BUCKET" ]; then
  # 키 값이 명령줄에 드러나지 않도록 환경변수 이름만 넘긴다
  export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
  # CLI는 체크섬 기본값이 바뀌기 전 버전(2.22)으로 고정한다 (S3 호환 서비스 호환성)
  docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -v "$BACKUP_DIR:/backup:ro" amazon/aws-cli:2.22.35 \
    --endpoint-url https://kr.object.ncloudstorage.com --region kr-standard \
    s3 cp "/backup/$NAME" "s3://$BUCKET/db/$NAME" --only-show-errors
fi
