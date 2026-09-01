# Start

작업 시작 전에는 이 파일만 먼저 읽습니다.

## 현재 원칙

- 현재 active main track은 기능 추가가 아니라 안정화와 회귀 방지입니다.
- 문서끼리 충돌하면 실제 코드, `current-state.md`, 각 문서군의 `*-current-state.md`, 최신 검증 artifact 순서로 봅니다.
- `phase-plan.md` 와 `troubleshooting-log.md` 는 긴 이력도 함께 담고 있으므로, 최신 상단 항목과 active 문서의 현재 기준을 우선합니다.
- `history/`, `archive/` 문서는 설계 배경과 과거 판단 기록입니다. 현재 실행 기준으로 쓰지 않습니다.

## 먼저 볼 파일

1. [current-state.md](./current-state.md)
2. teardown 또는 장기 중단 뒤 재개면 [core/restart-and-teardown-handoff-2026-09-01.md](./core/restart-and-teardown-handoff-2026-09-01.md)
3. [stabilization-handoff.md](./stabilization-handoff.md)
4. [core/stabilization-checklist.md](./core/stabilization-checklist.md)
5. [core/final-ops-closeout-checklist.md](./core/final-ops-closeout-checklist.md)
6. [work-guide.md](./work-guide.md)
7. 작업과 직접 관련된 문서군의 `*-docs-index.md` 또는 `*-current-state.md`

## 문서군 진입점

- 인증/세션: [auth/auth-docs-index.md](./auth/auth-docs-index.md)
- 수집: [collect/collect-docs-index.md](./collect/collect-docs-index.md)
- 추천: [recommendation/recommendation-docs-index.md](./recommendation/recommendation-docs-index.md)
- 정책 데이터/정규화: [policy/policy-docs-index.md](./policy/policy-docs-index.md)
- 프론트 QA: [frontend/frontend-qa-docs-index.md](./frontend/frontend-qa-docs-index.md)
- 성능: [performance/performance-docs-index.md](./performance/performance-docs-index.md)
- 공통 검증/운영: [core/system-docs-index.md](./core/system-docs-index.md)

## 검증 명령을 찾을 때

- 운영 closeout 순서: [core/final-ops-closeout-checklist.md](./core/final-ops-closeout-checklist.md)
- 로컬/통합 테스트 기준: [core/testing.md](./core/testing.md)
- 런타임 API smoke: [core/runtime-api-smoke-commands.md](./core/runtime-api-smoke-commands.md)
- 전체 문서 길찾기: [documentation-map.md](./documentation-map.md)

## 작업 기록

- 진행 상황은 [phase-plan.md](./phase-plan.md) 에 남깁니다.
- 재발 가능성이 있는 문제와 해결 이유는 [core/troubleshooting-log.md](./core/troubleshooting-log.md) 에 남깁니다.
- 새 문서를 만들기 전에 기존 current-state, runbook, docs-index에 흡수할 수 있는지 먼저 봅니다.
