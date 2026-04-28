# 배포 가이드

현재 백엔드는 Docker Compose 기준으로 `app + db + redis` 3개 컨테이너를 사용한다.
운영 서버는 EC2 `t4g.large`(ARM) 기준으로 정리한다.

## 1. 사전 준비

- `.env.example`을 복사해서 `.env` 생성
- 실제 운영 값 채우기:
  - `DB_PASSWORD`
  - `APP_PII_DB_URL`
  - `NOTIFICATION_PII_DB_URL`
  - `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`
  - `DB_APP_PII_USERNAME`, `DB_APP_PII_PASSWORD`
  - `DB_NOTIFICATION_PII_RO_USERNAME`, `DB_NOTIFICATION_PII_RO_PASSWORD`
  - `JWT_SECRET`
  - `AES_SECRET_KEY`
  - `SECURITY_ADMIN_EMAILS`
  - `GMAIL_USERNAME`, `GMAIL_PASSWORD`
  - `OPENAI_API_KEY`
  - `YOUTH_API_KEY`
  - `BOKJIRO_API_KEY`
  - `APP_BASE_URL`

```bash
cp .env.example .env
```

## 2. 최초 기동

```bash
docker compose -f docker-compose.yml up -d db redis
docker compose -f docker-compose.yml up -d --build app
```

- 앱 컨테이너는 내부에서 `db`, `redis` 서비스 이름으로 접속한다.
- 외부 노출 포트는 `8082 -> 8080`이다.
- DB 신규 초기화는 `backend/src/main/resources/db/schema.sql`로 처리된다.
- DB 신규 초기화 시 [`deploy/mysql/init/z90-create-runtime-db-users.sh`](../deploy/mysql/init/z90-create-runtime-db-users.sh)가 함께 실행되어 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정을 생성한다.
- 앱 컨테이너 기본 datasource 계정은 `.env`의 `DB_USERNAME` / `DB_PASSWORD`를 사용하며, 더 이상 `root`를 기본값으로 가정하지 않는다.
- 프로필 조회와 비밀번호 재설정 수신 주소 조회는 `app.datasource.pii-rw` 보조 datasource를 사용한다. 별도 DB 호스트를 아직 나누지 않았다면 `APP_PII_DB_URL`은 `DB_URL`과 같은 값을 사용해도 된다.
- 알림 발송 대상 이메일 조회는 `app.datasource.notification-pii-ro` 보조 datasource를 사용한다. 별도 DB 호스트를 아직 나누지 않았다면 `NOTIFICATION_PII_DB_URL`은 `DB_URL`과 같은 값을 사용해도 된다.

## 3. 기존 DB 업그레이드

기존 운영 DB는 `schema.sql`만으로 최신화되지 않는다.
최근 누적 변경분은 아래 SQL을 먼저 적용한 뒤 앱을 재배포해야 한다.

- 상세 절차와 최신 SQL 목록: [db-migration.md](db-migration.md)
- 기존 운영 DB 계정 생성과 앱 datasource 전환 절차: [db-account-cutover-runbook.md](./db-account-cutover-runbook.md)

권장 순서:

1. `db`, `redis`만 기동
2. 운영 DB 계정/권한 생성 또는 확인
3. 마이그레이션 SQL 적용
4. 앱 이미지 재빌드 후 `app` 기동

기존 볼륨/기존 운영 DB는 `docker-entrypoint-initdb.d` 스크립트가 다시 실행되지 않으므로 계정 생성은 수동으로 맞춰야 한다.
현재 권한 기준은 아래를 사용한다.

- `app_core_rw`: `youth_welfare.*` DML + 현재 단일 datasource 호환을 위한 `youth_welfare_pii.user_pii` DML
- `app_pii_rw`: `youth_welfare_pii.user_pii` DML
- `notification_pii_ro`: `youth_welfare_pii.user_pii(user_key, email_enc)` column-level SELECT
- `migration_admin`: `youth_welfare.*`, `youth_welfare_pii.*` 전체 권한

현재 코드 기준 datasource 사용 범위:

- 기본 JPA datasource (`DB_URL`, `DB_USERNAME`): 대부분의 core/runtime 경로
- 보조 datasource (`APP_PII_DB_URL`, `DB_APP_PII_USERNAME`): 프로필 조회, 비밀번호 재설정 수신 주소 조회, admin `user_pii` backfill write 경로
- 보조 datasource (`NOTIFICATION_PII_DB_URL`, `DB_NOTIFICATION_PII_RO_USERNAME`): 알림 스케줄러와 재시도 경로의 이메일 암호문 조회

## 4. 운영 확인

체크 포인트:

- `http://서버IP:8082/actuator/health`
- 로그인 / refresh / 로그아웃
- 운영 admin 계정 생성 및 `/api/admin/**` 권한 확인
- 정책 목록 필터
- 추천 북마크
- 알림 수신 거부 링크

운영 admin 계정의 최초 생성/회수 절차는 [admin-account-runbook.md](./admin-account-runbook.md)를 따릅니다.

컨테이너 확인:

```bash
docker ps
docker logs youth-welfare-app --tail 200
docker logs youth-welfare-db --tail 100
docker logs youth-welfare-redis --tail 100
```

## 5. HTTPS / 리버스 프록시

운영 기준 Nginx 설정 예시는 [`deploy/nginx/youth-welfare.conf`](../deploy/nginx/youth-welfare.conf)에 추가했다.

구성 기준:

- `80 -> 443` 리다이렉트
- `443 -> 127.0.0.1:8082` 프록시
- Let’s Encrypt 인증서 경로 사용
- `X-Forwarded-*` 헤더 전달
- HSTS 적용

적용 순서 예시:

```bash
sudo mkdir -p /var/www/certbot
sudo cp deploy/nginx/youth-welfare.conf /etc/nginx/sites-available/youth-welfare.conf
sudo ln -sf /etc/nginx/sites-available/youth-welfare.conf /etc/nginx/sites-enabled/youth-welfare.conf
sudo nginx -t
sudo systemctl reload nginx
```

인증서 발급 전에는 `ssl_certificate` 경로가 없어서 `nginx -t`가 실패할 수 있다.
이 경우 먼저 certbot으로 인증서를 발급하거나, 80포트 전용 설정으로 1차 기동 후 인증서를 발급한다.

최소 요구:

- HTTPS 종단은 Nginx 또는 ALB에서 처리
- `APP_BASE_URL`은 실제 HTTPS 도메인으로 설정
- Refresh Token `Secure` 쿠키가 동작하도록 HTTPS 환경 유지

## 6. 주의사항

- `.env`는 커밋 금지
- `SECURITY_ADMIN_EMAILS`를 바꾼 뒤에는 앱 재기동 필요
- DB 볼륨이 이미 존재하면 `schema.sql`은 다시 자동 적용되지 않음
- DB 볼륨이 이미 존재하면 `deploy/mysql/init/z90-create-runtime-db-users.sh`도 다시 자동 적용되지 않음
- 기존 운영 DB는 배포 전에 마이그레이션 SQL을 선적용해야 함
- Gmail 앱 비밀번호 미설정 시 알림 발송은 실패함
- Nginx 설정의 도메인/인증서 경로는 실제 운영 도메인에 맞게 수정해야 함
