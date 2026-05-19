# recommendation ai exclusion suite runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 현재 recommendation `AI exclusion` evidence를 한 번에 재검증하는 local suite 입니다.

한 번 실행하면 아래 네 축을 순서대로 봅니다.

1. fresh target family `ai stage gap`
2. fresh top `zero reason bucket`
3. latest batch `baseline vs target cohort` 비교
4. `REAL_USER` readiness + ready면 distribution

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-suite.sh
```

주요 입력:

- `TARGET_USER_KEY`
- `USER_EMAIL`, `USER_PASSWORD` 또는 `USER_ACCESS_TOKEN`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- `TARGET_SERVICE_IDS_CSV`
- `TOP_REFRESH_LIMIT`
- `TOP_N`
- `SAMPLE_LIMIT`
- `BASELINE_COHORT`
- `TARGET_COHORT`

기본 target family는 현재 local rebuilt runtime 기준 `3288,3289,3290,5837` 입니다.

## 출력 prefix

- `[stage-gap]`
- `[zero-reason-bucket]`
- `[cohort-compare]`
- `[real-user-ready]`

## 언제 쓰나

다음 중 하나일 때 씁니다.

1. 현재 local AI exclusion baseline을 한 번에 다시 확인하고 싶을 때
2. 코드/프롬프트/문서 변경 뒤 exclusion evidence drift가 없는지 보고 싶을 때
