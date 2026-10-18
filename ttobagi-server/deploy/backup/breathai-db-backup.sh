#!/bin/sh
# 매일 DB 덤프를 압축해 보관하고 14일 지난 것은 지운다
# /usr/local/bin/breathai-db-backup.sh 로 복사해 cron에 등록한다 (README 참고)
set -e
# 백업에는 실제 데이터가 들어 있으므로 root만 읽을 수 있게 만든다
umask 077
BACKUP_DIR=/var/backups/breathai
mkdir -p "$BACKUP_DIR"
cd /opt/breathai/ttobagi-server
OUT="$BACKUP_DIR/ttobagi_db_$(date +%Y%m%d_%H%M).sql.gz"
docker compose -f docker-compose.prod.yml exec -T mariadb \
  sh -c 'exec mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines --triggers ttobagi_db' | gzip > "$OUT"
find "$BACKUP_DIR" -name 'ttobagi_db_*.sql.gz' -mtime +14 -delete
