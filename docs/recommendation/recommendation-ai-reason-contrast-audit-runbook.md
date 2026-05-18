# recommendation ai reason contrast audit runbook

## 목적

이 문서는 fresh persisted batch 기준 `savedAi=0` row와 같은 source/category 양수 AI row의 **persisted `ai_reason`** 을 직접 비교합니다.

질문은 이것입니다.

- 모델이 0점 row를 실제로 어떤 이유로 낮게 봤는가
- 같은 category 양수 row는 어떤 이유로 높게 봤는가
- mixed life stage / 저소득 / 수급자 / 신혼부부 같은 표현이 실제 AI reason에도 드러나는가

즉 이 runbook은 `input contrast` 다음 단계의 **모델 해석 근거 확인** 용도입니다.

## 언제 쓰나

다음 둘이 이미 확인된 뒤에 씁니다.

1. `ai input contrast audit` 로 structured signal은 충분한데도 zero row가 남는다고 보일 때
2. same source/category 양수 AI peer가 실제로 존재할 때

## 실행

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
USER_EMAIL='<target user email>' \
USER_PASSWORD='<target user password>' \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
TARGET_USER_KEY='05c03e8cfda140cb8c410ac9dbc098fc' \
TOP_REFRESH_LIMIT=20 \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-ai-reason-contrast-audit.sh
```

또는 user token이 있으면:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
USER_ACCESS_TOKEN='<target user access token>' \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
TARGET_USER_KEY='05c03e8cfda140cb8c410ac9dbc098fc' \
TOP_REFRESH_LIMIT=20 \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-ai-reason-contrast-audit.sh
```

## 출력

### `[AI REASON CONTRAST]`

각 zero row마다

- `zero=...`
- `comp=...`

한 쌍으로 나옵니다.

핵심 필드:

- `savedAi`
- `savedRank`
- `aiReason`

## 읽는 법

### 1. zero row reason이 직접적으로 비청년 audience를 말하면

예:

- 취약계층 일반 지원
- 신혼부부 중심
- 특정 계층 중심

처럼 나오면, 다음 bounded step은 AI가 prompt line에서 읽는 primary audience를 더 청년 직접성 있게 보강하는 쪽입니다.

### 2. zero row reason이 모호하거나 비어 있고, positive row reason만 또렷하면

이 경우는 AI 입력/응답 품질 불안정 가능성도 있습니다.

### 3. 같은 category positive row reason이 청년 직접성 표현을 반복하면

예:

- 청년 주거비 부담 완화
- 청년 취업역량 강화

같은 표현이 positive 쪽에 몰리면, prompt wording 차이가 실제 원인일 가능성이 높습니다.

## 아직 안 할 일

1. global prompt 재설계
2. AI score 후처리 보정
3. category bonus
4. public ranking patch

## 목표

이번 단계 목표는 하나입니다.

- `3257/3209/3287` 의 `savedAi=0` 이 실제 AI reason에서도
  - mixed life stage
  - 비청년 primary audience
  - 직접 청년 표현 부족

으로 드러나는지 확인하는 것입니다.
