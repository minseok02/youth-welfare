# recommendation ai reason coverage audit runbook

## 목적

이 문서는 fresh persisted top window에서 `latestSavedAiReason` 이 **얼마나 채워져 있는지** 먼저 확인합니다.

질문은 이것입니다.

- `savedAiStatus=SCORED` 인 row 중 reason이 실제로 채워져 있는가
- blank reason이 특정 `sourceType` / `category` 에 몰려 있는가
- `ai reason contrast` 해석을 하기 전에 persisted reason coverage 자체가 충분한가

즉 이 runbook은 `ai reason contrast` 의 전단계로, **reason 내용 해석 전에 coverage부터 확인** 하는 용도입니다.

## 언제 쓰나

다음 중 하나일 때 씁니다.

1. `ai reason contrast` 를 돌렸는데 zero/positive 모두 `savedAiReason` 이 비어 있을 때
2. 특정 user family에서 AI reason 저장 자체가 되고 있는지 먼저 확인하고 싶을 때

## 실행

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
USER_EMAIL='<target user email>' \
USER_PASSWORD='<target user password>' \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
TARGET_USER_KEY='<target user key>' \
TOP_REFRESH_LIMIT=20 \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-ai-reason-coverage-audit.sh
```

또는 user token이 있으면:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
USER_ACCESS_TOKEN='<target user access token>' \
ADMIN_EMAIL='<admin email>' \
ADMIN_PASSWORD='<admin password>' \
TARGET_USER_KEY='<target user key>' \
TOP_REFRESH_LIMIT=20 \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-ai-reason-coverage-audit.sh
```

## 출력

핵심 metric:

- `ai_scored_count`
- `ai_scored_blank_reason_count`
- `ai_scored_non_blank_reason_count`
- `blank_reason_source_distribution`
- `blank_reason_category_distribution`

### `[BLANK REASON ROWS]`

reason이 비어 있는 `SCORED` row 목록입니다.

### `[NON BLANK REASON ROWS]`

reason이 채워진 `SCORED` row 목록입니다.

## 읽는 법

### 1. `ai_scored_blank_reason_count` 가 `ai_scored_count` 와 거의 같으면

이 경우는 reason 내용 해석보다 **persisted reason coverage 부족** 이 먼저입니다.

### 2. blank가 특정 `sourceType` / `category` 에만 몰리면

이 경우는 source/category별 prompt/response 또는 저장 경계 문제일 수 있습니다.

### 3. non-blank row가 충분히 있는데 zero row만 비면

그때부터는 zero row exclusion reason을 해석하는 `ai reason contrast` 가 의미가 있습니다.

## 목표

이번 단계 목표는 하나입니다.

- 특정 fresh top window에서 `latestSavedAiReason` 이 “해석 가능한 데이터”인지, 아니면 “거의 비어 있는 상태”인지 먼저 분리하는 것입니다.
