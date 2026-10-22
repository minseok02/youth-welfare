# Recommendation Docs

추천 파이프라인, 현재 상태, 검증 문서를 모아둔 폴더입니다.

시작점은 [recommendation-docs-index.md](./recommendation-docs-index.md) 입니다.
사용자에게 보이는 추천 메모 계약은 [recommendation-ai-reason-memo-contract.md](./recommendation-ai-reason-memo-contract.md) 를 같이 봅니다.

현재 구현과 실행 구분:

- [현재 상태](recommendation-current-state.md)에서 북마크·로그 보존·자격조건의 미해결 문제를 확인합니다.
- 기존 산출물만 보려면 [파일 조회·gate 해석](recommendation-ai-exclusion-latest-status-runbook.md), 실제 suite/refresh가 필요하면 [증거 재수집](recommendation-ai-exclusion-baseline-refresh-runbook.md)을 선택합니다. 후자는 쓰기·AI 비용을 수반할 수 있습니다.
- 앱 관측은 [observation runbook](recommendation-observation-runbook.md)에서 환경·표본·선택 step의 영향을 정한 뒤 수행합니다.
- 과거 `KEEP_OBSERVING/reopen_allowed=false/OBSERVE_REAL_USER_TRAFFIC`는 당시 관측/품질 튜닝 판정입니다. 현재 상태로 고정하거나 승인된 리팩토링·보안·데이터 유실 수정의 금지 조건으로 사용하지 않습니다.

과거 PR·인계·개선 순서의 근거가 필요할 때:

1. closeout PR 검토 범위
   - [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
2. 당시 draft 유지/해제 판단
   - [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
3. 당시 merge 후 인계 기준
   - [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
4. 당시 단계별 개선 제안
   - [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)

위 기록은 현재 PR의 draft/merge/승인 상태를 뜻하지 않습니다. 새 표본을 재검토할 때는 [실사용자 재확인](recommendation-real-user-recheck-checklist.md)을 참고하되 합성 계정과 실제 운영 표본을 구분하고 새 관측·기대 동작·완료 조건을 먼저 정합니다.
