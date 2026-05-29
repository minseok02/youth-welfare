# EC2 + RDS Deployment

이 문서는 `로컬 Docker Compose(app + db + redis)` 와 `운영 EC2 + RDS(app + redis + RDS)` 의 차이와 실제 전환 순서를 한 장으로 정리한다.

최근 보안/운영 hardening 현재 상태는 [core/security-hardening-current-state.md](./core/security-hardening-current-state.md) 를 먼저 본다.
이 문서는 그 상태를 실제 운영 절차에 어떻게 반영하는지에 집중한다.

현재 기준:

- 로컬 개발/검증은 계속 [docker-compose.yml](/home/minseok/youth-welfare/docker-compose.yml:1) 을 사용한다.
- 운영 EC2는 [docker-compose.prod.yml](/home/minseok/youth-welfare/docker-compose.prod.yml:1) 을 사용한다.
- 운영 DB는 Docker 컨테이너가 아니라 `Amazon RDS for PostgreSQL 16` 이다.

## 1. 구조 차이

로컬:

```text
docker compose
  -> app
  -> db
  -> redis
```

운영:

```text
EC2
  -> docker compose prod
      -> app
      -> redis
  -> nginx (host or separate service)
RDS PostgreSQL 16
```

핵심 차이:

- 로컬은 `db` 컨테이너가 있고 init script가 자동 적용된다.
- 운영은 `db` 컨테이너가 없고, RDS bootstrap을 EC2 shell에서 한 번 수행해야 한다.
- 앱 코드는 그대로 두고, `DB_URL` 등 같은 env key에 **다른 값**만 넣는다.

## 2. 파일 역할

- 로컬 compose: [docker-compose.yml](/home/minseok/youth-welfare/docker-compose.yml:1)
- 운영 compose: [docker-compose.prod.yml](/home/minseok/youth-welfare/docker-compose.prod.yml:1)
- 운영 env 예시: [env.production.example](/home/minseok/youth-welfare/env.production.example:1)
- RDS bootstrap script: [bootstrap-rds-runtime.sh](/home/minseok/youth-welfare/deploy/postgres/bootstrap-rds-runtime.sh:1)
- RDS privilege verify: [verify-rds-runtime-privileges.sh](/home/minseok/youth-welfare/deploy/postgres/verify-rds-runtime-privileges.sh:1)
- cutover verify wrapper: [run-prod-cutover-verification.sh](/home/minseok/youth-welfare/deploy/smoke/run-prod-cutover-verification.sh:1)
- nginx 예시:
  - bootstrap HTTP only: [youth-welfare.bootstrap.conf](/home/minseok/youth-welfare/deploy/nginx/youth-welfare.bootstrap.conf:1)
  - HTTPS: [youth-welfare.conf](/home/minseok/youth-welfare/deploy/nginx/youth-welfare.conf:1)
  - edge verify: [verify-edge-baseline.sh](/home/minseok/youth-welfare/deploy/nginx/verify-edge-baseline.sh:1)

## 3. 운영 `.env.production`

EC2에서는 `env.production.example` 을 `.env.production` 으로 복사해 실제 값을 채운다.

중요 원칙:

- 코드가 읽는 env key는 로컬과 운영이 같다.
- 달라지는 건 값뿐이다.
- 예:
  - 로컬 `DB_URL=jdbc:postgresql://db:5432/...`
  - 운영 `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/...`

## 4. RDS 생성 기준

권장 시작값:

- Engine: `PostgreSQL 16.14-R1`
- Template: `개발/테스트`
- Availability: `단일 AZ`
- Class: `db.t3.small` 또는 `db.t3.medium`
- Storage: `gp3`, `20GiB`
- Public access: `No`
- Initial database name: `youth_welfare`
- Automatic backups: `7 days`
- Deletion protection: `On`

보안 그룹:

- RDS SG inbound는 `PostgreSQL / 5432 / source = EC2 app SG` 한 줄만 남긴다.
- `0.0.0.0/0`, `My IP` 규칙은 남기지 않는다.

## 5. EC2 shell에서 하는 첫 bootstrap

RDS가 `Available` 이 되면 EC2 shell에서 아래 순서로 진행한다.

### 5-1. postgres client 설치

Ubuntu/Debian 예시:

```bash
sudo apt-get update
sudo apt-get install -y postgresql-client
```

### 5-2. 운영 env 파일 준비

```bash
cp env.production.example .env.production
chmod 600 .env.production
```

### 5-3. RDS bootstrap 실행

```bash
ENV_FILE=.env.production bash deploy/postgres/bootstrap-rds-runtime.sh
```

이 스크립트가 하는 일:

1. `schema.sql` 적용
2. runtime role 생성/비밀번호 맞춤
3. schema/table/sequence grant 적용
4. `deploy/postgres/patches/*.sql` 순차 적용

현재 기준으로 이 bootstrap에는 `chat_session_cleanup_rw`, `cluster_ai_cleanup_rw`,
`recommendation_retention_cleanup_rw`, `collect_execution_lock_cleanup_rw`,
`web_push_subscription_cleanup_rw` 전용 role까지 포함됩니다.

전제:

- `RDS_MASTER_USERNAME`
- `RDS_MASTER_PASSWORD`
- `DB_URL`
- runtime 계정용 username/password

가 `.env.production` 안에 채워져 있어야 한다.

## 6. 운영 env preflight

RDS bootstrap이 끝나면 app를 띄우기 전에 env가 올바른지 먼저 확인한다.

```bash
ENV_FILE=.env.production \
COMPOSE_FILE=docker-compose.prod.yml \
PRINT_SUMMARY=true \
bash deploy/smoke/preflight-runtime-cutover-env.sh
```

여기서 확인하는 것:

- `DB_URL` 이 `youth_welfare` 를 가리키는지
- `APP_PII_DB_URL` / `NOTIFICATION_PII_DB_URL` 이 `currentSchema=youth_welfare_pii` 인지
- split-account username 이 기대값과 맞는지

## 6-1. 운영 DB privilege verify

RDS bootstrap 직후에는 실제 login/grant 상태도 바로 다시 확인합니다.

```bash
ENV_FILE=.env.production \
bash deploy/postgres/verify-rds-runtime-privileges.sh
```

이 스크립트는:

- primary/admin-ro/chat-session-cleanup/cluster-ai-cleanup/recommendation-retention-cleanup/collect-execution-lock-cleanup/web-push-subscription-cleanup/pii-rw/notification-pii-ro/migration role login
- `app_core_rw` 의 `chat_sessions`, `cluster_ai_results`, `collect_execution_locks`, `web_push_subscriptions` `DELETE` revoke
- 각 cleanup role의 대응 `DELETE` 및 column `SELECT`
- `app_core_rw` 의 `youth_welfare_pii.user_pii` 직접 접근 차단

를 한 번에 검증합니다.

## 7. 운영 app/redis 기동

```bash
APP_ENV_FILE=.env.production \
docker compose --env-file .env.production -f docker-compose.prod.yml up -d redis app
```

현재 `docker-compose.prod.yml` 은:

- `app` 을 `127.0.0.1:8082`
- `redis` 를 `127.0.0.1:6379`

에 바인딩한다. nginx는 host에서 `127.0.0.1:8082` 로 reverse proxy 한다.

현재 운영 compose hardening 기준:

- `app` 이미지는 [backend/Dockerfile](/home/minseok/youth-welfare/backend/Dockerfile:1) 에서 `UID/GID 10001` non-root 사용자로 실행한다.
- `app` 은 `read_only: true`, `/tmp` tmpfs, `security_opt: no-new-privileges:true`, `pids_limit: 256`, `mem_limit: 1g` 를 사용한다.
- `redis` 는 현재 persistence를 기대하지 않는 운영 구조를 전제로 `read_only: true`, `/data`/`/tmp` tmpfs, `security_opt: no-new-privileges:true`, `pids_limit: 128`, `mem_limit: 256m` 으로 띄운다.
- app가 임시 파일을 써야 하는 경로는 `/tmp` 로 제한되고, Dockerfile entrypoint는 `-Djava.io.tmpdir=/tmp` 를 강제한다.

## 8. nginx 연결

현재 conf 기준:

- HTTP bootstrap only: [youth-welfare.bootstrap.conf](/home/minseok/youth-welfare/deploy/nginx/youth-welfare.bootstrap.conf:1)
- HTTPS: [youth-welfare.conf](/home/minseok/youth-welfare/deploy/nginx/youth-welfare.conf:1)

즉 app 컨테이너는 외부에 직접 노출하지 않고, nginx가 `127.0.0.1:8082` 로 프록시한다.

현재 운영 템플릿 원칙:

- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options` 는 edge nginx가 단일 책임으로 내려준다.
- `server_tokens off;` 로 edge nginx 버전 문자열을 응답에서 숨긴다.
- `/api/`, `/swagger-ui/`, `/v3/api-docs/`, `/actuator/` 프록시 경로에서는 upstream Spring이 내려준 같은 헤더를 `proxy_hide_header` 로 숨긴다.
- `/actuator/` 는 외부 인터넷에 공개하지 않고 `127.0.0.1` / `::1` 및 명시적으로 허용한 내부 모니터링 IP만 통과시킨다.

nginx reload 뒤에는 아래를 바로 다시 봅니다.

```bash
PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/nginx/verify-edge-baseline.sh
```

이 스크립트는:

- `/` 응답 status
- `Strict-Transport-Security`
- `X-Frame-Options`
- `X-Content-Type-Options`
- `Content-Security-Policy`
- `Server` 헤더의 nginx 버전 노출 여부
- 외부 `/actuator/health` status

를 같이 확인합니다.

## 9. 운영 smoke 최소 순서

app가 올라오면 최소한 이 순서로 확인한다.

```bash
curl -fsS http://127.0.0.1:8082/actuator/health

ENV_FILE=.env.production \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
bash deploy/smoke/run-local-admin-dashboard-smoke.sh

ENV_FILE=.env.production \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
bash deploy/smoke/run-local-auth-session-smoke.sh
```

관리자 비밀번호를 shell history에 남기고 싶지 않으면:

```bash
printf '%s\n' '<admin password>' >/tmp/youth-welfare-admin-smoke-password
chmod 600 /tmp/youth-welfare-admin-smoke-password

ENV_FILE=.env.production \
ADMIN_EMAIL='<admin email>' \
bash deploy/smoke/run-local-admin-forced-logout-smoke.sh
```

`run-local-admin-forced-logout-smoke.sh` 는 아래 우선순위로 admin 자격을 찾는다.

1. 현재 shell env의 `ADMIN_EMAIL`, `ADMIN_PASSWORD`
2. `/tmp/youth-welfare-admin-smoke-email`, `/tmp/youth-welfare-admin-smoke-password`
3. `ENV_FILE` 또는 기본 `.env` 안의 `ADMIN_EMAIL`, `ADMIN_PASSWORD`
4. `SECURITY_ADMIN_EMAILS` 의 첫 이메일

운영 직전에는 필요 시:

- `bash deploy/smoke/run-local-runtime-api-smoke.sh`
- `bash deploy/smoke/run-local-ops-baseline-suite.sh`
- `ENV_FILE=.env.production PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-prod-cutover-verification.sh`

까지 다시 확인한다.

## 10. 로컬과 운영을 섞지 않는 원칙

- 로컬:
  - `docker compose up -d`
  - `.env`
  - `db` 컨테이너 포함
- 운영:
  - `docker compose -f docker-compose.prod.yml`
  - `.env.production`
  - `db` 컨테이너 없음, RDS 사용

즉:

- Dockerfile은 하나
- compose와 env는 환경별로 분리
- 키 이름은 같고 값만 다르게 유지

## 11. 현재 추천 시작점

비용 민감 / 초기 운영 기준:

- EC2: 기존 `t3.medium`
- RDS: `db.t3.small` 또는 `db.t3.medium`
- 먼저 `db.t3.small` 로 시작하고, 지속 부하가 생기면 `medium` 으로 올리는 전략도 가능

## 12. 한 줄 요약

운영 전환의 본질은:

1. 로컬 `db` 컨테이너를 없애고 RDS endpoint를 쓰게 만들기
2. bootstrap SQL을 EC2 shell에서 한 번 실행하기
3. 운영용 compose/env로 app + redis만 띄우기

이다.
