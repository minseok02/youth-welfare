# 수집 문서 묶음

## 목적

`collect-*` 문서가 늘어나면서

- 현재 수집 경계가 무엇인지
- 실제 실행할 때 어떤 문서를 봐야 하는지
- 장애/기록은 어디에 남겨야 하는지

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 현재 코드/로컬 검증 기준

- [collect-current-state.md](./collect-current-state.md)
- [collect-operation-checklist.md](./collect-operation-checklist.md)
- [collect-ops.md](./collect-ops.md)

### 같이 보면 좋은 기준 문서

- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [testing.md](../core/testing.md)
- [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
- [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)
- [phase-plan.md](../phase-plan.md) ← 최신 상단 closeout 기록만 참고, 현재 collect 계약은 위 current-state/checklist를 우선

## 문서 역할

### 1. 현재 동작 기준

- [collect-current-state.md](./collect-current-state.md)

이 문서는

- collect entry
- source dispatch 구조
- 저장 레이어
- 정상/장애 해석

을 빠르게 보는 current-state 문서입니다.

### 2. 실행 체크리스트

- [collect-operation-checklist.md](./collect-operation-checklist.md)

이 문서는

- 어떤 collect 종류인지
- 실행 직후 무엇을 확인하는지
- 결과를 어떻게 해석하는지

를 짧게 따라가는 runbook 입니다.

### 3. 운영 기준

- [collect-ops.md](./collect-ops.md)

이 문서는

- `429`
- `0건`
- 부분 성공
- detail/gap-fill/backfill

같은 운영 해석 기준을 더 자세히 적어 둔 문서입니다.

### 4. 실행/장애 기록 템플릿

- [collect-incident-template.md](./collect-incident-template.md)

수집 실행이나 장애를 기록할 때 복사해서 쓰는 템플릿입니다.

### 5. collect closeout / quality audit

- [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
- [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)

이 문서군은

- `Gov24` collect closeout 뒤 coverage/raw shape/missing fact gap을 다시 볼 때
- retrieval/category baseline까지 함께 one-shot summary로 확인할 때

먼저 보는 bounded runtime runbook 입니다.

## 읽는 순서

### 현재 상태만 빨리 확인할 때

1. [collect-current-state.md](./collect-current-state.md)
2. [collect-operation-checklist.md](./collect-operation-checklist.md)

### 실제 실행 전후를 확인할 때

1. [collect-operation-checklist.md](./collect-operation-checklist.md)
2. [collect-ops.md](./collect-ops.md)
3. [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
4. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

### collect closeout / quality baseline을 다시 확인할 때

1. [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
2. [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)

### 기록을 남길 때

1. [collect-incident-template.md](./collect-incident-template.md)
2. [phase-plan.md](../phase-plan.md)
3. 필요하면 [troubleshooting-log.md](../core/troubleshooting-log.md)

## 요약

1. 현재 동작은 [collect-current-state.md](./collect-current-state.md) 부터 봅니다.
2. 실제 실행은 [collect-operation-checklist.md](./collect-operation-checklist.md) 기준으로 봅니다.
3. 운영 해석은 [collect-ops.md](./collect-ops.md) 에 더 자세히 적혀 있습니다.
4. collect closeout / quality baseline은 `Gov24 runtime audit` 과 `policy quality summary` runbook을 먼저 봅니다.
5. 기록은 [collect-incident-template.md](./collect-incident-template.md) 를 기준으로 남기고, 오래된 전환 로그는 `phase-plan` 을 보조 참고로만 봅니다.
