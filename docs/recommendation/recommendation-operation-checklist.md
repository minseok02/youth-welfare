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

기본 실행 wrapper:

- `KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh`

확인:

- sample A target row visibility 유지 또는 개선
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
현재 broad-suite current baseline은 예전 rescue snapshot처럼 `0->1` 개선을 매번 요구하는 단계가 아니라,
이미 확보한 target visibility가 깨지지 않는지를 먼저 보는 단계입니다.

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

- target row visibility 유지 또는 개선
- control sample stable

이상:

- target visibility regression
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

## 7. Gov24 bounded signal 확인

Gov24 source 자체가 구조적으로 억눌리는지 빠르게 확인할 때는
개별 정책 추적보다 bounded smoke를 먼저 봅니다.

기본 wrapper:

- `bash deploy/smoke/run-local-gov24-signal-suite.sh`

이 wrapper는 아래 두 fresh smoke를 순차 실행합니다.

- `run-local-gov24-housing-signal-smoke.sh`
  - `주거` 관심 + `HOUSING` priority fresh user
- `run-local-gov24-education-signal-smoke.sh`
  - `교육·직업훈련` 관심 + `EDUCATION` priority + `경기도/안산시` fresh user

확인:

- `[housing] gov24_top2_rows`
- `[education] gov24_top2_rows`
- 각 bounded smoke의 `top2_source_distribution`
- Gov24 상위 row의 `rule / ai / final`

현재 local 기준선(2026-05-17):

- `housing`
  - `gov24_top2_rows=1`
  - `5728 주택금융공사 월세자금보증`
  - `rank2`, `rule=54`, `ai=70`, `final=0.63261`
- `education`
  - `gov24_top2_rows=1`
  - `6790 지역인재육성을 위한 장학금 지원`
  - `rank1`, `rule=27`, `ai=80`, `final=1.03936`

읽는 법:

- 두 bounded smoke가 모두 green이면
  - Gov24 source 전체가 구조적으로 막혀 있다고 보긴 어렵습니다.
- `housing` 만 약하면
  - 주거/월세보증 계열 rule-side 신호를 더 봅니다.
- `education` 만 약하면
  - 지역/학생 장학금 계열의 AI/context 적합도를 더 봅니다.
- 둘 다 약하면
  - source 전체 retrieval/rule/AI 경계를 다시 봐야 합니다.

## 8. 실행 후 남길 최소 기록

- mode: `rule-only` / `real-openai`
- wrapper / command
- summary metric
- artifact dir
- fingerprint relation
- collect/snapshot 전제 상태
- next action

## 9. 운영 `REAL_USER` 기준선 확인

로컬 bounded seed가 아니라 운영 실사용 표본을 읽을 때는 아래 문서를 먼저 봅니다.

- [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)

운영 read-only wrapper:

- `deploy/smoke/run-local-real-user-readiness-check.sh`

이 단계에서는

- `REAL_USER users >= 3`
- `REAL_USER clicked users >= 3`
- `realUserTrafficGateInWindow`
- `recommendationReviewGate`

를 먼저 확인하고, gate가 열려도 `READY_CONCENTRATED_TOP1_REVIEW` 면 집중/분산 해석을 먼저 봅니다.

## 요약

1. replay는 먼저 precondition을 본다.
2. `rule-only` 가 기본 검증선이다.
3. `real-openai` 는 diagnostic 이다.
4. 운영 `REAL_USER` 표본 해석은 `recommendation-real-user-baseline-runbook` 기준으로 본다.
5. Gov24 source 전체 구조 의심은 bounded signal suite로 먼저 가른다.
6. exact score equality보다 target row visibility를 본다.
