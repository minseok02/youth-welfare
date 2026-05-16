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
- [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

현재 practical runtime wrapper:

- `KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh`
- `bash deploy/smoke/run-local-ctr-readiness-audit.sh`
- `bash deploy/smoke/run-local-recommendation-concentration-audit.sh`
- `bash deploy/smoke/run-local-no-priority-top1-sample.sh`
- `bash deploy/smoke/run-local-no-priority-candidate-audit.sh`
- `bash deploy/smoke/run-local-gov24-recommend-surface-audit.sh`
- `bash deploy/smoke/run-local-gov24-recommend-score-audit.sh`
- `bash deploy/smoke/run-local-gov24-zero-ai-audit.sh`
- `bash deploy/smoke/run-local-gov24-top2-competitor-audit.sh`
- `bash deploy/smoke/run-local-gov24-null-ai-audit.sh`

### 같이 보면 좋은 기준 문서

- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [testing.md](../core/testing.md)
- [phase-plan.md](../phase-plan.md) ← 최신 상단 closeout 기록만 참고, 현재 계약은 위 current-state/runbook 문서를 우선

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

### 5. concentration audit runbook

- [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md)

이 문서는

- 최신 저장 추천 batch의 top1 집중도
- 같은 서비스 반복 노출
- `HAS_PRIORITY` / `NO_PRIORITY` 차이
- 우선순위 반영 여부와 편중이 동시에 어떤 상태인지

를 한 번에 읽는 audit/runbook 입니다.

새 no-priority 코드 변경이 **다음 refresh 표본**에 실제로 먹는지 보려면 `bash deploy/smoke/run-local-no-priority-top1-sample.sh` 를 같이 봅니다.

후보군 자체가 왜 `BOKJIRO_CENTRAL` / `GOV24` / `BOKJIRO_LOCAL` 순으로 들어오는지 보려면 `bash deploy/smoke/run-local-no-priority-candidate-audit.sh` 로 top5 source/category/rule/AI 분포를 먼저 봅니다.

Gov24가 추천에 "안 보이는지"보다 "몇 위에서 어떤 서비스로 뜨는지"를 보려면 `bash deploy/smoke/run-local-gov24-recommend-surface-audit.sh` 로 top1/top3/top5/top10 Gov24 share, rank별 source 분포, Gov24 상위 서비스 concentration을 같이 봅니다.

Gov24가 왜 top1/2에서 약한지 더 좁히려면 `bash deploy/smoke/run-local-gov24-recommend-score-audit.sh` 로 latest batch 기준 source별 `avg rule_weighted_score / avg ai_score / avg final_score` 를 top2/top10/rank별로 같이 봅니다.

서버처럼 `Gov24 top2 avg rule은 높은데 avg ai가 0처럼 보이는` 상황이 나오면, 먼저 그 값이 실제 0점인지 NULL 평균이 `coalesce(..., 0)` 로 보인 것인지 나눠 봐야 합니다. 이를 위해 `bash deploy/smoke/run-local-gov24-zero-ai-audit.sh` 와 `bash deploy/smoke/run-local-gov24-null-ai-audit.sh` 를 같이 돌려 `GOV24 + ai_score=0` 과 `GOV24 + ai_score is null` 분포를 분리해서 봅니다.

그다음 실제로 왜 같은 사용자에서 Gov24가 `top2` 에만 머무는지 보려면 `bash deploy/smoke/run-local-gov24-top2-competitor-audit.sh` 로 `Gov24 top2 row` 와 같은 user의 `rank1 경쟁 후보` 를 나란히 비교합니다.

### 6. replay 템플릿

- [recommendation-replay-template.md](./recommendation-replay-template.md)

추천 replay 실험이나 비교 기록을 남길 때 복사해서 쓰는 템플릿입니다.

## 읽는 순서

### 현재 상태만 빨리 확인할 때

1. [recommendation-current-state.md](./recommendation-current-state.md)
2. [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)

### 실제 refresh/get/replay 를 확인할 때

1. [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
2. [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md)
3. [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md)
4. [recommendation-pipeline.md](./recommendation-pipeline.md)
5. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

### 실험/비교 기록을 남길 때

1. [recommendation-replay-template.md](./recommendation-replay-template.md)
2. wrapper/command, summary metric, fingerprint relation, clicked service concentration 같은 증거를 먼저 채웁니다.
3. active 기준선 반영이 필요할 때만 [phase-plan.md](../phase-plan.md)
4. drift 원인까지 기록해야 할 때 [troubleshooting-log.md](../core/troubleshooting-log.md)

## 요약

1. 현재 동작은 [recommendation-current-state.md](./recommendation-current-state.md) 부터 봅니다.
2. 실제 실행은 [recommendation-operation-checklist.md](./recommendation-operation-checklist.md) 기준으로 봅니다.
3. CTR 튜닝 readiness는 [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md) 로 먼저 판단합니다.
4. 추천 편중과 우선순위 반영 상태는 [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md) 로 따로 확인합니다.
5. 구조 설명은 [recommendation-pipeline.md](./recommendation-pipeline.md) 에 더 자세히 적혀 있습니다.
6. replay/CTR/집중도 기록은 [recommendation-replay-template.md](./recommendation-replay-template.md) 또는 각 runbook의 최소 기록 항목을 기준으로 남기고, 요약 문서 갱신보다 evidence 기록을 먼저 합니다.
