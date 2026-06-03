# policy quality summary runbook

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

## 목적

`retrieval evaluation`, `quality gate`, `category audit` 는 각각 useful 하지만, 운영자가 지금 당장 알고 싶은 건 보통 아래 셋입니다.

1. retrieval baseline이 아직 유지되는가
2. gate가 현재 통과 상태인가
3. category distribution이 대략 어떤 모양인가

이 문서는 그 세 가지를 **한 번의 요약 실행**으로 확인하는 절차입니다.
즉 active 문서에서 말하는 `retrieval/category one-shot summary smoke` 는 이 wrapper와 이 문서를 뜻합니다.

daily operator가 full retrieval/category baseline 숫자를 다시 읽기보다
`지금 baseline이 healthy 인가`, `gate가 실패했는가`, `다음 문서를 어디로 열어야 하는가`
같은 compact 판단만 먼저 보고 싶다면 아래 observation wrapper를 먼저 씁니다.

```bash
bash deploy/smoke/run-local-policy-quality-observation-suite.sh
```

server/RDS에서 compact 결과를 nightly 로그로만 적재하려면 아래 wrapper를 씁니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-nightly-policy-quality-observation.sh
```

이 wrapper는 기존 `run-local-policy-quality-summary.sh` child artifact를 재사용해
`decision_class`, `operator_reading`, `next_action` 을 summary/json/note artifact로 다시 남깁니다.
즉 이 문서는 raw baseline 숫자를 읽는 runbook이고,
compact handoff entrypoint는 observation wrapper라고 구분하는 편이 맞습니다.

## 실행

```bash
bash deploy/smoke/run-local-policy-quality-summary.sh
```

이 wrapper는

- app health
- admin login
- retrieval evaluation
- retrieval gate
- category audit

를 현재 local smoke baseline 방식으로 한 번에 묶습니다.
가능하면 개별 admin endpoint를 수동으로 다시 치기 전에 이 wrapper 출력부터 기록합니다.

## 포함 범위

이 스크립트는 아래 admin 경로를 순서대로 호출합니다.

1. `POST /api/admin/policies/retrieval-evaluations/run`
2. `POST /api/admin/policies/retrieval-evaluations/gate`
3. `GET /api/admin/policies/category-audit`

## 출력 항목

- `dataset_key`
- `scenario_count`
- `top1_hit_rate`
- `top3_hit_rate`
- `branch_suggestion_hit_rate`
- `fallback_count`
- `empty_result_count`
- `quality_gate_passed`
- `quality_gate_failure_reasons`
- `category_total_policies`
- `category_searchable_policies`
- `category_searchable_ratio`
- `category_unified_count`
- `category_top_unified`
- `category_top_unified_total_share`
- `category_top_unified_searchable_coverage`
- `youth_broad_top_source`
- `youth_broad_top_dominant_unified`
- `youth_broad_top_dominant_share`

## 현재 기준선 (2026-05-15)

이 기준선은 실행 환경 snapshot에 따라 약간 바뀔 수 있지만, 현재 local mainline에선 아래 축을 우선 봅니다.

- retrieval
  - `top1_hit_rate=1.0`
  - `top3_hit_rate=1.0`
  - `branch_suggestion_hit_rate=1.0`
  - `empty_result_count=0`
- gate
  - `quality_gate_passed=true`
- category
  - `category_searchable_ratio` 가 비정상적으로 떨어지지 않는지
  - top unified category가 한쪽으로 급격히 쏠리지 않는지
  - youth broad dominant mapping이 기존 기대에서 크게 흔들리지 않는지

## 해석 순서

### 1. retrieval 먼저 본다

빠른 go/no-go:

- `top1_hit_rate`
- `top3_hit_rate`
- `branch_suggestion_hit_rate`
- `empty_result_count`

여기서 깨지면 category 분포보다 retrieval 쪽을 먼저 봅니다.

### 2. gate로 통과/미통과를 본다

- `quality_gate_passed=true` 면 baseline은 통과
- `quality_gate_failure_reasons` 가 비어 있지 않으면 어떤 축이 미달인지 바로 확인
- 가능하면 retrieval 단계의 `dataset_key`, `scenario_count` 와 같이 남깁니다.

### 3. category는 분포를 읽는다

category audit는 pass/fail command가 아니라 분포 read 경로입니다.

특히 볼 것:

- `category_searchable_ratio`
- `category_top_unified`
- `category_top_unified_total_share`
- `youth_broad_top_source`
- `youth_broad_top_dominant_unified`
- `youth_broad_top_dominant_share`

## 실행 후 남길 최소 기록

- wrapper 실행 시각
- `dataset_key`, `scenario_count`
- retrieval 4종 (`top1/top3/branch/empty_result`)
- `quality_gate_passed`, `quality_gate_failure_reasons`
- category 핵심 6종
- baseline과 달라진 숫자 / follow-up 필요 여부

## 언제 이 경로를 먼저 여나

### 검색/챗 retrieval이 의심될 때

이 스크립트를 먼저 돌립니다.

이유:

- evaluation
- gate
- category distribution

세 축을 한 번에 봐야, retrieval regression인지 category drift인지 빨리 구분됩니다.

### embeddings rebuild 뒤 확인할 때

`embeddings/rebuild` 후에는 이 스크립트로 baseline 회복 여부를 바로 봅니다.

### category audit 결과를 운영 보고서처럼 요약해 보고 싶을 때

raw JSON 대신 이 스크립트 출력만 기록해도 첫 triage는 충분합니다.

## 관련 문서

- bounded admin 절차 전체: [policy-admin-runtime-runbook.md](./policy-admin-runtime-runbook.md)
- compact operator handoff는 `bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- Gov24 runtime closeout/deferred inventory audit: [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- Gov24 deferred support-code inventory: [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
- 현재 전체 기준선: [current-state.md](../current-state.md)
