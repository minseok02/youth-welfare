# 성능 문서 묶음

## 목적

성능 baseline 관련 문서와 실행 스크립트가 흩어져 있어도

- 어떤 baseline을 먼저 돌릴지
- 어떤 수치를 현재 기준으로 볼지
- 최적화 전후 비교를 어디에 남길지

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 현재 baseline과 실행 순서

- [performance-baseline-current.md](./performance-baseline-current.md)
- [performance-measurement-plan.md](./performance-measurement-plan.md)

### 같이 보면 좋은 문서

- [current-state.md](../current-state.md)
- [work-guide.md](../work-guide.md)
- [local-validation-docs-index.md](../core/local-validation-docs-index.md)
- [ops-baseline-runbook.md](../core/ops-baseline-runbook.md)
- [openai-runtime-contract.md](../core/openai-runtime-contract.md)
- [phase-plan.md](../phase-plan.md)

## 문서 역할

### 1. 현재 baseline

- [performance-baseline-current.md](./performance-baseline-current.md)

이 문서는

- 현재 accepted baseline 명령
- latest artifact 경로
- 현재 수치 요약
- known gap

을 빠르게 확인하는 current-state 문서입니다.

### 2. 측정 계획

- [performance-measurement-plan.md](./performance-measurement-plan.md)

이 문서는

- 왜 이 수치를 재는지
- baseline / load / stress / soak 를 어떻게 나눌지
- 어떤 스크립트를 어떤 목적에 쓰는지

를 정리한 measurement plan 입니다.

## 스크립트 진입점

### 가장 먼저 돌릴 것

- `bash deploy/performance/run-local-performance-baseline-suite.sh`

이 wrapper는

- API latency
- DB query baseline
- Redis baseline
- 필요하면 wrapper duration

을 한 번에 묶습니다.

### 더 깊게 볼 때

- `bash deploy/performance/run-local-performance-extended-suite.sh`
- `bash deploy/performance/run-local-performance-deep-observation-suite.sh`
- `bash deploy/performance/run-local-stateful-flow-duration-baseline.sh`

## 읽는 순서

### 성능 작업을 새로 열 때

1. [performance-baseline-current.md](./performance-baseline-current.md)
2. [performance-measurement-plan.md](./performance-measurement-plan.md)
3. 필요한 wrapper script
4. [phase-plan.md](../phase-plan.md)

### 최적화 전후 비교를 할 때

1. `performance-baseline-current.md` 의 현재 accepted 값 확인
2. 같은 wrapper와 같은 입력으로 재실행
3. delta를 문서와 artifact에 같이 남김

## 한 줄 요약

1. 현재 수치는 [performance-baseline-current.md](./performance-baseline-current.md) 를 먼저 봅니다.
2. 왜 그 수치를 재는지는 [performance-measurement-plan.md](./performance-measurement-plan.md) 를 봅니다.
3. 실행은 `deploy/performance` wrapper를 그대로 재사용합니다.
