# BE 배포 절차 (NCP)

서버 1대에 BE와 MariaDB를 도커로 함께 띄우고, nginx가 HTTPS(443)를 받아 BE로 넘긴다.
2026-10-18 실제 배포 과정을 정리한 것이다.

```
FE(GitHub Pages) ──HTTPS──▶ nginx(443) ──▶ BE 컨테이너(127.0.0.1:8080) ──▶ MariaDB 컨테이너(127.0.0.1:3306)
AI 서버(Vast) ◀── SSH 터널(서버가 연다) ──▶ BE·DB
```

## 1. 서버

| 항목 | 값 |
|---|---|
| 서버 | NCP VPC, Standard s2-g3a (vCPU 2, 메모리 8GB), 시간 요금제 |
| OS | Ubuntu 24.04 (콘솔에 22.04 이미지가 없음) |
| 디스크 | CB2 50GB (기본 디스크 하나) |
| 네트워크 | Public 서브넷, 공인 IP 1개 |

ACG 규칙

| 방향 | 프로토콜 | 대상 | 포트 | 용도 |
|---|---|---|---|---|
| Inbound | TCP | 관리자 PC IP/32 | 22 | SSH |
| Inbound | TCP | 0.0.0.0/0 | 80 | 인증서 발급 확인, HTTPS로 이동 |
| Inbound | TCP | 0.0.0.0/0 | 443 | HTTPS |
| Outbound | TCP·UDP | 0.0.0.0/0 | 1-65535 | 패키지 설치, 메일, 인증서, 터널 |
| Outbound | ICMP | 0.0.0.0/0 | - | |

직접 만든 ACG에는 기본 규칙이 없으므로 Outbound도 직접 넣는다. 3306, 8080은 열지 않는다.

## 2. 기본 설치

```bash
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=a
apt-get update && apt-get -y upgrade        # 끝나고 /var/run/reboot-required 가 있으면 reboot

# Docker 공식 저장소
apt-get install -y ca-certificates curl
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" \
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

## 3. 코드와 환경변수

```bash
git clone --branch develop https://github.com/seo-yn/BreathAI.git /opt/breathai
cd /opt/breathai/ttobagi-server
```

`.env`는 `.env.example`을 기준으로 만든다. Windows에서 만든 파일을 복사했다면 BOM과 CRLF를 먼저 지운다.

```bash
sed -i '1s/^\xEF\xBB\xBF//; s/\r$//' .env
chmod 600 .env
```

서버에서 따로 정하는 값

| 키 | 값 |
|---|---|
| `TTOBAGI_DB_URL` | `jdbc:mariadb://mariadb:3306/ttobagi_db` (compose가 덮어쓰지만 맞춰 둔다) |
| `TTOBAGI_DB_PASSWORD`, `TTOBAGI_DB_ROOT_PASSWORD`, `JWT_SECRET` | `openssl rand -hex 24` 등으로 서버에서 새로 만든다 |
| `TTOBAGI_AI_SERVER` | `http://host.docker.internal:18000` (SSH 터널 주소) |
| `TTOBAGI_AI_API_TOKEN`, `TTOBAGI_CALLBACK_TOKEN` | AI 서버와 같은 값 |
| `CORS_ALLOWED_ORIGINS` | FE 주소 (예: `https://아이디.github.io`) |
| `TTOBAGI_STORAGE_ENABLED` | `true`면 업로드 원본을 Object Storage에 보관 (아래 7번) |
| `TTOBAGI_STORAGE_ACCESS_KEY`, `TTOBAGI_STORAGE_SECRET_KEY` | Object Storage 권한만 있는 Sub Account의 API 인증키 |
| `TTOBAGI_STORAGE_UPLOAD_BUCKET`, `TTOBAGI_STORAGE_BACKUP_BUCKET` | `breathai-uploads`, `breathai-backup` |

키는 화면과 명령 기록에 남지 않게 입력받아 넣는다.

```bash
read -rp "Access Key ID: " AK; read -rsp "Secret Key: " SK; echo
# 이어서 .env에 TTOBAGI_STORAGE_ACCESS_KEY="$AK", TTOBAGI_STORAGE_SECRET_KEY="$SK"를 기록하고 unset AK SK
```

## 4. 실행

```bash
cd /opt/breathai/ttobagi-server
docker compose -f docker-compose.prod.yml up -d --wait mariadb     # DB만 먼저 (데이터 이관이 있으면 여기서 넣는다)
docker compose -f docker-compose.prod.yml up -d --build --wait app
curl -s http://127.0.0.1:8080/actuator/health                       # {"status":"UP"}
```

- 처음 빌드는 1~5분 걸린다.
- 이미 데이터가 있는 DB를 옮길 때는 BE를 띄우기 전에 덤프를 넣는다. 그다음 BE가 뜨면서 Flyway가 남은 마이그레이션을 적용한다.

## 5. HTTPS (IP 인증서)

도메인 없이 공인 IP로 Let's Encrypt 인증서를 받는다. IP 인증서는 유효기간이 6일이라 자동 갱신이 반드시 동작해야 한다.
Certbot 5.4 이상이 필요해 Ubuntu 기본 패키지(2.x) 대신 snap으로 설치한다.

```bash
apt-get install -y nginx
# NCP 서버는 IPv6가 꺼져 있어 기본 설정의 [::]:80 줄 때문에 nginx가 뜨지 않는다
sed -i '/listen \[::\]:80/d' /etc/nginx/sites-available/default
nginx -t && dpkg --configure -a

apt-get install -y snapd            # NCP 이미지에는 snap이 없다
snap wait system seed.loaded
snap install --classic certbot && ln -sf /snap/bin/certbot /usr/bin/certbot

IP=서버_공인_IP
certbot certonly --webroot -w /var/www/html --preferred-profile shortlived --ip-address $IP \
  --register-unsafely-without-email --agree-tos --non-interactive --dry-run      # 연습
certbot certonly --webroot -w /var/www/html --preferred-profile shortlived --ip-address $IP \
  --register-unsafely-without-email --agree-tos --non-interactive                # 실제 발급

cp /opt/breathai/ttobagi-server/deploy/nginx/breathai.conf /etc/nginx/sites-available/breathai
sed -i "s/211.233.202.231/$IP/g" /etc/nginx/sites-available/breathai             # 다른 IP라면
rm -f /etc/nginx/sites-enabled/default
ln -sf /etc/nginx/sites-available/breathai /etc/nginx/sites-enabled/breathai
nginx -t && systemctl reload nginx

# 갱신되면 nginx가 새 인증서를 읽게 한다
mkdir -p /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\nsystemctl reload nginx\n' > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
certbot renew --dry-run
systemctl list-timers | grep certbot      # snap.certbot.renew.timer
```

## 6. DB 백업

매일 04:00에 압축 덤프를 `/var/backups/breathai`에 남기고 14일 지난 것은 지운다.
`.env`에 Object Storage 키가 있으면 `breathai-backup` 버킷의 `db/`에도 올린다(버킷 수명 주기로 30일 뒤 자동 삭제).

```bash
install -m 700 /opt/breathai/ttobagi-server/deploy/backup/breathai-db-backup.sh /usr/local/bin/breathai-db-backup.sh
echo '0 4 * * * root /usr/local/bin/breathai-db-backup.sh >> /var/log/breathai-db-backup.log 2>&1' > /etc/cron.d/breathai-db-backup
/usr/local/bin/breathai-db-backup.sh && ls -l /var/backups/breathai/
```

업로드에는 AWS CLI 공식 도커 이미지(`amazon/aws-cli:2.22.35`)를 쓴다. 서버에 따로 설치하지 않는다.

## 7. Object Storage

| 버킷 | 내용 | 수명 주기 |
|---|---|---|
| `breathai-uploads` | 분석 업로드 원본 `uploads/연/월/업로드ID.확장자` | 90일 뒤 삭제 |
| `breathai-backup` | DB 백업 `db/ttobagi_db_날짜_시각.sql.gz` | 30일 뒤 삭제 |

- 둘 다 비공개, 잠금·암호화·버전 관리는 쓰지 않는다.
- 업로드 원본은 xlsx·csv 그대로(변환 전) 보관하고, 위치를 `gold_upload_file.file_path`에 `s3://버킷/키`로 기록한다.
- 보관에 실패해도 분석은 그대로 진행한다(서버 로그에 경고).
- 수명 주기로 지워진 뒤에도 `file_path`는 남는다. 위치 기록일 뿐 파일이 남아 있다는 뜻은 아니다.
- 인증키는 Sub Account(`NCP_OBJECT_STORAGE_MANAGER` 정책만)로 만든다. Sub Account의 접근 Source 제한은 Object Storage에 적용되지 않는다.

## 8. 업데이트

```bash
cd /opt/breathai && git pull
cd ttobagi-server && docker compose -f docker-compose.prod.yml up -d --build --wait app
```

## 9. 겪은 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| `./gradlew: Permission denied` | 저장소에 실행 권한 없이 기록됨 (Windows 빌드에서는 드러나지 않음) | #51에서 100755로 기록 |
| nginx 설치 중 시작 실패 `[::]:80 failed` | 서버에 IPv6가 없음 | 기본 설정에서 `[::]:80` 줄 삭제 |
| `snap: command not found` | NCP Ubuntu 이미지에 snapd 없음 | `apt-get install -y snapd` |
| `.env` 값 끝에 보이지 않는 문자 | Windows에서 복사한 BOM·CRLF | `sed`로 제거 |
| apt 업그레이드 중 설정 파일 질문 | NCP가 바꿔 둔 설정 파일 | `N`(현재 유지), 설치 시 `--force-confold` |
