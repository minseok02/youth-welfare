# recommendation pipeline lane audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 특정 target family가

- `latest window audit` 에서는 `24위`, `28위` 로 보이는데
- 실제 recommendation pipeline 에서는
  - `merged` 까지 들어오는지
  - `saved batch` 까지 살아남는지

를 한 번에 읽는 bounded audit runbook 입니다.

즉 이 문서는 `raw latest rank` 와 `실제 recommendation trace` 를 이어 주는 단계입니다.

## 언제 이 문서를 쓰나

아래 상황이면 이 runbook 이 맞습니다.

1. `region-window audit` 으로 branch inclusion은 확인됨
2. `latest-window audit` 으로 latest top 20 바깥 위치는 확인됨
3. 다음 질문이
   - `latest lane` 이 실제로 문제인지
   - 아니면 base/merged/scoring/saved 단계에서 이미 다른 경계가 더 큰지

인 경우

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-pipeline-lane-audit.sh
```

기본값:

- `TARGET_SERVICE_IDS_CSV` 를 비우면 current latest batch user context에서
  region-matched local 청년/생활지원 family를 자동 선택
  (`BOKJIRO_LOCAL` 이 있으면 우선 사용하고, 현재 Gov24 중심 데이터셋처럼 없으면 `GOV24/YOUTH` 지역 청년 후보를 사용)
- `TOP_COMPETITOR_LIMIT=3`

## 출력

### 1. metric

- `target_user_key`
- `target_service_ids_csv`
- `top_competitor_ids_csv`
- `base_candidate_count`
- `latest_candidate_count`
- `filtered_base_candidate_count`
- `filtered_latest_candidate_count`
- `merged_candidate_count`
- `latest_saved_candidate_count`
- `rerank_trace_mode`

### 2. pipeline

`[PIPELINE]` 에서 각 service마다 아래를 봅니다.

- `base`
- `latest`
- `pass_base`
- `pass_latest`
- `base_rank`
- `latest_rank`
- `retain_base`
- `retain_latest`
- `retain_base_rank`
- `retain_latest_rank`
- `primary`
- `age`
- `youthRelevant`
- `merged`
- `merged_rank`
- `post`
- `saved`
- `dropStage`
- `savedRank`
- `rerankRank`
- `ruleWeighted`
- `savedFinal`

즉 한 줄로

`retrieval -> filter -> merge -> post-scoring -> saved`

경계를 같이 읽습니다.

Top-level metric 에도 `no_priority_profile` 이 같이 찍힙니다.  
즉 현재 user가 no-priority source rebalance 대상인지 바로 확인할 수 있습니다.

## 읽는 법

### 1. `base=true`, `merged=true`, `saved=false`

이 경우 retrieval 문제는 아닙니다.  
남은 병목은 scoring / rerank / saved window 쪽입니다.

### 2. `base=true`, `latest=false`, `merged=true`

이 경우 latest lane은 약하지만 base lane 덕분에 후보는 실제 pipeline 안에 살아 있습니다.  
즉 `latest 20` 경계가 전부는 아닙니다.

### 3. `pass_base=false` 면 `primary/age/youthRelevant` 를 같이 본다

- `primary=false`, `age=true`
  - primary audience mismatch
- `primary=true`, `age=false`
  - age constraint mismatch
- `youthRelevant=true` 인데 `primary=false`
  - projection/target-group 계열 mismatch를 의심

### 4. `pass_base=true` 인데 `retain_base=false`

이 경우는 youth/age predicate는 통과했지만 `filteredBase.limit(50)` 또는 `filteredLatest.limit(5)` 전에 밀린 것입니다.  
즉 필터 mismatch가 아니라 `window trim` 이며 diagnostics `dropStage` 는 `TRIMMED_BY_BASE_OR_LATEST_LIMIT` 로 읽는 게 맞습니다.

### 5. `latest=true`, `pass_latest=true`, `merged=false`

이 경우는 merge window 또는 dedupe/limit 쪽을 의심합니다.

### 6. `merged=true`, `post=false`

이 경우는 post-scoring filter mismatch 입니다.

### 7. `post=true`, `saved=false`

이 경우는 rerank/saved top window 경쟁입니다.

## 관련 문서

1. [recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md)
2. [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md)
3. [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)

## 요약

1. 이 문서는 latest rank evidence를 actual recommendation trace와 연결합니다.
2. 다음 bounded fix를 retrieval/latest/scoring/saved 중 어디로 둘지 좁힐 때 씁니다.
