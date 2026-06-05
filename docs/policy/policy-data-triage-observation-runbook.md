# policy data triage observation runbook

## 목적

이 문서는 `정책 링크 review`, `정책 중복 review`, `source contract tail` 을
raw audit 세 개로 다시 내려가기 전에 한 번에 읽는 compact handoff entrypoint입니다.

## 실행

```bash
bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh
```

## 요약 항목

- `duplicate_groups_youth`
- `duplicate_groups_bokjiro_local`
- `exact_duplicate_groups`
- `mirror_variant_groups`
- `date_or_contract_drift_groups`
- `active_visible_youth_total`
- `benefit_support_count`
- `announcement_recruitment_count`
- `program_event_count`
- `other_count`
- `decision_class`

## 해석

- `DUPLICATE_THEN_LINK_PRIORITY`
  - `YOUTH exact/mirror duplicate` 를 먼저 줄입니다.
  - 링크 queue는 `benefit_support` 와 `announcement/program` bucket을 병행합니다.
- `LINK_REVIEW_PRIORITY`
  - duplicate exact 후보는 잔량이 작고, 현재는 link review backlog를 먼저 줄이는 편이 맞습니다.
- `BACKLOG_STABLE`
  - 큰 drift는 없고 현재 cadence만 유지하면 됩니다.

## 다음 액션

- duplicate 쪽이 우선이면 [policy-duplicate-review-runbook.md](./policy-duplicate-review-runbook.md)
- link review 쪽이 우선이면 [policy-link-review-queue-runbook.md](./policy-link-review-queue-runbook.md)
- raw field 품질을 다시 보려면 [policy-data-quality-audit-runbook.md](./policy-data-quality-audit-runbook.md)
