# recommendation saved batch gap audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 target family가

- `retain_base=true`
- `merged=true` 또는 `post=true`

인데도

- `saved=false`
- `dropStage=SCORED_BUT_NOT_IN_SAVED_BATCH`

로 남을 때, 현재 rerank/current trace와 latest saved batch를 같은 화면에서 비교하는 bounded audit runbook 입니다.

즉 이 문서는 retrieval 병목이 닫힌 뒤 남는
**saved batch 진입 전 단계**
를 읽기 위한 것입니다.

## 언제 이 문서를 쓰나

아래 순서가 이미 끝났을 때 씁니다.

1. `region-window audit` 로 branch inclusion 확인
2. `pipeline lane audit` 로 `pass_base=true`, `retain_base=true` 확인
3. target이 여전히 `saved=false`, `SCORED_BUT_NOT_IN_SAVED_BATCH` 인 경우

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-saved-batch-gap-audit.sh
```

기본값:

- `TARGET_SERVICE_IDS_CSV` 를 비우면 current latest batch user context에서
  region-matched `BOKJIRO_LOCAL` 청년/생활지원 family를 자동 선택
- `TOP_SAVED_LIMIT=10`

## 출력

### 1. metric

- `target_user_key`
- `target_service_ids_csv`
- `top_saved_ids_csv`
- `base_candidate_count`
- `latest_candidate_count`
- `filtered_base_candidate_count`
- `filtered_latest_candidate_count`
- `merged_candidate_count`
- `latest_saved_candidate_count`
- `rerank_trace_mode`
- `no_priority_profile`

### 2. `[TARGET SAVED GAP]`

각 target service마다 아래를 같이 봅니다.

- `base_rank`
- `retain_base_rank`
- `merged_rank`
- `rerank_rank`
- `saved_rank`
- `retain_base`
- `merged`
- `post`
- `saved`
- `dropStage`
- `ruleWeighted`
- `currentFinal`
- `savedFinal`
- `currentAi`
- `savedAi`
- `savedAiStatus`
- `diversityBucket`
- `diversityPenalty`
- `noPriorityAdj`

### 3. `[TOP SAVED BATCH]`

latest saved top N competitor를 같이 보여 줍니다.

즉 target과 실제 saved top competitor를 같은 trace 기준으로 비교합니다.

## 읽는 법

### 1. `retain_base=true`, `post=true`, `saved=false`

retrieval/filter/post-scoring 까지는 살아 있습니다.  
남은 병목은 saved top window 또는 rerank 이후 경쟁입니다.

### 2. `rerank_rank` 는 높은데 `saved_rank` 가 없다

current rerank trace 기준으론 경쟁력이 있는데,
persisted latest saved batch에는 안 들어간 상태입니다.

이 경우는

- current trace vs latest saved batch 시차
- AI score 차이
- saved top window 경쟁

을 같이 봐야 합니다.

### 3. `currentFinal` 과 `savedFinal` 차이가 크다

이 경우는 `rerankTraceMode` 가 `PRE_AI_POST_SCORING` 임을 같이 봅니다.  
즉 saved batch는 persisted AI 이후 결과일 수 있습니다.

### 4. `diversityPenalty`, `noPriorityAdj` 가 의미 있게 붙는다

retrieval 문제가 아니라 saved top window 직전의 rerank shaping 영향으로 읽습니다.

## 관련 문서

1. [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md)
2. [recommendation-rebalance-audit-runbook.md](./recommendation-rebalance-audit-runbook.md)
3. [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md)

## 요약

1. 이 문서는 retrieval 이후 남는 `saved batch gap` 을 읽습니다.
2. target family와 latest saved top competitor를 같은 diagnostics trace에서 비교합니다.
3. 다음 bounded fix를 `saved top window`, `rerank shaping`, `AI persisted gap` 중 어디로 둘지 좁힐 때 씁니다.
