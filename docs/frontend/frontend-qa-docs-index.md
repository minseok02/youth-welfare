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

을 브라우저 기준으로 빠르게 보는 current-state 문서입니다.

### 2. 실행 체크리스트

- [frontend-qa-checklist.md](./frontend-qa-checklist.md)

이 문서는

- 공개 탐색
- 보호 경로
- 세션 만료
- 북마크
- 마이페이지
- 추천 / 챗봇

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

### 실제 수동 QA를 돌릴 때

1. [frontend-qa-checklist.md](./frontend-qa-checklist.md)
2. [frontend-qa-current-state.md](./frontend-qa-current-state.md)
3. 필요하면 [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
4. 시간이 제한되면 checklist의 `권장 실행 순서 -> 1차 고위험 동선` 다섯 개를 먼저 돌립니다.

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
4. 결과 기록은 [frontend-qa-template.md](./frontend-qa-template.md) 를 기준으로 남기고, pass/fail보다 URL/query/state 증거를 먼저 적습니다.
