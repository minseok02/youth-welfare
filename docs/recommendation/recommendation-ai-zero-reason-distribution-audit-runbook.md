# recommendation ai zero reason distribution audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 **latest saved batch 기준 top N** 안의 zero-AI reason bucket 분포를 cohort 단위로 읽습니다.

질문은 이것입니다.

- `REAL_USER` latest batch에서도 zero-AI가 반복되는가
- 반복된다면 bucket이 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH`, `LOW_DIRECT_HELP` 중 어디에 몰리는가

즉 이 runbook은 refresh를 새로 만들지 않고, cohort 전반의 latest batch를 read-only로 보는 분포 audit 입니다.

## 언제 쓰나

다음 중 하나일 때 씁니다.

1. `REAL_USER` gate가 열렸고, 운영 latest batch에서도 같은 exclusion bucket이 반복되는지 보고 싶을 때
2. local `non_example` seed latest batch와 운영 `REAL_USER` latest batch를 같은 형식으로 비교하고 싶을 때

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh
```

주요 입력:

- `USER_COHORT`
  - `real_user`
  - `non_example`
  - `bounded_local`
  - `local_real_non_example_seed`
- `TOP_N`
- `SAMPLE_LIMIT`

## 예시

운영 `REAL_USER` latest batch:

```bash
USER_COHORT=real_user \
TOP_N=20 \
bash deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh
```

로컬 non-example baseline:

```bash
USER_COHORT=non_example \
TOP_N=20 \
bash deploy/smoke/run-local-recommendation-ai-zero-reason-distribution-audit.sh
```

## 출력

### metric

- `scope_users`
- `top_window_rows`
- `zero_ai_rows`
- `zero_ai_users`
- `zero_ai_reason_buckets`
- `zero_ai_source_distribution`
- `zero_ai_category_distribution`
- `zero_ai_account_origin_distribution`

### `[AI ZERO REASON DISTRIBUTION SAMPLE]`

latest batch top N 안의 zero-AI sample row를 그대로 보여 줍니다.

## 읽는 법

### 1. `zero_ai_reason_buckets` 가 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 중심이면

현재 zero-AI는 signal 부족보다 **product exclusion 유지 쪽** 으로 읽는 편이 맞습니다.

### 2. `LOW_DIRECT_HELP` 비중이 커지면

그때는 direct tuning이나 prompt 완화 검토 후보가 될 수 있습니다.

### 3. `REAL_USER` 와 `non_example` 분포가 비슷하면

로컬 seed에서 본 exclusion 패턴이 운영 latest batch에도 반복된다는 뜻입니다.
