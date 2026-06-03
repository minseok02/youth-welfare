# recommendation no-priority gap audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 최신 추천 배치에서 `NO_PRIORITY` 사용자와 `HAS_PRIORITY` 사용자의 상단 추천이 실제로 얼마나 다른지 빠르게 확인하기 위한 runbook 입니다.

현재 recommendation 품질 이슈가 region mismatch보다 `우선순위 미설정 사용자군 상단 쏠림` 쪽으로 이동했을 때 먼저 봅니다.

## 실행

```bash
bash deploy/smoke/run-local-recommendation-no-priority-gap-audit.sh
```

## 먼저 볼 artifact

- `tmp/recommendation-no-priority-gap-audit/latest-no-priority-gap-summary.txt`
- `tmp/recommendation-no-priority-gap-audit/latest-no-priority-gap-summary.json`
- `tmp/recommendation-no-priority-gap-audit/latest-no-priority-gap-note.md`

## 먼저 볼 값

- `all_priority_users`
- `all_no_priority_users`
- `all_no_priority_top1`
- `all_has_priority_top1`
- `all_priority_gap_detected`
- `real_user_no_priority_top1`
- `real_user_has_priority_top1`
- `real_user_priority_gap_detected`
- `recommended_next_action`

## 해석

- `all_no_priority_users` 가 `all_priority_users` 보다 훨씬 크고 `recommended_next_action=STRENGTHEN_PRIORITY_CAPTURE` 면
  - 다음 액션은 추천 로직 재튜닝보다 `우선순위 입력 유도` 입니다.
- `all_priority_gap_detected=true` 면
  - 우선순위가 실제로 상단 분산에 영향을 주고 있다는 뜻입니다.
- `real_user_priority_gap_detected=true` 면
  - example-heavy latest batch뿐 아니라 실사용자군에서도 우선순위 차이가 실제로 관측됩니다.

## 한 줄 요약

이 audit는 recommendation 코드 변경 전, 지금 보이는 상단 쏠림이 `모델 문제`인지 `우선순위 미입력 문제`인지 빠르게 가르는 entrypoint입니다.
