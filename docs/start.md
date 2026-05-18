# Start

작업 시작 전에는 이 파일만 먼저 읽습니다.

주제 문서는 `docs/` 루트에 흩어두지 않고 `auth/`, `collect/`, `recommendation/`, `policy/`, `frontend/`, `postgres/`, `core/` 폴더로 정리했습니다.

## active 문서와 legacy/history 문서 경계

- 현재 작업 기준은 `project-spec.md`, `current-state.md`, `work-guide.md`, `phase-plan.md`, 그리고 각 문서군의 `*-current-state.md` 입니다.
- `history-docs-index.md`, `history/`, `archive/` 문서는 설계 배경과 과거 판단 기록입니다.
- 문서끼리 충돌하면 active 문서와 실제 코드를 우선합니다.
- 특히 실행 순서, 검증 방법, 현재 계약은 history 문서가 아니라 active 문서에서 확인합니다.

## 먼저 볼 파일

- [project-spec.md](./project-spec.md)
  프로젝트 기본 정보, 사용 기술, 버전, 실행 환경을 봅니다.

- [current-state.md](./current-state.md)
  지금 단계가 무엇인지, 무엇을 먼저 해야 하는지 봅니다.

- [work-guide.md](./work-guide.md)
  작업 순서와 문서/검증/Git 원칙을 봅니다.

- [phase-plan.md](./phase-plan.md)
  진행 상황, 완료 항목, 다음 작업을 봅니다.

- [github-workflow.md](./github-workflow.md)
  브랜치, 커밋, staging, PR 규칙을 봅니다.

## 지금 기준 한 줄 요약

- `2026-05-18` 기준 YOUTH/Gov24 신호는 `raw -> fact/token -> admin diagnostics -> detail read-only -> policy card compact badge -> admin facet` 까지 닫혔습니다.
- `2026-05-18` 기준 `collect/runtime governance` 도 `lane inventory -> latestRun -> config summary` 까지 닫혔습니다.
- `2026-05-18` 기준 recommendation 의 남은 `3257류` 이슈는 retrieval/신호 부족 버그가 아니라, AI가 `수급자/신혼부부/학생` 같은 primary audience mismatch를 강한 exclusion으로 해석하는 제품 판단 경계로 좁혀졌습니다.
- recommendation/collect 쪽은 기준선 유지 단계이고, 다음 active track은 `Gov24 canonical promotion` 설계로 보는 편이 맞습니다.

## 필요할 때 보는 파일

- [architecture.md](./architecture.md)
- [srs-v2.10.md](core/srs-v2.10.md)
- [testing.md](core/testing.md)
- [local-validation-docs-index.md](core/local-validation-docs-index.md)
- [system-docs-index.md](core/system-docs-index.md)
- [ops-baseline-runbook.md](core/ops-baseline-runbook.md)
- [history-docs-index.md](./history-docs-index.md)
- [documentation-map.md](./documentation-map.md)

`history-docs-index.md` 는 배경이 필요할 때만 추가로 봅니다.
- [auth-docs-index.md](auth/auth-docs-index.md)
- [collect-docs-index.md](collect/collect-docs-index.md)
- [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- [policy-docs-index.md](policy/policy-docs-index.md)

## 폴더별 진입점

- [core/README.md](core/README.md)
- [auth/README.md](auth/README.md)
- [collect/README.md](collect/README.md)
- [recommendation/README.md](recommendation/README.md)
- [policy/README.md](policy/README.md)
- [frontend/README.md](frontend/README.md)
- [postgres/README.md](postgres/README.md)

## 주제별 현재 상태

- 인증 문서군 진입점: [auth-docs-index.md](auth/auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](collect/collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- 프론트 QA 문서군 진입점: [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- 정책 문서군 진입점: [policy-docs-index.md](policy/policy-docs-index.md)
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- 인증/세션: [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md)
- 수집: [collect-current-state.md](collect/collect-current-state.md)
- 추천: [recommendation-current-state.md](recommendation/recommendation-current-state.md)
- 정책 정규화: [policy-normalization-current-state.md](policy/policy-normalization-current-state.md)
- 다음 active track 우선순위: [policy-next-active-track-priority.md](policy/policy-next-active-track-priority.md)
- 로컬 closeout pending: [policy-local-closeout-pending-inventory.md](policy/policy-local-closeout-pending-inventory.md)
- `Gov24` runtime closeout / blocked-deferred track: [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
- `Gov24` canonical promotion 설계: [policy-gov24-canonical-promotion-plan.md](policy/policy-gov24-canonical-promotion-plan.md)
- 신규 source 구조: [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md)

배경 이력이 필요할 때만:

- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)
