# recommendation ai exclusion baseline refresh runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-overview-runbook.md](./recommendation-ai-exclusion-latest-overview-runbook.md)

## 현재 단계 해석

현재 local 기본값은 여전히 `WAIT_FOR_REAL_USER_TRAFFIC` 이고, latest reading도 `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 runbook은 recommendation을 지금 당장 다시 튜닝하기 위한 reopen entrypoint가 아니라, **현재 baseline 유지 단계에서 stable baseline / volatile observation을 더 깊게 다시 정리할 때 쓰는 one-shot wrapper** 로 읽는 편이 맞습니다.

## 목적

이 문서는 current local AI exclusion baseline을 다시 태우고,

- volatility audit
- baseline report

까지 한 번에 끝내는 one-shot entrypoint 입니다.

즉 helper를 각각 따로 돌리지 않고, 이번 실행의 stable baseline / volatile observation / recommended reading 을 바로 얻고 싶을 때 씁니다.

## 기본 스크립트

```bash
RUN_COUNT=2 bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh.sh
```

## 주요 출력

- `artifact_dir`
- `volatility_summary`
- `baseline_summary`
- `target_summary`
- `baseline_report`
- `summary_output`
- `latest_artifact_link`
- `latest_summary_link`
- `latest_report_link`

`summary_output` 은 운영 메모/핸드오프용 compact artifact 입니다. 여기에는 아래 high-signal key만 다시 정리됩니다.

- `drift_class`
- `recommended_reading`
- `stable_baseline_zero_ai_reason_buckets`
- `stable_dashboard_real_user_gate`
- `stable_breakdown_real_user_cohort_gate`
- `latest_fresh_top_ai_zero_count`
- `latest_ai_zero_count`
- `latest_ai_zero_reason_buckets`
- `volatility_reference_frequency`

최신 결과만 바로 집고 싶으면 `latest_artifact_link`, `latest_summary_link`, `latest_report_link` 를 보면 됩니다.

## 언제 쓰나

1. current local exclusion baseline을 다시 갱신하고 싶을 때
2. 코드/프롬프트 변경 뒤 “이번 실행을 어떻게 읽어야 하는지” 한 번에 알고 싶을 때
3. 운영 메모에 latest stable baseline / volatile observation을 바로 남기고 싶을 때
