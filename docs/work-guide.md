# 작업 가이드

이 문서는 작업 시작과 진행 순서만 짧게 고정합니다.

## 시작 순서

1. [current-state.md](./current-state.md)
2. [phase-plan.md](./phase-plan.md)
3. 작업과 직접 관련된 `*-docs-index.md` 또는 current-state 문서
4. 필요한 코드

작업 이력이나 과거 판단이 더 필요할 때만 [troubleshooting-log.md](core/troubleshooting-log.md) 또는 [documentation-map.md](./documentation-map.md)을 추가로 봅니다.

## 작업 원칙

- 없는 환경을 있다고 가정하지 않습니다.
- 현재 상태 문서와 이력 문서를 구분해서 읽습니다.
- 작업은 작은 task 단위로 나눕니다.
- 새 문서를 만들기 전에 기존 문서에 흡수 가능한지 먼저 봅니다.
- 코드와 문서가 다르면 실제 코드와 검증 결과를 우선합니다.

## 작업 흐름

1. 현재 상태를 짧게 요약
2. 작은 task로 분해
3. 관련 파일만 수정
4. 가능한 테스트/스모크 실행
5. 결과를 `phase-plan.md` 에 반영
6. 재발 가능성이 있으면 `troubleshooting-log.md` 에 기록
7. Git 작업은 [github-workflow.md](./github-workflow.md) 규칙 적용

## 문서 규칙

- `README.md` 는 짧은 진입점만 유지합니다.
- top-level 진입점에서는 먼저 `*-docs-index.md` 를 찾고, 그 다음 current-state / checklist / template 로 내려갑니다.
- `phase-plan.md` 는 진행 상황을 기록합니다.
- `troubleshooting-log.md` 는 문제 / 해결 / 이유를 기록합니다.
- 상세 배경 문서는 필요할 때만 봅니다.
