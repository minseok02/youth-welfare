# recommendation ai exclusion latest status export runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-ai-exclusion-latest-status-runbook.md](./recommendation-ai-exclusion-latest-status-runbook.md)

## 현재 단계 해석

현재 local 기본값은 full latest batch review gate `DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, latest recommendation reading `VOLATILE_ONLY_DRIFT` 입니다.

즉 이 export runbook은 새로운 tuning 결론을 만드는 문서가 아니라, **현재 latest refresh/drift artifact를 사람용 note와 machine-readable JSON으로 다시 포장해 handoff/운영 메모에 쓰기 쉽게 만드는 export helper** 로 읽는 것이 맞습니다.

## 목적

이 문서는 latest refresh summary와 latest drift summary를 읽어
바로 붙여넣기 가능한 Markdown 메모를 생성하는 entrypoint 입니다.

재실행 없이 현재 남아 있는 latest artifact 기준으로

- stable baseline
- latest volatile observation
- latest drift check

를 한 장짜리 note로 뽑고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh
```

## 주요 출력

- `latest-status-note.md`
- `latest-status.json`
- `latest_artifact_link`
- `latest_note_link`
- `latest_json_link`

`latest-status-note.md` 는 사람용 메모이고, `latest-status.json` 은 후속 스크립트나 자동화가 읽기 쉬운 machine-readable artifact 입니다.
둘 다 `generated_at_utc`, `generated_at_kst` 를 같이 남기므로, UTC artifact 경로(`...Z`)와 KST 실행 날짜를 한 화면에서 같이 읽을 수 있습니다.
또 `operator_next_step` 도 같이 남기므로, 현재 latest artifact 기준 다음 운영 행동을 한 줄로 읽을 수 있습니다.

## 읽는 법

- note의 `Stable Baseline` 섹션은 고정 baseline 메모로 봅니다.
- `Latest Observation` 섹션은 fresh window 관찰값 메모로 봅니다.
- `Latest Drift Check` 섹션에서
  - `stable_baseline_changed=false`, `latest_observation_changed=true` 면
    fresh observation만 흔들린 것으로 읽습니다.
