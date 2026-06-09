# stabilization checklist

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

이 문서는 기능 개발을 멈추고 서비스 안정화 단계로 들어갈 때 보는 운영 체크리스트입니다.

기본 원칙은 아래와 같습니다.

1. 새 기능은 열지 않습니다.
2. 이미 배포된 기능의 회귀, CI/nightly 실패, 운영 backlog만 처리합니다.
3. 추천 로직은 real-user sample이 충분해질 때까지 수정하지 않습니다.
4. raw audit 잔량과 실제 운영 `OPEN` queue를 구분합니다.

## 현재 안정화 기준선

현재 기준으로 기능 개발 트랙은 닫고, 아래 상태를 유지합니다.

- backend health: `UP`
- CI: backend unit, frontend lint/build/browser smoke green
- nightly/current-priority: 실패하면 실제 장애와 smoke 결함을 먼저 분리
- attention feed:
  - `standard-code-backlog` 는 자동 보정 후보나 충돌 gap이 없으면 사용자 입력 backlog로 관찰
  - `notification-backlog` 는 terminal failed가 없으면 총량 신호로 보고, 14일 이상 target cluster가 없으면 cadence/가치 관찰
  - `notification-stale-backlog` 는 14일 이상 target cluster가 있을 때만 hide 후보로 처리
  - 정책 오류/링크/중복 queue는 `OPEN` 이 생길 때만 review
- recommendation gate:
  - `KEEP_OBSERVING`
  - `reopen_allowed=false`
  - real-user traffic이 얇으면 추천 로직을 다시 열지 않음

## 매일 또는 작업 시작 전

운영 서버 기준으로 먼저 아래를 봅니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION=false \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

summary에서 먼저 볼 값:

- `decision_class`
- `attention_feed_item_keys`
- `attention_feed_warning_item_count`
- `collect_failed_jobs_in_window`
- `collect_partial_success_jobs_in_window`
- `open_collect_circuits`
- `policy_data_triage_decision_class`
- `user_profile_standard_code_safe_reconcile_candidate_rows`

추천 표준코드 matrix까지 다시 확인해야 할 때만 아래 full wrapper를 씁니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
FRONTEND_E2E_MODE=deployed-origin \
FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-nightly-standard-code-observation.sh
```

## 실패가 났을 때 순서

### 1. CI 실패

먼저 실패 job을 구분합니다.

- backend unit 실패: backend 코드/테스트 회귀로 처리
- frontend lint/build 실패: 정적 회귀로 처리
- frontend browser smoke 실패: 실제 UX 회귀인지 smoke 계정/데이터/rate limit 문제인지 분리

반복 실패 메일이 온 경우:

1. GitHub Actions run의 실패 job 로그를 봅니다.
2. 같은 실패가 local/server smoke에서도 재현되는지 확인합니다.
3. smoke 결함이면 smoke를 고치고, 제품 결함이면 제품 코드를 고칩니다.
4. 고친 뒤 PR CI까지 green으로 닫습니다.

### 2. nightly 실패

nightly는 즉시 기능 수정으로 들어가지 않습니다.

1. `/var/log/youth-welfare/*/nightly-summary-YYYY-MM-DD.log` 를 봅니다.
2. `ops`, `current_priority`, `policy`, `blocker_class` 중 어디가 바뀌었는지 나눕니다.
3. `attention_feed_item_keys` 에 새 warning이 생겼는지 봅니다.
4. 새 warning이 없고 raw audit만 흔들렸으면 관찰로 둡니다.

### 3. attention warning

우선순위는 아래 순서입니다.

1. `collect-drift`
2. `policy-error-report-backlog`
3. `support-inquiry-backlog`
4. `policy-link-review-backlog`
5. `policy-duplicate-backlog`
6. `notification-stale-backlog`
7. `standard-code-backlog`

`standard-code-backlog` 는 자동 보정 후보가 있을 때만 직접 처리합니다.

- `safe_reconcile_candidate_rows > 0`: bounded reconcile 후보
- `conflicting_value_gap_rows > 0`: 수동 conflict 확인 후보
- `safe_reconcile_candidate_rows = 0` 이고 `conflicting_value_gap_rows = 0`: 사용자 입력 유도/관찰

## 알림 backlog 기준

아래 순서로 봅니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-notification-backlog-audit.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-notification-backlog-sample-audit.sh

ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-notification-stale-target-audit.sh
```

처리 기준:

- `terminal_failed_total > 0`: 채널/영구 실패 원인 확인
- `retryable_failed_due_now > 0`: retry runner 또는 채널 상태 확인
- `stale_14d_total > 0`: `hide-stale` 후보를 target 단위로 좁혀 처리
- `stale_unread_7d > 0` 이고 14일 이상이 아니면: cadence/가치 관찰

2026-06-09 server/RDS 기준 current reading:

- failed notification은 없음
- `unread_total=24`, 모두 `RECOMMENDATION_DIGEST`
- `stale_unread_7d=12`
- `stale_unread_14d=0`
- stale target audit은 `NO_STALE_TARGETS`

따라서 현재 알림 backlog는 장애나 hide 작업이 아니라 `7일 초과 recommendation digest tail` 관찰 단계입니다.

## 정책 backlog 기준

정책은 raw audit 잔량과 운영 queue를 구분합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh
```

해석:

- `policy_duplicate_open_groups > 0`: duplicate queue 처리
- `policy_link_open_reviews > 0`: link review queue 처리
- `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`: 운영 queue는 닫힘, raw 잔량은 관찰

raw duplicate/link 숫자가 남아 있어도 운영 `OPEN` queue가 0이면 새 작업을 열지 않습니다.

2026-06-09 server/RDS 기준 current reading:

- `policy_duplicate_open_groups=0`
- `policy_duplicate_open_rows=0`
- `policy_link_open_reviews=0`
- raw 후보는 `duplicate_groups_youth=82`, `duplicate_groups_bokjiro_local=59`, `active_visible_youth_total=172`
- `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`

따라서 현재 정책 backlog는 처리 queue가 아니라 raw 품질 잔량 관찰 단계입니다.

## 추천 안정화 기준

추천은 지금 손대지 않습니다.

다시 열 수 있는 조건은 아래가 모두 충족될 때입니다.

- real-user sample이 충분함
- top1 leader signal이 smoke/example 데이터에만 끌리지 않음
- latest status가 `KEEP_OBSERVING` 에서 실제 review 가능 상태로 바뀜

확인 명령:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-reopen-precheck.sh
```

`reopen_allowed=false` 이면 추천 로직을 수정하지 않습니다.

2026-06-09 server/RDS 기준 current reading:

- `reopen_precheck_status=KEEP_OBSERVING`
- `real_user_dashboard_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `real_user_breakdown_cohort_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `real_user_review_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `real_user_top1_leader_signal_summary=EXAMPLE_SMOKE_ONLY_LEADER`
- `review_gate_policy_promotion_execution_status=DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW`

따라서 현재 recommendation 안정화 결론은 관찰 유지이며, score/weight/prompt를 다시 열지 않습니다.

## 배포 후 확인

배포 후에는 아래만 기본으로 확인합니다.

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
curl -fsS https://youthmoa.kr/ >/tmp/youthmoa-index.html
```

프론트 번들을 배포했다면:

- `index.html` 이 새 bundle을 가리키는지 확인
- 주요 문구 또는 변경된 기능 문자열이 bundle에 포함됐는지 확인
- 필요하면 `FRONTEND_E2E_MODE=deployed-origin` 으로 frontend observation을 실행

## 작업을 열어도 되는 경우

새 PR은 아래 중 하나일 때만 엽니다.

1. CI/nightly가 실제로 실패함
2. attention feed에 새 warning이 생김
3. 배포/운영 smoke가 깨짐
4. 문서가 실제 운영 기준과 달라져 다음 판단을 틀리게 만듦

그 외에는 관찰로 남깁니다.

## 완료 기준

안정화 작업 하나가 닫히려면 아래를 만족해야 합니다.

1. 원인 분리: 제품 결함인지 smoke/문서/운영 데이터 결함인지 명확함
2. 수정 범위 최소화
3. 관련 smoke 또는 CI green
4. current-state 또는 관련 runbook 갱신
5. PR merge 후 local `main` 이 `origin/main` 과 일치
