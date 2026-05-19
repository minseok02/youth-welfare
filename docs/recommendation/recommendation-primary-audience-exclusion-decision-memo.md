# recommendation primary audience exclusion decision memo

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 fresh runtime 기준 `savedAi=0` 이유를 제품적으로 어떻게 읽을지 고정합니다.

질문은 이것입니다.

- 어떤 zero-AI exclusion은 그대로 유지하는 편이 맞는가
- 어떤 exclusion은 나중에 완화 검토 대상으로 남길 수 있는가

## 현재 증거

`2026-05-19` local rebuilt app 기준 fresh runtime evidence는 아래 순서로 다시 확인했습니다.

1. `run-local-recommendation-ai-stage-gap-audit.sh`
2. `run-local-recommendation-ai-reason-contrast-audit.sh`
3. `run-local-recommendation-ai-zero-reason-bucket-audit.sh`

대표 계정은 `<example local recommendation user>` 이고, fresh top `20` 기준 zero-AI bucket은 아래 두 개였습니다.

- `INCOME_MISMATCH:1`
- `STUDENT_AUDIENCE_MISMATCH:1`

대표 row:

- `3289`: `기초생활수급자를 대상으로 하여 소득이 5분위인 사용자에게는 해당되지 않습니다.`
- `5837`: `중소기업 취업연계 장학금은 대학생을 대상으로 하여 미취업 사용자에게는 해당되지 않습니다.`

즉 현재 fresh runtime 기준 zero-AI 핵심은 broad signal 부족이 아니라 **명시적 audience exclusion** 입니다.

같은 날 read-only latest batch cohort 기준으로 `USER_COHORT=non_example`, `TOP_N=20` distribution audit도 다시 확인했습니다. 이 결과는 `scope_users=4`, `zero_ai_rows=8`, `zero_ai_reason_buckets=AUDIENCE_MISMATCH:4,STUDENT_AUDIENCE_MISMATCH:4` 였고, 반복 row는 `3689(인천 재직청년 복지포인트)` 와 `6790(지역인재육성을 위한 장학금 지원)` 이었습니다. 즉 refresh 한 번의 family-level evidence뿐 아니라, latest batch cohort 집계 기준으로도 **재직청년/학생 대상 mismatch가 반복 bucket** 으로 보입니다.

## 권장 판단

현재 권장안은 아래입니다.

### 1. `INCOME_MISMATCH` 는 유지

`기초생활수급자`, `저소득층`, `소득 5분위와 직접 불일치` 같은 이유는 현재 제품적으로 **정상 exclusion** 으로 보는 편이 맞습니다.

이건 단순 phrasing 문제가 아니라 실제 대상군 조건에 가깝습니다.

### 2. `STUDENT_AUDIENCE_MISMATCH` 도 기본 유지

`대학생 대상`, `장학금`, `학자금`, `학생 전용` 같은 이유도 현재 제품적으로는 **기본 유지** 쪽이 맞습니다.

특히 현재 사용자 맥락이 `미취업 청년` 인데 정책이 `대학생 특정 집단` 에 직접 묶여 있으면, zero-AI는 과도한 오탐보다 합리적 exclusion에 가깝습니다.

### 3. `LOW_DIRECT_HELP` 는 나중 완화 후보

`직접적인 도움이 적음`, `연관성이 낮음`, `간접적 도움만 있음` 같은 이유는 hard exclusion보다 soft downgrade에 더 가깝습니다.

현재 latest fresh top에서는 이 bucket이 zero-AI 핵심으로 남지 않았으므로 immediate fix 대상은 아니지만, 다시 반복되면 완화 검토 후보로 볼 수 있습니다.

## 지금 안 할 일

아래는 현재 권장하지 않습니다.

1. `INCOME_MISMATCH` 완화
2. `STUDENT_AUDIENCE_MISMATCH` 완화
3. global prompt 재설계
4. source/category 전체 bonus
5. post-processing으로 zero-AI 강제 상향

## reopen 관점 해석

현재 evidence만 기준으로 하면 recommendation reopen을 바로 code-tuning lane으로 옮길 이유는 약합니다.

정리하면:

- `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH`
  - product exclusion으로 유지
- `LOW_DIRECT_HELP`
  - 반복되면 later review
- 지금 immediate lane
  - direct tuning이 아니라 **제품 정책 유지 확인 + evidence 기록**

## 다음에 다시 열 조건

아래 중 하나가 생기면 이 memo를 다시 엽니다.

1. 실제 운영 `REAL_USER` 에서도 같은 bucket이 과도하게 많이 반복될 때
2. 제품 요구상 `대학생/장학금` 류도 청년 general discovery에 더 노출해야 한다는 목표가 생길 때
3. zero-AI 주된 bucket이 `LOW_DIRECT_HELP` 쪽으로 이동할 때

## 한 줄 결론

현재 local rebuilt runtime 기준 zero-AI 핵심은 `소득 불일치 + 학생/대학생 대상 불일치` 이고, 둘 다 지금은 **완화보다 유지가 맞는 product exclusion** 입니다.
