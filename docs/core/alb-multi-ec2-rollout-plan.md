# ALB 기반 EC2 2대 전환 계획

작성 기준일: 2026-07-04

## 목적

현재 운영 기준은 `EC2 1대 + Docker Compose app + ElastiCache Valkey + RDS PostgreSQL` 이다.
이 문서는 RDS는 유지하고 EC2를 1대 추가해 ALB 뒤에 web 노드 2대를 두는 전환 계획을 고정한다.

목표는 아래 두 가지다.

1. EC2 web 노드 1대가 내려가도 사용자 사이트/API가 계속 응답한다.
2. 기존 보안 경계, RDS runtime role, nginx edge guard, smoke/ops 관측 기준을 깨지 않는다.

이 전환은 DB 고가용성 전환이 아니다. RDS 장애까지 포함한 전체 HA는 RDS Multi-AZ/restore rehearsal과 별도 범위로 본다.

## 현재 기준

현재 저장소 기준:

- 운영 DB는 이미 RDS PostgreSQL 16 이다.
- [docker-compose.prod.elasticache.yml](../../docker-compose.prod.elasticache.yml) 은 `app` 만 포함한다.
- [docker-compose.prod.yml](../../docker-compose.prod.yml) 은 local Redis rollback/단일 서버 호환 경로로 남긴다.
- app은 `127.0.0.1:8082` 로만 노출되고 nginx가 reverse proxy 한다.
- nginx는 frontend 정적 파일, `/api/` proxy, security headers, `/actuator`/Swagger/OpenAPI 차단을 담당한다.
- Redis 호환 상태 저장소는 현재 ElastiCache Valkey 기준이다.
- `@Scheduled` 작업은 Spring app process 안에서 실행된다.

따라서 EC2만 하나 더 만들고 같은 compose를 그대로 복제하면 안 된다.

위험한 이유:

- Redis가 노드별로 갈라지면 refresh token, logout cutoff, access revoke, password reset, email verification, rate limit, recommendation lock, notification lock이 서로 다르게 보인다.
- scheduler가 두 노드에서 동시에 실행되면 수집, 알림, 정리 작업이 중복 실행될 수 있다.
- 기존 HTTPS nginx conf는 EC2 직접 TLS 종료 기준이라, ALB TLS 종료 뒤 EC2:80 target 구조와 그대로 맞지 않는다.

## 최종 목표 구조

```text
Route53
  -> ALB
      listener 80  -> 443 redirect
      listener 443 -> target group HTTP:80
          -> EC2-1 nginx:80 -> Spring app 127.0.0.1:8082
          -> EC2-2 nginx:80 -> Spring app 127.0.0.1:8082

RDS PostgreSQL 16 existing
ElastiCache Redis shared
```

노드 역할:

| 노드 | 역할 | scheduler |
| --- | --- | --- |
| EC2-1 | web active target, 운영/cron 기준 노드 | enabled |
| EC2-2 | web active target | disabled |
| RDS | 기존 PostgreSQL 운영 DB | 해당 없음 |
| ElastiCache Redis | auth/rate-limit/lock 공유 상태 | 해당 없음 |

EC2-1이 내려가면 사이트/API는 EC2-2로 계속 처리한다. 이때 scheduler/ops cron은 멈출 수 있다. 이는 사용자 요청 가용성과 batch/ops 가용성을 분리한 1차 목표다.

## 리소스 사양

### EC2

선택:

- 기존 EC2-1: 유지
- 신규 EC2-2: `t3.medium`, x86_64, Ubuntu 24.04 LTS
- Root volume: `gp3 30GiB` 이상
- AZ: 가능하면 EC2-1과 다른 AZ

근거:

- 현재 프로젝트는 Spring Boot app, Docker build/cache, nginx, frontend artifact, smoke/ops script가 같은 서버에 있다.
- 기존 문서의 EC2 단일 구조 측정에서 root disk 여유가 작았던 이력이 있으므로 신규 노드는 20GiB보다 30GiB 이상으로 시작한다.
- `t3.small` 도 web-only로는 가능하지만 운영 smoke, Docker build, JVM 여유를 감안해 `t3.medium` 을 기본값으로 둔다.

권장 host 패키지:

- Docker Engine
- Docker Compose plugin
- nginx
- git
- curl
- jq
- postgresql-client
- AWS CLI
- Node/npm은 frontend를 서버에서 build할 경우만 설치

### ALB

선택:

- Type: Application Load Balancer
- Scheme: internet-facing
- IP address type: IPv4
- Subnets: public subnet 2개 이상, 서로 다른 AZ
- Listener 80: HTTPS redirect
- Listener 443: ACM certificate, target group forward

Target group:

- Type: instance
- Protocol: HTTP
- Port: 80
- Health check path: `/alb-health`
- Success code: `200`
- Interval: 15s
- Timeout: 5s
- Healthy threshold: 2
- Unhealthy threshold: 2
- Deregistration delay: 30s

### Redis

현재 운영 생성값:

- Service: ElastiCache
- Engine: Valkey 9.1.0
- Redis protocol compatible
- Cluster mode: disabled
- Shards: 1
- Nodes: 1
- Node type: `cache.t4g.micro`
- Multi-AZ: disabled
- Automatic failover: disabled
- Encryption in transit: disabled
- Encryption at rest: enabled
- Parameter group: `default.valkey9`
- Public access: 없음

앱 연결 규칙:

- `REDIS_HOST` 는 primary endpoint hostname을 사용한다.
- `REDIS_PORT=6379` 를 사용한다.
- reader endpoint는 앱 runtime에 쓰지 않는다. refresh token, logout cutoff, access revoke, password reset, email verification, rate limit, lock 경로가 모두 write를 포함한다.
- endpoint hostname, ARN, AWS account ID는 운영 secret/config 쪽에만 두고 이 계획 문서에는 남기지 않는다.
- 현재 단일 EC2 단계에서는 단일 노드 Valkey를 유지한다.
- EC2 web node를 2대로 늘리는 시점에는 replica, Multi-AZ, automatic failover를 같이 켠다.
- TLS/Auth token을 켜는 경우 Spring Redis `ssl`, `password` env 지원을 먼저 추가한다.

초기 트래픽이 작으면 `cache.t4g.micro` 로 충분하다. Redis CPU, memory, evictions, current connections가 올라가면 같은 engine family의 상위 node type으로 올린다.

Redis를 단일 노드로 만들면 EC2 web failover는 되지만 Redis가 단일 장애점으로 남는다. ALB 전환의 의도가 장애 대응이면 replication group을 기본값으로 둔다.

### RDS

선택:

- 기존 RDS PostgreSQL 16 유지
- schema, runtime role, PII schema, pgvector 구조 변경 없음

확인:

- backup retention 7일 이상
- deletion protection enabled
- encrypted storage enabled
- public access disabled
- app EC2 security group에서만 5432 허용

주의:

- web app이 2대로 늘면 DB connection upper bound도 늘어난다.
- 초기는 per-node pool을 보수적으로 잡는다.

권장 runtime env:

```env
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=0
DB_APP_PII_POOL_MAX_SIZE=2
DB_APP_PII_POOL_MIN_IDLE=0
DB_ADMIN_RO_POOL_MAX_SIZE=1
DB_ADMIN_RO_POOL_MIN_IDLE=0
DB_NOTIFICATION_PII_RO_POOL_MAX_SIZE=1
DB_NOTIFICATION_PII_RO_POOL_MIN_IDLE=0
```

command/cleanup datasource는 현재 기본값처럼 대부분 max `1` 을 유지한다.

## Security Group

### ALB SG

Inbound:

- TCP 80 from `0.0.0.0/0`
- TCP 443 from `0.0.0.0/0`

Outbound:

- TCP 80 to EC2 app SG

### EC2 app SG

Inbound:

- TCP 80 from ALB SG
- TCP 22 from operator fixed IP only

Optional inbound:

- TCP 443 from operator fixed IP only, rollback/debug 기간에만 유지

Outbound:

- TCP 5432 to RDS SG
- TCP 6379 to Redis SG
- HTTPS 443 to external APIs
- SMTP provider port as currently required

### RDS SG

Inbound:

- TCP 5432 from EC2 app SG only

### Redis SG

Inbound:

- TCP 6379 from EC2 app SG only

## 필요한 코드/설정 변경

### 1. ALB용 nginx template 추가

새 파일:

- `deploy/nginx/youth-welfare.alb.conf`

요구사항:

- `listen 80`
- HTTPS redirect 없음. redirect는 ALB listener 80에서 처리한다.
- frontend root는 기존과 동일하게 `/var/www/youth-welfare/frontend`
- `/api/` 는 `http://127.0.0.1:8082` 로 proxy
- Spring forwarded header는 외부 기준으로 전달한다.

```nginx
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Port 443;
proxy_set_header X-Forwarded-Host $host;
```

- 외부 `/actuator`, `/swagger-ui`, `/v3/api-docs` 차단 유지
- dotfile, env/log/sql/archive류, scanner path 차단 유지
- 기존 CSP/security header 유지
- HSTS header 유지. ALB->EC2는 HTTP지만 client->ALB는 HTTPS라 client는 HTTPS 응답으로 본다.
- ALB health check 전용 path 추가

```nginx
location = /alb-health {
    proxy_pass http://127.0.0.1:8082/actuator/health;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto https;
    proxy_set_header X-Forwarded-Port 443;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header Connection "";
}
```

기존 `deploy/nginx/youth-welfare.conf` 는 직접 EC2 rollback용으로 유지한다.

### 2. scheduler node 제어 추가

새 env:

```env
APP_SCHEDULER_ENABLED=true
```

운영 값:

- EC2-1: `APP_SCHEDULER_ENABLED=true`
- EC2-2: `APP_SCHEDULER_ENABLED=false`

적용 대상:

- `CollectBatchService`
- `StatusUpdateService`
- `NotificationScheduleService`
- `UserPiiSyncRetryScheduler`
- `UserPiiSyncQueueRetentionService`
- `RecommendationRetentionService`
- `OperationalLogRetentionService`
- `AdminPolicyRegionAuditService`

구현 방식:

- 각 scheduled method 시작부에서 global scheduler flag를 확인하고 false면 return 한다.
- 또는 scheduled component 단위로 `@ConditionalOnProperty` 를 적용한다.
- 수동 admin/API 실행 경로는 막지 않는다. 자동 scheduler만 제어한다.

주의:

- `UserPiiSyncRetryScheduler` 는 이미 `user.pii-sync.retry.enabled` 가 있지만, 노드 역할 제어는 별도 global flag로 통일한다.
- 수집은 DB lock, 알림은 Redis lock이 있지만, 모든 scheduler가 같은 수준으로 멀티 노드 안전한 것은 아니므로 1차에서는 한 노드만 실행한다.

### 3. shared Redis용 compose 정리

현재 ElastiCache 운영 경로는 `docker-compose.prod.elasticache.yml` 을 사용하며, local redis service와 `app.depends_on.redis` 를 갖지 않는다.

ALB 구조에서는 모든 web app이 ElastiCache Redis를 보므로 app은 local redis에 의존하면 안 된다.

변경 방향:

- `docker-compose.prod.yml` 은 local Redis rollback/단일 서버 호환을 위해 유지한다.
- `docker-compose.prod.elasticache.yml` 을 app-only 운영 compose로 사용한다.

예:

- `docker-compose.prod.elasticache.yml`

요구사항:

- app service만 실행 가능해야 한다.
- `app.depends_on.redis` 를 제거한다.
- `REDIS_HOST` 는 `.env.runtime.production` 의 ElastiCache endpoint를 사용한다.
- local `redis` service는 ALB web node에서 실행하지 않는다.

### 4. runtime env allowlist 확인

`deploy/env/render-app-runtime-env.sh` 는 이미 `REDIS_HOST`, `REDIS_PORT` 를 allowlist/required key로 가진다.

추가 필요:

- `APP_SCHEDULER_ENABLED`
- 필요하면 DB pool env keys

추가하지 않으면 `.env.production` 에 값을 넣어도 `.env.runtime.production` 으로 전달되지 않는다.

### 5. contract test 추가

추가할 계약:

- ALB nginx template이 기존 HTTPS/bootstrap과 같은 blocked edge path, CSP, safe access log format을 유지하는지
- ALB nginx template이 `/alb-health` 를 포함하는지
- runtime env renderer가 `APP_SCHEDULER_ENABLED` 를 allowlist에 포함하는지
- scheduler false일 때 각 scheduled service가 자동 작업을 skip하는지

## 전환 절차

### Phase 0. 현재 기준선 저장

기존 EC2에서 실행:

```bash
git status --short --branch
docker ps --filter name=youth-welfare-app --format '{{.Names}}\t{{.Status}}'
curl -fsS http://127.0.0.1:8082/actuator/health
```

운영 관측:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

edge 검증:

```bash
PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/nginx/verify-edge-baseline.sh
```

기준선이 green이 아니면 ALB 전환을 시작하지 않는다.

### Phase 1. 코드/설정 변경 반영

작업:

1. `deploy/nginx/youth-welfare.alb.conf` 추가
2. `APP_SCHEDULER_ENABLED` 코드/설정 반영
3. shared Redis compose override 추가
4. runtime env renderer allowlist 보강
5. 계약 테스트 추가

검증:

```bash
cd backend && ./gradlew test --no-daemon
cd frontend && npm run lint && npm run build
git diff --check
```

### Phase 2. ElastiCache Redis 생성 및 기존 EC2 전환

1. ElastiCache replication group 생성
2. Redis SG에 EC2 app SG inbound 허용
3. 기존 EC2 `.env.production` 수정

```env
REDIS_HOST=<elasticache-primary-endpoint>
REDIS_PORT=6379
APP_SCHEDULER_ENABLED=true
```

4. runtime env 렌더

```bash
SOURCE_ENV_FILE=.env.production \
TARGET_ENV_FILE=.env.runtime.production \
bash deploy/env/render-app-runtime-env.sh
```

5. app 재기동

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.prod.elasticache.yml \
  up -d --build app
```

Redis 전환 시점에는 기존 local Redis의 refresh/password reset/email verification token이 사라질 수 있다.

1차 운영 정책:

- 짧은 maintenance window를 잡고 Redis session reset을 허용한다.
- 사용자는 재로그인이 필요할 수 있다.
- 전환 직전 발급된 password reset/email verification code는 다시 요청하게 한다.

무중단 세션 유지가 필요하면 local Redis key를 ElastiCache로 이전하는 별도 작업을 먼저 설계한다.

### Phase 3. EC2-2 생성 및 배포

신규 EC2:

- `t3.medium`
- Ubuntu 24.04 LTS
- gp3 30GiB 이상
- EC2-1과 다른 AZ 권장
- EC2 app SG 부여
- SSM/CloudWatch 등 기존 운영 IAM policy가 있으면 동일하게 적용

설치:

```bash
sudo apt-get update
sudo apt-get install -y git curl jq nginx postgresql-client
# Docker / Compose plugin 설치는 기존 운영 서버 절차와 동일하게 맞춘다.
```

배포:

```bash
git clone <repo> youth-welfare
cd youth-welfare
git checkout <current-production-commit>
```

env:

```env
REDIS_HOST=<elasticache-primary-endpoint>
REDIS_PORT=6379
APP_SCHEDULER_ENABLED=false
```

runtime env:

```bash
SOURCE_ENV_FILE=.env.production \
TARGET_ENV_FILE=.env.runtime.production \
bash deploy/env/render-app-runtime-env.sh
```

frontend:

```bash
cd frontend
npm ci
npm run build
sudo rsync -a --delete dist/ /var/www/youth-welfare/frontend/
```

app:

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.prod.elasticache.yml \
  up -d --build app
```

nginx:

```bash
sudo cp deploy/nginx/youth-welfare.alb.conf /etc/nginx/sites-available/youth-welfare
sudo ln -sfn /etc/nginx/sites-available/youth-welfare /etc/nginx/sites-enabled/youth-welfare
sudo nginx -t
sudo systemctl reload nginx
```

local verify:

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
curl -fsS http://127.0.0.1/alb-health
curl -I -H 'Host: youthmoa.kr' http://127.0.0.1/
```

### Phase 4. ALB 생성 및 target 연결

1. ALB 생성
2. ACM certificate 연결
3. target group 생성
4. EC2-1, EC2-2 등록
5. target health가 둘 다 healthy인지 확인

ALB DNS 사전 확인:

```bash
curl -k -I -H 'Host: youthmoa.kr' https://<alb-dns-name>/
curl -k -sS -H 'Host: youthmoa.kr' https://<alb-dns-name>/alb-health
```

주의:

- ALB DNS로 직접 HTTPS 요청하면 인증서 이름이 맞지 않을 수 있어 `-k` 를 테스트에만 사용한다.
- 실제 검증 권위는 Route53 전환 뒤 `https://youthmoa.kr` 기준 smoke다.

### Phase 5. Route53 전환

1. 전환 전 DNS TTL을 낮춘다.
2. `youthmoa.kr`, `www.youthmoa.kr` 을 ALB alias로 변경한다.
3. 기존 EC2 direct EIP는 rollback을 위해 유지한다.
4. 기존 EC2의 직접 HTTPS nginx conf와 certificate도 ALB 안정화 전까지 삭제하지 않는다.

전환 직후:

```bash
curl -I https://youthmoa.kr/
curl -I https://www.youthmoa.kr/
```

### Phase 6. 운영 smoke

edge:

```bash
PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/nginx/verify-edge-baseline.sh
```

cutover:

```bash
ENV_FILE=.env.production \
PUBLIC_BASE_URL='https://youthmoa.kr' \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-prod-cutover-verification.sh
```

runtime:

```bash
ENV_FILE=.env.production \
APP_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-local-runtime-api-smoke.sh
```

ops:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

frontend:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='https://youthmoa.kr' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-local-frontend-observation-suite.sh
```

### Phase 7. 장애 드릴

EC2-1 app stop:

```bash
docker stop youth-welfare-app
```

확인:

- ALB target EC2-1 unhealthy
- EC2-2 healthy
- `https://youthmoa.kr/` 200
- login/refresh/logout smoke 통과

복구:

```bash
docker compose \
  --env-file .env.production \
  -f docker-compose.prod.elasticache.yml \
  up -d app
```

EC2-2 app stop도 같은 방식으로 확인한다.

Redis failover:

- ElastiCache primary failover event 후 app reconnect가 되는지 확인한다.
- failover 중 일부 auth/rate-limit request 실패 가능성은 관찰한다.

## 운영 후 모니터링

ALB:

- HealthyHostCount
- UnHealthyHostCount
- HTTPCode_ELB_5XX_Count
- HTTPCode_Target_5XX_Count
- TargetResponseTime p95/p99

EC2:

- CPUUtilization
- memory 사용률
- disk 사용률
- docker container health
- nginx access/error log

RDS:

- DatabaseConnections
- CPUUtilization
- FreeStorageSpace
- ReadLatency/WriteLatency
- deadlocks
- long running query

Redis:

- EngineCPUUtilization
- DatabaseMemoryUsagePercentage
- Evictions
- CurrConnections
- ReplicationLag
- Failover event

앱:

- `run-local-ops-observation-suite.sh`
- notification backlog audit
- collect governance observation
- auth observation
- frontend observation

## 롤백 계획

Route53 rollback:

1. `youthmoa.kr`, `www.youthmoa.kr` alias를 기존 EC2 EIP로 되돌린다.
2. 기존 EC2에서 direct HTTPS nginx conf를 복구한다.
3. nginx reload:

```bash
sudo cp deploy/nginx/youth-welfare.conf /etc/nginx/sites-available/youth-welfare
sudo nginx -t
sudo systemctl reload nginx
```

4. 기존 EC2 app을 single-node mode로 되돌린다.

```env
APP_SCHEDULER_ENABLED=true
REDIS_HOST=redis
REDIS_PORT=6379
```

5. runtime env 렌더 후 기존 compose로 재기동한다.

```bash
SOURCE_ENV_FILE=.env.production \
TARGET_ENV_FILE=.env.runtime.production \
bash deploy/env/render-app-runtime-env.sh

docker compose --env-file .env.production -f docker-compose.prod.yml up -d redis app
```

주의:

- Redis를 local로 되돌리면 ElastiCache에 있던 active refresh/reset/email verification token은 끊긴다.
- rollback 중 세션 reset을 허용할지, Redis key migration을 할지 사전에 정한다.

## 완료 기준

아래가 모두 성립해야 ALB 전환을 완료로 본다.

- ALB target EC2-1, EC2-2가 모두 healthy다.
- EC2-1 app 중지 시 EC2-2로 서비스가 계속된다.
- EC2-2 app 중지 시 EC2-1로 서비스가 계속된다.
- 로그인, refresh, logout, older token revoke가 ALB 경유로 통과한다.
- password reset, email verification이 ALB 경유로 통과한다.
- recommendation refresh lock/rate limit이 shared Redis 기준으로 동작한다.
- scheduler는 한 노드에서만 실행된다.
- 외부 `/actuator`, `/swagger-ui`, `/v3/api-docs`, 민감 정적 경로가 403/404다.
- `run-prod-cutover-verification.sh` 가 통과한다.
- `run-local-runtime-api-smoke.sh` 가 통과한다.
- `run-local-ops-observation-suite.sh` 가 `BASELINE_HEALTHY` 다.

## 작업 순서 요약

1. 현재 운영 기준선 저장
2. ALB nginx template 추가
3. scheduler node flag 추가
4. shared Redis compose/env 정리
5. ElastiCache Redis 생성
6. 기존 EC2를 shared Redis로 먼저 전환
7. EC2-2 생성 및 배포
8. ALB/target group 구성
9. Route53 alias 전환
10. smoke/ops 검증
11. EC2 장애 드릴
12. rollback 경로 보존 상태 확인
