# nightly ops handoff cron runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

운영 서버에서 아래 관측·실행 검증을 묶어 nightly 결과를 남깁니다. **기존 artifact만 조회하는 작업이 아닙니다.** 기본 auth lane은 가입·로그인 실패·계정 잠금·탈퇴·강제 로그아웃을 실행하며, 다른 lane도 설정에 따라 평가 결과 저장·추천 재생성·외부 호출을 포함할 수 있습니다.

- standard-code / recommendation observation
- policy quality observation
- collect governance observation
- auth observation
- 필요 시 frontend observation

이 문서는 **cron 등록 전 수동 검증**, **crontab 예시**, **cleanup 분리**까지 같이 다룹니다.

앱/DB·Redis와 테스트 계정·하위 step의 쓰기/비용·반복 실행 범위를 승인한 뒤 사용합니다. 단순 조회는 [운영 baseline](ops-baseline-runbook.md)을 선택합니다. `RUN_AUTH_OBSERVATION=false`는 auth lane만 끄며 다른 lane까지 읽기 전용으로 바꾸지 않습니다. lane을 끈 실행에서는 기존 latest artifact의 시각과 이번 실행 여부를 구분합니다.

운영 인증은 [공통 선택 순서](ops-baseline-runbook.md#실행-대상과-인증)를 따릅니다. 아래 예시는 직접 mint를 끄고 승인된 토큰/보호 credential file 또는 로그인 자격을 사용합니다. token file은 만료 시 자동 갱신되지 않으므로, cron은 다음 실행 시에도 유효한 인증 공급 경로가 있어야 합니다.

## 기본 wrapper

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=false \
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

cron 등록 전, 승인된 테스트 계정과 선택한 lane의 영향을 확인한 뒤 아래를 한 번 실행합니다. 쓰기 검증이 허용되지 않은 운영 환경에서는 실행하지 않습니다.

```bash
cd /path/to/youth-welfare

ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=false \
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
ALLOW_ADMIN_JWT_MINT=false \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-nightly-ops-handoff.sh
```

## crontab 예시

아래 경로는 실제 설치 위치로 치환합니다. 보호된 점검 설정/credential file을 준비하고, 토큰·비밀번호·JWT secret을 crontab 본문에 넣지 않습니다. inline 환경 변수 대입은 같은 명령 인자의 `$APP_ROOT` 확장에 반영되지 않으므로 script와 env에 명시 경로를 사용합니다.

nightly handoff:

```cron
10 1 * * * ENV_FILE=/path/to/youth-welfare/.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' ALLOW_ADMIN_JWT_MINT=false bash /path/to/youth-welfare/deploy/smoke/run-nightly-ops-handoff.sh >> /var/log/youth-welfare/nightly-ops-handoff/nightly-cron.log 2>&1
```

weekly frontend 포함:

```cron
30 1 * * 1 RUN_FRONTEND_OBSERVATION=true ENV_FILE=/path/to/youth-welfare/.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' ALLOW_ADMIN_JWT_MINT=false bash /path/to/youth-welfare/deploy/smoke/run-nightly-ops-handoff.sh >> /var/log/youth-welfare/nightly-ops-handoff/frontend-weekly-cron.log 2>&1
```

cleanup:

```cron
45 1 * * * NIGHTLY_OPS_HANDOFF_LOG_ROOT=/var/log/youth-welfare/nightly-ops-handoff SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 bash /path/to/youth-welfare/deploy/smoke/cleanup-nightly-ops-handoff-artifacts.sh >> /var/log/youth-welfare/nightly-ops-handoff/cleanup-cron.log 2>&1
```

직접 `crontab -e` 로 붙이지 않고 idempotent block install을 쓰려면 아래를 사용합니다. **현재 installer의 `ALLOW_ADMIN_JWT_MINT` 기본값은 `true`**이므로 운영 설치와 preview에서 `false`를 생략하지 않습니다. 설치는 기존 관리 block을 교체하는 쓰기 작업이며, 승인되지 않은 nightly lane은 설치 전에 실행 구성을 별도로 검토합니다.

```bash
cd /path/to/youth-welfare

APP_ROOT="$(pwd)" \
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
ALLOW_ADMIN_JWT_MINT=false \
bash deploy/smoke/install-nightly-ops-handoff-cron.sh
```

실제 적용 전 block preview만 볼 때:

```bash
ALLOW_ADMIN_JWT_MINT=false PRINT_ONLY=true bash deploy/smoke/install-nightly-ops-handoff-cron.sh
```

`PRINT_ONLY=true`는 crontab을 쓰지 않지만 로그 디렉터리는 생성할 수 있습니다. installer 코드와 기존 서버 cron은 이 안내 수정으로 변경되지 않으므로 실제 적용값을 따로 확인합니다.

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

2026-06-27 기준 PR #363 이후 축소 nightly 검증은 아래 조건으로 통과했다. **당시 실행 기록**으로서 mint 옵션을 보존하며, 현재 운영에서 그대로 재실행하거나 인증 우회를 승인하는 예시가 아니다.

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

- `ADMIN_PASSWORD is empty`: 사용할 토큰을 찾지 못했고 로그인 비밀번호도 없는 상태다. 승인된 token/credential 공급·유효기간·점검 계정 권한을 확인한다. 운영에서 mint를 켜는 것을 기본 복구로 사용하지 않는다.
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
