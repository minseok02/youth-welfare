# recommendation review gate promotion approval record smoke runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md)
- [recommendation-ai-exclusion-latest-status-runbook.md](./recommendation-ai-exclusion-latest-status-runbook.md)
- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

## 목적

이 문서는 recommendation review gate promotion approval record의 실제 write path를 로컬에서 검증하는 bounded smoke입니다.

즉 다음 경계를 한 번에 확인합니다.

- baseline pending tuple 확인
- explicit approval record write
- approved tuple 전이 확인
- explicit approval record clear
- baseline pending tuple 복귀 확인

## 현재 단계 해석

현재 local 기준 review gate ladder는 approval record write 직전까지 prerequisite이 모두 충족된 상태입니다.

- `reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`
- `reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`

즉 이 smoke는 새 정책을 자동 승격하는 도구가 아니라, **explicit approval record를 실제로 쓰고 지웠을 때 admin/API surface가 expected approved tuple로 바뀌는지 확인하는 bounded write-path 검증**으로 읽는 편이 맞습니다.

## 기존 volume 주의사항

기존 로컬 PostgreSQL volume에서는 앱 재빌드만으로 새 table/grant가 적용되지 않을 수 있습니다.

relation missing 또는 `app_core_rw permission denied` 가 보이면 먼저 아래를 실행합니다.

```bash
bash deploy/postgres/apply-local-runtime-schema-patch.sh
```

## 기본 스크립트

```bash
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-admin-recommendation-review-gate-promotion-approval-record-smoke.sh
```

## 기대 baseline / approved / cleared tuple

baseline pending tuple:

- `reviewGatePolicyPromotionApprovalDecisionStatus=AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`
- `reviewGatePolicyPromotionApprovalRecordStatus=PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`
- `reviewGatePolicyPromotionReviewRunStatus=PENDING_BOUNDED_PROMOTION_REVIEW_RUN`
- `reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`

approved tuple:

- `reviewGatePolicyPromotionApprovalDecisionStatus=APPROVED_FOR_BOUNDED_PROMOTION_REVIEW`
- `reviewGatePolicyPromotionApprovalRecordStatus=EXPLICIT_PROMOTION_APPROVAL_RECORDED`
- `reviewGatePolicyPromotionReviewRunStatus=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN`
- `reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus=BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_COMPLETED`

cleared tuple:

- 위 baseline pending tuple과 동일해야 합니다.

## 읽는 법

- smoke가 approved tuple까지 갔다가 clear 뒤 baseline tuple로 복귀하면 write path는 정상입니다.
- approved tuple이 보이더라도 이 smoke는 cleanup에서 record를 지우므로, **current 운영 상태를 영구 승격하는 경로가 아닙니다.**
- 운영 판단은 여전히 [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md) 기준으로 따로 읽습니다.
