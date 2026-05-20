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

## 지금 기준 한 줄 요약

- `2026-05-19` 기준 로컬 full collect baseline도 다시 닫혔습니다. `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`, `GOV24`, `GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS`, `YOUTH_DETAILS`, `BOKJIRO_DETAIL_REFRESH` 를 실제로 다시 태워 hard failure 없이 끝냈고, `run-local-ops-baseline-suite.sh` 도 같은 로컬 runtime에서 다시 통과했습니다.
- `2026-05-18` 기준 YOUTH/Gov24 신호는 `raw -> fact/token -> admin diagnostics -> detail read-only -> policy card compact badge -> admin facet` 까지 닫혔습니다.
- `2026-05-18` 기준 `collect/runtime governance` 도 `lane inventory -> latestRun -> config summary` 까지 닫혔습니다.
- `2026-05-19` 기준 recommendation 의 `savedAi=0` 이슈는 구조 버그보다 AI exclusion evidence 정리 단계로 넘어갔고, 현재 one-shot 기준선은 `drift_class=VOLATILE_ONLY_DRIFT`, `recommended_reading=READ_LATEST_AS_VOLATILE_OBSERVATION` 입니다.
- recommendation, `Gov24 canonical promotion`, collect/runtime governance 는 모두 로컬 closeout 뒤 기준선 유지 단계이고, 지금 다음 관찰 track은 recommendation `AI exclusion baseline` 재확인과 `REAL_USER` cohort readiness gate가 열리는지 보는 것입니다.
- 메일 발송은 SMTP 기반이며 `MAIL_*` 설정을 우선 사용합니다. provider-neutral 경계는 닫혔고, 다음 운영 선택지는 Gmail 유지보다 AWS SES SMTP 전환 검토가 우선입니다.
- `2026-05-20` KST 기준 local generic-domain signup 기반 `REAL_USER` 표본은 이제 distributed baseline `30명` + targeted cohort library `50명` 으로 총 `80명`까지 확보됐습니다. live readiness는 계속 `READY_REAL_USER_TRAFFIC / READY_REAL_USER_COHORT` 이고, real-user-only concentration은 `top1_leader=드림나래(인천청년 면접복장 지원)`, `top1_share_pct=7.50`, `concentration_readiness=NO_PRIORITY_DOMINANT` 로 더 분산됐습니다. real-user latest top-N zero-AI 분포는 계속 `zero_ai_rows=53`, `zero_ai_users=17`, `zero_ai_reason_buckets=AUDIENCE_MISMATCH:8,INCOME_MISMATCH:11,OTHER:13,REGION_MISMATCH:1,STUDENT_AUDIENCE_MISMATCH:20` 입니다.
- 같은 `2026-05-20` 기준 mixed latest batch review gate는 여전히 `recommendation_review_gate=DEFERRED_NON_REAL_LEADER_SIGNAL` 이고, mixed leader `2622(청년월세 지원사업)` 는 real-user 쪽 `top1/top3/top5/top10/any-rank` 에 계속 `0회` 입니다. 더 중요한 건 example-heavy leader와 exact same persona(`인천광역시/중구/income=5/미취업/1인 가구`)를 generic-domain `REAL_USER` 로 10명 더 시드해도 path가 열리지 않았다는 점입니다. 즉 current blocker는 표본 수 부족보다 **mixed batch non-real dominance with no real-user path** 로 읽는 편이 맞고, 다음 행동도 “housing-like 표본을 더 많이 만든다”보다 “same-profile example vs real-user differential을 더 추적한다” 쪽이 더 정확합니다.

## recommendation 관찰 순서

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
- 재사용 가능한 `REAL_USER` cohort library(`housing / education / job / finance`)를 다시 시드하려면 `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-real-user-cohort-library-seed.sh`
- `REAL_USER` traffic/cohort가 실제로 생긴 뒤에는 [recommendation-real-user-recheck-checklist.md](./recommendation/recommendation-real-user-recheck-checklist.md) 순서대로 다시 확인합니다.
- strict gate(`FAIL_ON_LATEST_OBSERVATION_CHANGE=true`)가 fail 하더라도 `latest_drift_class=VOLATILE_ONLY_DRIFT` 와 `stable_baseline_changed=false` 면 stable baseline 회귀가 아니라 fresh window 흔들림으로 읽습니다.
- artifact 경로와 `generated_at` 은 UTC(`...Z`) 기준이라 KST 자정 이후 실행도 전날처럼 보일 수 있습니다. 최신 실행 여부는 `tmp/.../latest` symlink 이동으로 확인합니다.

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
- `Gov24` runtime closeout / blocked-deferred track: [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
- `Gov24` canonical promotion 설계: [policy-gov24-canonical-promotion-plan.md](policy/policy-gov24-canonical-promotion-plan.md)
- 신규 source 구조: [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md)

배경 이력이 필요할 때만:

- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)
