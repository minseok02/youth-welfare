# final ops closeout checklist

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

이 문서는 기능 추가를 멈춘 안정화 단계에서 PR/배포/운영 handoff를 닫기 전에 같은 순서로 확인할 항목을 고정합니다.

판단 기준은 [stabilization-checklist.md](./stabilization-checklist.md) 를 따르고, 이 문서는 실제 실행 순서만 짧게 둡니다.

## 1. 작업 전 상태 확인

```bash
git status --short --branch
docker ps --filter name=youth-welfare-app --format '{{.Names}}\t{{.Status}}'
curl -fsS http://127.0.0.1:8082/actuator/health
```

기대값:

- 작업 branch와 변경 파일 범위가 의도와 맞음
- `youth-welfare-app` 이 `healthy`
- health가 `UP`

## 2. 운영 observation 기준선

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION=false \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

먼저 볼 값:

- `decision_class`
- `attention_feed_warning_item_count`
- `attention_feed_item_keys`
- `wrapper_promoted_alert_severity`
- `collect_failed_jobs_in_window`
- `collect_partial_success_jobs_in_window`
- `open_collect_circuits`
- `policy_data_triage_decision_class`

현재 안정화 기준:

- `decision_class=BASELINE_HEALTHY`
- `attention_feed_warning_item_count=0`
- `wrapper_promoted_alert_severity=info`
- policy triage는 `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 이면 관찰

## 3. 알림 backlog 확인

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-notification-backlog-audit.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-notification-backlog-sample-audit.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-notification-stale-target-audit.sh
```

현재 안정화 기준:

- failed notification이 있으면 채널/재시도부터 처리
- `stale_14d_total > 0` 이면 target 단위 `hide-stale` 후보
- 현재처럼 `stale_unread_14d=0`, `NO_STALE_TARGETS` 이면 digest/reminder cadence 관찰

## 4. 정책 raw backlog 확인

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh
```

현재 안정화 기준:

- `policy_duplicate_open_groups > 0`: duplicate queue 처리
- `policy_link_open_reviews > 0`: link review queue 처리
- `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`: raw 후보는 관찰, 새 review 작업은 열지 않음

## 5. 추천 reopen precheck

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-reopen-precheck.sh
```

현재 안정화 기준:

- `reopen_precheck_status=KEEP_OBSERVING` 이면 추천 로직을 수정하지 않음
- `DEFERRED_REAL_USER_SAMPLE_THIN` 이면 real-user sample 관찰 유지
- `EXAMPLE_SMOKE_ONLY_LEADER` 이면 leader signal 관찰 유지
- `DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW` 이면 promotion/review run을 실행하지 않음

## 6. 코드 검증

변경 범위가 문서만이면:

```bash
git diff --check
```

backend 코드가 바뀌었으면:

```bash
cd backend && ./gradlew test --no-daemon
```

frontend 코드가 바뀌었으면:

```bash
cd frontend && npm run lint && npm run build
```

배포 UI나 브라우저 smoke가 관련되면:

```bash
cd frontend && npm run test:e2e
```

## 7. 배포 후 확인

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build app
curl -fsS http://127.0.0.1:8082/actuator/health
```

그 뒤 2번 운영 observation 기준선을 다시 실행합니다.

## 8. 문서 handoff

작업이 끝나면 아래를 갱신합니다.

- [current-state.md](../current-state.md)
- 관련 runbook
- [phase-plan.md](../phase-plan.md)

최종 보고에는 아래만 남깁니다.

- 무엇을 바꿨는지
- 어떤 smoke/test를 돌렸는지
- 현재 decision/status
- 다음에 열어도 되는 조건
