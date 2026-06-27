# Recommendation Docs

추천 파이프라인, 현재 상태, 검증 문서를 모아둔 폴더입니다.

시작점은 [recommendation-docs-index.md](./recommendation-docs-index.md) 입니다.
사용자에게 보이는 추천 메모 계약은 [recommendation-ai-reason-memo-contract.md](./recommendation-ai-reason-memo-contract.md) 를 같이 봅니다.

현재 active 기준 요약:

- daily operator entrypoint는 `bash deploy/smoke/run-local-recommendation-observation-suite.sh` 입니다.
- AI exclusion latest overview가 필요할 때만 `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh` 를 추가로 봅니다.
- 현재 기본 해석은 `KEEP_OBSERVING`, `reopen_allowed=false`, `OBSERVE_REAL_USER_TRAFFIC` 입니다.
- `reopen_allowed=false` 동안에는 score/weight/prompt/source balancing을 열지 않고 real-user sample과 leader signal을 관찰합니다.

PR / handoff / reopen 문서를 읽는 순서:

1. reviewer가 범위를 먼저 읽을 때
   - [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
2. author가 현재 draft 유지/해제 기준을 볼 때
   - [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
3. merge 뒤 current 해석과 follow-up을 유지할 때
   - [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
4. `REAL_USER` traffic/cohort가 실제로 생긴 뒤 다시 열 때
   - [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
