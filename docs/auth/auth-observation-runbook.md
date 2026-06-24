# Auth Observation Runbook

문서군 진입점: [auth-docs-index.md](./auth-docs-index.md)

## 목적

daily operator가 auth/session smoke 묶음 전체를 다시 해석하지 않아도,
현재 auth/session baseline을 compact하게 읽도록 합니다.

이 runbook의 entrypoint는 아래 wrapper입니다.

- `bash deploy/smoke/run-local-auth-observation-suite.sh`
- 운영 서버/RDS:
  - `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`

## latest artifact

- `tmp/auth-observation/latest-auth-observation-summary.txt`
- `tmp/auth-observation/latest-auth-observation-note.md`
- `tmp/auth-observation/latest-auth-observation.json`

`KEEP_ARTIFACTS=false` 기본값에서도 stable snapshot은 남습니다.

## 현재 해석

### `decision_class=BASELINE_HEALTHY`

- logout, withdraw, login failure tracking, account lockout, admin forced logout 경계가 현재 baseline을 유지한다는 뜻입니다.
- 현재 구현을 바꾸기보다 current-state/runbook 기준을 그대로 유지합니다.

## summary/json에서 먼저 볼 값

- `decision_class`
- `enabled_smoke_steps`
- `operator_reading`
- `next_action`

## app log observation에서 볼 값

auth smoke가 흔들리거나 운영 4xx/429가 늘면 아래를 같이 봅니다.

```bash
bash deploy/performance/run-local-app-log-observability-baseline.sh
```

먼저 볼 prefix/key:

- `[AuthAudit] event=login outcome=invalid_credentials|account_locked|success`
- `[AuthAudit] event=email_verification_send|email_verification_verify`
- `[AuthAudit] event=password_reset_request|password_reset_confirm`
- `[AuthAudit] event=rate_limit outcome=exceeded`
- `[AdminAudit] event=forced_logout outcome=accepted`
- `[ApiRequest] errorCode=A006|A012|C003|R004`

## 다음 액션

- `next_action=docs/auth/auth-session-revocation-current-state.md` 이면 현재 구현 계약을 다시 읽는 쪽이 우선입니다.
- 실제 경계 하나를 더 깊게 확인하려면 [auth-operation-checklist.md](./auth-operation-checklist.md) 로 내려갑니다.
