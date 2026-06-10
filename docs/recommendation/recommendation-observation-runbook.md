# recommendation observation runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 recommendation 트랙을 다시 여는 문서가 아닙니다.
현재 운영 truth가 `KEEP_OBSERVING / WAIT_FOR_REAL_USER_TRAFFIC` 인 동안,
operator가 **무엇을 먼저 보고 어떻게 해석할지** 를 짧게 고정하는 daily runbook 입니다.

핵심 질문은 아래 둘입니다.

- 지금 recommendation 코드를 다시 열 단계인가
- 아니라면 무엇을 관찰하고 어떤 artifact를 handoff 기준으로 남길 것인가

## 기본 명령

로컬/기본:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-observation-suite.sh
```

nightly 운영 wrapper:

```bash
bash deploy/smoke/run-nightly-standard-code-observation.sh
```

운영 서버/RDS:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-observation-suite.sh
```

## 먼저 볼 artifact

- `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`
- `tmp/recommendation-observation/latest-recommendation-observation.json`
- `tmp/recommendation-observation/latest-recommendation-observation-note.md`
- `tmp/recommendation-observation/latest/housing-standard-code-effect.out`
- `tmp/recommendation-observation/latest/welfare-standard-code-matrix.out`
- `tmp/recommendation-observation/latest/recommendation-standard-code-adoption.out`

사람이 먼저 읽을 때는 `note.md`, 자동 파싱이나 handoff 스크립트는 `summary/json` 을 우선합니다.

## 먼저 볼 값

- `precheck_status`
- `precheck_reason`
- `decision_class`
- `reopen_allowed`
- `observation_blocker`
- `recommended_cadence`
- `next_action`
- `housing_standard_code_effect_status`
- `housing_standard_code_effect_positive_rule_delta_rows`
- `housing_standard_code_effect_max_rule_delta`
- `welfare_standard_code_matrix_status`
- `welfare_standard_code_matrix_positive_rule_scenarios`
- `welfare_standard_code_matrix_max_rule_delta`
- `recommendation_standard_code_adoption_status`
- `recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct`
- `recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes`

## 상태 해석

### `precheck_status=KEEP_OBSERVING`

- recommendation 코드는 다시 열지 않습니다.
- `DEFERRED_REAL_USER_SAMPLE_THIN` 또는 `EXAMPLE_SMOKE_ONLY_LEADER` 가 같이 보이면 real-user sample/leader signal 관찰을 유지합니다.
- operator 해석:
  - reopen decision 문서로 넘어가지 않습니다.
  - global weight, prompt, source/category balancing 을 건드리지 않습니다.

### `decision_class=OBSERVE_REAL_USER_TRAFFIC`

- recommendation 코드는 다시 열지 않습니다.
- daily cadence로 observation만 유지합니다.
- operator 해석:
  - real-user traffic이 아직 부족합니다.
  - `latest overview` 는 필요할 때만 추가로 엽니다.

### `decision_class=OBSERVE_LEADER_SIGNAL`

- readiness는 일부 열렸지만 leader signal이 약합니다.
- recommendation 튜닝보다 blocker audit가 먼저입니다.
- 다음 명령:
  - `bash deploy/smoke/run-local-recommendation-review-gate-blocker-audit.sh`

### `decision_class=SUPPLEMENTAL_POLICY_REVIEW`

- recent-window signal은 참고할 수 있지만 primary baseline을 바꾸는 단계는 아닙니다.
- explicit policy review가 먼저입니다.
- 다음 문서:
  - [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)

### `decision_class=REOPEN_DECISION_READY`

- 이때만 recommendation reopen 판단 문서로 넘어갑니다.
- 다음 문서:
  - [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)

### `decision_class=INVESTIGATE_BASELINE_DRIFT`

- recommendation tuning 전에 baseline drift를 먼저 정리합니다.
- 다음 명령:
  - `bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh`

## 운영 handoff 최소 기록

- 실행 시각
- `precheck_status`
- `precheck_reason`
- `decision_class`
- `reopen_allowed`
- `observation_blocker`
- `recommended_cadence`
- `next_action`
- `housing_standard_code_effect_status`
- `housing_standard_code_effect_positive_rule_delta_rows`
- `housing_standard_code_effect_max_rule_delta`
- `welfare_standard_code_matrix_status`
- `welfare_standard_code_matrix_positive_rule_scenarios`
- `welfare_standard_code_matrix_max_rule_delta`
- `recommendation_standard_code_adoption_status`
- `recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct`
- `recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes`

## 현재 server/RDS 기준

2026-06-10 최신 reopen precheck 기준:

- `reopen_precheck_status=KEEP_OBSERVING`
- `real_user_dashboard_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `real_user_breakdown_cohort_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `real_user_review_gate=DEFERRED_REAL_USER_SAMPLE_THIN`
- `dashboard_real_user_users_in_window=1`
- `breakdown_real_user_users_in_window=1`
- `real_user_top1_leader_signal_summary=EXAMPLE_SMOKE_ONLY_LEADER`
- `review_gate_policy_promotion_execution_status=DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW`

따라서 현재 observation 결과는 recommendation reopen이 아니라 관찰 유지로 handoff 합니다.

## 한 줄 요약

`observation suite` 는 recommendation을 다시 열지 말지 daily 수준에서 빠르게 판단하는 entrypoint이고,
`reopen_allowed=true` 가 아니면 기본 해석은 **코드 reopen이 아니라 관찰 유지** 입니다.
이제 여기에는 `housing standard code effect` 와 `welfare standard code matrix` 도 같이 묶여 있어서,
주거형태/주택유형뿐 아니라 `기초생활수급권자 / 장애등급` 계열 표준코드 효과도 같은 artifact에서 바로 확인할 수 있습니다.
추가로 `recommendation standard code adoption` audit가 latest batch 기준 `표준코드가 실제로 들어간 추천 사용자 비중`까지 같이 남깁니다.
nightly wrapper는 여기에 더해 `run-local-recommendation-review-gate-blocker-audit.sh` 를 같이 실행해서, `blocker_class`, `operator_next_step`, `mixed_concentration_readiness`, `real_user_concentration_readiness` 까지 `/var/log/youth-welfare/standard-code-observation/nightly-summary-YYYY-MM-DD.log` 에 남깁니다.
