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
- `policy_duplicate_open_groups`
- `policy_duplicate_open_rows`
- `policy_link_open_reviews`
- `decision_class`

## 해석

- `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`
  - raw audit에는 duplicate/link 후보가 계속 보이지만 실제 운영 `OPEN` queue는 닫힌 상태입니다.
  - 이 경우 raw 후보는 source/data 품질 잔량으로 관찰하고, 새 `OPEN` queue가 생길 때만 review를 재개합니다.
- `DUPLICATE_THEN_LINK_PRIORITY`
  - `YOUTH exact/mirror duplicate` 를 먼저 줄입니다.
  - 링크 queue는 `benefit_support` 와 `announcement/program` bucket을 병행합니다.
- `LINK_REVIEW_PRIORITY`
  - duplicate exact 후보는 잔량이 작고, 현재는 link review backlog를 먼저 줄이는 편이 맞습니다.
- `BACKLOG_STABLE`
  - 큰 drift는 없고 현재 cadence만 유지하면 됩니다.

## 현재 server/RDS 기준

2026-06-09 최신 server/RDS observation 기준:

- `duplicate_groups_youth=82`
- `duplicate_groups_bokjiro_local=59`
- `exact_duplicate_groups=12`
- `mirror_variant_groups=16`
- `date_or_contract_drift_groups=41`
- `active_visible_youth_total=172`
- `benefit_support_count=33`
- `announcement_recruitment_count=13`
- `program_event_count=10`
- `other_count=111`
- `policy_duplicate_open_groups=0`
- `policy_duplicate_open_rows=0`
- `policy_link_open_reviews=0`
- `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`

따라서 현재 정책 데이터 backlog는 raw 후보 숫자가 남아도 운영자가 처리할 열린 queue가 없는 상태다.
새 중복/링크 review 작업은 `policy_duplicate_open_groups > 0` 또는 `policy_link_open_reviews > 0` 로 바뀔 때만 다시 연다.

## 다음 액션

- duplicate 쪽이 우선이면 [policy-duplicate-review-runbook.md](./policy-duplicate-review-runbook.md)
- link review 쪽이 우선이면 [policy-link-review-queue-runbook.md](./policy-link-review-queue-runbook.md)
- raw field 품질을 다시 보려면 [policy-data-quality-audit-runbook.md](./policy-data-quality-audit-runbook.md)
