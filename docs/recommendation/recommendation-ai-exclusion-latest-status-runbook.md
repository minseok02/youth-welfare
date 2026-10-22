# 추천 AI exclusion 산출물 조회와 해석

기존 snapshot·refresh·drift 결과를 읽거나 비교하는 안내입니다. **산출물 파일 비교와 실제 추천 재실행은 다릅니다.** 실행을 포함하는 suite/snapshot/refresh 절차는 [증거 재수집](recommendation-ai-exclusion-baseline-refresh-runbook.md)에 있습니다. [환경 안내](../core/environment-guide.md)에 따라 환경·코호트·시각이 같은 자료를 선택합니다.

## 기존 산출물 조회

summary/JSON이 없거나 다른 환경이면 먼저 자료를 확보합니다. 없다는 이유로 운영에서 refresh를 자동 실행하지 않습니다.

```bash
REFRESH_SUMMARY='/abs/path/run/baseline-refresh-summary.txt' \
DRIFT_SUMMARY='/abs/path/run/baseline-refresh-drift-summary.txt' \
STATUS_JSON='/abs/path/run/latest-status.json' \
AUTO_REFRESH_STATUS_JSON_IF_STALE=false \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh
```

default summary는 `tmp/recommendation-ai-exclusion-baseline-refresh/latest-baseline-refresh-summary.txt`, `tmp/recommendation-ai-exclusion-baseline-refresh-drift-check/latest-baseline-refresh-drift-summary.txt`입니다. `latest`는 환경·실행 시점을 보장하지 않습니다. `generated_at_utc/generated_at_kst`, 입력 경로·커밋·코호트와 함께 읽습니다.

`status_json_stale_relative_to_summaries=true`이면 `RERUN_LATEST_STATUS_EXPORT`가 필요합니다. `AUTO_REFRESH_STATUS_JSON_IF_STALE=true`는 JSON export를 다시 만드는 옵션이지 추천을 재생성하는 옵션이 아닙니다. 기본값은 status/gate에서 false입니다.

## 파일만 읽는 helper

아래는 기본 구현에서 기존 파일을 입력으로 사용합니다. export/report는 로컬 파일·latest 링크를 만들 수 있지만 앱/DB 추천을 새로 생성하지 않습니다.

| 목적 | Script `deploy/smoke/` 아래 | 명시 입력 / 핵심 출력 |
| --- | --- | --- |
| Snapshot 2개 비교 | [snapshot-compare](../../deploy/smoke/run-local-recommendation-ai-exclusion-snapshot-compare.sh) | `BASELINE_SUMMARY`, `TARGET_SUMMARY`; drift_detected·changed_keys·baseline/target 값 |
| Refresh 요약 2개 비교 | [baseline-refresh-compare](../../deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh) | `BASELINE_REFRESH_SUMMARY`, `TARGET_REFRESH_SUMMARY`; interpretation/stable/latest 변화 분리 |
| Stable/volatile 분류 | [stability-report](../../deploy/smoke/run-local-recommendation-ai-exclusion-stability-report.sh) | `VOLATILITY_SUMMARY`; stable_keys·volatile_keys·run별 값 |
| Drift 분류 | [drift-classify](../../deploy/smoke/run-local-recommendation-ai-exclusion-drift-classify.sh) | `VOLATILITY_SUMMARY`, `BASELINE_SUMMARY`, `TARGET_SUMMARY`; drift_class·stable_changed_keys·volatile_changed_keys |
| 사람용 보고서 | [baseline-report](../../deploy/smoke/run-local-recommendation-ai-exclusion-baseline-report.sh) | stability+classify; recommended_reading·stable baseline·latest observation |
| 상태 note/JSON 생성 | [latest-status-export](../../deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh) | refresh/drift summary → latest-status-note.md·latest-status.json·latest 링크 |
| 상태 조회 | [latest-status](../../deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh) | REFRESH_SUMMARY·DRIFT_SUMMARY·STATUS_JSON; gate/status/next step |
| 자동 판정 | [latest-gate](../../deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh) | STATUS_JSON; gate_status·gate_reason·policy/action·생성 시각 |

snapshot-compare는 기본으로 snapshot root의 최신 2개, refresh-compare는 최신 refresh 요약을 선택합니다. 환경이 섞일 수 있으므로 운영 비교에서는 baseline/target 파일을 명시합니다. drift-classify/baseline-report는 volatility와 snapshot의 시각·대상도 맞춰야 합니다.

## Gate와 overview

```bash
AUTO_REFRESH_STATUS_JSON_IF_STALE=false \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh

AUTO_REFRESH_STATUS_JSON_IF_STALE=false FAIL_ON_LATEST_OBSERVATION_CHANGE=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-gate.sh
```

기본 gate는 `interpretation_changed=true` 또는 `stable_baseline_changed=true`이면 실패합니다. strict는 latest observation 변화도 실패로 봅니다. `PASS`는 선택한 artifact의 drift 정책 통과이지 모델 품질·운영 health·데이터 보존·승격 승인 증거가 아닙니다.

[latest-overview](../../deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh)는 export → status → basic/strict gate → 선택적 readiness를 묶습니다. strict 실패는 `strict_gate_exit_code`로 기록하고 overview 자체는 계속될 수 있습니다. 기본 `INCLUDE_REAL_USER_READINESS=auto`는 admin 자격을 발견하면 앱 호출을 시도하므로 파일만 보고 싶으면 다음처럼 끕니다.

```bash
INCLUDE_REAL_USER_READINESS=false AUTO_REFRESH_STATUS_JSON_IF_STALE=false \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

overview는 이 설정에서도 export와 `latest-overview-summary.txt / latest-overview-note.md / latest-overview.json`을 생성합니다. `latest_artifact_link/latest_summary_link/latest_note_link/latest_json_link`는 해당 로컬 산출물 포인터입니다. 실제 readiness를 포함하려면 앱 대상·권한·비밀 전달을 먼저 정하고 [실사용자 재검토](recommendation-real-user-recheck-checklist.md)를 따릅니다.

## 비교 결과 해석

| 결과 | 읽는 법 |
| --- | --- |
| `NO_DRIFT` / drift_detected=false | 추적 key가 같음. 누락된 지표나 실제 서비스 동일성까지 증명하지 않음 |
| `VOLATILE_ONLY_DRIFT` | fresh window만 변화. stable cohort/gate와 분리 |
| `STABLE_BASELINE_DRIFT` | 고정 기준으로 삼던 key 변화. baseline·코호트·원인 확인 |
| `MIXED_DRIFT` | stable/volatile이 함께 변화. 자동 baseline 교체 금지 |
| `READ_LATEST_AS_VOLATILE_OBSERVATION` | 최신 fresh 값은 관찰값으로 유지 |
| `UPDATE_STABLE_BASELINE` | helper의 갱신 검토 신호. 사용자 승인/실제 기준선 변경을 대신하지 않음 |
| `INVESTIGATE_BEFORE_BASELINE_UPDATE` | 원인 조사 뒤 갱신 판단 |

`fresh_top_ai_zero_count/ai_zero_count/ai_zero_reason_buckets`와 `baseline_zero_ai_reason_buckets`를 구분합니다. `INCOME_MISMATCH/STUDENT_AUDIENCE_MISMATCH`는 기록된 exclusion reason이며 올바른 자격판정의 자동 증거는 아닙니다. `target_scope_users=0`·`real_user_distribution_executed=false`는 실제 target 분포가 없다는 뜻입니다. `dashboard_real_user_gate/breakdown_real_user_cohort_gate`가 안정적이라는 것만으로 gate가 READY라는 뜻은 아닙니다.

`stable_baseline_changed=false, latest_observation_changed=true`는 fresh 변화이고, `interpretation_changed=true`이면 결론 문구도 재검토합니다. `changed_keys/stable_changed_keys/latest_changed_keys/interpretation_changed_keys`와 baseline_*/target_*를 함께 남깁니다.

## 정책 해석과 승인 상태

`operator_next_step`은 older baseline pointer일 수 있습니다. `effective_operator_next_step`, `gate_action_class`, `gate_policy_status/reason`, `review_gate_context`를 함께 읽습니다. 예를 들어 `PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`는 primary historical blocker와 supplemental recent-window clear가 공존한다는 뜻입니다.

`primary_review_gate_blocker_class`, `recent_window_recommendation_review_reading`, `historical_example_dominance_detected`, `review_gate_interpretation_class/operating_mode`로 primary `ALL_TIME_LATEST_PER_USER`와 recent-window를 구분합니다. `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`는 해당 관측 창의 결과이지 모든 과거/실사용자 표본의 결론이 아닙니다.

상세 상태 key는 `review_gate_policy_promotion_*`의 다음 층으로 묶어 읽습니다. 전체 key·reason과 계산은 [status 코드](../../deploy/smoke/run-local-recommendation-ai-exclusion-latest-status.sh), [export 코드](../../deploy/smoke/run-local-recommendation-ai-exclusion-latest-status-export.sh)가 기준입니다.

| 층 | 의미 |
| --- | --- |
| candidate / promotion / action | 후보 신호인지, 명시적 policy review가 필요한지, primary 유지인지 |
| readiness / execution / approval_criteria | 검토 전제와 실제 실행·승인 근거를 구분 |
| approval / approval_decision / approval_record | 준비됨·승인 대기·실제 record 존재를 구분 |
| review_run / review_run_criteria / review_run_decision | 실제 bounded review 실행과 실행 전제·결정 대기 |
| review_run_approval_criteria / review_run_approval_decision / review_run_approval | 마지막 run 승인 전제·결정·승인 상태 |
| review_run_approval_record_criteria / review_run_approval_record | record 작성 전제와 실제 record |
| review_run_approval_record_transition / review_run_approval_record_write | write 전환 조건과 실제 실행 여부 |

`READY_FOR_*`는 승인이나 write 완료가 아닙니다. `PENDING_*`·`AWAIT_*`를 기존 record가 있다는 뜻으로 읽지 않습니다. 문서 통합·artifact export만으로 정책을 승격하거나 승인 record를 쓰지 않습니다. 실제 절차는 [bounded promotion](recommendation-bounded-promotion-review-runbook.md), [승인 record smoke](recommendation-review-gate-promotion-approval-record-smoke-runbook.md)에 있습니다.

과거 local 문서의 `DEFERRED_NON_REAL_LEADER_SIGNAL`, `VOLATILE_ONLY_DRIFT`, basic PASS/strict LATEST_OBSERVATION_CHANGED는 당시 관측입니다. 새 결과로 덮어 쓰거나 현재 기본값으로 고정하지 않습니다. 실사용자 여부는 `account_origin=REAL_USER`만으로 입증하지 않습니다. PR 해석은 [review brief](recommendation-pr-review-brief.md), [draft exit](recommendation-pr-draft-exit-checklist.md), [post-merge follow-up](recommendation-post-merge-followup-checklist.md)을 참고하고, 과거 관찰 방침으로 보안·데이터 유실 수정을 막지 않습니다.
