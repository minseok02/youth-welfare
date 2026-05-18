# recommendation ai prompt line contrast audit runbook

## 목적

이 문서는 `savedAi=0` row와 같은 source/category 양수 AI row를 **실제 AI prompt에 들어가는 candidate line** 기준으로 비교합니다.

현재 `RealtimeAiGateway` prompt는 각 정책에 대해 주로 아래만 봅니다.

- `제목`
- `분류`
- `내용(description short, 80자)`

즉 `supportContent`, `service_tags`, `target group` 같은 값은 사람이 보기엔 좋아도 prompt에 직접 안 들어갈 수 있습니다.

이 runbook의 질문은 이것입니다.

- 0점 row가 사람 기준으론 좋아 보이지만 prompt line은 약한가
- 양수 row는 prompt line에서 `청년`, `이사비`, `주거비`, `지원` 같은 직접 표현이 더 강한가

## 언제 쓰나

다음 둘이 이미 확인된 뒤에 씁니다.

1. `ai zero contrast audit` 로 same category 안에 양수 AI peer가 있음
2. `ai input contrast audit` 로 구조화 신호 자체는 0점 row에도 충분히 있음

즉 이 단계는 **입력 신호 부족** 과 **prompt 노출 부족** 을 가르는 단계입니다.

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
bash deploy/smoke/run-local-recommendation-ai-prompt-line-contrast-audit.sh
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
bash deploy/smoke/run-local-recommendation-ai-prompt-line-contrast-audit.sh
```

## 출력

### `[PROMPT LINE CONTRAST]`

각 zero row마다

- `zero=...`
- `comp=...`

가 한 쌍으로 나옵니다.

핵심 필드:

- `promptLine`
- `descriptionShort`
- `supportContent`
- `keywordRaw`
- `lifeStageRaw`

## 읽는 법

### 1. `supportContent` 는 좋은데 `promptLine` 이 약하면

이 경우는 AI가 좋은 structured signal을 못 보는 구조일 수 있습니다.

즉 다음 bounded step은:

- prompt line에 `supportContent` 일부를 더 싣기
- `lifeStage/target group` 직접 노출 보강

같은 입력 노출 개선입니다.

### 2. `promptLine` 자체에도 청년 직접성이 약하면

예:

- `주거급여수급자`
- `신혼부부`
- `저소득`
- mixed life stage

같은 표현이 강하고 `청년 직접성` 이 약하면, AI가 선택적으로 0점을 줄 가능성이 높습니다.

이 경우 다음 bounded step은:

- prompt enrichment
- 청년 직접성 표현 보강

입니다.

## 아직 안 할 일

1. global AI score 보정
2. category-wide bonus
3. prompt 전체 재설계
4. public ranking patch

## 목표

이번 단계 목표는 하나입니다.

- `3257/3209/3287` 의 0점이
  - prompt에 **좋은 신호가 안 보이기 때문인지**
  - 아니면 prompt에 보여도 **AI가 선택적으로 낮게 해석하는지**

를 더 좁히는 것입니다.
