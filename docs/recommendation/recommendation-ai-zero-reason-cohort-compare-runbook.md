# recommendation ai zero reason cohort compare runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 latest batch zero-AI reason bucket 분포를 **baseline cohort** 와 **target cohort** 로 나란히 비교합니다.

질문은 이것입니다.

- 현재 로컬 `non_example` baseline과 운영 `REAL_USER` 분포가 비슷한가
- 다르다면 어떤 bucket이 새로 커졌는가

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-zero-reason-cohort-compare.sh
```

기본값:

- `BASELINE_COHORT=non_example`
- `TARGET_COHORT=real_user`

추가 입력:

- `TOP_N`
- `SAMPLE_LIMIT`

## 읽는 법

### 1. `target_scope_users=0`

아직 `REAL_USER` latest batch 표본이 없다는 뜻입니다.

### 2. `baseline_zero_ai_reason_buckets` 와 `target_zero_ai_reason_buckets` 가 비슷하면

로컬 seed에서 본 exclusion 패턴이 운영 latest batch에도 반복된다고 읽습니다.

### 3. target에서 `LOW_DIRECT_HELP` 비중이 새로 커지면

그때는 완화 검토 후보가 운영에서도 실제로 보인다고 읽을 수 있습니다.
