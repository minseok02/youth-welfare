# recommendation PR review brief

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 PR reviewer가 이번 recommendation / Gov24 closeout 변경을

- 어떤 덩어리로 보면 되는지
- 어디부터 읽으면 되는지
- 지금 무엇이 아직 blocker인지

를 짧게 파악하게 정리합니다.

## 이 PR에서 먼저 볼 세 덩어리

### 1. Gov24 canonical promotion closeout

핵심 질문:

- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE_TOKEN`
- `GOV24_BENEFIT_TYPE_TOKEN`

이 세 축이 recommendation read-model, admin diagnostics/facet, presentation, AI prompt까지 term-first로 일관되게 연결됐는가

우선 파일:

- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-gov24-canonical-promotion-plan.md](../policy/policy-gov24-canonical-promotion-plan.md)
- [recommendation-current-state.md](./recommendation-current-state.md)

### 2. recommendation AI exclusion observability

핵심 질문:

- latest baseline을 daily one-shot으로 다시 읽을 수 있는가
- stable baseline과 volatile observation을 분리해서 해석하는가
- `REAL_USER` gate가 열리면 같은 entrypoint로 재확인할 수 있는가
- full latest batch review gate와 recent-window supplemental gate를 같이 읽도록 정리됐는가

우선 파일:

- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)
- [recommendation-ai-exclusion-latest-status-runbook.md](./recommendation-ai-exclusion-latest-status-runbook.md)
- [recommendation-real-user-exclusion-readiness-check-runbook.md](./recommendation-real-user-exclusion-readiness-check-runbook.md)
- [recommendation-review-gate-staleness-audit-runbook.md](./recommendation-review-gate-staleness-audit-runbook.md)
- [recommendation-review-gate-recent-window-audit-runbook.md](./recommendation-review-gate-recent-window-audit-runbook.md)
- [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

### 3. active 문서 / handoff hygiene

핵심 질문:

- active 문서가 지금 truth와 맞는가
- 로컬 예시 자격과 example 계정을 public-facing 진입점에서 분리했는가
- troubleshooting에 왜 그렇게 했는지 남아 있는가
- repo-wide grep에 아직 남는 credential-like 문자열이 active 누락인지, intentional smoke/test/history scope인지 구분돼 있는가

우선 파일:

- [start.md](../start.md)
- [current-state.md](../current-state.md)
- [troubleshooting-log.md](../core/troubleshooting-log.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)

review hint:

- 현재 남아 있는 `admin@example.com`, `password123!`, `Password123!`, `welfare1234!` 류 문자열은 active/current/support/handoff 누락보다 `phase-plan` / `troubleshooting-log` 이력, `deploy/smoke` local 기본값, `backend/src/test/**`, `application-integration.yml` fixture에 집중돼 있다
- 즉 reviewer는 “문서 hygiene 누락”과 “local smoke/test fixture 계약”을 같은 문제로 보지 않는 편이 맞다

## reviewer가 먼저 확인할 현재 판정

- PR review readiness status: `REVIEWER_READY`
- PR draft maintenance status: `DRAFT_MAINTAINED_BY_POLICY_GATE`
- current local recommendation latest status: `VOLATILE_ONLY_DRIFT`
- basic latest gate: `PASS`
- strict latest gate: `LATEST_OBSERVATION_CHANGED`
- full latest batch review blocker: `DEFERRED_NON_REAL_LEADER_SIGNAL`
- full latest batch reading: `historical example latest batch dominance`
- recent-window supplemental reading: `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
- review gate interpretation class: `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- review gate operating mode: `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`
- gate action class: `READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES`
- gate policy status: `PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`
- gate policy reason: `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- review gate policy candidate status: `RECENT_WINDOW_POLICY_CANDIDATE`
- review gate policy candidate reason: `PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR`
- review gate policy promotion status: `REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`
- review gate policy promotion reason: `RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST`
- review gate policy promotion action status: `KEEP_PRIMARY_BASELINE`
- review gate policy promotion action reason: `PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW`
- review gate policy promotion readiness status: `READY_FOR_BOUNDED_PROMOTION_REVIEW`
- review gate policy promotion readiness reason: `EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET`
- review gate policy promotion execution status: `AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`
- review gate policy promotion execution reason: `READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING`
- review gate policy promotion approval criteria status: `READY_FOR_EXPLICIT_PROMOTION_APPROVAL`
- review gate policy promotion approval criteria reason: `PRIMARY_STALENESS_AND_RECENT_WINDOW_SIGNAL_CONFIRMED`
- review gate policy promotion approval status: `PENDING_EXPLICIT_PROMOTION_APPROVAL`
- review gate policy promotion approval reason: `EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED`
- review gate policy promotion approval decision status: `AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`
- review gate policy promotion approval decision reason: `APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED`
- review gate policy promotion approval record status: `PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`
- review gate policy promotion approval record reason: `APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`
- review gate policy promotion review run status: `PENDING_BOUNDED_PROMOTION_REVIEW_RUN`
- review gate policy promotion review run reason: `EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN`
- review gate policy promotion review run criteria status: `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN`
- review gate policy promotion review run criteria reason: `BOUNDED_REVIEW_RUN_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`
- review gate policy promotion review run decision status: `AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`
- review gate policy promotion review run decision reason: `REVIEW_RUN_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`
- review gate policy promotion review run approval criteria status: `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
- review gate policy promotion review run approval criteria reason: `REVIEW_RUN_APPROVAL_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`
- review gate policy promotion review run approval decision status: `AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`
- review gate policy promotion review run approval decision reason: `REVIEW_RUN_APPROVAL_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`
- review gate policy promotion review run approval status: `PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
- review gate policy promotion review run approval reason: `REVIEW_RUN_APPROVAL_DECISION_PENDING_BECAUSE_APPROVAL_RECORD_NOT_WRITTEN`
- review gate policy promotion review run approval record criteria status: `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
- review gate policy promotion review run approval record criteria reason: `REVIEW_RUN_APPROVAL_RECORD_PREREQUISITES_MET_BUT_RECORD_PENDING`
- review gate policy promotion review run approval record status: `PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
- review gate policy promotion review run approval record reason: `REVIEW_RUN_APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`
- operator next step: `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`

explicit promotion review 기준은 [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md) 를 같이 봅니다.

즉 현재 PR은 **review는 바로 가능한 closeout + observability + handoff 정리 PR** 이지만, undraft는 아직 policy gate가 막는 상태로 읽는 편이 맞습니다.

## reviewer가 깊게 파지 않아도 되는 것

- historical `3257/3209` 사례의 모든 세부 이력
- `history/` 문서군의 과거 drift
- `REAL_USER` traffic이 아직 없는 상태에서의 제품 완화 결정

이 PR은 그 이전 단계인 “현재 기준선이 무엇인지 다시 읽게 만드는 작업”에 더 가깝습니다.

## reviewer가 남길 만한 질문

1. active 문서가 실제 wrapper/output과 아직 어긋나는 곳이 없는가
2. `latest-overview` / `latest-status` / `latest-gate` 의 역할 경계가 충분히 분명한가
3. `REAL_USER` 표본이 생긴 뒤 다시 열 순서가 문서만 보고 바로 따라갈 수 있는가

## 한 줄 요약

이 PR은 **Gov24 canonical promotion closeout + recommendation AI exclusion observability + active handoff hygiene** 를 묶은 closeout PR이고, 현재 남은 blocker는 코드 결함보다 **historical full latest batch review gate와 current recent-window signal을 분리해서 읽는 운영 해석 경계** 입니다.
