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
- [gov24-recommendation-audit-runbook.md](./gov24-recommendation-audit-runbook.md)
- [recommendation-ctr-readiness-runbook.md](./recommendation-ctr-readiness-runbook.md)
- [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md)
- [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)
- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
- [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
- [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)
- [recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md)
- [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md)
- [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md)
- [recommendation-saved-batch-gap-audit-runbook.md](./recommendation-saved-batch-gap-audit-runbook.md)
- [recommendation-fresh-saved-gap-audit-runbook.md](./recommendation-fresh-saved-gap-audit-runbook.md)
- [recommendation-ai-stage-gap-audit-runbook.md](./recommendation-ai-stage-gap-audit-runbook.md)
- [recommendation-ai-zero-cohort-audit-runbook.md](./recommendation-ai-zero-cohort-audit-runbook.md)
- [recommendation-ai-zero-contrast-audit-runbook.md](./recommendation-ai-zero-contrast-audit-runbook.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

현재 practical runtime wrapper:

- `KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh`
- `bash deploy/smoke/run-local-ctr-readiness-audit.sh`
- `bash deploy/smoke/run-local-recommendation-concentration-audit.sh`
- `bash deploy/smoke/run-local-real-user-readiness-check.sh`
- `bash deploy/smoke/run-local-no-priority-top1-sample.sh`
- `bash deploy/smoke/run-local-no-priority-candidate-audit.sh`
- `bash deploy/smoke/run-local-recommendation-signal-gap-audit.sh`
- `bash deploy/smoke/run-local-recommendation-region-window-audit.sh`
- `bash deploy/smoke/run-local-recommendation-latest-window-audit.sh`
- `bash deploy/smoke/run-local-recommendation-pipeline-lane-audit.sh`
- `bash deploy/smoke/run-local-recommendation-saved-batch-gap-audit.sh`
- `bash deploy/smoke/run-local-recommendation-fresh-saved-gap-audit.sh`
- `bash deploy/smoke/run-local-recommendation-ai-stage-gap-audit.sh`
- `bash deploy/smoke/run-local-recommendation-ai-zero-cohort-audit.sh`
- `bash deploy/smoke/run-local-recommendation-ai-zero-contrast-audit.sh`
- `bash deploy/smoke/run-local-gov24-recommend-surface-audit.sh`
- `bash deploy/smoke/run-local-gov24-recommend-score-audit.sh`
- `bash deploy/smoke/run-local-gov24-zero-ai-audit.sh`
- `bash deploy/smoke/run-local-gov24-top2-competitor-audit.sh`
- `bash deploy/smoke/run-local-gov24-null-ai-audit.sh`
- `bash deploy/smoke/run-local-gov24-null-ai-cause-audit.sh`
- `bash deploy/smoke/run-local-gov24-ai-status-audit.sh`
- `bash deploy/smoke/run-local-gov24-top2-rule-rank-audit.sh`
- `bash deploy/smoke/run-local-gov24-fresh-batch-audit.sh`
- `bash deploy/smoke/run-local-gov24-fresh-score-breakdown-audit.sh`
- `bash deploy/smoke/run-local-gov24-fresh-upstream-audit.sh`
- `bash deploy/smoke/run-local-gov24-housing-signal-smoke.sh`
- `bash deploy/smoke/run-local-gov24-education-signal-smoke.sh`
- `bash deploy/smoke/run-local-gov24-signal-suite.sh`
- `bash deploy/smoke/run-local-gov24-recommendation-suite.sh`

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

### 6. real-user baseline runbook

- [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)

이 문서는

- 운영 `REAL_USER` 표본이 recommendation gate를 다시 열 수 있는지
- `recommendationReviewGate` 를 어떤 순서로 읽어야 하는지
- 서버에서 어떤 read-only wrapper를 먼저 실행해야 하는지

를 정리한 운영 runbook 입니다.

### 7. reopen decision runbook

- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)

이 문서는

- gate 가 열린 뒤 어떤 종류의 recommendation 과제를 다시 열지
- `2736` 류 local 청년 정책 사례를 source 일반론이 아니라 정책군 사례로 어떻게 읽을지
- `local 신호 구조화 -> diversity/balancing -> direct tuning` 중 무엇이 먼저인지

를 정리한 decision runbook 입니다.

### 8. next lane brief

- [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)

이 문서는

- 지금 recommendation 을 다시 열면 어떤 lane 부터 고르는 편이 맞는지
- 왜 현재 권장안이 `local 신호 구조화` 인지
- `diversity/balancing` 과 `direct tuning` 을 왜 뒤로 미루는지

를 한 장으로 정리한 brief 입니다.

### 9. signal gap audit runbook

- [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)

이 문서는

- `2736` 류 local 정책군과 latest batch competitor 를 같이 읽는 법
- 어떤 structured signal 이 비어 있는지 먼저 좁히는 법
- source 전체 일반론이 아니라 정책군 단위 local signal 과제로 이어가는 법

을 정리한 bounded audit runbook 입니다.

### 10. region window audit runbook

- [recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md)

이 문서는

- `searchYouthRelevant` 와 local structured signal이 살아난 뒤에도
- target family가 왜 `NOT_IN_SQL_RETRIEVAL` 인지
- actual retrieval branch가 `REGION_CODE` / `SIDO` 중 무엇이고
- target family가 query 안에는 들어오는지, 들어오면 몇 위인지

를 읽는 bounded audit runbook 입니다.

### 11. latest window audit runbook

- [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md)

이 문서는 branch inclusion 뒤에도 target family가 latest `20-window` 밖에 남을 때 top 20 과 blocker `21~40위` 를 같이 읽고, latest ordering vs latest fetch size 문제를 좁히는 bounded audit runbook 입니다.

### 12. pipeline lane audit runbook

- [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md)

이 문서는 target family가 actual recommendation trace에서 `base/latest/filter/merged/saved` 중 어디까지 들어오는지 한 줄로 읽고, 다음 bounded fix를 retrieval/latest/scoring/saved 중 어디로 둘지 좁히는 runbook 입니다.

### 13. saved batch gap audit runbook

- [recommendation-saved-batch-gap-audit-runbook.md](./recommendation-saved-batch-gap-audit-runbook.md)

이 문서는 retrieval 경계가 닫힌 뒤에도 target family가 `SCORED_BUT_NOT_IN_SAVED_BATCH` 로 남을 때, current rerank trace와 latest saved top competitor를 같은 화면에서 비교해 saved window 직전 병목을 좁히는 runbook 입니다.

### 14. fresh saved gap audit runbook

- [recommendation-fresh-saved-gap-audit-runbook.md](./recommendation-fresh-saved-gap-audit-runbook.md)

이 문서는 `personal=true` fresh refresh를 강제로 다시 돌린 뒤 target family가 fresh persisted batch에도 빠지는지 확인해, stale latest batch와 fresh persisted gap을 분리하는 runbook 입니다.

### 15. ai stage gap audit runbook

- [recommendation-ai-stage-gap-audit-runbook.md](./recommendation-ai-stage-gap-audit-runbook.md)

이 문서는 fresh persisted batch까지 다시 만든 뒤에도 남는 `savedAi=0` / `NOT_REQUESTED` / `savedFinal-currentFinal delta` 를 읽어 AI stage 이후 차이를 좁히는 runbook 입니다.

### 16. ai zero cohort audit runbook

- [recommendation-ai-zero-cohort-audit-runbook.md](./recommendation-ai-zero-cohort-audit-runbook.md)

이 문서는 fresh top batch 안에서 `savedAi=0` 인 서비스를 묶어 봐서, `3257` 단일 이상치인지 반복 패턴인지 가르는 runbook 입니다.

### 17. ai zero contrast audit runbook

- [recommendation-ai-zero-contrast-audit-runbook.md](./recommendation-ai-zero-contrast-audit-runbook.md)

이 문서는 `savedAi=0` cohort를 같은 fresh top 안의 양수 AI peer와 대비해 category-wide 현상인지 개별 row 문제인지 가르는 runbook 입니다.

### 18. ai input contrast audit runbook

- [recommendation-ai-input-contrast-audit-runbook.md](./recommendation-ai-input-contrast-audit-runbook.md)

이 문서는 `savedAi=0` row와 같은 fresh top 안의 same source/category 양수 AI peer를 `keyword / lifeStage / INTEREST_THEME / KEYWORD / TARGET_GROUP / supportContent` 입력 신호 기준으로 나란히 비교하는 runbook 입니다.

Gov24가 추천에 "안 보이는지"보다 "몇 위에서 어떤 서비스로 뜨는지"를 보려면 `bash deploy/smoke/run-local-gov24-recommend-surface-audit.sh` 로 top1/top3/top5/top10 Gov24 share, rank별 source 분포, Gov24 상위 서비스 concentration을 같이 봅니다.

Gov24 추천 추적만 별도로 빠르게 따라가려면 [gov24-recommendation-audit-runbook.md](./gov24-recommendation-audit-runbook.md) 를 먼저 봅니다. 이 문서는 `surface -> score -> zero/null/ai_status -> fresh batch -> bounded signal suite` 순서를 한 번에 정리합니다.

같은 순서를 local baseline으로 한 번에 재확인하려면 `bash deploy/smoke/run-local-gov24-recommendation-suite.sh` 를 씁니다. 이 wrapper는 `recommend-surface-audit`, `recommend-score-audit`, `ai-status-audit`, `signal-suite` 를 순차 실행하고 stdout에 prefix를 붙여 출력합니다.

Gov24가 왜 top1/2에서 약한지 더 좁히려면 `bash deploy/smoke/run-local-gov24-recommend-score-audit.sh` 로 latest batch 기준 source별 `avg rule_weighted_score / avg ai_score / avg final_score` 를 top2/top10/rank별로 같이 봅니다.

서버처럼 `Gov24 top2 avg rule은 높은데 avg ai가 0처럼 보이는` 상황이 나오면, 먼저 그 값이 실제 0점인지 NULL 평균이 `coalesce(..., 0)` 로 보인 것인지 나눠 봐야 합니다. 이를 위해 `bash deploy/smoke/run-local-gov24-zero-ai-audit.sh` 와 `bash deploy/smoke/run-local-gov24-null-ai-audit.sh` 를 같이 돌려 `GOV24 + ai_score=0` 과 `GOV24 + ai_score is null` 분포를 분리해서 봅니다.

`NULL` 이 실제로 보이면 그다음은 `AI_TOP_N 밖이라 원래 AI를 안 받은 것인지`, 아니면 `top15 안인데 AI 결과가 비어 저장된 것인지`를 `bash deploy/smoke/run-local-gov24-null-ai-cause-audit.sh` 로 분리해서 봅니다.

그다음 실제로 왜 같은 사용자에서 Gov24가 `top2` 에만 머무는지 보려면 `bash deploy/smoke/run-local-gov24-top2-competitor-audit.sh` 로 `Gov24 top2 row` 와 같은 user의 `rank1 경쟁 후보` 를 나란히 비교합니다.

`ai_score` 기반 간접 추정 대신 새 상태값을 직접 보려면 `bash deploy/smoke/run-local-gov24-ai-status-audit.sh` 로 latest batch의 Gov24 `ai_status` (`NOT_REQUESTED`, `SCORED`, `PARTIAL_MISSING`, `CALL_FAILED`, `RULE_ONLY`) 분포와 `top2/top10` 상태 분포를 읽습니다.

만약 `top2 Gov24` 가 왜 `NOT_REQUESTED` 인지 보려면 `bash deploy/smoke/run-local-gov24-top2-rule-rank-audit.sh` 로 해당 row의 `final_rank` 와 `rule_rank`, 그리고 `AI_TOP_N=15` 포함 여부(`inside_ai_top_n/outside_ai_top_n`)를 직접 비교합니다.

특정 fresh user/batch를 놓고 Gov24 row와 실제 `top1/top2` rival을 같이 읽으려면 `bash deploy/smoke/run-local-gov24-fresh-batch-audit.sh` 를 씁니다. `TARGET_USER_KEY` 를 주면 그 사용자 최신 batch만 읽고, Gov24 `final_rank/rule_rank/ai_status/inside_ai_top_n` 과 `top2` rival score를 한 번에 비교합니다.

fresh batch에서 "rule/ai는 어느 정도인데 final이 왜 밀렸는지"를 더 직접 보려면 `bash deploy/smoke/run-local-gov24-fresh-score-breakdown-audit.sh` 를 씁니다. 이 스크립트는 저장된 `rule_weight_used / ai_weight_used` 와 batch `rule_max` 를 기준으로 `norm_rule`, `norm_ai`, `base_blend_score`, `actual_final`, `delta(actual-final - base-blend)` 를 같이 출력합니다.

그 다음 실제 upstream 입력을 확인하려면 `bash deploy/smoke/run-local-gov24-fresh-upstream-audit.sh` 를 씁니다. `TARGET_USER_KEY` 기준 fresh batch에서 Gov24 row와 top2 rival의 user context, summary labels, taxonomy terms, target groups, fact keys, raw tags를 한 번에 출력해 `4689` 처럼 rule이 낮은 서비스가 왜 그런 신호를 갖는지 직접 읽을 수 있습니다.

Gov24 source 전체가 구조적으로 억눌리는지 보려면 bounded smoke 두 개를 같이 봅니다. `bash deploy/smoke/run-local-gov24-housing-signal-smoke.sh` 는 `주거` 관심 + `HOUSING` priority fresh user에서 Gov24 주거 계열이 실제로 top2까지 올라오는지 확인하고, `bash deploy/smoke/run-local-gov24-education-signal-smoke.sh` 는 `교육·직업훈련` 관심 + `EDUCATION` priority + `경기도/안산시` fresh user에서 교육/장학금 Gov24가 실제로 top1/top2까지 올라오는지 확인합니다.

두 bounded smoke를 한 번에 재검증하려면 `bash deploy/smoke/run-local-gov24-signal-suite.sh` 를 씁니다. 이 wrapper는 `housing`, `education` 결과를 각각 prefix를 붙여 출력하고, `KEEP_ARTIFACTS=true` 면 각 suite artifact 경로도 따로 남깁니다.

주의: `ai_status` 는 `e7e591a` 이후 새로 생성된 batch에서만 직접 원인값으로 믿는 편이 맞습니다. migration backfill은 기존 `NULL ai_score` row를 전부 `NOT_REQUESTED` 로 채웠기 때문에, old batch에선 `rule_rank<=15 인데 NOT_REQUESTED` 같은 row가 backfill artifact일 수 있습니다. 이런 경우는 `bash deploy/smoke/run-local-gov24-top2-rule-rank-audit.sh` 를 같이 봐야 합니다.

### 10. replay 템플릿

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
4. 운영 `REAL_USER` 표본이면 [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)
5. reopen 종류를 고를 때 [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
6. 현재 권장 lane 을 바로 확인할 때 [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
7. local 신호 부족을 실제로 좁힐 때 [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)
8. latest/base/saved 경계를 같이 볼 때 [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md)
9. `pass_base=true`, `retain_base=false` 를 source rebalance 기준으로 좁힐 때 [recommendation-rebalance-audit-runbook.md](./recommendation-rebalance-audit-runbook.md)
10. [recommendation-pipeline.md](./recommendation-pipeline.md)
11. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

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
5. 운영 `REAL_USER` 표본 해석은 [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md) 로 따로 확인합니다.
6. gate 가 열린 뒤 어떤 종류의 recommendation 과제를 다시 열지 고를 때는 [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md) 를 봅니다.
7. 현재 권장 reopen lane 을 빠르게 확인할 때는 [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md) 를 봅니다.
8. local 정책군 signal gap 을 실제로 좁힐 때는 [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md) 와 `run-local-recommendation-signal-gap-audit.sh` 를 씁니다.
9. latest/base/saved 경계를 같이 읽을 때는 [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md) 를 봅니다.
10. `pass_base=true`, `retain_base=false` 인 retained ordering 병목은 [recommendation-rebalance-audit-runbook.md](./recommendation-rebalance-audit-runbook.md) 로 좁힙니다.
11. 구조 설명은 [recommendation-pipeline.md](./recommendation-pipeline.md) 에 더 자세히 적혀 있습니다.
12. replay/CTR/집중도 기록은 [recommendation-replay-template.md](./recommendation-replay-template.md) 또는 각 runbook의 최소 기록 항목을 기준으로 남기고, 요약 문서 갱신보다 evidence 기록을 먼저 합니다.
