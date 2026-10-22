# 운영 baseline과 observation 실행

health·admin dashboard·collect failures·recommendation breakdown의 **조회용 surface contract**를 확인합니다. 로그인/토큰 발급·로컬 artifact 생성은 있을 수 있으므로 “어떤 상태도 바뀌지 않는다”는 뜻은 아닙니다. 일상 triage·배포 후 순서는 [운영 점검](final-ops-closeout-checklist.md)을 따릅니다.

## 실행 대상과 인증

[환경 안내](environment-guide.md)로 앱/DB·로컬/서버를 확인합니다. 운영 Compose 앱의 runtime env와 점검용 DB 계정은 역할이 다릅니다. `.env.runtime.production`에 query/migration 비밀이 없을 수 있으므로 승인된 점검 설정·DB_QUERY 계정을 사용하고 앱에 과도한 비밀을 추가하지 않습니다.

운영/RDS는 `SMOKE_DB_MODE=postgres`와 실제 `APP_BASE_URL`을 명시하고 앱 DB와 점검 DB를 맞춥니다. admin 자격은 비밀 관리 또는 허용된 credential file로 전달하고 문서/로그에 비밀번호를 복사하지 않습니다.

[공통 인증 도우미](../../deploy/smoke/smoke-common.sh)의 실제 선택 순서는 다음과 같습니다.

1. `ADMIN_ACCESS_TOKEN`, 없으면 `ADMIN_ACCESS_TOKEN_FILE` 또는 기본 token file을 읽습니다. 파일이 있다는 것만으로 유효한 토큰이라는 뜻은 아니며, 만료 토큰의 자동 갱신도 보장하지 않습니다.
2. 토큰이 없고 `ALLOW_ADMIN_JWT_MINT=true`일 때만 JWT secret·DB 조회로 직접 발급을 시도합니다. 도우미 기본값은 `false`이며 실행 위치를 로컬로 강제하는 guard는 없습니다.
3. 사용할 토큰이 없으면 하위 admin smoke가 `ADMIN_EMAIL/ADMIN_PASSWORD`로 앱 로그인을 수행합니다. 자격이 없으면 실패하며 자동으로 mint를 허용하지 않습니다.

운영 예시는 `ALLOW_ADMIN_JWT_MINT=false`를 명시하고 승인된 토큰 또는 로그인 자격을 준비합니다. 직접 발급은 승인된 격리 로컬 검증 예외로만 사용합니다. 이 실행 지침과 현재 코드가 허용하는 범위를 구분하며, 기존 운영 cron의 설정이 이미 바뀌었다고 가정하지 않습니다.

```bash
(
  set -euo pipefail
  : "${OPS_ENV_FILE:?승인된 점검 설정 파일이 필요합니다}"
  : "${OPS_APP_BASE_URL:?실제 앱 URL을 명시하세요}"
  ENV_FILE="${OPS_ENV_FILE}" SMOKE_DB_MODE=postgres \
  APP_BASE_URL="${OPS_APP_BASE_URL}" ALLOW_ADMIN_JWT_MINT=false \
  bash deploy/smoke/run-local-ops-baseline-suite.sh
)
```

## Wrapper 선택

| 목적 | Script `deploy/smoke/` 아래 | 범위 |
| --- | --- | --- |
| 운영 surface | [ops-baseline-suite](../../deploy/smoke/run-local-ops-baseline-suite.sh) | admin-dashboard → admin-collect-failures → admin-recommendation-breakdowns |
| Compact handoff | [ops-observation-suite](../../deploy/smoke/run-local-ops-observation-suite.sh) | baseline·표준코드/추천 관측·attention summary/JSON |
| Collect lane | [collect-governance-observation-suite](../../deploy/smoke/run-local-collect-governance-observation-suite.sh) | scheduled/rotation/manual·최근 실패·설정·circuit |
| Nightly 표준코드 | [nightly-standard-code-observation](../../deploy/smoke/run-nightly-standard-code-observation.sh) | 선택된 runtime/frontend 관측 포함; 실행 전 하위 step 영향 확인 |
| Nightly handoff | [nightly-ops-handoff](../../deploy/smoke/run-nightly-ops-handoff.sh) | nightly/current-priority/policy triage 묶음, raw stdout보다 summary 우선 |
| 전체 validation | [active-baseline-suite](../../deploy/smoke/run-local-active-baseline-suite.sh) | 코드·브라우저·legacy repair 등 포함 가능. read-only 점검과 구분 |
| 운영 cutover | [prod-cutover-verification](../../deploy/smoke/run-prod-cutover-verification.sh) | env/RDS privilege/nginx 검증. 별도 배포 범위 |

observation에서 추천 표준코드 full 관측이 필요 없으면 `RUN_RECOMMENDATION_STANDARD_CODE_OBSERVATION=false`로 선택합니다. housing effect와 welfare matrix 감사는 기본 auto이며 policy row가 `STANDARD_CODE_EFFECT_MIN_POLICY_ROWS` 미만이면 skipped일 수 있습니다. 강제 옵션 `RUN_HOUSING_STANDARD_CODE_EFFECT_AUDIT=true/RUN_WELFARE_STANDARD_CODE_MATRIX_AUDIT=true`는 승인된 데이터·호출/쓰기 영향을 확인한 경우에만 켭니다.

Nightly를 배포 프론트로 검증할 때는 `FRONTEND_E2E_MODE=deployed-origin`, `FRONTEND_PUBLIC_BASE_URL`을 지정합니다. 배포 번들의 dev-only 제외·admin 권한/opt-in은 유지하고 [프론트 사용자 흐름](../frontend/frontend-user-flow-runbook.md)의 flow_families별 범위를 봅니다. 단순 점검을 위해 repair/가입/browser write까지 자동 실행하지 않습니다.

cron 등록/수정·cleanup은 별도 상태 변경입니다. [cron runbook](nightly-ops-handoff-cron-runbook.md)과 [idempotent installer](../../deploy/smoke/install-nightly-ops-handoff-cron.sh)를 사용하되 설치 요청 없이 실행하지 않습니다. 현재 installer의 mint 기본값은 공통 도우미와 달리 `true`이므로, 운영 preview/설치에도 `ALLOW_ADMIN_JWT_MINT=false`를 명시해야 합니다.

## 입력과 artifact

기본 입력은 APP_BASE_URL·ADMIN_EMAIL/ADMIN_PASSWORD(또는 지원 credential file), SUMMARY_WINDOW_DAYS·TREND_WINDOW_DAYS_CSV·COLLECT_LIMIT·BREAKDOWN_LIMIT·KEEP_ARTIFACTS입니다. 설정 파일을 통째로 출력/source하지 않습니다.

baseline summary는 app_base_url·summary_window_days·artifact_dir·admin_dashboard_stdout·admin_collect_failures_stdout·admin_recommendation_breakdowns_stdout를 남깁니다. 보존 artifact는 sanitizer/redaction·cookie 제거·directory 700/file 600 경계를 유지합니다. summary가 다른 run의 latest를 가리키는지 시각·환경을 확인합니다.

## 읽을 지표

| Surface | 핵심 값 |
| --- | --- |
| Dashboard | generatedAt, collect/recommendation/search/notification, trend, unreadAlerts·staleUnread7d/14d·retryableFailedNotifications·terminalFailedNotifications |
| Collect | failedJobsInWindow·partialSuccessJobsInWindow·collectSourceLanes·latestRun·configEntries |
| Recommendation | trafficMixInWindow·latestBatchConcentration·youthOfficialFacetGroups·gov24FacetGroups |
| Observation | decision_class·attention_feed.items/count/keys/titles·warning 수·wrapper_promoted_alert_severity/message |
| 표준코드 coverage | users/non_example/example_smoke missing-all·users-with-any·safe_reconcile_candidate_rows·conflicting_value_gap_rows |
| 표준코드 추천 관측 | housing positive rule delta, welfare matrix positive scenarios/max delta, latest-batch adoption share·missing-all |

단순 미입력은 info/사용자 입력 유도이고 safe reconcile 또는 conflict가 있을 때만 조치 후보입니다. current-priority 이전 summary에 metric이 없으면 0으로 채우지 않고 “이전값 없음”으로 읽습니다. `flow_families`는 어떤 프론트 검증을 실행했는지의 범위이며 브라우저 전체 회귀를 보장하지 않습니다.

2026-05-18 서버 baseline 메모의 failed/partial/open-circuit 0·lane_count 10은 당시 결과입니다. 현재 runtime은 env override·lane 추가로 달라질 수 있습니다. health와 운영 화면 contract 성공은 feature correctness·품질·세션 내구성·데이터 보존까지 증명하지 않습니다. 실제 backlog 처리·hide/reconcile·promotion write는 [운영 점검](final-ops-closeout-checklist.md)과 해당 분야 runbook의 별도 승인 범위로 둡니다.
