# 추천 문서 묶음

## 목적

추천 문서군은 추천 로직 자체보다 관측, gate, AI exclusion, review, Gov24, 과거 audit runbook이 많이 누적되어 있습니다.

이 문서는 모든 파일을 같은 우선순위로 나열하지 않고, 지금 무엇을 먼저 읽고 어떤 문서는 필요할 때만 내려갈지 고정합니다.

## 현재 해석

- 현재 active main track은 recommendation 튜닝이 아니라 관찰 유지입니다.
- `KEEP_OBSERVING` 또는 `reopen_allowed=false` 이면 score, weight, prompt, source/category balancing을 열지 않습니다.
- `REAL_USER` 표본과 leader signal이 충분해지고 gate가 실제 review 가능 상태로 바뀔 때만 reopen 문서로 넘어갑니다.
- 오래된 `2026-05` local audit family는 설계/진단 이력으로 읽고, 현재 운영 판단은 [recommendation-current-state.md](./recommendation-current-state.md), [recommendation-observation-runbook.md](./recommendation-observation-runbook.md), latest artifact를 우선합니다.

## 먼저 볼 문서

1. [recommendation-current-state.md](./recommendation-current-state.md)
2. [recommendation-observation-runbook.md](./recommendation-observation-runbook.md)
3. [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
4. [recommendation-ai-reason-memo-contract.md](./recommendation-ai-reason-memo-contract.md)
5. [recommendation-pipeline.md](./recommendation-pipeline.md)
6. [recommendation-ai-latency-quality-first-plan-2026-07-17.md](./recommendation-ai-latency-quality-first-plan-2026-07-17.md)

## Daily 관측

운영 current truth를 compact하게 다시 볼 때:

```bash
bash deploy/smoke/run-local-recommendation-observation-suite.sh
```

운영 서버/RDS:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-observation-suite.sh
```

먼저 볼 artifact:

- `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`
- `tmp/recommendation-observation/latest-recommendation-observation-note.md`
- `tmp/recommendation-observation/latest-recommendation-observation.json`

관련 문서:

- [recommendation-observation-runbook.md](./recommendation-observation-runbook.md)
- [recommendation-standard-code-coverage-and-observation-closeout.md](./recommendation-standard-code-coverage-and-observation-closeout.md)
- [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

## Reopen 판단

추천 코드를 다시 열 수 있는지 볼 때:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-reopen-precheck.sh
```

읽는 순서:

1. [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
2. [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
3. [recommendation-primary-audience-exclusion-decision-memo.md](./recommendation-primary-audience-exclusion-decision-memo.md)

`reopen_allowed=false` 이면 아래 문서들은 진단 이력으로만 보고, 구현 작업으로 이어가지 않습니다.

단, refresh 대기 시간을 줄이되 추천 산식과 후보를 바꾸지 않는 UX/API 개선은 [recommendation-ai-latency-quality-first-plan-2026-07-17.md](./recommendation-ai-latency-quality-first-plan-2026-07-17.md) 기준으로 별도 검토합니다. 이 경로는 score, weight, prompt, source/category balancing reopen 이 아닙니다.

## PR / Handoff

PR이나 handoff 맥락에서 현재 recommendation 범위를 읽을 때:

1. [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
2. [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
3. [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
4. [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

## AI Exclusion

AI exclusion 상태를 daily overview로 볼 때:

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

먼저 볼 문서:

- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)
- [recommendation-ai-exclusion-latest-status-runbook.md](./recommendation-ai-exclusion-latest-status-runbook.md)
- [recommendation-ai-exclusion-latest-status-export-runbook.md](./recommendation-ai-exclusion-latest-status-export-runbook.md)
- [recommendation-ai-exclusion-latest-gate-runbook.md](./recommendation-ai-exclusion-latest-gate-runbook.md)

Baseline과 drift를 깊게 볼 때:

- [recommendation-ai-exclusion-suite-runbook.md](./recommendation-ai-exclusion-suite-runbook.md)
- [recommendation-ai-exclusion-snapshot-runbook.md](./recommendation-ai-exclusion-snapshot-runbook.md)
- [recommendation-ai-exclusion-snapshot-compare-runbook.md](./recommendation-ai-exclusion-snapshot-compare-runbook.md)
- [recommendation-ai-exclusion-drift-check-runbook.md](./recommendation-ai-exclusion-drift-check-runbook.md)
- [recommendation-ai-exclusion-volatility-audit-runbook.md](./recommendation-ai-exclusion-volatility-audit-runbook.md)
- [recommendation-ai-exclusion-stability-report-runbook.md](./recommendation-ai-exclusion-stability-report-runbook.md)
- [recommendation-ai-exclusion-drift-classify-runbook.md](./recommendation-ai-exclusion-drift-classify-runbook.md)
- [recommendation-ai-exclusion-baseline-report-runbook.md](./recommendation-ai-exclusion-baseline-report-runbook.md)
- [recommendation-ai-exclusion-baseline-refresh-runbook.md](./recommendation-ai-exclusion-baseline-refresh-runbook.md)
- [recommendation-ai-exclusion-baseline-refresh-compare-runbook.md](./recommendation-ai-exclusion-baseline-refresh-compare-runbook.md)
- [recommendation-ai-exclusion-baseline-refresh-drift-check-runbook.md](./recommendation-ai-exclusion-baseline-refresh-drift-check-runbook.md)

AI 입력/이유/zero cohort를 조사할 때:

- [recommendation-ai-input-contrast-audit-runbook.md](./recommendation-ai-input-contrast-audit-runbook.md)
- [recommendation-ai-prompt-line-contrast-audit-runbook.md](./recommendation-ai-prompt-line-contrast-audit-runbook.md)
- [recommendation-ai-reason-contrast-audit-runbook.md](./recommendation-ai-reason-contrast-audit-runbook.md)
- [recommendation-ai-reason-coverage-audit-runbook.md](./recommendation-ai-reason-coverage-audit-runbook.md)
- [recommendation-ai-upstream-reason-trace-audit-runbook.md](./recommendation-ai-upstream-reason-trace-audit-runbook.md)
- [recommendation-ai-stage-gap-audit-runbook.md](./recommendation-ai-stage-gap-audit-runbook.md)
- [recommendation-ai-zero-cohort-audit-runbook.md](./recommendation-ai-zero-cohort-audit-runbook.md)
- [recommendation-ai-zero-contrast-audit-runbook.md](./recommendation-ai-zero-contrast-audit-runbook.md)
- [recommendation-ai-zero-reason-bucket-audit-runbook.md](./recommendation-ai-zero-reason-bucket-audit-runbook.md)
- [recommendation-ai-zero-reason-distribution-audit-runbook.md](./recommendation-ai-zero-reason-distribution-audit-runbook.md)
- [recommendation-ai-zero-reason-cohort-compare-runbook.md](./recommendation-ai-zero-reason-cohort-compare-runbook.md)

## Review Gate

Review gate와 bounded promotion을 볼 때:

- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)
- [recommendation-review-gate-staleness-audit-runbook.md](./recommendation-review-gate-staleness-audit-runbook.md)
- [recommendation-review-gate-recent-window-audit-runbook.md](./recommendation-review-gate-recent-window-audit-runbook.md)
- [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md)
- [recommendation-review-gate-promotion-approval-record-smoke-runbook.md](./recommendation-review-gate-promotion-approval-record-smoke-runbook.md)
- [recommendation-bounded-promotion-review-runbook.md](./recommendation-bounded-promotion-review-runbook.md)

같은 프로필에서 example/real-user 차이를 볼 때:

- [recommendation-same-profile-origin-differential-audit-runbook.md](./recommendation-same-profile-origin-differential-audit-runbook.md)
- [recommendation-same-profile-path-differential-audit-runbook.md](./recommendation-same-profile-path-differential-audit-runbook.md)
- [recommendation-same-profile-fresh-saved-differential-audit-runbook.md](./recommendation-same-profile-fresh-saved-differential-audit-runbook.md)

## Gov24 / 지역 / 후보 창

Gov24 추천 노출과 점수를 볼 때:

- [gov24-recommendation-audit-runbook.md](./gov24-recommendation-audit-runbook.md)
- `bash deploy/smoke/run-local-gov24-recommendation-suite.sh`
- `bash deploy/smoke/run-local-gov24-signal-suite.sh`

지역 mismatch와 stale saved batch를 분리할 때:

- [recommendation-region-mismatch-repair-runbook.md](./recommendation-region-mismatch-repair-runbook.md)
- [recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md)
- [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md)
- [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md)
- [recommendation-saved-batch-gap-audit-runbook.md](./recommendation-saved-batch-gap-audit-runbook.md)
- [recommendation-fresh-saved-gap-audit-runbook.md](./recommendation-fresh-saved-gap-audit-runbook.md)
- [recommendation-rebalance-audit-runbook.md](./recommendation-rebalance-audit-runbook.md)
- [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)

## 품질 / UX 보조 신호

- [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md)
- [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md)
- [recommendation-no-priority-gap-audit-runbook.md](./recommendation-no-priority-gap-audit-runbook.md)
- [recommendation-real-user-cohort-library-manifest.md](./recommendation-real-user-cohort-library-manifest.md)
- [recommendation-real-user-exclusion-readiness-check-runbook.md](./recommendation-real-user-exclusion-readiness-check-runbook.md)
- [recommendation-similar-users-viewed-policy.md](./recommendation-similar-users-viewed-policy.md)
- [recommendation-similar-users-viewed-audit-runbook.md](./recommendation-similar-users-viewed-audit-runbook.md)

## 기록 템플릿

- [recommendation-replay-template.md](./recommendation-replay-template.md)

실험/비교 기록은 summary metric만 남기지 말고 wrapper, 입력 env, artifact path, baseline과 달라진 key를 같이 남깁니다.

## 같이 볼 문서

- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [testing.md](../core/testing.md)
- [phase-plan.md](../phase-plan.md)

## 요약

1. 현재 상태는 [recommendation-current-state.md](./recommendation-current-state.md) 부터 봅니다.
2. daily 관측은 [recommendation-observation-runbook.md](./recommendation-observation-runbook.md) 와 `run-local-recommendation-observation-suite.sh` 를 봅니다.
3. AI exclusion은 [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md) 를 먼저 봅니다.
4. `reopen_allowed=false` 이면 튜닝 문서로 내려가지 않고 관찰 유지로 handoff 합니다.
5. 세부 audit runbook은 원인 조사용입니다. current-state와 latest artifact가 항상 우선입니다.
