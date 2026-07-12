# Stabilization Handoff

## 현재 모드

현재 active main track은 기능 추가가 아니라 안정화와 회귀 방지입니다.

운영 서버는 현재 ElastiCache 경로인 `docker-compose.prod.elasticache.yml` 의 `app` 구성으로 떠 있고, DB는 RDS PostgreSQL을 사용합니다.
`docker-compose.prod.yml` 은 local Redis rollback/단일 서버 호환 경로로 남깁니다.
현재 앱 health 확인 기본 URL은 `http://127.0.0.1:8082/actuator/health` 입니다.

## 2026-07-12 HA 준비 handoff

목표는 졸업작품 제출/시연 때 `ALB + EC2 2대 + RDS + ElastiCache` 구조를 보여주고, 제출 이후에는 비용 절감을 위해 EC2 1대 운영으로 낮출 수 있게 하는 것입니다.

완료된 로컬/운영 준비:

- `bbbaf55a Prepare ALB HA runtime mode`
  - `APP_SCHEDULER_ENABLED` 공통 scheduler gate 추가
  - EC2-1은 `true`, EC2-2는 `false` 기준
  - ALB용 nginx 템플릿 `deploy/nginx/youth-welfare.alb.conf` 추가
  - runtime env renderer allowlist에 scheduler/pool key 추가
- `7f370722 Document AWS instance specs`
  - EC2-1/EC2-2 스펙 문서 [aws-instance-specs.md](core/aws-instance-specs.md) 추가
  - ALB 전환 계획과 측정 계획에서 해당 문서를 참조

검증 완료:

```bash
cd backend && ./gradlew test --no-daemon
cd frontend && npm run lint && npm run build
SOURCE_ENV_FILE=env.production.example TARGET_ENV_FILE="$(mktemp -d)/.env.runtime.production" STRICT_REQUIRED_RUNTIME_ENV=true bash deploy/env/render-app-runtime-env.sh
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml config
PUBLIC_BASE_URL='https://youthmoa.kr' ENV_FILE=.env.production KEEP_ARTIFACTS=true bash deploy/smoke/run-prod-cutover-verification.sh
```

운영 env 준비 상태:

- `.env.production` 과 `.env.runtime.production` 은 git 추적 대상이 아니며, EC2-1 기준으로 `APP_SCHEDULER_ENABLED=true` 와 보수적인 DB pool 값이 준비되어 있습니다.
- 현재 실행 중인 app container는 이 env 변경 전에 생성된 것이므로, 새 값은 다음 app recreate 때 적용됩니다.
- 의도적으로 아직 app 재기동은 하지 않았습니다.

현재 멈춘 지점:

- EC2-2 AWS instance 생성/접속/배포는 아직 진행하지 않았습니다.
- ALB, target group, Route53 전환도 아직 진행하지 않았습니다.
- 사용자는 EC2-2 생성 화면에서 `youthmoa-prod-ec2-2`, Ubuntu 24.04 LTS x86, `t3.medium`, `youthmoa-new-key`, `vpc-093662d691f85e844`, `ap-northeast-2c`, `youthmoa-ec2-sg`, root 20GiB gp3, EBS 미암호화, termination protection enabled 기준으로 진행하기로 결정했습니다.

다음 재개 순서:

1. EC2-2가 생성되면 instance id, public IPv4, private IPv4를 확인합니다.
2. EC2-2에 접속해 Docker Engine, Docker Compose plugin, nginx, git, curl, jq, postgresql-client를 설치합니다.
3. repo를 clone/pull하고 `refactor/admin-dashboard-sections` 최신 commit `7f370722` 이상을 checkout합니다.
4. EC2-1의 `.env.production` 을 EC2-2로 복사하되, EC2-2에서는 반드시 `APP_SCHEDULER_ENABLED=false` 로 바꿉니다.
5. EC2-2에서 runtime env를 렌더링합니다.

```bash
SOURCE_ENV_FILE=.env.production \
TARGET_ENV_FILE=.env.runtime.production \
bash deploy/env/render-app-runtime-env.sh
```

6. EC2-2 frontend build와 nginx ALB 템플릿 적용을 진행합니다.
7. EC2-2 app을 `docker-compose.prod.elasticache.yml` 로 실행하고 아래를 확인합니다.

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
curl -fsS http://127.0.0.1/alb-health
docker exec youth-welfare-app sh -lc 'printenv APP_SCHEDULER_ENABLED'
```

기대값은 app health `UP`, `/alb-health` 200, `APP_SCHEDULER_ENABLED=false` 입니다.

주의:

- EC2-2가 같은 `youthmoa-ec2-sg` 를 쓰면 RDS/Redis 접근은 기존 허용 경계를 재사용할 수 있습니다.
- ALB 생성 전에는 `youthmoa-ec2-sg` 의 HTTP/HTTPS direct inbound가 남아 있어도 bootstrap/debug 용도로 허용합니다.
- ALB 전환 완료 후에는 EC2 inbound 80을 ALB security group source로 좁히고, direct 443은 rollback 기간 후 제거합니다.

## 현재 기준선

2026-06-24 server/RDS 문서 기준:

- app health: `UP`
- ops observation: `BASELINE_HEALTHY`
- attention warning count: `0`
- collect failed/partial/open circuit: `0`
- policy duplicate/link/error/support `OPEN` queue: `0`
- policy triage: `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`
- notification failed count: `0`
- notification stale 14d target: `0`
- notification unread: `26` (`RECOMMENDATION_DIGEST`), stale 7d `14`, stale 14d `0`
- recommendation observation: `KEEP_OBSERVING`, `reopen_allowed=false`
- recommendation decision class: `OBSERVE_REAL_USER_TRAFFIC`
- chat observability: `CHAT_BASELINE_HEALTHY`
- frontend deployed-origin observation: user Playwright smoke green, admin E2E는 명시 credential opt-in
- privacy/consent drift: active user 기준 필수/선택/민감정보 동의 누락 `0`
- web push: enabled subscription `1`, authenticated push public key 정상

세부 최신값은 [current-state.md](./current-state.md) 와 `tmp/*/latest-*` stable artifact를 우선합니다.

## 먼저 볼 문서

1. [current-state.md](./current-state.md)
2. [core/stabilization-checklist.md](core/stabilization-checklist.md)
3. [core/final-ops-closeout-checklist.md](core/final-ops-closeout-checklist.md)
4. [core/ops-baseline-runbook.md](core/ops-baseline-runbook.md)
5. [recommendation/recommendation-current-state.md](recommendation/recommendation-current-state.md)
6. [policy/policy-data-triage-observation-runbook.md](policy/policy-data-triage-observation-runbook.md)
7. [core/notification-backlog-audit-runbook.md](core/notification-backlog-audit-runbook.md)

## 작업 원칙

- 새 기능을 열지 않습니다.
- CI/nightly/smoke 실패, attention warning, 실제 운영 queue만 처리합니다.
- raw audit 숫자와 운영 `OPEN` queue를 구분합니다.
- recommendation은 `KEEP_OBSERVING` 동안 score/weight/prompt를 수정하지 않습니다.
- 문서와 실제 관측값이 다르면 문서를 같은 작업 단위에서 고칩니다.
- 수치가 자주 바뀌는 backlog는 이 문서에 장기 고정하지 않고 latest artifact와 current-state를 우선합니다.

## 반복 확인 명령

운영 closeout 순서는 [core/final-ops-closeout-checklist.md](core/final-ops-closeout-checklist.md) 를 따릅니다.

가장 기본 확인:

```bash
git status --short --branch
docker ps --filter name=youth-welfare-app --format '{{.Names}}\t{{.Status}}'
curl -fsS http://127.0.0.1:8082/actuator/health
```

운영 observation:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION=false \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

## 다음에 열어도 되는 조건

- attention feed에 새 `warning` 이 생김
- CI 또는 nightly가 실제 제품 회귀로 실패함
- policy duplicate/link `OPEN` queue가 다시 생김
- notification failed 또는 14일 이상 stale target cluster가 생김
- recommendation real-user sample이 충분해지고 `KEEP_OBSERVING` 에서 review 가능 상태로 바뀜

그 전까지는 관찰 유지가 기본값입니다.
