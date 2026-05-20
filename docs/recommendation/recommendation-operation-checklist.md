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

## 8. AI exclusion baseline 확인

현재 로컬에서 AI exclusion evidence를 한 번에 다시 태울 때는 아래 suite를 먼저 씁니다.

PR / handoff / reopen 문서를 같이 볼 때는 아래 순서를 기준으로 읽습니다.

1. 범위/현재 해석 quick summary
   - [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
2. 현재 draft 유지/해제 기준
   - [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
3. merge 뒤 follow-up current 해석
   - [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
4. `REAL_USER` traffic/cohort가 생긴 뒤 재실행 순서
   - [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)

daily operator entrypoint:

- `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh`
- `REAL_USER` readiness까지 같이 보려면
  - `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' INCLUDE_REAL_USER_READINESS=true bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh`
- latest overview artifact:
  - `tmp/recommendation-ai-exclusion-latest-overview/latest-overview-summary.txt`
  - `tmp/recommendation-ai-exclusion-latest-overview/latest-overview-note.md`
  - `tmp/recommendation-ai-exclusion-latest-overview/latest-overview.json`

기본 wrapper:

- `bash deploy/smoke/run-local-recommendation-ai-exclusion-suite.sh`
- 날짜별 summary artifact까지 같이 남길 때는 `bash deploy/smoke/run-local-recommendation-ai-exclusion-snapshot.sh`

이 wrapper는 아래 네 축을 순서대로 다시 봅니다.

- `[stage-gap]`
- `[zero-reason-bucket]`
- `[cohort-compare]`
- `[real-user-ready]`

현재 local 기준선(2026-05-19 historical pre-live baseline):

- `[stage-gap]`
  - fresh target family 기준 zero-AI 핵심은 `3289`, `5837`
  - same refresh top window에는 `3290`, `3611` 도 `savedAi=0` 로 함께 보일 수 있습니다.
- `[zero-reason-bucket]`
  - `INCOME_MISMATCH:1`
  - `STUDENT_AUDIENCE_MISMATCH:2`
  - `OTHER:1`
- `[cohort-compare]`
  - `baseline_zero_ai_reason_buckets=AUDIENCE_MISMATCH:4,STUDENT_AUDIENCE_MISMATCH:4`
  - `target_scope_users=0`
- `[real-user-ready]`
  - `dashboard_real_user_gate=DEFERRED_NO_REAL_USER_TRAFFIC`
  - `breakdown_real_user_cohort_gate=DEFERRED_NO_REAL_USER_COHORT`

위 블록은 **live readiness가 열리기 전 local historical sample output** 입니다. current live truth는 아래의 “지금 local에서 recommendation 관찰을 다시 시작할 때”와 `latest-overview/readiness/review-gate` 현재형 해석을 우선합니다.

읽는 법:

- fresh zero-AI가 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 중심이면
  - broad signal 부족보다 product exclusion 유지 쪽으로 읽습니다.
- `cohort-compare` 의 `non_example` baseline이 계속 같은 bucket이면
  - local seed latest batch에서도 같은 exclusion 패턴이 반복된다는 뜻입니다.
- `real-user-ready` 가 아직 deferred면
  - 운영 `REAL_USER` 분포 비교는 아직 열지 않습니다.
- `OTHER` bucket이 1건 정도 섞여도
  - 지금 local baseline의 주된 해석은 여전히 audience exclusion 중심입니다.
- 직전 실행과 drift만 보고 싶으면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-snapshot-compare.sh` 로 summary 2개를 바로 비교합니다.
- 새 snapshot 생성과 compare를 한 번에 끝내려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-drift-check.sh` 를 씁니다.
- fresh target window 변동성까지 보고 싶으면
  - `RUN_COUNT=3 bash deploy/smoke/run-local-recommendation-ai-exclusion-volatility-audit.sh` 를 씁니다.
  - current local `RUN_COUNT=2` baseline은 `fresh_top_ai_zero_count_frequency=2:1,4:1` 이고, drift는 fresh window key에만 나타났습니다.
- volatility audit와 baseline report를 one-shot으로 다시 닫으려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh.sh` 를 먼저 씁니다.
  - 현재 최신 refresh 판정은 `drift_class=VOLATILE_ONLY_DRIFT`, `recommended_reading=READ_LATEST_AS_VOLATILE_OBSERVATION` 입니다.
- refresh compact summary 두 개만 high-signal 기준으로 바로 비교하려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh` 를 씁니다.
  - `stable_baseline_changed=false`, `latest_observation_changed=true` 면 fresh window 관찰값만 흔들린 것으로 읽습니다.
- 새 refresh를 다시 태운 뒤 직전 refresh summary와 one-shot으로 비교하려면
  - `RUN_COUNT=2 bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh` 를 씁니다.
  - 긴 compare 출력 대신 compact key만 보려면 같은 artifact의 `baseline-refresh-drift-summary.txt` 또는 `latest-baseline-refresh-drift-summary.txt` 를 봅니다.
- 재실행 없이 지금 latest baseline/drift 상태만 바로 보려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh` 를 씁니다.
- 재실행 없이 붙여넣기 가능한 Markdown 메모를 바로 만들려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh` 를 씁니다.
  - 같은 실행에서 machine-readable artifact가 필요하면 `latest-status.json` 또는 `latest_json_link` 를 봅니다.
- latest JSON을 자동 판정용 gate로 읽으려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh` 를 씁니다.
  - 기본값은 `interpretation_changed=true` 또는 `stable_baseline_changed=true` 일 때만 fail 합니다.
  - `FAIL_ON_LATEST_OBSERVATION_CHANGE=true` strict 모드는 `latest_observation_changed=true` 만으로도 fail 하므로, current local처럼 `VOLATILE_ONLY_DRIFT + stable_baseline_changed=false` 인 상태에서는 regression이 아니라 fresh window 흔들림으로 읽습니다.
- 지금 local에서 recommendation 관찰을 다시 시작할 때 최소 순서는
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh`
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh`
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh`
  - `APP_BASE_URL='http://127.0.0.1:8082' ADMIN_EMAIL='<local admin email>' ADMIN_PASSWORD='<local admin password>' bash deploy/smoke/run-local-real-user-exclusion-readiness-check.sh`
  - current local 결과는 `latest gate=PASS`, strict gate=`LATEST_OBSERVATION_CHANGED`, live readiness=`READY_REAL_USER_TRAFFIC / READY_REAL_USER_COHORT`, full latest batch review gate=`DEFERRED_NON_REAL_LEADER_SIGNAL`, recent-window supplemental reading=`RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE` 입니다.
- stable baseline key와 volatile key를 자동 분류하려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-stability-report.sh` 를 씁니다.
  - current local 분류 기준 stable key는 cohort/gate 계열이고, volatile key는 `fresh_top_ai_zero_count`, `ai_zero_count`, `ai_zero_reason_buckets` 입니다.
- 개별 compare가 volatile-only drift인지 stable baseline drift인지 판정하려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-drift-classify.sh` 를 씁니다.
- 사람이 읽는 한 장 report로 보려면
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-report.sh` 를 씁니다.
- volatility audit와 baseline report를 한 번에 다시 태우려면
  - `RUN_COUNT=2 bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh.sh` 를 씁니다.

## 8. 실행 후 남길 최소 기록

snapshot wrapper를 썼다면 `ai-exclusion-snapshot-summary.txt` 를 이 섹션 템플릿 대신 바로 첨부해도 됩니다.

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

운영 surface를 더 넓게 다시 볼 때는 아래 baseline suite도 같이 씁니다.

- [ops-baseline-runbook.md](../core/ops-baseline-runbook.md)
- `deploy/smoke/run-local-ops-baseline-suite.sh`

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
