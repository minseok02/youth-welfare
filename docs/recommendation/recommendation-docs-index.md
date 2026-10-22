# 추천 문서 목차

현재 구현과 미해결 문제는 [추천 현재 상태](recommendation-current-state.md)를 먼저 확인합니다. [환경 안내](../core/environment-guide.md)에 따라 로컬 seed·로컬 앱과 RDS·서버 관측을 구분합니다.

과거 `KEEP_OBSERVING`과 `reopen_allowed=false`는 당시 품질 튜닝/관측의 상태값입니다. 현재 승인된 리팩토링, 데이터 유실·보안·계약 불일치 수정을 일괄 금지하지 않습니다. `account_origin=REAL_USER` 값도 실제 사람의 운영 표본이라는 보증이 아닙니다.

## 실행 전 구분

- 스크립트 이름의 `local`은 앱·DB 대상을 강제하지 않습니다. 각 runbook의 env·권한·실행 영향을 확인합니다.
- audit에도 로그인·로컬 artifact 생성이 있을 수 있고, refresh/repair/seed/approval smoke는 앱 또는 DB를 변경합니다.
- 합성 cohort는 격리된 테스트용입니다. 실제 사용자 관측·CTR·품질 근거와 분리합니다.
- 운영 gate의 과거 기준선과 현재 관측 결과를 섞지 않으며, 원본 날짜·커밋·표본을 보존합니다.

## 진입점과 사용자 계약

- [ai-reason-memo-contract](recommendation-ai-reason-memo-contract.md)
- [current-state](recommendation-current-state.md)
- [pipeline](recommendation-pipeline.md)
- [similar-users-viewed-policy](recommendation-similar-users-viewed-policy.md)

## 운영 관측과 표본

- [concentration-audit-runbook](recommendation-concentration-audit-runbook.md)
- [ctr-readiness-runbook](recommendation-ctr-readiness-runbook.md)
- [observation-runbook](recommendation-observation-runbook.md)
- [operation-checklist](recommendation-operation-checklist.md)
- [real-user-baseline-runbook](recommendation-real-user-baseline-runbook.md)
- [real-user-exclusion-readiness-check-runbook](recommendation-real-user-exclusion-readiness-check-runbook.md)
- [real-user-recheck-checklist](recommendation-real-user-recheck-checklist.md)
- [reopen-decision-runbook](recommendation-reopen-decision-runbook.md)
- [replay-template](recommendation-replay-template.md)
- [similar-users-viewed-audit-runbook](recommendation-similar-users-viewed-audit-runbook.md)

## 후보 검색과 저장 차이

- [gov24-recommendation-audit-runbook](gov24-recommendation-audit-runbook.md)
- [fresh-saved-gap-audit-runbook](recommendation-fresh-saved-gap-audit-runbook.md)
- [latest-window-audit-runbook](recommendation-latest-window-audit-runbook.md)
- [no-priority-gap-audit-runbook](recommendation-no-priority-gap-audit-runbook.md)
- [pipeline-lane-audit-runbook](recommendation-pipeline-lane-audit-runbook.md)
- [rebalance-audit-runbook](recommendation-rebalance-audit-runbook.md)
- [region-window-audit-runbook](recommendation-region-window-audit-runbook.md)
- [same-profile-fresh-saved-differential-audit-runbook](recommendation-same-profile-fresh-saved-differential-audit-runbook.md)
- [same-profile-origin-differential-audit-runbook](recommendation-same-profile-origin-differential-audit-runbook.md)
- [same-profile-path-differential-audit-runbook](recommendation-same-profile-path-differential-audit-runbook.md)
- [saved-batch-gap-audit-runbook](recommendation-saved-batch-gap-audit-runbook.md)
- [signal-gap-audit-runbook](recommendation-signal-gap-audit-runbook.md)

## AI 점수와 사유

- [ai-input-contrast-audit-runbook](recommendation-ai-input-contrast-audit-runbook.md)
- [ai-prompt-line-contrast-audit-runbook](recommendation-ai-prompt-line-contrast-audit-runbook.md)
- [ai-reason-contrast-audit-runbook](recommendation-ai-reason-contrast-audit-runbook.md)
- [ai-reason-coverage-audit-runbook](recommendation-ai-reason-coverage-audit-runbook.md)
- [ai-stage-gap-audit-runbook](recommendation-ai-stage-gap-audit-runbook.md)
- [ai-upstream-reason-trace-audit-runbook](recommendation-ai-upstream-reason-trace-audit-runbook.md)
- [ai-zero-cohort-audit-runbook](recommendation-ai-zero-cohort-audit-runbook.md)
- [ai-zero-contrast-audit-runbook](recommendation-ai-zero-contrast-audit-runbook.md)
- [ai-zero-reason-bucket-audit-runbook](recommendation-ai-zero-reason-bucket-audit-runbook.md)
- [ai-zero-reason-cohort-compare-runbook](recommendation-ai-zero-reason-cohort-compare-runbook.md)
- [ai-zero-reason-distribution-audit-runbook](recommendation-ai-zero-reason-distribution-audit-runbook.md)

## Snapshot과 gate 비교

- [AI exclusion 산출물 조회·비교·gate 해석](recommendation-ai-exclusion-latest-status-runbook.md): 기존 파일 기반 helper와 overview/readiness의 차이.
- [AI exclusion 증거 재수집](recommendation-ai-exclusion-baseline-refresh-runbook.md): suite/snapshot/drift/volatility/refresh 실제 실행과 쓰기·외부 호출 경계.
- [review-gate-blocker-audit-runbook](recommendation-review-gate-blocker-audit-runbook.md)
- [review-gate-recent-window-audit-runbook](recommendation-review-gate-recent-window-audit-runbook.md)
- [review-gate-staleness-audit-runbook](recommendation-review-gate-staleness-audit-runbook.md)

## 승인과 상태 변경

조회용 진단으로 실행하지 않습니다. write 범위·대상·복구와 명시적 작업 범위를 확인합니다.

- [bounded-promotion-review-runbook](recommendation-bounded-promotion-review-runbook.md)
- [real-user-cohort-library-manifest](recommendation-real-user-cohort-library-manifest.md)
- [region-mismatch-repair-runbook](recommendation-region-mismatch-repair-runbook.md)
- [review-gate-policy-promotion-checklist](recommendation-review-gate-policy-promotion-checklist.md)
- [review-gate-promotion-approval-record-smoke-runbook](recommendation-review-gate-promotion-approval-record-smoke-runbook.md)

## 과거 단계와 제안

특정 closeout/PR/관측 시점의 기록입니다. 최신 작업 지시나 기능 완료 증거로 재사용하지 않습니다.

- [단계별 개선 검토 기록](recommendation-next-lane-brief.md)
- [closeout PR merge 후 인계 기록](recommendation-post-merge-followup-checklist.md)
- [closeout PR draft 판단 기록](recommendation-pr-draft-exit-checklist.md)
- [closeout PR 검토 기록](recommendation-pr-review-brief.md)
- [primary-audience-exclusion-decision-memo](recommendation-primary-audience-exclusion-decision-memo.md)
- [standard-code-coverage-and-observation-closeout](recommendation-standard-code-coverage-and-observation-closeout.md)

## 결과 기록

실제 실행한 wrapper·설정·커밋·환경·artifact·비교 기준과 미실행 부분을 기록합니다. [작업 현황](../phase-plan.md)과 [정리 전 추천 관측](../history/recommendation/recommendation-state-before-doc-cleanup.md)을 구분합니다.
