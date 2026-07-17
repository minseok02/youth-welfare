# nightly ops handoff cron runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

운영 서버에서 nightly 기준으로 아래 관측을 한 번에 남기는 가장 작은 cron 경로를 고정합니다.

- standard-code / recommendation observation
- policy quality observation
- collect governance observation
- auth observation
- 필요 시 frontend observation

이 문서는 **cron 등록 전 수동 검증**, **crontab 예시**, **cleanup 분리**까지 같이 다룹니다.

## 기본 wrapper

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

기본 실행에 포함되는 lane:

1. `run-nightly-standard-code-observation.sh`
2. `run-nightly-policy-quality-observation.sh`
3. `run-local-policy-data-triage-observation-suite.sh`
4. `run-local-collect-governance-observation-suite.sh`
5. `run-local-auth-observation-suite.sh`
6. `deploy/postgres/audit-operational-db-state.sh`

기본값에서 제외되는 lane:

- `frontend observation`

브라우저 smoke는 비용이 더 크고 `deployed-origin` 자격증명/환경에 더 민감하므로, `RUN_FRONTEND_OBSERVATION=true` 일 때만 넣는 편이 맞습니다.
DB audit는 읽기 전용이며 `RUN_OPERATIONAL_DB_AUDIT=false` 일 때만 제외합니다.
운영 CORS는 public origin만 허용하므로, wrapper는 기본적으로 `SMOKE_TRUSTED_ORIGIN=${FRONTEND_PUBLIC_BASE_URL}` 와 `SMOKE_TRUSTED_REFERER=${SMOKE_TRUSTED_ORIGIN}/` 를 하위 auth smoke에 전달합니다. API 호출 대상은 내부 `APP_BASE_URL=http://127.0.0.1:8082` 여도 `Origin` 은 `https://youthmoa.kr` 이어야 합니다.

## 수동 선검증

cron 등록 전 아래를 한 번 직접 실행합니다.

```bash
cd /path/to/youth-welfare

ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=true \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

기대 결과:

- `/var/log/youth-welfare/nightly-ops-handoff/nightly-summary-YYYY-MM-DD.log` append
- `ops=passed`
- `policy=passed`
- `collect=passed`
- `auth=passed`
- `db_audit=ok`
- auth smoke output의 `smoke_trusted_origin=https://youthmoa.kr`

opt-in frontend까지 같이 볼 때:

```bash
RUN_FRONTEND_OBSERVATION=true \
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

## crontab 예시

nightly handoff:

```cron
10 1 * * * APP_ROOT=/path/to/youth-welfare ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' ALLOW_ADMIN_JWT_MINT=true bash "$APP_ROOT/deploy/smoke/run-nightly-ops-handoff.sh" >> /var/log/youth-welfare/nightly-ops-handoff/nightly-cron.log 2>&1
```

weekly frontend 포함:

```cron
30 1 * * 1 APP_ROOT=/path/to/youth-welfare RUN_FRONTEND_OBSERVATION=true ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' ALLOW_ADMIN_JWT_MINT=true bash "$APP_ROOT/deploy/smoke/run-nightly-ops-handoff.sh" >> /var/log/youth-welfare/nightly-ops-handoff/frontend-weekly-cron.log 2>&1
```

cleanup:

```cron
45 1 * * * APP_ROOT=/path/to/youth-welfare NIGHTLY_OPS_HANDOFF_LOG_ROOT=/var/log/youth-welfare/nightly-ops-handoff SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 bash "$APP_ROOT/deploy/smoke/cleanup-nightly-ops-handoff-artifacts.sh" >> /var/log/youth-welfare/nightly-ops-handoff/cleanup-cron.log 2>&1
```

직접 `crontab -e` 로 붙이지 않고 idempotent block install을 쓰려면:

```bash
cd /path/to/youth-welfare

APP_ROOT="$(pwd)" \
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=true \
bash deploy/smoke/install-nightly-ops-handoff-cron.sh
```

실제 적용 전 block preview만 볼 때:

```bash
PRINT_ONLY=true bash deploy/smoke/install-nightly-ops-handoff-cron.sh
```

## 로그와 artifact

기본 로그 루트:

- `/var/log/youth-welfare/nightly-ops-handoff`

주요 파일:

- `nightly-summary-YYYY-MM-DD.log`
- `nightly-cron.log`
- `cleanup-cron.log`
- `artifacts/<UTC timestamp>/...`
  - `operational-db-audit.out`

summary 한 줄은 아래 축을 같이 남깁니다.

- `ops`
- `policy`
- `collect`
- `auth`
- `frontend`
- `db_audit`
- `frontend flow_families` 는 `tmp/frontend-observation/latest-frontend-observation-note.md` 와 json에서 확인
- `attention_keys`
- `missing_all_standard_codes`
- `adoption_any_share_pct`

DB audit 해석 기준:

- `active_users_without_pii=0` 이어야 한다.
- `withdrawn_or_inactive_users_without_pii` 는 탈퇴/비활성 계정 PII 삭제 잔여로 분리해서 본다.
- `auth_without_users`, `profiles_without_users`, `pii_without_users` 는 0이어야 하며, 0이 아니면 user projection drift로 본다.

## 최근 운영 확인 기준

2026-07-17 기준 backend drift closeout 배포 뒤 전체 nightly wrapper 수동 재실행은 아래 조건으로 통과했다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=true \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

관찰된 결과:

- summary: `/var/log/youth-welfare/nightly-ops-handoff/nightly-summary-2026-07-17.log`
- artifact: `/var/log/youth-welfare/nightly-ops-handoff/artifacts/20260717T085519Z`
- `ops=passed`
- `policy=passed`
- `policy_triage=passed`
- `collect=passed`
- `auth=passed`
- `frontend=skipped`
- `db_audit=ok`
- auth smoke output: `smoke_trusted_origin=https://youthmoa.kr`
- DB alert evaluator: `OP_ALERT_STATUS=ok`

The earlier scheduled `2026-07-17T01:10Z` cron ran before the backend drift closeout deployment and stopped at
`current-priority -> active baseline -> backend_tests`. Treat that run as pre-fix evidence, not the current
post-closeout baseline. The next scheduled cron should be checked only to confirm the same passing summary appears
from the automatic schedule.

2026-06-27 기준 PR #363 이후 축소 nightly 검증은 아래 조건으로 통과했다.

```bash
RUN_STANDARD_CODE_OBSERVATION=false \
RUN_POLICY_QUALITY_OBSERVATION=false \
RUN_POLICY_DATA_TRIAGE_OBSERVATION=false \
RUN_COLLECT_GOVERNANCE_OBSERVATION=false \
RUN_AUTH_OBSERVATION=true \
RUN_FRONTEND_OBSERVATION=false \
RUN_OPERATIONAL_DB_AUDIT=true \
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

관찰된 결과:

- `auth=passed`
- `db_audit=ok`
- `smoke_trusted_origin=https://youthmoa.kr`
- `OPERATIONAL_DB_AUDIT_SUMMARY=<operational-db-audit.out> bash deploy/smoke/evaluate-operational-alert-thresholds.sh` -> `OP_ALERT_STATUS=ok`

## 문제 분리

- `ADMIN_PASSWORD is empty`: cron env에 `ALLOW_ADMIN_JWT_MINT=true` 가 없거나 admin token mint에 필요한 `SECURITY_ADMIN_EMAILS`, `JWT_SECRET`, DB 조회 경계가 깨진 상태다.
- `403 Invalid CORS request`: 하위 smoke의 `SMOKE_TRUSTED_ORIGIN` 이 prod CORS allowlist 밖이다. nightly wrapper에서는 `FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr'` 가 설정돼야 하며, 필요하면 `SMOKE_TRUSTED_ORIGIN` 을 명시한다.
- summary 파일이 생성되지 않고 `nightly-cron.log` 만 늘어남: wrapper가 summary append 전에 실패한 것이다. 최신 로그에서 위 두 오류를 먼저 확인한다.
- `db_audit` artifact가 없음: DB audit lane 이전 단계에서 중단됐거나 `RUN_OPERATIONAL_DB_AUDIT=false` 로 실행된 것이다.

## 운영 해석

이 wrapper는 운영 handoff를 compact하게 남기기 위한 entrypoint입니다.

- recommendation/product observation
- collect governance
- auth baseline
- policy quality

를 분리된 스크립트로 돌리되, summary만 보면 nightly 상태를 빠르게 파악할 수 있게 하는 것이 목적입니다.

frontend browser smoke는 주간/변경 직후 보강용으로 두는 편이 맞고, 매일 기본값에 강제로 넣지 않는 편이 안정적입니다.

## 관련 문서

1. [ops-baseline-runbook.md](./ops-baseline-runbook.md)
2. [current-state.md](../current-state.md)
3. [start.md](../start.md)
