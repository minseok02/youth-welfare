# recommendation review gate policy promotion checklist

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)
- [recommendation-review-gate-staleness-audit-runbook.md](./recommendation-review-gate-staleness-audit-runbook.md)
- [recommendation-review-gate-recent-window-audit-runbook.md](./recommendation-review-gate-recent-window-audit-runbook.md)
- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)

## 목적

이 문서는 recent-window review gate를

- 계속 supplemental candidate로 둘지
- explicit policy review로 올릴지
- 실제 primary gate 승격 검토를 시작할지

를 한 장으로 고정합니다.

이 문서는 code patch checklist가 아닙니다.
핵심은 **recent-window를 primary full latest batch gate 후보로 승격 검토할 최소 조건** 을 정하는 것입니다.

## 현재 기준선

현재 local/live 기준 recommendation review gate 해석은 아래와 같습니다.

- full latest batch review gate:
  - `DEFERRED_NON_REAL_LEADER_SIGNAL`
- review gate interpretation class:
  - `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- review gate operating mode:
  - `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`
- review gate policy candidate status:
  - `RECENT_WINDOW_POLICY_CANDIDATE`
- review gate policy candidate reason:
  - `PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR`
- review gate policy promotion status:
  - `REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`
- review gate policy promotion reason:
  - `RECENT_WINDOW_IS_A_CANDIDATE_BUT_PRIMARY_BASELINE_IS_STILL_ALL_TIME_LATEST`
- review gate policy promotion action status:
  - `KEEP_PRIMARY_BASELINE`
- review gate policy promotion action reason:
  - `PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW`
- review gate policy promotion readiness status:
  - `READY_FOR_BOUNDED_PROMOTION_REVIEW`
- review gate policy promotion readiness reason:
  - `EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET`
- review gate policy promotion execution status:
  - `AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`
- review gate policy promotion execution reason:
  - `READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING`
- review gate policy promotion approval status:
  - `PENDING_EXPLICIT_PROMOTION_APPROVAL`
- review gate policy promotion approval reason:
  - `EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED`
- review gate policy promotion approval criteria status:
  - `READY_FOR_EXPLICIT_PROMOTION_APPROVAL`
- review gate policy promotion approval criteria reason:
  - `PRIMARY_STALENESS_AND_RECENT_WINDOW_SIGNAL_CONFIRMED`
- review gate policy promotion approval decision status:
  - `AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`
- review gate policy promotion approval decision reason:
  - `APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED`
- review gate policy promotion approval record status:
  - `PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`
- review gate policy promotion approval record reason:
  - `APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`
- review gate policy promotion review run status:
  - `PENDING_BOUNDED_PROMOTION_REVIEW_RUN`
- review gate policy promotion review run reason:
  - `EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN`
- review gate policy promotion review run criteria status:
  - `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN`
- review gate policy promotion review run criteria reason:
  - `BOUNDED_REVIEW_RUN_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`
- review gate policy promotion review run approval status:
  - `PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
- review gate policy promotion review run approval reason:
  - `REVIEW_RUN_APPROVAL_DECISION_PENDING_BECAUSE_APPROVAL_RECORD_NOT_WRITTEN`
- review gate policy promotion review run approval criteria status:
  - `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
- review gate policy promotion review run approval criteria reason:
  - `REVIEW_RUN_APPROVAL_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`
- review gate policy promotion review run approval decision status:
  - `AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`
- review gate policy promotion review run approval decision reason:
  - `REVIEW_RUN_APPROVAL_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`
- review gate policy promotion review run approval record criteria status:
  - `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
- review gate policy promotion review run approval record criteria reason:
  - `REVIEW_RUN_APPROVAL_RECORD_PREREQUISITES_MET_BUT_RECORD_PENDING`
- review gate policy promotion review run approval record status:
  - `PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
- review gate policy promotion review run approval record reason:
  - `REVIEW_RUN_APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`

즉 current truth는:

1. recent-window는 이미 `policy candidate`
2. 하지만 아직 `automatic promotion` 단계는 아님
3. explicit policy review를 먼저 거쳐야 함
4. current action은 promotion 실행이 아니라 `KEEP_PRIMARY_BASELINE`
5. 다만 bounded promotion review를 열 prerequisite 자체는 이미 `READY_FOR_BOUNDED_PROMOTION_REVIEW`
6. current execution status는 `AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`
7. current approval status는 `PENDING_EXPLICIT_PROMOTION_APPROVAL`
8. current approval criteria status는 `READY_FOR_EXPLICIT_PROMOTION_APPROVAL`
9. current approval decision status는 `AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`
10. current approval record status는 `PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`
11. current review run status는 `PENDING_BOUNDED_PROMOTION_REVIEW_RUN`
12. current review run criteria status는 `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN`
13. current review run decision status는 `AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`
14. current review run approval criteria status는 `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
15. current review run approval decision status는 `AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`
16. current review run approval status는 `PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`
17. current review run approval record criteria status는 `READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`
18. current review run approval record status는 `PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`

입니다.

## 이 문서를 언제 쓰나

아래 중 하나가 생기면 이 문서를 봅니다.

1. admin summary/breakdowns 에서 `reviewGatePolicyCandidateStatus=RECENT_WINDOW_POLICY_CANDIDATE` 가 계속 유지될 때
2. latest artifact에서도 같은 candidate/promotion status가 반복될 때
3. draft 유지 이유가 더 이상 readiness 부족이 아니라 gate policy promotion pending 으로 좁혀졌을 때

반대로 아래면 아직 이 문서를 쓸 단계가 아닙니다.

1. `REAL_USER` readiness가 다시 닫힘
2. recent-window reading 자체가 흔들려 `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE` 가 유지되지 않음
3. primary blocker/staleness evidence가 다시 재현되지 않음

## 먼저 확인할 evidence

promotion review 전 최소한 아래 4개는 같이 봅니다.

1. admin summary/breakdowns
   - `recommendationReviewGate`
   - `reviewGateStaleness`
   - `recentWindowRecommendationReviewReading`
   - `reviewGatePolicyCandidateStatus`
   - `reviewGatePolicyPromotionStatus`
2. `run-local-recommendation-review-gate-blocker-audit.sh`
3. `run-local-recommendation-review-gate-staleness-audit.sh`
4. `run-local-recommendation-review-gate-recent-window-audit.sh`

한 줄로 줄이면:

- `primary gate가 왜 막히는가`
- `recent-window는 왜 clear인가`

를 둘 다 본 뒤에만 promotion review로 갑니다.

## 승격 검토 최소 조건

아래를 모두 만족해야 recent-window를 primary gate 후보로 **공식 검토** 할 수 있습니다.

### 1. readiness는 이미 열려 있어야 한다

- `READY_REAL_USER_TRAFFIC`
- `READY_REAL_USER_COHORT`

즉 readiness 자체가 다시 blocker면 promotion review를 하지 않습니다.

### 2. staleness evidence가 직접 보여야 한다

최소한 아래가 같이 보여야 합니다.

- `primaryReferenceMode=ALL_TIME_LATEST_PER_USER`
- `exampleTargetTop1Users > 0`
- `exampleTargetTop1Last24h = 0`
- `realUserLatestUsers > 0`
- `realUserTargetTop1Users = 0`

즉 primary gate가 all-time latest reference 때문에 historical example inertia를 읽고 있다는 근거가 직접 있어야 합니다.

### 3. recent-window clear가 실제 current-live signal을 가져야 한다

최소한 아래가 같이 보여야 합니다.

- `recentWindowRecommendationReviewReading=RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
- recent latest batch에서 target `2622 top1 = 0`
- recent latest batch leader가 real-user signal을 실제 포함

현재 local 기준 예시는:

- `recent_top1_leader_title=인천 청년도약기지(취업아카데미)`
- `recent_top1_leader_real_user_users=5`

즉 recent-window가 단순 empty/degenerate 상태가 아니라, **real-user current signal을 실제로 들고 있어야** 합니다.

### 4. current 운영 해석이 먼저 고정돼 있어야 한다

아래 값이 같이 유지돼야 합니다.

- `gate_action_class=READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES`
- `gate_policy_status=PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`
- `effective_operator_next_step=USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`

즉 current 운영 해석을 먼저 고정한 뒤에만 promotion review로 갑니다.

## 승격 검토를 막는 조건

아래 중 하나면 recent-window를 계속 candidate로만 둡니다.

1. recent-window clear가 한 번만 보이고 재현되지 않음
2. staleness evidence가 약하거나, example inertia가 current 데이터와 섞여 있어 분리 설명이 안 됨
3. recent-window leader가 real-user current signal을 충분히 대표하지 못함
4. zero-AI distribution / concentration / fallback 관찰이 recent-window 기준으로 과도하게 흔들림

즉 `candidate` 와 `promotion ready` 를 같은 뜻으로 쓰지 않습니다.

## explicit policy review에서 남길 최소 항목

1. 실행 시각
2. primary gate 값과 reason
3. staleness snapshot
4. recent-window reading
5. candidate status / reason
6. promotion status / reason
7. `keep primary baseline`
   또는
   `promote recent-window-aware gate for bounded trial`
8. 이번 단계에서 **안 바꾸는 것**
9. `reviewGatePolicyPromotionReadinessStatus/Reason`
10. `reviewGatePolicyPromotionExecutionStatus/Reason`
11. `reviewGatePolicyPromotionApprovalCriteriaStatus/Reason`
12. `reviewGatePolicyPromotionApprovalStatus/Reason`
13. `reviewGatePolicyPromotionApprovalDecisionStatus/Reason`
14. `reviewGatePolicyPromotionApprovalRecordStatus/Reason`
15. `reviewGatePolicyPromotionReviewRunStatus/Reason`
16. `reviewGatePolicyPromotionReviewRunCriteriaStatus/Reason`
17. `reviewGatePolicyPromotionReviewRunDecisionStatus/Reason`
18. `reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus/Reason`
19. `reviewGatePolicyPromotionReviewRunApprovalDecisionStatus/Reason`
20. `reviewGatePolicyPromotionReviewRunApprovalStatus/Reason`
21. `reviewGatePolicyPromotionReviewRunApprovalRecordStatus/Reason`

## 현재 추천 판단

현재 local truth에서는 아래처럼 읽는 편이 맞습니다.

1. recent-window는 이미 `RECENT_WINDOW_POLICY_CANDIDATE`
2. 하지만 promotion status는 `REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`
3. 따라서 지금 당장은 primary full latest batch gate를 코드에서 자동 교체하지 않음
4. 운영 문서와 handoff artifact에서는 `candidate / promotion / policy gate` 세 층을 같이 읽음

즉 현재 단계는 **승격 실행** 이 아니라 **승격 검토 기준 고정** 입니다.

## 한 줄 요약

recent-window review gate는 현재 **정책 후보까지는 올라왔지만**, primary all-time latest baseline을 자동으로 대체할 단계는 아니고, staleness evidence와 recent current-live signal을 같이 본 explicit policy review를 먼저 통과해야 합니다.
