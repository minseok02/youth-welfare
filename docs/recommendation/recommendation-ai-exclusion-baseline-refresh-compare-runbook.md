# recommendation ai exclusion baseline refresh compare runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `baseline-refresh-summary.txt` 두 개만 놓고

- stable baseline이 바뀌었는지
- latest volatile observation만 바뀌었는지
- 해석(`drift_class`, `recommended_reading`) 자체가 바뀌었는지

를 high-signal key 기준으로 바로 비교하는 runbook 입니다.

긴 `baseline-report.out` 전체를 다시 읽지 않고,
핸드오프/운영 메모용 compact summary끼리만 빠르게 비교할 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh
```

명시적으로 두 파일을 비교하려면:

```bash
BASELINE_REFRESH_SUMMARY=/abs/path/older/baseline-refresh-summary.txt \
TARGET_REFRESH_SUMMARY=/abs/path/newer/baseline-refresh-summary.txt \
bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh
```

## 주요 출력

- `drift_detected`
- `interpretation_changed`
- `stable_baseline_changed`
- `latest_observation_changed`
- `changed_keys`
- `interpretation_changed_keys`
- `stable_changed_keys`
- `latest_changed_keys`

그리고 tracked key 전체에 대해 `baseline_*`, `target_*` 값을 같이 출력합니다.

## 읽는 법

- `stable_baseline_changed=false`, `latest_observation_changed=true`
  - fresh window 관찰값만 흔들린 것입니다.
- `stable_baseline_changed=true`
  - 운영 메모/current-state에 올린 stable baseline 자체를 다시 봐야 합니다.
- `interpretation_changed=true`
  - `drift_class` 또는 `recommended_reading` 이 바뀐 것이므로, 이번 비교의 결론 문구도 같이 갱신해야 합니다.
