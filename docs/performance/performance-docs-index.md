# 성능 문서 묶음

## 목적

성능 baseline 관련 문서와 실행 스크립트가 흩어져 있어도

- 어떤 baseline을 먼저 돌릴지
- 어떤 수치를 현재 기준으로 볼지
- 최적화 전후 비교를 어디에 남길지

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 현재 baseline과 실행 순서

- [alb-current-baseline-2026-07-13.md](./alb-current-baseline-2026-07-13.md)
- [alb-measurement-2026-07-14.md](./alb-measurement-2026-07-14.md)
- [performance-baseline-current.md](./performance-baseline-current.md)
- [performance-optimization-log.md](./performance-optimization-log.md)
- [performance-change-ledger-2026-07-14.md](./performance-change-ledger-2026-07-14.md)
- [post-optimization-stability-check-2026-07-14.md](./post-optimization-stability-check-2026-07-14.md)
- [policy-cache-tail-measurement-2026-07-14.md](./policy-cache-tail-measurement-2026-07-14.md)
- [ranking-cold-breakdown-measurement-2026-07-14.md](./ranking-cold-breakdown-measurement-2026-07-14.md)
- [ranking-app-timing-instrumentation-2026-07-15.md](./ranking-app-timing-instrumentation-2026-07-15.md)
- [ranking-candidate-mode-evaluation-2026-07-15.md](./ranking-candidate-mode-evaluation-2026-07-15.md)
- [ranking-candidate-runtime-plan-2026-07-15.md](./ranking-candidate-runtime-plan-2026-07-15.md)
- [ranking-precomputed-snapshot-plan-2026-07-15.md](./ranking-precomputed-snapshot-plan-2026-07-15.md)
- [post-snapshot-current-baseline-2026-07-15.md](./post-snapshot-current-baseline-2026-07-15.md)
- [policy-list-timing-instrumentation-2026-07-15.md](./policy-list-timing-instrumentation-2026-07-15.md)
- [policy-list-projection-query-optimization-2026-07-15.md](./policy-list-projection-query-optimization-2026-07-15.md)
- [post-projection-current-baseline-2026-07-15.md](./post-projection-current-baseline-2026-07-15.md)
- [post-projection-load-soak-2026-07-15.md](./post-projection-load-soak-2026-07-15.md)
- [search-timing-instrumentation-2026-07-15.md](./search-timing-instrumentation-2026-07-15.md)
- [search-generated-fields-optimization-2026-07-16.md](./search-generated-fields-optimization-2026-07-16.md)
- [search-summary-projection-optimization-2026-07-16.md](./search-summary-projection-optimization-2026-07-16.md)
- [search-miss-remeasurement-2026-07-16.md](./search-miss-remeasurement-2026-07-16.md)
- [post-summary-stability-check-2026-07-16.md](./post-summary-stability-check-2026-07-16.md)
- [final-load-capacity-check-2026-07-16.md](./final-load-capacity-check-2026-07-16.md)
- [report-grade-performance-measurement-plan-2026-07-16.md](./report-grade-performance-measurement-plan-2026-07-16.md)
- [report-grade-read-api-measurement-2026-07-16.md](./report-grade-read-api-measurement-2026-07-16.md)
- [report-grade-auth-flow-measurement-2026-07-16.md](./report-grade-auth-flow-measurement-2026-07-16.md)
- [report-grade-recommendation-flow-measurement-2026-07-16.md](./report-grade-recommendation-flow-measurement-2026-07-16.md)
- [ranking-snapshot-rollback-runbook-2026-07-15.md](./ranking-snapshot-rollback-runbook-2026-07-15.md)
- [performance-next-checkpoint-2026-07-14.md](./performance-next-checkpoint-2026-07-14.md)
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

- [alb-current-baseline-2026-07-13.md](./alb-current-baseline-2026-07-13.md)
- [alb-measurement-2026-07-14.md](./alb-measurement-2026-07-14.md)
- [performance-baseline-current.md](./performance-baseline-current.md)
- [performance-optimization-log.md](./performance-optimization-log.md)

이 문서는

- ALB 전환 후 외부 기준 URL의 accepted baseline
- 현재 accepted baseline 명령
- latest artifact 경로
- 현재 수치 요약
- known gap

을 빠르게 확인하는 current-state 문서입니다.

### 1-1. 최적화 로그

- [performance-optimization-log.md](./performance-optimization-log.md)
- [performance-change-ledger-2026-07-14.md](./performance-change-ledger-2026-07-14.md)

이 문서는

- 어떤 병목 수치가 작업을 열게 했는지
- 무엇을 어떻게 바꿨는지
- before/after delta가 서버에서 어떻게 닫혔는지

를 기록하는 change log 입니다.

`performance-change-ledger-2026-07-14.md` 는 여러 문서에 흩어진 2026-07-14 작업을 문제, 변경, 이유, 전/후 수치, 현재 판정으로 압축한 한 장짜리 ledger입니다.

### 1-2. 안정화와 다음 병목 측정

- [post-optimization-stability-check-2026-07-14.md](./post-optimization-stability-check-2026-07-14.md)
- [policy-cache-tail-measurement-2026-07-14.md](./policy-cache-tail-measurement-2026-07-14.md)
- [ranking-cold-breakdown-measurement-2026-07-14.md](./ranking-cold-breakdown-measurement-2026-07-14.md)
- [ranking-app-timing-instrumentation-2026-07-15.md](./ranking-app-timing-instrumentation-2026-07-15.md)
- [ranking-candidate-mode-evaluation-2026-07-15.md](./ranking-candidate-mode-evaluation-2026-07-15.md)
- [ranking-candidate-runtime-plan-2026-07-15.md](./ranking-candidate-runtime-plan-2026-07-15.md)
- [ranking-precomputed-snapshot-plan-2026-07-15.md](./ranking-precomputed-snapshot-plan-2026-07-15.md)
- [post-snapshot-current-baseline-2026-07-15.md](./post-snapshot-current-baseline-2026-07-15.md)
- [policy-list-timing-instrumentation-2026-07-15.md](./policy-list-timing-instrumentation-2026-07-15.md)
- [policy-list-projection-query-optimization-2026-07-15.md](./policy-list-projection-query-optimization-2026-07-15.md)
- [post-projection-current-baseline-2026-07-15.md](./post-projection-current-baseline-2026-07-15.md)
- [post-projection-load-soak-2026-07-15.md](./post-projection-load-soak-2026-07-15.md)
- [search-timing-instrumentation-2026-07-15.md](./search-timing-instrumentation-2026-07-15.md)
- [search-generated-fields-optimization-2026-07-16.md](./search-generated-fields-optimization-2026-07-16.md)
- [search-summary-projection-optimization-2026-07-16.md](./search-summary-projection-optimization-2026-07-16.md)
- [search-miss-remeasurement-2026-07-16.md](./search-miss-remeasurement-2026-07-16.md)
- [post-summary-stability-check-2026-07-16.md](./post-summary-stability-check-2026-07-16.md)
- [final-load-capacity-check-2026-07-16.md](./final-load-capacity-check-2026-07-16.md)
- [report-grade-performance-measurement-plan-2026-07-16.md](./report-grade-performance-measurement-plan-2026-07-16.md)
- [report-grade-read-api-measurement-2026-07-16.md](./report-grade-read-api-measurement-2026-07-16.md)
- [report-grade-auth-flow-measurement-2026-07-16.md](./report-grade-auth-flow-measurement-2026-07-16.md)
- [report-grade-recommendation-flow-measurement-2026-07-16.md](./report-grade-recommendation-flow-measurement-2026-07-16.md)
- [ranking-snapshot-rollback-runbook-2026-07-15.md](./ranking-snapshot-rollback-runbook-2026-07-15.md)
- [performance-next-checkpoint-2026-07-14.md](./performance-next-checkpoint-2026-07-14.md)

이 문서는

- 기능/운영 안정성이 유지되는지
- cache cold/warm 구분 후 다음 병목이 무엇인지
- ranking cold 비용이 DB/계산/앱 내부 중 어디에 가까운지
- ranking cold 비용이 live app 내부 어느 단계에 몰리는지
- ranking 후보 축소가 품질을 보존하는지
- ranking 후보 축소를 runtime guard 뒤에 어떻게 구현할지
- ranking 계산을 요청 경로 밖으로 빼는 precomputed snapshot을 어떻게 붙일지
- snapshot 적용 후 현재 baseline에서 다음 병목을 어디로 볼지
- policy list 기본 목록 tail이 앱 내부 어느 단계에 몰리는지
- policy list projection SQL을 어떤 근거로 바꾸고 결과가 어땠는지
- projection 최적화 후 다음 병목을 열지 말지
- projection 최적화 후 낮은 동시성 지속 부하에서도 안정적인지
- search keyword/filtered 병목이 앱 내부 어디에 몰리는지
- search filtered/deadline SQL을 왜 generated fields로 바꿨는지
- search summary enrichment를 왜 summary-only projection으로 분리했는지
- summary projection 이후 fresh search miss tail이 반복 병목인지
- 성능 작업 이후 현재 배포본이 운영/기능/로그 기준으로 안정적인지
- 최종 배포본이 단계별 부하에서 어디까지 오류 없이 버티고 어디서 rate limit이 시작되는지
- 결과보고서용 공식 측정을 조회/인증/추천/챗봇/통합 여정으로 어떻게 분리해서 실행할지
- 결과보고서용 공개 조회 API 대표 응답시간이 ALB 경유 환경에서 어느 정도인지
- 결과보고서용 인증 흐름 대표 응답시간과 rate-limit 회피 조건이 어느 정도인지
- 결과보고서용 추천 조회/생성 대표 응답시간과 OpenAI 영향이 어느 정도인지
- ranking snapshot을 어떻게 되돌릴지
- 재측정 후 다음 작업을 열지 말지

를 순서대로 확인하는 문서입니다.

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
2. [performance-optimization-log.md](./performance-optimization-log.md)
3. [performance-measurement-plan.md](./performance-measurement-plan.md)
4. 필요한 wrapper script
5. [phase-plan.md](../phase-plan.md)

### 최적화 전후 비교를 할 때

1. `performance-baseline-current.md` 의 현재 accepted 값 확인
2. `performance-optimization-log.md` 에 이번 변경 배경과 before 값을 먼저 기록
3. 같은 wrapper와 같은 입력으로 재실행
4. delta를 문서와 artifact에 같이 남김

## 한 줄 요약

1. 현재 수치는 [performance-baseline-current.md](./performance-baseline-current.md) 를 먼저 봅니다.
2. 왜 그 수치를 재는지는 [performance-measurement-plan.md](./performance-measurement-plan.md) 를 봅니다.
3. 실행은 `deploy/performance` wrapper를 그대로 재사용합니다.
