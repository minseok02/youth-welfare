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
bash deploy/smoke/run-nightly-ops-handoff.sh
```

기본 실행에 포함되는 lane:

1. `run-nightly-standard-code-observation.sh`
2. `run-nightly-policy-quality-observation.sh`
3. `run-local-policy-data-triage-observation-suite.sh`
4. `run-local-collect-governance-observation-suite.sh`
5. `run-local-auth-observation-suite.sh`

기본값에서 제외되는 lane:

- `frontend observation`

브라우저 smoke는 비용이 더 크고 `deployed-origin` 자격증명/환경에 더 민감하므로, `RUN_FRONTEND_OBSERVATION=true` 일 때만 넣는 편이 맞습니다.

## 수동 선검증

cron 등록 전 아래를 한 번 직접 실행합니다.

```bash
cd /home/ubuntu/youth-welfare

ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

기대 결과:

- `/var/log/youth-welfare/nightly-ops-handoff/nightly-summary-YYYY-MM-DD.log` append
- `ops=passed`
- `policy=passed`
- `collect=passed`
- `auth=passed`

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
10 1 * * * ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash /home/ubuntu/youth-welfare/deploy/smoke/run-nightly-ops-handoff.sh >> /var/log/youth-welfare/nightly-ops-handoff/nightly-cron.log 2>&1
```

weekly frontend 포함:

```cron
30 1 * * 1 RUN_FRONTEND_OBSERVATION=true ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash /home/ubuntu/youth-welfare/deploy/smoke/run-nightly-ops-handoff.sh >> /var/log/youth-welfare/nightly-ops-handoff/frontend-weekly-cron.log 2>&1
```

cleanup:

```cron
45 1 * * * NIGHTLY_OPS_HANDOFF_LOG_ROOT=/var/log/youth-welfare/nightly-ops-handoff SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 bash /home/ubuntu/youth-welfare/deploy/smoke/cleanup-nightly-ops-handoff-artifacts.sh >> /var/log/youth-welfare/nightly-ops-handoff/cleanup-cron.log 2>&1
```

직접 `crontab -e` 로 붙이지 않고 idempotent block install을 쓰려면:

```bash
cd /home/ubuntu/youth-welfare

APP_ROOT=/home/ubuntu/youth-welfare \
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
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

summary 한 줄은 아래 축을 같이 남깁니다.

- `ops`
- `policy`
- `collect`
- `auth`
- `frontend`
- `frontend flow_families` 는 `tmp/frontend-observation/latest-frontend-observation-note.md` 와 json에서 확인
- `attention_keys`
- `missing_all_standard_codes`
- `adoption_any_share_pct`

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
