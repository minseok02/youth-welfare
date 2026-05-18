# recommendation ai zero contrast audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `savedAi=0` cohort를 fresh top 안의 **양수 AI 후보** 와 바로 비교해,

- 0점이 특정 category 전체 현상인지
- 같은 source/category 안에도 양수 AI peer가 있는지

를 보는 bounded contrast audit 입니다.

## 언제 이 문서를 쓰나

아래가 보일 때 씁니다.

1. `ai zero cohort audit` 에서 `ai_zero_cohort_count >= 2`
2. `3257` 이 개별 이상치가 아니라 repeated pattern으로 의심될 때

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-zero-contrast-audit.sh
```

필수 입력:

- `USER_ACCESS_TOKEN` 또는 `USER_EMAIL`, `USER_PASSWORD`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- `TARGET_USER_KEY`

## 출력

### metric

- `ai_zero_count`
- `ai_positive_count`
- `ai_zero_sources`
- `ai_zero_categories`
- `ai_positive_sources`
- `ai_positive_categories`

### `[ZERO VS POSITIVE CATEGORY COUNTS]`

fresh top 안에서 source/category별

- `zero`
- `positive`

count를 같이 보여 줍니다.

### `[ZERO CONTRAST]`

각 0점 row마다

- 같은 `sourceType + category` 의 양수 AI comparator
- 없으면 같은 `sourceType` 양수 comparator

를 붙여 보여 줍니다.

## 읽는 법

### 1. 같은 category에 positive도 있다

category 전체가 0점인 게 아니라, **동일 category 안에서 일부만 0점** 인 상태입니다.

### 2. 같은 source엔 positive가 있는데 category엔 없다

source 전체 문제보다는 category/표현/개별 정책 설명 차이를 먼저 의심하는 편이 맞습니다.

## 관련 문서

1. [recommendation-ai-zero-cohort-audit-runbook.md](./recommendation-ai-zero-cohort-audit-runbook.md)
2. [recommendation-ai-stage-gap-audit-runbook.md](./recommendation-ai-stage-gap-audit-runbook.md)

## 요약

1. 이 문서는 0점 cohort를 양수 AI peer와 대비합니다.
2. category-wide 현상인지, 개별 row 문제인지 가를 때 씁니다.
