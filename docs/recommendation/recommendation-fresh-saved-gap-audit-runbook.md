# recommendation fresh saved gap audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 latest saved batch와 current diagnostics rerank가 어긋날 때,

- latest saved batch가 단순히 오래된 batch인지
- `personal=true` fresh refresh 뒤에도 target family가 여전히 빠지는지

를 확인하는 bounded audit 입니다.

즉 이 문서는 `saved batch gap` 을
**stale latest batch**
와
**fresh persisted batch에서도 유지되는 gap**
으로 분리합니다.

## 언제 이 문서를 쓰나

아래가 이미 보일 때 씁니다.

1. `saved batch gap audit` 에서 target이 `rerank_rank` 는 높지만 `saved_rank` 가 없음
2. latest saved batch top competitor가 target보다 낮은 current rerank를 보이는데도 saved top에 있음
3. latest saved batch가 stale/non-personal cache reuse 결과인지 의심되는 경우

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-fresh-saved-gap-audit.sh
```

필수 입력:

- user auth
  - `USER_ACCESS_TOKEN`
  - 또는 `USER_EMAIL`, `USER_PASSWORD`
- admin auth
  - `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- `TARGET_USER_KEY`

기본값:

- `TARGET_SERVICE_IDS_CSV=2736,3257,3281,3575,3714`
- `TOP_REFRESH_LIMIT=10`

## 동작

1. user token으로 `POST /api/recommendations/refresh?personal=true`
2. fresh refresh top N serviceId 추출
3. admin diagnostics로 target family + fresh top N 동시 조회
4. current rerank / fresh saved batch를 같은 시점으로 비교

## 출력

### metric

- `target_user_key`
- `target_service_ids_csv`
- `fresh_refresh_ids_csv`
- `rerank_trace_mode`
- `refresh_count`
- `latest_saved_candidate_count`

### `[FRESH REFRESH TOP]`

fresh `personal=true` 응답 상위 N에 대해:

- `savedRank`
- `rerankRank`
- `savedFinal`
- `currentFinal`
- `savedAi`

를 같이 봅니다.

### `[TARGET FRESH GAP]`

각 target service마다:

- `inFreshTop`
- `rerankRank`
- `savedRank`
- `retainBaseRank`
- `mergedRank`
- `dropStage`
- `currentFinal`
- `savedFinal`
- `savedAi`
- `savedAiStatus`
- `diversityPenalty`
- `noPriorityAdj`

를 같이 봅니다.

## 읽는 법

### 1. `inFreshTop=true`

latest saved gap은 stale batch 이슈였을 가능성이 큽니다.

### 2. `inFreshTop=false`, `savedRank` 도 없음

fresh persisted batch에서도 target이 빠집니다.  
이 경우는 saved top window / AI / rerank shaping 문제를 계속 추적해야 합니다.

### 3. `rerankRank` 는 높지만 `inFreshTop=false`

current diagnostics rerank와 fresh persisted batch 사이에

- persisted AI gap
- saved top window 경쟁

이 남아 있습니다.

### 4. `savedAi` 가 비어 있고 `savedRank` 도 없음

AI top-N 바깥 또는 persisted AI 미부여 가능성을 같이 봅니다.

## 관련 문서

1. [recommendation-saved-batch-gap-audit-runbook.md](./recommendation-saved-batch-gap-audit-runbook.md)
2. [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md)
3. [recommendation-current-state.md](./recommendation-current-state.md)

## 요약

1. 이 문서는 latest saved batch와 fresh persisted batch를 분리해서 봅니다.
2. `personal=true` refresh 뒤에도 target이 빠지는지 확인합니다.
3. 다음 bounded fix를 stale batch 대응으로 둘지, fresh persisted scoring/AI 경쟁으로 둘지 좁힐 때 씁니다.
