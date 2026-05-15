# 추천 문서 묶음

## 목적

`recommendation-*` 문서가 흩어져 있어도

- 현재 추천 계약이 무엇인지
- 실제 로컬 검증 때 어떤 문서를 먼저 봐야 하는지
- replay / pipeline / 체크리스트가 어디에 있는지

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 현재 코드/로컬 검증 기준

- [recommendation-current-state.md](./recommendation-current-state.md)
- [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
- [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

### 같이 보면 좋은 기준 문서

- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [testing.md](../core/testing.md)
- [phase-plan.md](../phase-plan.md)

## 문서 역할

### 1. 현재 동작 기준

- [recommendation-current-state.md](./recommendation-current-state.md)

이 문서는

- retrieval
- scoring
- AI scoring
- reranking
- canonical projection bridge

를 빠르게 보는 current-state 문서입니다.

### 2. 실행 체크리스트

- [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)

이 문서는

- refresh/get/replay 전후 확인
- baseline을 무엇으로 보는지
- 결과를 어떻게 해석하는지

를 짧게 따라가는 runbook 입니다.

### 3. 파이프라인 구조

- [recommendation-pipeline.md](./recommendation-pipeline.md)

이 문서는

- 추천 단계 구조
- 서비스 책임 분리
- 저장/조회 경계

를 코드 기준으로 더 길게 설명한 구조 문서입니다.

### 4. CTR readiness runbook

- [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md)

이 문서는

- 현재 CTR 표본이 실제 튜닝 가능한 수준인지
- fallback/AI 클릭 분포가 어떤지
- weight bucket 분산이 어느 정도인지

를 한 번에 읽는 audit/runbook 입니다.

### 5. replay 템플릿

- [recommendation-replay-template.md](./recommendation-replay-template.md)

추천 replay 실험이나 비교 기록을 남길 때 복사해서 쓰는 템플릿입니다.

## 읽는 순서

### 현재 상태만 빨리 확인할 때

1. [recommendation-current-state.md](./recommendation-current-state.md)
2. [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)

### 실제 refresh/get/replay 를 확인할 때

1. [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
2. [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md)
3. [recommendation-pipeline.md](./recommendation-pipeline.md)
4. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

### 실험/비교 기록을 남길 때

1. [recommendation-replay-template.md](./recommendation-replay-template.md)
2. [phase-plan.md](../phase-plan.md)
3. 필요하면 [troubleshooting-log.md](../core/troubleshooting-log.md)

## 요약

1. 현재 동작은 [recommendation-current-state.md](./recommendation-current-state.md) 부터 봅니다.
2. 실제 실행은 [recommendation-operation-checklist.md](./recommendation-operation-checklist.md) 기준으로 봅니다.
3. CTR 튜닝 readiness는 [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md) 로 먼저 판단합니다.
4. 구조 설명은 [recommendation-pipeline.md](./recommendation-pipeline.md) 에 더 자세히 적혀 있습니다.
5. replay 기록은 [recommendation-replay-template.md](./recommendation-replay-template.md) 를 기준으로 남깁니다.
