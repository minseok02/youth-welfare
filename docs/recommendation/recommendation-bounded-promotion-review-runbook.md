# recommendation bounded promotion review runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md)
- [recommendation-review-gate-promotion-approval-record-smoke-runbook.md](./recommendation-review-gate-promotion-approval-record-smoke-runbook.md)
- [recommendation-review-gate-recent-window-audit-runbook.md](./recommendation-review-gate-recent-window-audit-runbook.md)
- [recommendation-review-gate-staleness-audit-runbook.md](./recommendation-review-gate-staleness-audit-runbook.md)

## 목적

이 문서는 recommendation review gate의 bounded promotion review를 실제로 한 번 실행하는 one-shot wrapper 입니다.

이 wrapper는 상태값을 더 늘리지 않고 아래 3가지만 한 번에 확인합니다.

- explicit approval record write/clear path가 실제로 동작하는지
- recent-window signal이 historical `2622` dominance에서 벗어났는지
- historical example latest batch staleness가 여전히 primary blocker인지

## 기본 스크립트

```bash
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-recommendation-bounded-promotion-review.sh
```

기본 target은 `2622`, recent window는 `24h` 입니다.

```bash
TARGET_SERVICE_ID=2622 RECENT_WINDOW_HOURS=24 \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-recommendation-bounded-promotion-review.sh
```

## 현재 해석

현재 local 기준 bounded promotion review는 다음 두 근거를 같이 보는 bounded go/no-go 실행입니다.

- recent-window:
  - `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
- staleness:
  - `HISTORICAL_EXAMPLE_LATEST_BATCH_DOMINANCE`

즉 지금 단계에서 이 wrapper는 “정책을 바로 승격”하는 도구가 아니라, **recent-window candidate를 실제 promotion review 대상으로 볼 만한지 한 번에 압축해서 확인하는 실행 wrapper** 로 읽는 편이 맞습니다.

## 주요 출력

- `approval_record_smoke_passed`
- `recent_window_blocker_class`
- `staleness_blocker_class`
- `bounded_promotion_review_result_status`
- `bounded_promotion_review_result_reason`
- `bounded_promotion_review_recommended_action`

## 읽는 법

- `bounded_promotion_review_result_status=PASS_RECENT_WINDOW_POLICY_CANDIDATE`
  - approval record preflight가 정상이고
  - recent-window는 historical `2622` dominance에서 벗어났고
  - staleness audit은 historical example inertia를 여전히 primary blocker로 가리킨다는 뜻입니다.
- `bounded_promotion_review_result_status=INCONCLUSIVE_BOUNDED_PROMOTION_REVIEW`
  - recent-window 또는 staleness 신호가 기대 패턴과 달라 bounded promotion review 판단을 보류해야 한다는 뜻입니다.
- 이 wrapper는 explicit approval record를 영구히 남기지 않습니다.
  - approval-record smoke가 내부에서 write -> verify -> clear 를 수행하고 baseline으로 되돌립니다.
