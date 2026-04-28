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
  - `USER_PII_SYNC_RETRY_ENABLED`
  - `USER_PII_SYNC_RETRY_BATCH_SIZE`
  - `USER_PII_SYNC_RETRY_FIXED_DELAY_MS`
  - `USER_PII_SYNC_RETRY_INITIAL_DELAY_MS`
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

운영 `.env`를 채운 뒤 앱 재기동 전에 아래 preflight를 먼저 실행한다.

```bash
ENV_FILE=.env deploy/smoke/preflight-runtime-cutover-env.sh
```

실제 cutover 직전에는 아래처럼 redacted summary까지 같이 확인하는 편이 안전하다.

```bash
ENV_FILE=.env PRINT_SUMMARY=true deploy/smoke/preflight-runtime-cutover-env.sh
```

운영 전환 순서를 한 페이지로 빠르게 따라가려면 [runtime-cutover-checklist.md](./runtime-cutover-checklist.md)를 같이 본다.

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
- 프로필 조회와 비밀번호 재설정 수신 주소 조회는 `app.datasource.pii-rw` 보조 datasource를 사용한다. 별도 DB 호스트를 아직 나누지 않았다면 `APP_PII_DB_URL`은 `DB_URL`과 같은 host를 써도 되지만, database/schema 이름은 `youth_welfare_pii` 로 분리해야 한다.
- 알림 발송 대상 이메일 조회는 `app.datasource.notification-pii-ro` 보조 datasource를 사용한다. 별도 DB 호스트를 아직 나누지 않았다면 `NOTIFICATION_PII_DB_URL`도 같은 host를 써도 되지만, database/schema 이름은 `youth_welfare_pii` 여야 한다.
- Docker Compose 앱 컨테이너는 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 기본값도 `db` 서비스명의 `youth_welfare_pii` schema로 강제하고, secondary datasource username 기본값은 `app_pii_rw` / `notification_pii_ro`, password 기본값은 `DB_PASSWORD` 로 고정했다. 운영에서 비밀번호를 분리할 경우에는 `DB_APP_PII_PASSWORD`, `DB_NOTIFICATION_PII_RO_PASSWORD` 를 명시해야 한다.
- 최신 코드 기준 앱은 startup 시 secondary datasource URL의 schema를 검사한다. `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 `youth_welfare_pii` 가 아니면 요청 도중이 아니라 부팅 시점에 바로 실패한다.
- `deploy/smoke/preflight-runtime-cutover-env.sh` 는 startup 이전에 같은 규칙을 shell 레벨에서 먼저 확인하고, 가능하면 `docker compose config` 렌더링까지 같이 검증한다.
- 요청 경로 `user_pii` sync 실패 row는 `user_pii_sync_queue` 에 남고, 앱은 `USER_PII_SYNC_RETRY_*` 환경변수 기준 fixed-delay batch retry 를 수행한다. 운영 기본값은 `enabled=true`, `batch-size=100`, `initial-delay-ms=60000`, `fixed-delay-ms=300000` 이다.

## 3. 기존 DB 업그레이드

기존 운영 DB는 `schema.sql`만으로 최신화되지 않는다.
최근 누적 변경분은 아래 SQL을 먼저 적용한 뒤 앱을 재배포해야 한다.

- 상세 절차와 최신 SQL 목록: [db-migration.md](db-migration.md)
- 기존 운영 DB 계정 생성과 앱 datasource 전환 절차: [db-account-cutover-runbook.md](./db-account-cutover-runbook.md)
- cutover fast path: [runtime-cutover-checklist.md](./runtime-cutover-checklist.md)

권장 순서:

1. `db`, `redis`만 기동
2. 운영 DB 계정/권한 생성 또는 확인
3. 마이그레이션 SQL 적용
4. 앱 이미지 재빌드 후 `app` 기동

기존 볼륨/기존 운영 DB는 `docker-entrypoint-initdb.d` 스크립트가 다시 실행되지 않으므로 계정 생성은 수동으로 맞춰야 한다.
현재 권한 기준은 아래를 사용한다.

- `app_core_rw`: `youth_welfare.*` DML
- `app_pii_rw`: `youth_welfare_pii.user_pii` DML
- `notification_pii_ro`: `youth_welfare_pii.user_pii(user_key, email_enc)` column-level SELECT
- `migration_admin`: `youth_welfare.*`, `youth_welfare_pii.*` 전체 권한

최신 코드 기준으로 기본 datasource는 더 이상 `user_pii` 를 직접 읽거나 쓰지 않는다. 신규 init 스크립트와 계정 템플릿도 이 기준으로 정리했으므로 `app_core_rw` 는 `youth_welfare_pii.user_pii` 권한을 더 이상 가지지 않는다. 기존 운영 DB는 runbook 기준으로 revoke SQL을 실제 적용해야 한다.

현재 코드 기준 datasource 사용 범위:

- 기본 JPA datasource (`DB_URL`, `DB_USERNAME`): 대부분의 core/runtime 경로. 최신 코드 기준 `user_pii` 직접 read/write 없음
- 보조 datasource (`APP_PII_DB_URL`, `DB_APP_PII_USERNAME`): 프로필 조회, 비밀번호 재설정 수신 주소 조회, admin `user_pii` backfill 상태 조회/수정, queue replay/retry 최종 upsert 경로
- 보조 datasource (`NOTIFICATION_PII_DB_URL`, `DB_NOTIFICATION_PII_RO_USERNAME`): 알림 스케줄러와 재시도 경로의 이메일 암호문 조회
- 스케줄러 (`USER_PII_SYNC_RETRY_*`): `FAILED` 우선, 이후 `PENDING` queue batch를 `UserPiiSyncReplayService` 재사용으로 재처리
- 운영 모니터링 API (`GET /api/admin/users/pii-sync-status`): queue 적체 count, oldest pending/failed, recent sync 시각, failed sample 조회

## 4. 운영 확인

체크 포인트:

- `http://서버IP:8082/actuator/health`
- 로그인 / refresh / 로그아웃
- 운영 admin 계정 생성 및 `/api/admin/**` 권한 확인
- 정책 목록 필터
- 추천 북마크
- 알림 수신 거부 링크
- `user_pii_sync_queue` 자동 retry 로그와 admin replay API smoke 확인
- `GET /api/admin/users/pii-sync-status?failedSampleLimit=5` 응답 확인
- 필요하면 `deploy/smoke/user-pii-sync-cutover-smoke.sh` 로 회원가입 -> 로그인 -> 프로필 수정 -> queue `SYNCED` 까지 one-shot smoke 실행
- one-shot smoke의 cross-schema DB 확인 쿼리는 `migration_admin` 또는 `DB_QUERY_*` 로 지정한 점검 계정을 사용

운영 admin 계정의 최초 생성/회수 절차는 [admin-account-runbook.md](./admin-account-runbook.md)를 따릅니다.

`user_pii_sync_queue` 운영 기준:

- `failedCount > 0` 이면 즉시 `failedSamples` 와 `lastError` 를 확인하고 `POST /api/admin/users/pii-sync-replay` 재처리를 준비
- `oldestPendingEnqueuedAt` 가 현재 시각 기준 5분 이상 오래됐으면 after-commit listener 또는 `app_pii_rw` 연결 이상 여부 점검
- `oldestFailedAttemptAt` 가 10분 이상 오래됐으면 자동 retry만으로 복구되지 않는 상태로 보고 DB 계정/권한 또는 PII schema 연결 점검
- `failedSamples[*].attemptCount >= 5` row 가 보이면 같은 payload가 반복 실패하는 상태로 간주하고 운영 개입 대상에 올림

one-shot smoke 예시:

```bash
ENV_FILE=.env APP_BASE_URL=http://127.0.0.1:8082 \
  deploy/smoke/user-pii-sync-cutover-smoke.sh
```

- `.env` 의 JDBC URL에는 `&` 가 들어가므로 shell `source .env` 대신 `ENV_FILE=.env ...` 형태를 기준으로 사용한다.
로컬 fresh init + 앱 기동 + one-shot smoke를 한 번에 실행하려면 아래 래퍼를 사용한다.

```bash
SMOKE_RESET_DB=true \
  deploy/smoke/run-local-pii-sync-cutover-smoke.sh
```

- 이 smoke는 회원가입/프로필 수정에서 PII 암호화를 태우므로 앱 컨테이너/서버의 `AES_SECRET_KEY` 가 비어 있으면 `C002` 500으로 실패한다.
- `app_core_rw` 권한 회수 후에는 `DB_MIGRATION_*` 또는 `DB_QUERY_*` 가 비어 있으면 smoke가 `user_pii` 확인 단계에서 권한 부족으로 중단될 수 있다.
- secondary datasource URL이 `youth_welfare` 를 가리키면 `app_pii_rw` / `notification_pii_ro` 가 DB 연결 단계에서 바로 거부된다. 운영 `.env` 와 secret store에서도 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 `youth_welfare_pii` 를 가리키는지 확인한다.

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
- scheduler 동작이 테스트나 운영 초기 smoke를 방해하면 `USER_PII_SYNC_RETRY_INITIAL_DELAY_MS` 를 일시적으로 크게 주고 수동 replay로 먼저 검증할 수 있음
- `GET /api/admin/users/pii-sync-status` 의 `failedSampleLimit` 는 1~20 범위로 제한되어 있으며, 운영 조회도 기본값 `5` 를 유지하는 편이 안전함
- Gmail 앱 비밀번호 미설정 시 알림 발송은 실패함
- Nginx 설정의 도메인/인증서 경로는 실제 운영 도메인에 맞게 수정해야 함
