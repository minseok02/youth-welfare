# stabilization handoff

## 현재 모드

현재 active main track은 기능 추가가 아니라 안정화와 회귀 방지입니다.

운영 서버는 `docker-compose.prod.yml` 의 `app + redis` 구성으로 떠 있고, DB는 RDS PostgreSQL을 사용합니다.
현재 앱 health 확인 기본 URL은 `http://127.0.0.1:8082/actuator/health` 입니다.

## 현재 기준선

2026-06-10 server/RDS 기준:

- app health: `UP`
- ops observation: `BASELINE_HEALTHY`
- attention warning count: `0`
- wrapper promoted alert: `info`
- notification failed count: `0`
- notification stale 14d target: `0`
- notification unread: `14` (`RECOMMENDATION_DIGEST`), stale 7d `0`
- standard-code missing all: total `299`, non-example `2`, EXAMPLE_SMOKE `297`
- standard-code reconcile/conflict candidates: `0`
- policy duplicate/link open queue: `0`
- policy triage: `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`
- recommendation precheck: `KEEP_OBSERVING`
- recommendation gate: `DEFERRED_REAL_USER_SAMPLE_THIN`
- recommendation leader signal: `EXAMPLE_SMOKE_ONLY_LEADER`
- DB runtime privilege verification: passed
- DB migration resource versions: unique
- frontend deployed-origin observation: lint/build/browser smoke green

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
