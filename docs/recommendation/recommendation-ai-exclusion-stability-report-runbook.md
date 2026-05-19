# recommendation ai exclusion stability report runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-volatility-audit-runbook.md](./recommendation-ai-exclusion-volatility-audit-runbook.md)

## 현재 단계 해석

현재 local 기본값은 `WAIT_FOR_REAL_USER_TRAFFIC`, latest reading은 `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 runbook은 recommendation을 지금 다시 열지 말지 결정하는 문서가 아니라, **현재 유지 중인 evidence에서 어떤 key를 stable baseline으로 볼지, 어떤 key를 volatile observation으로 볼지 정리하는 해석 helper** 로 읽는 편이 맞습니다.

## 목적

이 문서는 `volatility-summary.txt` 를 읽어,

- 어떤 key를 stable baseline으로 볼지
- 어떤 key를 fresh window volatility로 볼지

를 자동 분류하는 helper 입니다.

즉 volatility 숫자를 사람이 다시 읽지 않고, “이 값은 고정 truth”, “이 값은 흔들리는 관찰값”을 바로 나누고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-stability-report.sh
```

기본 동작:

- `tmp/recommendation-ai-exclusion-volatility/` 아래 최신 `volatility-summary.txt` 를 자동 선택

명시적으로 지정하려면:

```bash
VOLATILITY_SUMMARY=/path/to/volatility-summary.txt \
bash deploy/smoke/run-local-recommendation-ai-exclusion-stability-report.sh
```

## 주요 출력

- `stable_keys`
- `volatile_keys`
- `stable_<key>=...`
- `volatile_<key>=baseline | run-01 | run-02 ...`

## 언제 쓰나

1. current-state에 어떤 숫자를 고정 truth로 써야 할지 정리할 때
2. 운영 메모에 “stable baseline vs volatile observation” 을 분리해 적고 싶을 때
3. `REAL_USER` gate가 열리기 전 local evidence를 과도하게 해석하지 않도록 guardrail이 필요할 때

## 읽는 법

- `stable_keys` 에 `baseline_zero_ai_reason_buckets`, `dashboard_real_user_gate`, `breakdown_real_user_cohort_gate` 가 있으면
  - cohort baseline과 `REAL_USER` gate는 현재 안정적인 기준선입니다.
- `volatile_keys` 에 `fresh_top_ai_zero_count`, `ai_zero_reason_buckets` 가 있으면
  - target family fresh window는 관찰값으로만 읽고 fixed truth처럼 박지 않습니다.
