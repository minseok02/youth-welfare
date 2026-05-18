# recommendation ai zero cohort audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 fresh top batch 안에서 `savedAi=0`, `savedAiStatus=SCORED` 인 서비스들을 한 묶음으로 뽑아

- `3257` 이 단일 이상치인지
- 비슷한 local/YOUTH 후보 몇 개가 반복적으로 0점을 받는지

를 보는 bounded cohort audit 입니다.

## 언제 이 문서를 쓰나

아래가 이미 보일 때 씁니다.

1. `ai stage gap audit` 에서 `3257 savedAi=0`
2. fresh top 안에 `savedAi=0` 이 2~3건 이상 존재
3. “이게 3257만의 문제인가?” 를 가르고 싶을 때

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-zero-cohort-audit.sh
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
- `refresh_count`
- `fresh_top_count`
- `ai_zero_cohort_count`
- `ai_zero_sources`
- `ai_zero_categories`

### `[AI ZERO COHORT]`

fresh top 안에서 `savedAi=0`, `savedAiStatus=SCORED` 인 row만 보여 줍니다.

같이 보는 값:

- `savedRank`
- `rerankRank`
- `currentFinal`
- `savedFinal`
- `sourceType`
- `category`
- YOUTH official labels

### `[TARGET ZERO MEMBERSHIP]`

target family가 이 0점 cohort 안에 드는지 여부를 보여 줍니다.

## 읽는 법

### 1. `ai_zero_cohort_count=1`

`3257` 개별 케이스일 가능성이 큽니다.

### 2. `ai_zero_cohort_count>=2`

단일 이상치보다 **반복 패턴** 을 의심하는 편이 맞습니다.

### 3. source/category가 한쪽으로 몰림

예:
- `BOKJIRO_LOCAL`
- `주거/교육·직업훈련`

같은 식이면, 그 묶음의 AI input/summary 표현이 공통적으로 약할 수 있습니다.

## 관련 문서

1. [recommendation-ai-stage-gap-audit-runbook.md](./recommendation-ai-stage-gap-audit-runbook.md)
2. [recommendation-fresh-saved-gap-audit-runbook.md](./recommendation-fresh-saved-gap-audit-runbook.md)

## 요약

1. 이 문서는 `savedAi=0` 서비스를 cohort로 묶어 봅니다.
2. `3257` 단일 문제인지 반복 패턴인지 가를 때 씁니다.
3. 반복이면 AI input/response 품질 쪽 next step 근거가 됩니다.
