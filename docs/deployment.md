# EC2 + RDS Deployment

이 문서는 `로컬 Docker Compose(app + db + redis)` 와 `운영 EC2 + RDS + ElastiCache(app + RDS + Valkey)` 의 차이와 실제 전환 순서를 한 장으로 정리한다.

최근 보안/운영 hardening 현재 상태는 [core/security-hardening-current-state.md](./core/security-hardening-current-state.md) 를 먼저 본다.
이 문서는 그 상태를 실제 운영 절차에 어떻게 반영하는지에 집중한다.

현재 기준:

- 로컬 개발/검증은 계속 [docker-compose.yml](../docker-compose.yml) 을 사용한다.
- 운영 EC2는 ElastiCache 연결 기준으로 [docker-compose.prod.elasticache.yml](../docker-compose.prod.elasticache.yml) 을 사용한다.
- [docker-compose.prod.yml](../docker-compose.prod.yml) 은 local Redis rollback/단일 서버 호환 경로로 남긴다.
- 운영 DB는 Docker 컨테이너가 아니라 `Amazon RDS for PostgreSQL 16` 이다.
- EC2 web 노드를 2대로 늘리고 ALB/공유 Redis를 붙이는 후속 계획은 [alb-multi-ec2-rollout-plan.md](./core/alb-multi-ec2-rollout-plan.md) 를 기준으로 한다.

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
  -> nginx (host or separate service)
RDS PostgreSQL 16
ElastiCache Valkey
```

핵심 차이:

- 로컬은 `db` 컨테이너가 있고 init script가 자동 적용된다.
- 운영은 `db` 컨테이너가 없고, RDS bootstrap을 EC2 shell에서 한 번 수행해야 한다.
- 앱 코드는 그대로 두고, `DB_URL` 등 같은 env key에 **다른 값**만 넣는다.

## 2. 파일 역할

- 로컬 compose: [docker-compose.yml](../docker-compose.yml)
- 운영 compose: [docker-compose.prod.elasticache.yml](../docker-compose.prod.elasticache.yml)
- local Redis 호환 compose: [docker-compose.prod.yml](../docker-compose.prod.yml)
- 운영 env 예시: [env.production.example](../env.production.example)
- RDS bootstrap script: [bootstrap-rds-runtime.sh](../deploy/postgres/bootstrap-rds-runtime.sh)
- RDS privilege verify: [verify-rds-runtime-privileges.sh](../deploy/postgres/verify-rds-runtime-privileges.sh)
- cutover verify wrapper: [run-prod-cutover-verification.sh](../deploy/smoke/run-prod-cutover-verification.sh)
- nginx 예시:
  - bootstrap HTTP only: [youth-welfare.bootstrap.conf](../deploy/nginx/youth-welfare.bootstrap.conf)
  - HTTPS: [youth-welfare.conf](../deploy/nginx/youth-welfare.conf)
  - edge verify: [verify-edge-baseline.sh](../deploy/nginx/verify-edge-baseline.sh)

## 3. 운영 `.env.production`

EC2에서는 `env.production.example` 을 `.env.production` 으로 복사해 실제 값을 채운다.

중요 원칙:

- 코드가 읽는 env key는 로컬과 운영이 같다.
- 달라지는 건 값뿐이다.
- 예:
  - 로컬 `DB_URL=jdbc:postgresql://db:5432/...`
  - 운영 `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/...`
  - 로컬 `REDIS_HOST=redis`
  - 운영 `REDIS_HOST=<elasticache-primary-endpoint>`

로컬 PC는 보통 ElastiCache endpoint에 직접 접근하지 않는다. ElastiCache는 VPC 내부 리소스이므로 로컬 개발은 기존 `docker-compose.yml` 의 Redis 컨테이너를 사용한다. 운영 EC2에서만 ElastiCache primary endpoint를 `.env.production` / `.env.runtime.production` 에 넣는다.

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

현재 기준으로 이 bootstrap에는 `recommendation_review_gate_command_rw`,
`recommendation_persistence_command_rw`, `chat_session_cleanup_rw`, `cluster_ai_cleanup_rw`,
`recommendation_retention_cleanup_rw`, `collect_execution_lock_cleanup_rw`,
`web_push_subscription_cleanup_rw` 전용 role까지 포함됩니다.

전제:

- `RDS_MASTER_USERNAME`
- `RDS_MASTER_PASSWORD`
- `DB_URL`
- runtime 계정용 username/password 전체

가 `.env.production` 안에 채워져 있어야 한다.
특히 recommendation command role은 운영 profile/bootstrap/preflight/verify에서
`DB_URL`/`DB_PASSWORD`로 fallback하지 않으므로 아래 6개를 반드시 명시한다.

- `RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL`
- `DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME`
- `DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD`
- `RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL`
- `DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME`
- `DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD`

## 6. 운영 env preflight

RDS bootstrap이 끝나면 app를 띄우기 전에 env가 올바른지 먼저 확인한다.

```bash
ENV_FILE=.env.production \
COMPOSE_FILE=docker-compose.prod.elasticache.yml \
PRINT_SUMMARY=true \
bash deploy/smoke/preflight-runtime-cutover-env.sh
```

여기서 확인하는 것:

- `DB_URL` 이 `youth_welfare` 를 가리키는지
- `APP_PII_DB_URL` / `NOTIFICATION_PII_DB_URL` 이 `currentSchema=youth_welfare_pii` 인지
- split-account username 이 기대값과 맞는지
- recommendation review/persistence command datasource가 명시 env로 들어왔는지
- `AUTH_REFRESH_COOKIE_SECURE=true` 로 운영 refresh cookie가 Secure 속성을 유지하는지
- runtime DB role password가 서로 다른지. 운영 기본값은 password 재사용 시 실패하며, 로컬/예외 상황에서만 `ALLOW_SHARED_RUNTIME_DB_PASSWORDS=true`로 우회한다.

## 6-1. 운영 DB privilege verify

RDS bootstrap 직후에는 실제 login/grant 상태도 바로 다시 확인합니다.

```bash
ENV_FILE=.env.production \
bash deploy/postgres/verify-rds-runtime-privileges.sh
```

이 스크립트는:

- primary/admin-ro/chat-session-cleanup/cluster-ai-cleanup/recommendation-retention-cleanup/collect-execution-lock-cleanup/web-push-subscription-cleanup/pii-rw/notification-pii-ro/migration role login
- `admin_dashboard_ro` 의 대표 운영 대시보드 테이블 `SELECT` 및 write 권한 차단
- `app_core_rw` 의 `chat_sessions`, `cluster_ai_results`, `collect_execution_locks`, `web_push_subscriptions` `DELETE` revoke
- 각 cleanup role의 대응 `DELETE` 및 column `SELECT`
- `app_core_rw` 의 `youth_welfare_pii.user_pii` 직접 접근 차단
- `notification_pii_ro` 의 `user_key`, `email_enc` column read만 허용하고 `name_enc`, `birth_date_enc`, write 권한은 차단

를 한 번에 검증합니다.

## 7. 운영 app 기동

```bash
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d app
```

현재 `docker-compose.prod.elasticache.yml` 은:

- `app` 을 `127.0.0.1:8082`
- `app.env_file` 을 `${APP_RUNTIME_ENV_FILE:-.env.runtime.production}`

에 바인딩한다. nginx는 host에서 `127.0.0.1:8082` 로 reverse proxy 한다. Redis 호환 프로토콜은 ElastiCache Valkey primary endpoint를 `REDIS_HOST`, `REDIS_PORT=6379` 로 사용한다.

운영 서버의 `.env.production` 은 compose interpolation용 파일이며, `APP_RUNTIME_ENV_FILE` 로 실제 app runtime env 파일을 지정한다. 값이 없으면 기본값 `.env.runtime.production` 을 사용한다.

현재 운영 compose hardening 기준:

- `app` 이미지는 [backend/Dockerfile](../backend/Dockerfile) 에서 `UID/GID 10001` non-root 사용자로 실행한다.
- `app` 은 `read_only: true`, `/tmp` tmpfs, `security_opt: no-new-privileges:true`, `pids_limit: 256`, `mem_limit: 1g` 를 사용한다.
- `app` 은 DB/JWT/API secret을 compose에 직접 inline하지 않고 `${APP_RUNTIME_ENV_FILE:-.env.runtime.production}` 만 env file로 읽는다.
- app가 임시 파일을 써야 하는 경로는 `/tmp` 로 제한되고, Dockerfile entrypoint는 `-Djava.io.tmpdir=/tmp` 를 강제한다.

## 8. nginx 연결

현재 conf 기준:

- HTTP bootstrap only: [youth-welfare.bootstrap.conf](../deploy/nginx/youth-welfare.bootstrap.conf)
- HTTPS: [youth-welfare.conf](../deploy/nginx/youth-welfare.conf)

즉 app 컨테이너는 외부에 직접 노출하지 않고, nginx가 `127.0.0.1:8082` 로 프록시한다.

현재 운영 템플릿 원칙:

- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options` 는 edge nginx가 단일 책임으로 내려준다.
- `Content-Security-Policy` 의 `connect-src` 는 same-origin 기본값인 `self` 와 운영 public host `https://youthmoa.kr`, `https://www.youthmoa.kr` 를 함께 허용한다.
- prod Spring은 `server.forward-headers-strategy=framework` 로 nginx의 `X-Forwarded-Proto`, `X-Forwarded-Port`, `X-Forwarded-Host` 를 해석한다. HTTPS 템플릿은 `https/443`, bootstrap 템플릿은 `http/80` 을 명시한다.
- refresh cookie는 `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/auth` 기준이며, 운영 preflight는 `AUTH_REFRESH_COOKIE_SECURE=true` 를 요구한다.
- `server_tokens off;` 로 edge nginx 버전 문자열을 응답에서 숨긴다.
- HTTPS와 bootstrap 템플릿 모두 dotfile, env/log/sql/archive류 확장자, `wp-login.php`/`xmlrpc.php`/`cgi-bin` 스캐너 경로를 edge에서 404로 막는다.
- `youth_welfare_timed` access log format으로 `$request_time`, `$upstream_response_time`, `$request_id`, `$upstream_status` 를 남긴다. request line은 `$request_method $uri $server_protocol` 로 기록해 query string을 제외하고, `Referer`도 query/fragment 제거 값만 남긴다.
- 프록시 요청에는 `X-Request-Id: $request_id` 를 전달해 nginx access log와 Spring `[ApiRequest]` 로그를 같은 id로 맞춘다.
- Spring `[ApiRequest]` 로그는 query/body/header/cookie 원문을 남기지 않고, backend exception/provider error 계열 로그는 `LogSanitizer` 경계를 통과한다.
- `/api/`, `/swagger-ui`, `/v3/api-docs`, `/actuator` 프록시 경로에서는 upstream Spring이 내려준 같은 헤더를 `proxy_hide_header` 로 숨긴다.
- `/actuator`, `/swagger-ui`, `/v3/api-docs` 는 trailing slash 유무와 관계없이 외부 인터넷에 공개하지 않고 `127.0.0.1` / `::1` 및 명시적으로 허용한 내부 모니터링 IP만 통과시킨다.

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
- CSP `connect-src` 의 필수 source(`self`, `https://youthmoa.kr`, `https://www.youthmoa.kr`)
- `Server` 헤더의 nginx 버전 노출 여부
- 외부 `/actuator/health` status
- 외부 `/actuator`, `/swagger-ui`, `/v3/api-docs`, dotfile, env/log/sql/archive류 정적 경로, 대표 스캐너 경로의 403/404 차단 여부
- nginx reload 뒤 성능 로그 관찰은 `bash deploy/performance/run-local-nginx-log-observability-baseline.sh` 로 다시 확인한다.

를 같이 확인합니다.

## 9. 운영 smoke 최소 순서

운영 서버에서 ElastiCache/RDS 전환 뒤 다시 확인할 때는 아래 wrapper를 우선 사용한다.
이 wrapper는 `.env.production` 을 읽고, API는 EC2 내부 `http://127.0.0.1:8082`,
브라우저 origin은 `https://youthmoa.kr` 기준으로 고정한다. endpoint/ARN/account id는
문서나 명령줄에 반복하지 않는다.

```bash
bash deploy/smoke/run-prod-runtime-smoke-suite.sh
```

필요하면 단계별로 끌 수 있다.

```bash
RUN_CUTOVER_VERIFY=false bash deploy/smoke/run-prod-runtime-smoke-suite.sh
RUN_RUNTIME_API_SMOKE=false bash deploy/smoke/run-prod-runtime-smoke-suite.sh
RUN_AUTH_OBSERVATION=false bash deploy/smoke/run-prod-runtime-smoke-suite.sh
```

wrapper가 실패했거나 특정 단계만 분리해서 볼 때는 아래 순서로 쪼갠다.

1. runtime env 렌더링

```bash
SOURCE_ENV_FILE=.env.production \
TARGET_ENV_FILE=.env.runtime.production \
bash deploy/env/render-app-runtime-env.sh
```

`render-app-runtime-env.sh` 는 기본적으로 운영 app runtime 필수 key가 source env에 모두 있는지 먼저 확인합니다.
누락이 있으면 target runtime env를 부분 생성하지 않고 실패합니다. 임시 분석 목적의 비엄격 렌더만
`STRICT_REQUIRED_RUNTIME_ENV=false` 로 우회합니다.

2. env preflight

```bash
ENV_FILE=.env.production \
COMPOSE_FILE=docker-compose.prod.elasticache.yml \
PRINT_SUMMARY=true \
bash deploy/smoke/preflight-runtime-cutover-env.sh
```

3. RDS runtime privilege verify

```bash
ENV_FILE=.env.production \
PRINT_SUMMARY=true \
bash deploy/postgres/verify-rds-runtime-privileges.sh
```

4. app 기동과 health 확인

```bash
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d app
curl -fsS http://127.0.0.1:8082/actuator/health
```

5. nginx edge baseline

```bash
PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/nginx/verify-edge-baseline.sh
```

`run-prod-cutover-verification.sh` 는 1번의 strict render 가능성 확인과 2, 3, 5번을 한 번에 묶는 wrapper다.
실제 `.env.runtime.production` 을 덮어쓰지 않고 임시 0600 파일로 렌더 가능성만 확인한 뒤 삭제한다.
각 단계 stdout/stderr는 token/password/API key/cookie/JDBC URL/email/userKey 계열 값을 redaction한 뒤 artifact로 남기고, 보존 artifact는 cleanup에서도 다시 sanitizer를 통과한다.
app/redis 재기동과 runtime API smoke는 포함하지 않으므로 별도로 실행한다. 전체 운영 smoke는 `run-prod-runtime-smoke-suite.sh` 로 묶어 실행한다.

```bash
ENV_FILE=.env.production \
PUBLIC_BASE_URL='https://youthmoa.kr' \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-prod-cutover-verification.sh
```

6. runtime API smoke

```bash
ENV_FILE=.env.production \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-runtime-api-smoke.sh
```

7. 관리자/운영 smoke

```bash
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

운영 직전 추가 관측은 필요 시 아래를 더 태운다.

- `bash deploy/smoke/run-local-ops-baseline-suite.sh`

이 순서에서 실패하면 뒤 단계를 진행하지 않고, 실패 단계의 artifact/log를 먼저 남긴다.

## 10. 로컬과 운영을 섞지 않는 원칙

- 로컬:
  - `.env` 에 `ALLOW_LOCAL_DOCKER_DB=true` 를 명시한 뒤 `docker compose up -d`
  - `.env`
  - `db` 컨테이너 포함
- 운영:
  - `docker compose -f docker-compose.prod.yml`
  - `.env.production`
  - `db` 컨테이너 없음, RDS 사용

`docker-compose.yml` 의 `db` 서비스는 `ALLOW_LOCAL_DOCKER_DB` 가 없으면 compose 해석 단계에서 실패한다.
운영 EC2/RDS 서버에서는 이 값을 설정하지 않는다. 서버에서 실수로 `docker compose up -d` 를 실행하면 로컬 PostgreSQL 컨테이너가 생성되기 전에 막히는 것이 정상이다.

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
3. 운영용 compose/env로 app만 띄우고 Redis 호환 상태는 ElastiCache Valkey를 쓰게 하기

이다.
