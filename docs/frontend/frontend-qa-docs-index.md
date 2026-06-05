# 프론트 QA 문서 묶음

## 목적

`frontend-qa-*` 문서가 흩어져 있어도

- 현재 프론트 QA를 어떤 기준으로 보는지
- 실제 브라우저 검증 때 어떤 문서를 먼저 봐야 하는지
- 결과를 어디에 어떤 형식으로 남기는지

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 현재 코드/로컬 검증 기준

- [frontend-qa-current-state.md](./frontend-qa-current-state.md)
- [frontend-observation-runbook.md](./frontend-observation-runbook.md)
- [frontend-qa-checklist.md](./frontend-qa-checklist.md)
- [frontend-core-user-flow-runbook.md](./frontend-core-user-flow-runbook.md)
- [frontend-policy-search-detail-flow-runbook.md](./frontend-policy-search-detail-flow-runbook.md)
- [frontend-authenticated-user-flow-runbook.md](./frontend-authenticated-user-flow-runbook.md)
- [frontend-protected-user-flow-runbook.md](./frontend-protected-user-flow-runbook.md)
- [frontend-session-recovery-runbook.md](./frontend-session-recovery-runbook.md)
- [frontend-retention-flow-runbook.md](./frontend-retention-flow-runbook.md)
- [frontend-account-lifecycle-runbook.md](./frontend-account-lifecycle-runbook.md)
- [frontend-admin-operator-flow-runbook.md](./frontend-admin-operator-flow-runbook.md)
- [frontend-recommendation-chat-flow-runbook.md](./frontend-recommendation-chat-flow-runbook.md)
- [frontend-help-surface-runbook.md](./frontend-help-surface-runbook.md)

### 같이 보면 좋은 기준 문서

- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [testing.md](../core/testing.md)
- [auth-operation-checklist.md](../auth/auth-operation-checklist.md)
- [recommendation-operation-checklist.md](../recommendation/recommendation-operation-checklist.md)
- [phase-plan.md](../phase-plan.md)

## 문서 역할

### 1. 현재 동작 기준

- [frontend-qa-current-state.md](./frontend-qa-current-state.md)

이 문서는

- 로그인 필요 경로
- 세션 만료 경로
- 뒤로가기 / 재진입
- 북마크 / 마이페이지 / 추천 / 챗봇
- `/guide` / `/support` / `정책 오류 제보`
- 로그인 후 메인 `가이드 배너 / 추천 보강`
- 정책 검색 / 필터 / 상세 읽기
- 보호 경로 `/chat` / `/mypage`
- 세션 만료 / reset-password 복구
- 알림 / 북마크 재방문 유지
- 계정 생성 / 비밀번호 변경 / 재로그인
- 운영자 대시보드 진입 / queue / attention
- 추천 / 챗봇 보조 흐름

을 브라우저 기준으로 빠르게 보는 current-state 문서입니다.

### 2. 실행 체크리스트

- [frontend-qa-checklist.md](./frontend-qa-checklist.md)

이 문서는

- 공개 탐색
- 공개 핵심 사용자 흐름
- 보호 경로
- 세션 만료
- 북마크
- 마이페이지
- 추천 / 챗봇
- 도움 경로

을 실제 수동 QA 순서대로 따라가는 runbook 입니다.

### 3. 결과 기록 템플릿

- [frontend-qa-template.md](./frontend-qa-template.md)
- [frontend-qa-session-2026-05-15.md](./frontend-qa-session-2026-05-15.md)
- [frontend-qa-session-2026-05-17.md](./frontend-qa-session-2026-05-17.md)

브라우저 QA 결과를 남길 때 복사해서 쓰는 템플릿입니다.
`frontend-qa-session-2026-05-15.md` 는 오늘 기준 high-risk 동선과 baseline을 미리 채워 둔 세션 시트입니다.
`frontend-qa-session-2026-05-17.md` 는 admin dashboard 수동 QA 결과를 남긴 최신 세션 시트입니다.

## 읽는 순서

### 현재 상태만 빨리 확인할 때

1. [frontend-qa-current-state.md](./frontend-qa-current-state.md)
2. [frontend-qa-checklist.md](./frontend-qa-checklist.md)
3. 공개 핵심 흐름은 [frontend-core-user-flow-runbook.md](./frontend-core-user-flow-runbook.md)
4. 검색/상세 흐름은 [frontend-policy-search-detail-flow-runbook.md](./frontend-policy-search-detail-flow-runbook.md)
5. 로그인 사용자 흐름은 [frontend-authenticated-user-flow-runbook.md](./frontend-authenticated-user-flow-runbook.md)
6. 보호 경로 흐름은 [frontend-protected-user-flow-runbook.md](./frontend-protected-user-flow-runbook.md)
7. 세션 복구 흐름은 [frontend-session-recovery-runbook.md](./frontend-session-recovery-runbook.md)
8. 개인 유지 흐름은 [frontend-retention-flow-runbook.md](./frontend-retention-flow-runbook.md)
9. 계정 라이프사이클은 [frontend-account-lifecycle-runbook.md](./frontend-account-lifecycle-runbook.md)
10. admin 운영 흐름은 [frontend-admin-operator-flow-runbook.md](./frontend-admin-operator-flow-runbook.md)
11. 추천/챗봇 흐름은 [frontend-recommendation-chat-flow-runbook.md](./frontend-recommendation-chat-flow-runbook.md)
12. 도움 경로는 [frontend-help-surface-runbook.md](./frontend-help-surface-runbook.md)

### 실제 수동 QA를 돌릴 때

1. [frontend-qa-checklist.md](./frontend-qa-checklist.md)
2. [frontend-qa-current-state.md](./frontend-qa-current-state.md)
3. 공개 핵심 흐름은 [frontend-core-user-flow-runbook.md](./frontend-core-user-flow-runbook.md)
4. 검색/상세 흐름은 [frontend-policy-search-detail-flow-runbook.md](./frontend-policy-search-detail-flow-runbook.md)
5. 로그인 사용자 흐름은 [frontend-authenticated-user-flow-runbook.md](./frontend-authenticated-user-flow-runbook.md)
6. 보호 경로 흐름은 [frontend-protected-user-flow-runbook.md](./frontend-protected-user-flow-runbook.md)
7. 세션 복구 흐름은 [frontend-session-recovery-runbook.md](./frontend-session-recovery-runbook.md)
8. 개인 유지 흐름은 [frontend-retention-flow-runbook.md](./frontend-retention-flow-runbook.md)
9. 계정 라이프사이클은 [frontend-account-lifecycle-runbook.md](./frontend-account-lifecycle-runbook.md)
10. admin 운영 흐름은 [frontend-admin-operator-flow-runbook.md](./frontend-admin-operator-flow-runbook.md)
11. 추천/챗봇 흐름은 [frontend-recommendation-chat-flow-runbook.md](./frontend-recommendation-chat-flow-runbook.md)
12. 도움 경로는 [frontend-help-surface-runbook.md](./frontend-help-surface-runbook.md)
13. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
14. 시간이 제한되면 checklist의 `권장 실행 순서 -> 1차 고위험 동선` 다섯 개를 먼저 돌립니다.

### 결과를 남길 때

1. [frontend-qa-template.md](./frontend-qa-template.md)
2. 바로 실행을 시작하려면 [frontend-qa-session-2026-05-15.md](./frontend-qa-session-2026-05-15.md) 를 사용합니다.
3. admin dashboard 결과를 확인하려면 [frontend-qa-session-2026-05-17.md](./frontend-qa-session-2026-05-17.md) 를 사용합니다.
4. 재현 URL, query/state, `reason`, `state.from`, `?session=`/`?tab=` 같은 증거를 먼저 채웁니다.
5. 이슈가 재현되면 [troubleshooting-log.md](../core/troubleshooting-log.md)
6. 실행 결과를 active 기준선에 반영할 필요가 있을 때만 [phase-plan.md](../phase-plan.md)

## 요약

1. 현재 프론트 QA 기준은 [frontend-qa-current-state.md](./frontend-qa-current-state.md) 부터 봅니다.
2. daily operator 관점의 compact handoff는 [frontend-observation-runbook.md](./frontend-observation-runbook.md) 와 `bash deploy/smoke/run-local-frontend-observation-suite.sh` 를 먼저 봅니다.
3. 실제 브라우저 검증은 [frontend-qa-checklist.md](./frontend-qa-checklist.md) 기준으로 진행합니다.
4. 공개 onboarding/search/detail 흐름은 [frontend-core-user-flow-runbook.md](./frontend-core-user-flow-runbook.md) 를 먼저 봅니다.
5. 검색 query/filter/detail은 [frontend-policy-search-detail-flow-runbook.md](./frontend-policy-search-detail-flow-runbook.md) 를 먼저 봅니다.
6. 로그인 사용자 메인 보강 흐름은 [frontend-authenticated-user-flow-runbook.md](./frontend-authenticated-user-flow-runbook.md) 를 먼저 봅니다.
7. 보호 경로와 로그인 복귀는 [frontend-protected-user-flow-runbook.md](./frontend-protected-user-flow-runbook.md) 를 먼저 봅니다.
8. 세션 만료와 reset-password는 [frontend-session-recovery-runbook.md](./frontend-session-recovery-runbook.md) 를 먼저 봅니다.
9. 알림/북마크 재방문은 [frontend-retention-flow-runbook.md](./frontend-retention-flow-runbook.md) 를 먼저 봅니다.
10. 계정 생성/비밀번호 재설정/재로그인은 [frontend-account-lifecycle-runbook.md](./frontend-account-lifecycle-runbook.md) 를 먼저 봅니다.
11. admin 운영 경로는 [frontend-admin-operator-flow-runbook.md](./frontend-admin-operator-flow-runbook.md) 를 먼저 봅니다.
12. 추천/챗봇 보조 흐름은 [frontend-recommendation-chat-flow-runbook.md](./frontend-recommendation-chat-flow-runbook.md) 를 먼저 봅니다.
13. `/guide` / `/support` / `정책 오류 제보` 구분은 [frontend-help-surface-runbook.md](./frontend-help-surface-runbook.md) 를 먼저 봅니다.
14. 결과 기록은 [frontend-qa-template.md](./frontend-qa-template.md) 를 기준으로 남기고, pass/fail보다 URL/query/state 증거를 먼저 적습니다.
