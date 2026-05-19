# recommendation ai zero reason bucket audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 fresh top 안의 `savedAi=0`, `savedAiStatus=SCORED` row를 **이유 유형별 bucket** 으로 묶어 봅니다.

질문은 이것입니다.

- zero-AI가 주로 `소득 불일치` 때문인가
- `대학생/학생 대상` 같은 audience mismatch가 많은가
- 아니면 `직접적인 도움 부족` 쪽이 더 큰가

즉 이 runbook은 zero-AI family를 제품 판단용으로 더 압축해 읽는 bounded audit 입니다.

## 언제 쓰나

다음이 이미 확인된 뒤에 씁니다.

1. `ai stage gap` 또는 `ai reason contrast` 에서 zero-AI row가 2건 이상 보일 때
2. 개별 reason 문장 대신 반복 패턴을 숫자로 보고 싶을 때

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-zero-reason-bucket-audit.sh
```

필수 입력:

- `USER_ACCESS_TOKEN` 또는 `USER_EMAIL`, `USER_PASSWORD`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- `TARGET_USER_KEY`

## 출력

### metric

- `ai_zero_count`
- `ai_zero_reason_buckets`
- `ai_zero_source_distribution`
- `ai_zero_category_distribution`

### `[AI ZERO REASON BUCKETS]`

fresh top 안의 zero-AI row를 아래 heuristic bucket으로 나눠서 보여 줍니다.

- `INCOME_MISMATCH`
- `STUDENT_AUDIENCE_MISMATCH`
- `REGION_MISMATCH`
- `LOW_DIRECT_HELP`
- `AUDIENCE_MISMATCH`
- `BLANK_REASON`
- `OTHER`

## 읽는 법

### 1. `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 가 많으면

AI가 대상군 조건을 강한 exclusion으로 읽는 쪽이 메인 패턴입니다.

### 2. `LOW_DIRECT_HELP` 가 많으면

정책 자체는 청년 대상일 수 있지만, 현재 사용자 맥락에서 직접성 부족 때문에 깎이는 쪽입니다.

### 3. `BLANK_REASON` 이 나오면

먼저 [recommendation-ai-reason-coverage-audit-runbook.md](./recommendation-ai-reason-coverage-audit-runbook.md) 로 reason coverage를 다시 봅니다.
