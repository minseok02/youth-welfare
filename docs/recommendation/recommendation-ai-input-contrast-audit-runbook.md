# recommendation ai input contrast audit runbook

## 목적

이 문서는 fresh top 안에서 `savedAi=0` 인 후보와, 같은 `source/category` 의 양수 AI 후보를 **입력 신호 관점**에서 나란히 비교합니다.

핵심 질문은 이것입니다.

- `3257` 류 0점이 category-wide 현상인가
- 아니면 같은 category 안에서도 일부 row만 0점인가
- 그렇다면 0점 row가 양수 row와 비교해
  - `keyword`
  - `lifeStage`
  - `INTEREST_THEME`
  - `KEYWORD`
  - `TARGET_GROUP`
  - `supportContent`
중 어떤 입력 신호가 약한가

즉 이 runbook은 **AI prompt/입력 대비 증거 수집** 용도입니다.  
점수 patch나 prompt 수정 전 단계입니다.

## 언제 쓰나

다음 둘이 이미 확인된 뒤에 씁니다.

1. `run-local-recommendation-ai-zero-cohort-audit.sh` 로 `savedAi=0` cohort가 반복 패턴임이 보였을 때
2. `run-local-recommendation-ai-zero-contrast-audit.sh` 로 same category 안에도 양수 AI peer가 있음을 확인했을 때

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
bash deploy/smoke/run-local-recommendation-ai-input-contrast-audit.sh
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
bash deploy/smoke/run-local-recommendation-ai-input-contrast-audit.sh
```

## 출력

### 1. 상단 metric

- `ai_zero_count`
- `ai_positive_count`
- `ai_zero_sources`
- `ai_positive_sources`

### 2. `[ZERO INPUT CONTRAST]`

각 zero row마다

- `zero=...`
- `comp=...`

두 줄이 연속으로 나옵니다.

둘 다 아래 입력 신호를 포함합니다.

- `savedAiStatus`
- `savedAiReason`
- `keywordRaw`
- `lifeStageRaw`
- `interestThemes`
- `keywordTags`
- `targetGroups`
- `lifeStageTags`
- `searchYouthRelevant`
- `supportContent`

## 읽는 법

### 1. 같은 category, 같은 source인데 structured signal도 거의 같으면

이 경우는 단순 taxonomy 부족보다

- title/summary wording
- AI prompt framing
- 설명 텍스트 밀도

쪽을 의심하는 편이 맞습니다.

특히 zero row의 `savedAiReason` 이 `저소득층`, `신혼부부`, `학생 대상` 같은
명시적 exclusion 문구라면, 이 단계의 병목은 signal 부족보다
**AI primary audience mismatch 해석** 에 더 가깝습니다.

### 2. zero row만 `targetGroups`, `interestThemes`, `keywordTags` 가 비어 있거나 약하면

이 경우는 AI 문제라기보다, 아직도 입력 signal structuring 부족일 수 있습니다.

즉 다음 bounded step은

- signal enrichment
- raw/support summary 파생 tag 추가

쪽이 됩니다.

### 3. zero row와 positive row의 차이가 `supportContent` 문장 품질에 몰리면

이 경우는 category-wide bug보다 row-level textual quality 문제일 가능성이 큽니다.

## 이번 lane에서 아직 안 할 일

1. AI prompt 대규모 수정
2. global AI score 보정
3. category별 강제 bonus
4. public UX 변경

## 현재 기대 결론

이번 단계 목표는 하나입니다.

- `3257/3209/3287` 같은 zero row가
  - **입력 신호도 약한지**
  - 아니면 **입력은 비슷한데 AI만 선택적으로 0을 주는지**

를 확정하는 것입니다.
