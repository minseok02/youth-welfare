# Auth Observation Runbook

문서군 진입점: [auth-docs-index.md](./auth-docs-index.md)

## 목적

daily operator가 auth/session smoke 묶음 전체를 다시 해석하지 않아도,
현재 auth/session baseline을 compact하게 읽도록 합니다.

이 wrapper는 기존 artifact 조회기가 아니라 하위 검증을 실제로 실행합니다. 기본으로 가입/로그인·실패 추적·계정 잠금·탈퇴·강제 로그아웃이 포함되므로 운영 사용자 대상으로 일반 조회처럼 실행하지 않습니다. 결과만 필요하면 아래 latest artifact를 먼저 읽고, 재실행은 [검증 체크리스트](auth-operation-checklist.md)에서 테스트 계정·환경·step 범위를 정한 뒤 진행합니다.

이 runbook의 entrypoint는 아래 wrapper입니다.

- `bash deploy/smoke/run-local-auth-observation-suite.sh`
- 승인된 테스트 계정으로 운영형 검증을 별도 수행할 때의 대상 지정 형태 (실제 쓰기/세션 회수 주의):
  - `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`

## latest artifact

- `tmp/auth-observation/latest-auth-observation-summary.txt`
- `tmp/auth-observation/latest-auth-observation-note.md`
- `tmp/auth-observation/latest-auth-observation.json`
- `tmp/auth-observation/latest/`

`KEEP_ARTIFACTS=false` 기본값에서도 stable snapshot은 남습니다.
하위 auth/session 응답 JSON은 summary/json의 `auth_session_artifact_dir` 와
활성 step별 `*_artifact_dir` 값을 따라가서 봅니다.

## 현재 해석

### `decision_class=BASELINE_HEALTHY`

- logout, withdraw, login failure tracking, account lockout, admin forced logout 경계가 현재 baseline을 유지한다는 뜻입니다.
- 실제 enabled step에서 관측한 회귀 결과입니다. 미실행 경계와 Redis 내구성을 보장하거나 승인된 보안·데이터 보호 수정을 금지하지 않습니다.

## summary/json에서 먼저 볼 값

- `decision_class`
- `enabled_smoke_steps`
- `auth_session_artifact_dir`
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
