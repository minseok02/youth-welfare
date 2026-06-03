# Start

작업 시작 전에는 이 파일만 먼저 읽습니다.

주제 문서는 `docs/` 루트에 흩어두지 않고 `auth/`, `collect/`, `recommendation/`, `policy/`, `frontend/`, `postgres/`, `core/` 폴더로 정리했습니다.

## active 문서와 legacy/history 문서 경계

- 현재 작업 기준은 `project-spec.md`, `current-state.md`, `work-guide.md`, `phase-plan.md`, 그리고 각 문서군의 `*-current-state.md` 입니다.
- `history-docs-index.md`, `history/`, `archive/` 문서는 설계 배경과 과거 판단 기록입니다.
- 문서끼리 충돌하면 active 문서와 실제 코드를 우선합니다.
- 특히 실행 순서, 검증 방법, 현재 계약은 history 문서가 아니라 active 문서에서 확인합니다.

## 먼저 볼 파일

- [project-spec.md](./project-spec.md)
  프로젝트 기본 정보, 사용 기술, 버전, 실행 환경을 봅니다.

- [current-state.md](./current-state.md)
  지금 단계가 무엇인지, 무엇을 먼저 해야 하는지 봅니다.

- [work-guide.md](./work-guide.md)
  작업 순서와 문서/검증/Git 원칙을 봅니다.

- [phase-plan.md](./phase-plan.md)
  진행 상황, 완료 항목, 다음 작업을 봅니다.

- [github-workflow.md](./github-workflow.md)
  브랜치, 커밋, staging, PR 규칙을 봅니다.

- [deployment.md](./deployment.md)
  `EC2 + RDS` 전환을 실제로 준비하거나 실행할 때만 봅니다. 로컬 compose와 운영 compose/env 차이, RDS bootstrap, 운영 smoke 순서를 한 장으로 정리합니다.

- [core/security-hardening-current-state.md](./core/security-hardening-current-state.md)
  최근 보안 점검 follow-up과 운영 반영 상태를 볼 때 먼저 봅니다. dependency hardening, logout/session revoke, nginx edge 경계, admin forced logout smoke 입력값을 한 장으로 정리합니다.

- [performance/performance-docs-index.md](./performance/performance-docs-index.md)
  성능 baseline이나 최적화 전후 비교를 열 때 먼저 봅니다. current baseline, measurement plan, wrapper 진입점을 한 장으로 정리합니다.

## 지금 기준 한 줄 요약

- 최근 보안/운영 후속은 [core/security-hardening-current-state.md](./core/security-hardening-current-state.md) 를 먼저 봅니다.
- collect, recommendation, policy, auth 는 각 문서군의 `*-current-state.md` 와 `*-docs-index.md` 를 source of truth로 읽습니다.
- `start.md` 와 [current-state.md](./current-state.md) 는 긴 이력 저장소가 아니라 “무엇을 먼저 읽고 무엇을 먼저 실행할지”만 빠르게 찾는 진입 문서로 유지합니다.
- 긴 상태 설명, 수치 기준선, 과거 판단은 아래 주제 문서나 `history/` 로 내려 보냅니다.
- 지금 우선순위는 `현재 active 문서 확인 -> 필요한 smoke/test 실행 -> 코드 변경 -> 문서/검증/Git 정리` 순서입니다.

## recommendation 관찰 순서

- 운영 current truth만 compact하게 다시 보려면 `bash deploy/smoke/run-local-recommendation-observation-suite.sh`
- 운영 서버/RDS에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
- observation artifact latest 경로는 `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`, `latest-recommendation-observation-note.md`, `latest-recommendation-observation.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- 같은 latest snapshot 안에 `recommendation-standard-code-adoption.out` 도 남아서 latest batch 기준 표준코드 입력 사용자 비중을 바로 읽을 수 있습니다.
- collect governance를 compact하게 다시 보려면 `bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- 운영 서버/RDS에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- collect governance artifact latest 경로는 `tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt`, `latest-collect-governance-observation-note.md`, `latest-collect-governance-observation.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- auth/session baseline을 compact하게 다시 보려면 `bash deploy/smoke/run-local-auth-observation-suite.sh`
- 운영 서버/RDS에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
- auth observation artifact latest 경로는 `tmp/auth-observation/latest-auth-observation-summary.txt`, `latest-auth-observation-note.md`, `latest-auth-observation.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- ops baseline을 compact하게 다시 보려면 `bash deploy/smoke/run-local-ops-observation-suite.sh`
- 운영 서버/RDS에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
- ops observation artifact latest 경로는 `tmp/ops-observation/latest-ops-observation-summary.txt`, `latest-ops-observation-note.md`, `latest-ops-observation.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- 서버/RDS nightly wrapper는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-nightly-standard-code-observation.sh`
- nightly wrapper 기본 로그 루트는 `/var/log/youth-welfare/standard-code-observation` 이고 compact summary를 `nightly-summary-YYYY-MM-DD.log` 에 append 합니다.
- frontend baseline을 compact하게 다시 보려면 `bash deploy/smoke/run-local-frontend-observation-suite.sh`
- 운영 서버에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-frontend-observation-suite.sh`
- 위 기본 명령은 fresh e2e user/bootstrap을 먼저 태우고, `@dev-only`, `@admin-required` 케이스는 제외합니다. admin dashboard smoke까지 포함하려면 `RUN_FRONTEND_ADMIN_E2E=true` 를 명시합니다.
- frontend observation artifact latest 경로는 `tmp/frontend-observation/latest-frontend-observation-summary.txt`, `latest-frontend-observation-note.md`, `latest-frontend-observation.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- policy quality를 compact하게 다시 보려면 `bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- 운영 서버/RDS에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- policy quality artifact latest 경로는 `tmp/policy-quality-observation/latest-policy-quality-observation-summary.txt`, `latest-policy-quality-observation-note.md`, `latest-policy-quality-observation.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- baseline 유지와 recommendation 관찰을 한 번에 다시 보려면 `bash deploy/smoke/run-local-current-priority-suite.sh`
- 운영 서버/RDS에서는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-current-priority-suite.sh`
- current priority artifact latest 경로는 `tmp/current-priority-suite/latest-current-priority-summary.txt`, `latest-current-priority-summary.json` 을 먼저 봅니다. `KEEP_ARTIFACTS=false` 기본값에서도 이 stable snapshot은 남습니다.
- current priority latest summary/json 에는 `recommendation_standard_code_*`, `active_baseline_attention_feed_*`, `active_baseline_user_profile_standard_code_*` 가 같이 올라옵니다.
- active baseline standalone latest 경로는 `tmp/active-baseline-suite/latest-active-baseline-summary.txt`, `latest-active-baseline-summary.json` 입니다.
- active baseline latest summary/json 에는 `ops_attention_feed_*`, `ops_user_profile_standard_code_*`, `ops_recommendation_standard_code_*` 가 같이 올라옵니다.
- current priority는 같은 설정의 recent passed `active_baseline` latest가 TTL 안에 있으면 이를 재사용하고 `active_baseline_reused=true` 로 남깁니다.
- daily one-shot으로 recommendation 상태를 다시 보려면 `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh`
- `REAL_USER` readiness까지 같이 보려면 `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' INCLUDE_REAL_USER_READINESS=true bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh`
- overview artifact는 `tmp/recommendation-ai-exclusion-latest-overview/latest-overview-summary.txt`, `latest-overview-note.md`, `latest-overview.json` 을 먼저 봅니다.
- reviewer/handoff 관점으로 이번 closeout 범위를 먼저 읽으려면 [recommendation-pr-review-brief.md](./recommendation/recommendation-pr-review-brief.md)
- 현재 draft PR을 왜 유지하는지와 draft 해제 조건을 보려면 [recommendation-pr-draft-exit-checklist.md](./recommendation/recommendation-pr-draft-exit-checklist.md)
- merge 뒤 follow-up과 `REAL_USER` reopen 전 current 해석을 보려면 [recommendation-post-merge-followup-checklist.md](./recommendation/recommendation-post-merge-followup-checklist.md)
- recommendation PR lifecycle 전체 순서를 한눈에 보려면 [recommendation-docs-index.md](./recommendation/recommendation-docs-index.md) 의 `PR lifecycle order` 섹션을 먼저 봅니다.
- 지금 recommendation 상태만 빠르게 보려면 `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh`
- 운영 메모/핸드오프용 산출물이 필요하면 `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh`
- 자동 판정만 보려면 `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh`
- `REAL_USER` gate와 review gate를 실제로 같이 보려면 `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-real-user-exclusion-readiness-check.sh`
- mixed latest batch review gate가 왜 아직 `DEFERRED_NON_REAL_LEADER_SIGNAL` 인지 직접 보려면 `bash deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh`
- exact same profile에서 `EXAMPLE_SMOKE` 와 `REAL_USER` path가 왜 갈리는지 직접 보려면 `bash deploy/smoke/run-local-recommendation-same-profile-origin-differential-audit.sh`
- exact same profile 대표 example/real-user에서 `2622` 가 saved batch에만 남는지, SQL retrieval부터 없는지 직접 보려면 `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-recommendation-same-profile-path-differential-audit.sh`
- exact same profile 대표 example/real-user에 fresh personal refresh를 다시 태워 stale saved batch인지 확인하려면 `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-recommendation-same-profile-fresh-saved-differential-audit.sh`
- mixed leader `2622` 가 현재 flow보다 old example latest batch에 더 의존하는지 보려면 `bash deploy/smoke/run-local-recommendation-review-gate-staleness-audit.sh`
- stale batch를 제외한 recent 24h latest batch 기준 보조 review gate를 보려면 `bash deploy/smoke/run-local-recommendation-review-gate-recent-window-audit.sh`
- recent-window가 policy candidate인 상태에서 실제 승격 검토 기준을 보려면 [recommendation-review-gate-policy-promotion-checklist.md](./recommendation/recommendation-review-gate-policy-promotion-checklist.md)
- explicit promotion approval record write/clear path를 실제로 검증하려면 `ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-admin-recommendation-review-gate-promotion-approval-record-smoke.sh`
- 위 smoke에서 기존 로컬 PostgreSQL volume이 relation missing 또는 permission denied로 끊기면 먼저 `bash deploy/postgres/apply-local-runtime-schema-patch.sh`
- 상태값을 더 쪼개지 않고 bounded promotion review go/no-go만 한 번에 보려면 `ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-recommendation-bounded-promotion-review.sh`
- 재사용 가능한 `REAL_USER` cohort library(`housing / education / job / finance`)를 다시 시드하려면 `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-real-user-cohort-library-seed.sh`
- `REAL_USER` traffic/cohort가 실제로 생긴 뒤에는 [recommendation-real-user-recheck-checklist.md](./recommendation/recommendation-real-user-recheck-checklist.md) 순서대로 다시 확인합니다.
- strict gate(`FAIL_ON_LATEST_OBSERVATION_CHANGE=true`)가 fail 하더라도 `latest_drift_class=VOLATILE_ONLY_DRIFT` 와 `stable_baseline_changed=false` 면 stable baseline 회귀가 아니라 fresh window 흔들림으로 읽습니다.
- artifact 경로와 `generated_at` 은 UTC(`...Z`) 기준이라 KST 자정 이후 실행도 전날처럼 보일 수 있습니다. 최신 실행 여부는 `tmp/.../latest*` stable snapshot 갱신 시각으로 확인합니다.

## 필요할 때 보는 파일

- [architecture.md](./architecture.md)
- [srs-v2.10.md](core/srs-v2.10.md)
- [testing.md](core/testing.md)
- [local-validation-docs-index.md](core/local-validation-docs-index.md)
- [system-docs-index.md](core/system-docs-index.md)
- [ops-baseline-runbook.md](core/ops-baseline-runbook.md)
- [history-docs-index.md](./history-docs-index.md)
- [documentation-map.md](./documentation-map.md)

`history-docs-index.md` 는 배경이 필요할 때만 추가로 봅니다.
- [auth-docs-index.md](auth/auth-docs-index.md)
- [collect-docs-index.md](collect/collect-docs-index.md)
- [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- [policy-docs-index.md](policy/policy-docs-index.md)

## 폴더별 진입점

- [core/README.md](core/README.md)
- [auth/README.md](auth/README.md)
- [collect/README.md](collect/README.md)
- [recommendation/README.md](recommendation/README.md)
- [policy/README.md](policy/README.md)
- [frontend/README.md](frontend/README.md)
- [postgres/README.md](postgres/README.md)

## 주제별 현재 상태

- 인증 문서군 진입점: [auth-docs-index.md](auth/auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](collect/collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- 프론트 QA 문서군 진입점: [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- 정책 문서군 진입점: [policy-docs-index.md](policy/policy-docs-index.md)
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- 인증/세션: [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md)
- 수집: [collect-current-state.md](collect/collect-current-state.md)
- 추천: [recommendation-current-state.md](recommendation/recommendation-current-state.md)
- 정책 정규화: [policy-normalization-current-state.md](policy/policy-normalization-current-state.md)
- 다음 active track 우선순위: [policy-next-active-track-priority.md](policy/policy-next-active-track-priority.md)
- 로컬 closeout pending: [policy-local-closeout-pending-inventory.md](policy/policy-local-closeout-pending-inventory.md)
- `Gov24` bounded lane closeout: [policy-gov24-lane-closeout.md](policy/policy-gov24-lane-closeout.md)
- `Gov24` runtime closeout / blocked-deferred track: [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
- `Gov24` canonical promotion 설계: [policy-gov24-canonical-promotion-plan.md](policy/policy-gov24-canonical-promotion-plan.md)
- 신규 source 구조: [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md)

배경 이력이 필요할 때만:

- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)
