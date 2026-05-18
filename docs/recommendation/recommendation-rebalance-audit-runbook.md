# recommendation rebalance audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 no-priority recommendation 에서

- raw base retrieval 안에는 들어온 후보가
- 왜 `filteredBase.limit(50)` 안에는 못 남는지

를 `source round-robin rebalance` 기준으로 직접 읽는 bounded audit 입니다.

핵심 질문은 이것입니다.

- youth/age predicate는 통과했는데
- source rebalance 이후 `base 50` 밖으로 밀렸는가

## 언제 이 문서를 쓰나

아래 조건이 모두 맞으면 이 runbook 이 맞습니다.

1. `pipeline lane audit` 에서 `pass_base=true`, `retain_base=false`
2. `dropStage=TRIMMED_BY_BASE_OR_LATEST_LIMIT`
3. target family가 raw base retrieval 안에는 들어와 있음

즉 filter mismatch가 아니라 **retained ordering** 층을 봐야 할 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-rebalance-audit.sh
```

기본값:

- `TARGET_SERVICE_IDS_CSV=2736,3257,3281,3575,3714`
- `BASE_FETCH_SIZE=150`
- `BASE_WINDOW_LIMIT=50`

## 출력

### 1. metric

- `raw_base_candidate_count`
- `passed_base_candidate_count`
- `base_window_limit`

### 2. target rebalance

`[TARGET REBALANCE]` 는 target family에 대해 아래를 같이 보여 줍니다.

- `raw_rank`
- `pass_base`
- `source`
- `source_pass_rank`
- `rebalance_rank`
- `retain_base`
- `retain_sim`
- `dropStage`
- `projection`
- `category`

### 3. top / blocker

- `[TOP REBALANCED BASE]`
  - simulated round-robin 뒤 `base 50` 안에 남는 후보
- `[BLOCKER REBALANCED BASE]`
  - `51~70` 구간 blocker

## 읽는 법

### 1. `raw_rank` 는 높지만 `rebalance_rank` 가 50 밖

이 경우 raw SQL ordering 자체보다 **source round-robin rebalance** 가 직접 원인입니다.

예:

- `raw_rank=8`
- `pass_base=true`
- `rebalance_rank=61`
- `retain_base=false`

이면 SQL branch는 통과했지만 no-priority source balancing 때문에 `base 50` 밖으로 밀린 것입니다.

### 2. `retain_sim=true` 인데 `retain_base=false`

이 경우 script simulation 과 app runtime 이 어긋난 것이므로,
rebalance 외 다른 정렬/limit 경계를 다시 봐야 합니다.

### 3. `source_pass_rank` 가 큰데 같은 source가 blocker 구간에 많다

이 경우는 현재 source round-robin 규칙이
target source 안의 후순위 후보를 자연스럽게 뒤로 미는 구조임을 뜻합니다.

## 요약

1. 이 문서는 filter mismatch가 아니라 **retained ordering** 을 확인하는 단계입니다.
2. 다음 bounded fix를 `source rebalance 완화` 로 둘지, `base window size` 로 둘지 좁힐 때 씁니다.
