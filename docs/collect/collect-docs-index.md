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
- [collect-detail-execution-contract.md](./collect-detail-execution-contract.md)
- [collect-operation-checklist.md](./collect-operation-checklist.md)
- [collect-ops.md](./collect-ops.md)
- [collect-external-api-smoke-runbook.md](./collect-external-api-smoke-runbook.md)
- [collect-governance-observation-runbook.md](./collect-governance-observation-runbook.md)
- [collect-source-resilience-audit-runbook.md](./collect-source-resilience-audit-runbook.md)
- [youth-regionless-audit-runbook.md](./youth-regionless-audit-runbook.md)
- `Collect external API DB smoke`: `bash deploy/smoke/run-local-collect-external-api-smoke.sh`
- `Gov24 async collect smoke`: `bash deploy/smoke/run-local-gov24-async-collect-smoke.sh`

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
- [collect-detail-execution-contract.md](./collect-detail-execution-contract.md)

이 문서는

- collect entry
- source dispatch 구조
- 저장 레이어
- 정상/장애 해석

을 빠르게 보는 current-state 문서입니다.

`collect-detail-execution-contract.md` 는 scheduled `collect/all` 의 list snapshot, forced detail, 요일별 rotation detail 순서와 호출량 경계를 고정합니다.

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

### 4. external API DB smoke

- [collect-external-api-smoke-runbook.md](./collect-external-api-smoke-runbook.md)

이 문서는

- `api_sync_logs`
- `raw_api_payloads`
- `collect_runtime_statuses`
- `collect_execution_locks`

를 외부 API 재호출 없이 compact하게 읽고, `NO_COLLECT_HISTORY`와 실제 실패를 분리하는 post-run smoke entrypoint입니다.

### 5. daily governance observation

- [collect-governance-observation-runbook.md](./collect-governance-observation-runbook.md)

이 문서는

- nightly/manual lane inventory
- latest run 상태
- partial/failure/open circuit 유무

를 compact하게 다시 읽는 operator entrypoint 입니다.

### 6. source resilience audit

- [collect-source-resilience-audit-runbook.md](./collect-source-resilience-audit-runbook.md)

이 문서는

- source별 retry/rate-limit/duplicate-run guard inventory
- fail-on-empty snapshot source
- recent failed job/open circuit와 함께 보는 resilience baseline

을 compact하게 다시 읽는 audit entrypoint 입니다.

### 7. youth regionless audit

- [youth-regionless-audit-runbook.md](./youth-regionless-audit-runbook.md)

이 문서는

- `YOUTH` source의 남은 `regionless` row가
- 누락 필드 문제인지
- 전국형 `zipCd` 해석 문제인지
- local suspicious subset이 얼마나 되는지

를 compact하게 다시 읽는 audit entrypoint 입니다.

### 8. 실행/장애 기록 템플릿

- [collect-incident-template.md](./collect-incident-template.md)

수집 실행이나 장애를 기록할 때 복사해서 쓰는 템플릿입니다.

### 9. collect closeout / quality audit

- [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
- [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)

이 문서군은

- `Gov24` collect closeout 뒤 coverage/raw shape/missing fact gap을 다시 볼 때
- retrieval/category baseline까지 함께 one-shot summary로 확인할 때

먼저 보는 bounded runtime runbook 입니다.

## 읽는 순서

### 현재 상태만 빨리 확인할 때

1. [collect-current-state.md](./collect-current-state.md)
2. [collect-detail-execution-contract.md](./collect-detail-execution-contract.md)
3. [collect-operation-checklist.md](./collect-operation-checklist.md)

### 실제 실행 전후를 확인할 때

1. [collect-operation-checklist.md](./collect-operation-checklist.md)
2. [collect-detail-execution-contract.md](./collect-detail-execution-contract.md)
3. [collect-ops.md](./collect-ops.md)
4. [collect-external-api-smoke-runbook.md](./collect-external-api-smoke-runbook.md)
5. [collect-governance-observation-runbook.md](./collect-governance-observation-runbook.md)
6. [collect-source-resilience-audit-runbook.md](./collect-source-resilience-audit-runbook.md)
7. [youth-regionless-audit-runbook.md](./youth-regionless-audit-runbook.md)
8. [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
9. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

### collect closeout / quality baseline을 다시 확인할 때

1. [policy-gov24-runtime-audit-runbook.md](../policy/policy-gov24-runtime-audit-runbook.md)
2. [policy-quality-summary-runbook.md](../policy/policy-quality-summary-runbook.md)

### 기록을 남길 때

1. [collect-incident-template.md](./collect-incident-template.md)
2. [phase-plan.md](../phase-plan.md)
3. 필요하면 [troubleshooting-log.md](../core/troubleshooting-log.md)

## 요약

1. 현재 동작은 [collect-current-state.md](./collect-current-state.md) 부터 봅니다.
2. scheduled detail 순서와 호출량은 [collect-detail-execution-contract.md](./collect-detail-execution-contract.md) 를 봅니다.
3. 실제 실행은 [collect-operation-checklist.md](./collect-operation-checklist.md) 기준으로 봅니다.
4. 외부 API 수집 DB 이력은 [collect-external-api-smoke-runbook.md](./collect-external-api-smoke-runbook.md) 로 먼저 봅니다.
5. 운영 해석은 [collect-ops.md](./collect-ops.md) 에 더 자세히 적혀 있습니다.
6. daily operator 관찰은 [collect-governance-observation-runbook.md](./collect-governance-observation-runbook.md) 를 먼저 봅니다.
7. source별 보호 장치 inventory는 [collect-source-resilience-audit-runbook.md](./collect-source-resilience-audit-runbook.md) 를 먼저 봅니다.
8. `YOUTH` 지역 품질 잔여 이슈는 [youth-regionless-audit-runbook.md](./youth-regionless-audit-runbook.md) 로 먼저 봅니다.
9. `Gov24` manual list 수집은 이제 동기 endpoint보다 `async trigger/status + run-local-gov24-async-collect-smoke.sh` 를 기본 운영 경로로 봅니다.
10. collect closeout / quality baseline은 `Gov24 runtime closeout/deferred inventory audit` 과 `policy quality summary` runbook을 먼저 봅니다.
11. 기록은 [collect-incident-template.md](./collect-incident-template.md) 를 기준으로 남기고, 오래된 전환 로그는 `phase-plan` 을 보조 참고로만 봅니다.
