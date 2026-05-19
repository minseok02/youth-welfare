# recommendation ai upstream reason trace audit runbook

## 목적

이 문서는 `latestSavedAiReason` blank가 **OpenAI 응답 단계에서부터 비는지**, 아니면 저장 이후 경계에서 비는지 가르기 위한 runbook 입니다.

질문은 이것입니다.

- fresh top window의 persisted `savedAiReason` blank coverage는 어떤가
- 같은 refresh 구간의 app log에서 `RealtimeAiGateway` 가 본 upstream `reason` blank coverage는 어떤가

즉 이 runbook은 `ai reason coverage audit` 의 다음 단계로, **upstream vs persisted 분리** 용도입니다.

## 언제 쓰나

다음 둘이 이미 확인된 뒤에 씁니다.

1. `ai reason coverage audit` 에서 blank reason 비율이 높을 때
2. blank가 OpenAI 응답인지 저장 경계인지 더 좁히고 싶을 때

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
bash deploy/smoke/run-local-recommendation-ai-upstream-reason-trace-audit.sh
```

## 출력

1. `run-local-recommendation-ai-reason-coverage-audit.sh` 와 동일한 persisted coverage 출력
2. `[UPSTREAM REASON COVERAGE LOGS]`

두 번째 블록은 app log의

- `resultsCount`
- `blankReasonCount`
- `nonBlankReasonCount`
- `blankReasonServiceIds`

를 그대로 보여 줍니다.

## 읽는 법

### 1. persisted blank도 높고 upstream `blankReasonCount` 도 높으면

이 경우는 OpenAI 응답 단계에서부터 reason이 비는 쪽으로 읽는 편이 맞습니다.

### 2. persisted blank는 높은데 upstream `blankReasonCount=0` 이면

이 경우는 저장/매핑/후속 경계에서 reason이 비워지는지 봐야 합니다.

### 3. log가 안 잡히면

현재 app 컨테이너가 최신 코드 이전 빌드일 수 있습니다. 이 경우 `docker compose up -d --build app` 후 다시 봅니다.
