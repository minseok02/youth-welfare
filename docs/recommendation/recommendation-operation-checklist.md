# 추천 / Replay 실행 체크리스트

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-current-state.md](./recommendation-current-state.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [history/ai/policy-normalization-education-priority-replay-procedure.md](../history/ai/policy-normalization-education-priority-replay-procedure.md)

## 목적

이 문서는 추천/리플레이 검증을 실제로 실행할 때
무엇부터 확인하고 어떤 순서로 해석할지 정리한 짧은 runbook 입니다.

## 1. 먼저 확인할 것

- 지금 확인하려는 게 `일반 추천 API` 인가
- `education replay` smoke 인가
- `rule-only` baseline 인가
- `real-openai` diagnostic 인가

이 네 가지를 먼저 구분합니다.

## 2. 일반 추천 확인

체크:

- 로그인 성공
- refresh 성공
- `/api/recommendations`
- 필요 시 bookmark / detail / refresh

확인 포인트:

- `success=true`
- `data[]` 구조 정상
- `unifiedCategory` 응답 계약 유지
- 저장 후 재조회 정상

## 3. replay 전 precondition

replay 전에 아래를 봅니다.

- `welfare_services` snapshot 존재
- canonical sidecar / read-model 데이터 존재
- target row 존재

현재는 replay script가 integrated schema 존재 여부와 zero target row 같은 precondition을 먼저 확인하고,
필요한 local helper 경계를 태워 self-heal 할 수 있습니다.

하지만:

- base snapshot 자체가 비어 있으면
- collect 또는 snapshot restore가 먼저 필요합니다.

## 4. rule-only replay

기본 검증선입니다.

확인:

- sample A target row top10 증가
- sample B control count 유지
- artifact 생성
- summary metric 확인

현재 핵심 metric:

- `A_top10_target`
- `B_top10_target`
- `A_target_total`
- `B_target_total`

현재 local broad-suite 기준선(2026-05-15)은 아래처럼 읽습니다.

- `A_top10_target=9->9`
- `B_top10_target=1->1`
- `A_fp=same`
- `B_fp=same`
- `reason_changed=0`

즉 현재 rule-only baseline에서는 target visibility regression이 없어야 하고,
fingerprint나 reason membership도 불필요하게 흔들리지 않는 상태를 정상으로 봅니다.

## 5. real-openai replay

이건 hard gate가 아니라 diagnostic 입니다.

확인:

- request trace 존재
- response trace 존재
- fingerprint relation 확인
- target row count 변화 확인

주의:

- same prompt / seed / fingerprint 여도 drift 가능
- score exact match 로 실패 판정하지 않음

## 6. 결과 해석

### rule-only 기준

정상:

- target row 개선
- control sample stable

이상:

- target 개선 사라짐
- control sample 이상 증가
- sidecar/projection 문제

### real-openai 기준

정상 범주:

- `ai_score` drift
- `final_score` drift
- same input인데 결과가 조금 다름

이상:

- request trace 자체가 달라짐
- fingerprint churn 없이 대규모 구조 변화
- target row visibility가 깨짐

## 7. 실행 후 남길 최소 기록

- mode: `rule-only` / `real-openai`
- summary metric
- artifact dir
- fingerprint relation
- collect/snapshot 전제 상태
- next action

## 요약

1. replay는 먼저 precondition을 본다.
2. `rule-only` 가 기본 검증선이다.
3. `real-openai` 는 diagnostic 이다.
4. exact score equality보다 target row visibility를 본다.
