# recommendation ai stage gap audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 fresh persisted batch까지 다시 만든 뒤에도 target family와 top batch 사이에 남는 차이를,

- `savedAi`
- `savedAiStatus`
- `savedAiReason`
- `currentFinal -> savedFinal delta`

관점에서 읽는 bounded audit 입니다.

즉 이 문서는 retrieval도 아니고 stale batch도 아닌,
**AI stage 이후 persisted final 차이**
를 읽기 위한 것입니다.

## 언제 이 문서를 쓰나

아래가 이미 보일 때 씁니다.

1. `fresh saved gap audit` 에서
   - `3257 inFreshTop=true`
   - 하지만 `savedAi=0`, `savedFinal < currentFinal`
2. 또는 target이 fresh saved batch에는 들어왔지만 순위가 AI 이후 더 내려가는 경우
3. 혹은 `savedAiStatus=NOT_REQUESTED` 로 fresh에서도 AI top-N 밖에 머무는 경우

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-stage-gap-audit.sh
```

필수 입력:

- `USER_ACCESS_TOKEN` 또는 `USER_EMAIL`, `USER_PASSWORD`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- `TARGET_USER_KEY`

## 출력

### metric

- `target_user_key`
- `target_service_ids_csv`
- `fresh_refresh_ids_csv`
- `rerank_trace_mode`
- `refresh_count`
- `fresh_top_ai_requested_count`
- `fresh_top_ai_zero_count`

### `[FRESH TOP AI]`

fresh persisted top batch에 대해:

- `savedRank`
- `rerankRank`
- `ruleWeighted`
- `currentFinal`
- `savedFinal`
- `delta`
- `savedAi`
- `savedAiStatus`
- `savedAiReason`

를 같이 봅니다.

### `[TARGET AI STAGE]`

각 target service마다:

- `inFreshTop`
- `rerankRank`
- `savedRank`
- `ruleWeighted`
- `currentFinal`
- `savedFinal`
- `delta`
- `savedAi`
- `savedAiStatus`
- `savedAiReason`
- `dropStage`

를 같이 봅니다.

## 읽는 법

### 1. `savedAiStatus=SCORED`, `savedAi=0`

AI request는 갔지만 persisted AI 점수가 0점입니다.  
이 경우 current rerank보다 saved final이 내려가며, next step은 AI input/response 품질 쪽입니다.

### 2. `savedAiStatus=NOT_REQUESTED`

fresh persisted batch에서도 AI top-N 밖에 머문 것입니다.  
이 경우는 AI stage 이전 경쟁력 자체가 낮다고 읽는 편이 맞습니다.

### 3. `savedAiReason` 이 명시적 exclusion 문구일 때

예: `저소득층 지원`, `신혼부부 대상`, `학생 대상` 같은 문구가 직접 내려오면  
이 케이스는 signal 부족보다 **AI primary audience mismatch 해석** 에 더 가깝습니다.

### 4. `delta < 0`

persisted final이 current rerank trace보다 더 낮아졌습니다.  
대체로 AI score 또는 persisted 후속 보정의 영향입니다.

## 관련 문서

1. [recommendation-fresh-saved-gap-audit-runbook.md](./recommendation-fresh-saved-gap-audit-runbook.md)
2. [recommendation-saved-batch-gap-audit-runbook.md](./recommendation-saved-batch-gap-audit-runbook.md)
3. [recommendation-current-state.md](./recommendation-current-state.md)

## 요약

1. 이 문서는 fresh persisted batch 이후 남는 AI-stage 차이를 봅니다.
2. `savedAi=0` 과 `NOT_REQUESTED` 를 분리합니다.
3. `savedAiReason` 으로 signal 부족인지 primary audience exclusion인지도 같이 읽습니다.
4. 다음 bounded fix를 AI input/response 쪽으로 둘지, AI top-N 이전 경쟁력 쪽으로 둘지 좁힐 때 씁니다.
