# 추천 AI exclusion 증거 재수집

suite/snapshot/volatility/refresh를 **실제로 다시 실행**하는 절차입니다. 기존 파일만 읽으려면 [조회와 해석](recommendation-ai-exclusion-latest-status-runbook.md)을 사용합니다. `run-local` 이름과 `127.0.0.1:8082`는 격리된 개발 앱을 보장하지 않습니다.

## 실행 전

- [환경 안내](../core/environment-guide.md)에서 앱·DB·Redis·권한·비용·실행 영향을 확인하고 승인된 테스트 계정/코호트를 고릅니다.
- 하위 stage-gap/zero-reason/readiness는 로그인·추천 refresh·DB 조회·실제 AI 요청을 할 수 있습니다. 반복 횟수만큼 호출·쓰기·과금이 늘 수 있습니다.
- `TARGET_USER_KEY`, `USER_EMAIL/USER_PASSWORD` 또는 `USER_ACCESS_TOKEN`, `ADMIN_EMAIL/ADMIN_PASSWORD`는 승인된 비밀 전달 경로로 제공합니다. 명령/문서/artifact에 원문 비밀을 복사하지 않습니다.
- 기본 `TARGET_SERVICE_IDS_CSV=3288,3289,3290,5837`은 기존 로컬 snapshot ID입니다. 다른 DB에서 같은 정책을 뜻한다고 가정하지 말고 실제 대상 ID를 명시합니다.
- `TOP_REFRESH_LIMIT=20`, `TOP_N=20`, `SAMPLE_LIMIT=10`, `BASELINE_COHORT=non_example`, `TARGET_COHORT=real_user` 기본값을 확인합니다. 합성 계정을 실사용자 근거로 사용하지 않습니다.
- `ARTIFACT_DIR/KEEP_ARTIFACTS`와 보존 범위를 정합니다. snapshot/refresh는 기본 true지만 suite는 기본 false입니다. sanitizer의 cookie 삭제·redaction·권한 700/600 경계를 유지합니다.

## 재수집 경로

| 목적 | Script `deploy/smoke/` 아래 | 실제 실행 |
| --- | --- | --- |
| Evidence suite | [suite](../../deploy/smoke/run-local-recommendation-ai-exclusion-suite.sh) | stage-gap → zero-reason-bucket → baseline/target cohort compare → real-user readiness·조건부 distribution |
| Snapshot | [snapshot](../../deploy/smoke/run-local-recommendation-ai-exclusion-snapshot.sh) | suite 재실행 후 날짜 붙은 summary 생성 |
| Snapshot drift | [drift-check](../../deploy/smoke/run-local-recommendation-ai-exclusion-drift-check.sh) | 기존 baseline 선택 → 새 snapshot → compare |
| 반복 변동성 | [volatility-audit](../../deploy/smoke/run-local-recommendation-ai-exclusion-volatility-audit.sh) | baseline 고정 → RUN_COUNT회 drift-check, 기본 3회 |
| Baseline refresh | [baseline-refresh](../../deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh.sh) | volatility 실행 → baseline-report, 기본 RUN_COUNT=2 |
| Refresh drift | [baseline-refresh-drift-check](../../deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh) | 새 refresh → 이전 refresh summary와 compare |

승인한 격리 환경에서만 필요한 하나를 선택합니다. 아래 예시는 실행 형태이며 앱 URL·실제 서비스 ID·비밀 준비가 없으면 시작하지 않습니다.

```bash
(
  set -euo pipefail
  : "${EXCLUSION_APP_BASE_URL:?격리 앱 URL이 필요합니다}"
  : "${EXCLUSION_TARGET_SERVICE_IDS_CSV:?해당 DB의 실제 정책 ID가 필요합니다}"
  APP_BASE_URL="${EXCLUSION_APP_BASE_URL}" \
  TARGET_SERVICE_IDS_CSV="${EXCLUSION_TARGET_SERVICE_IDS_CSV}" \
  RUN_COUNT=2 \
  bash deploy/smoke/run-local-recommendation-ai-exclusion-baseline-refresh.sh
)
```

drift-check의 `BASELINE_SUMMARY`, refresh-drift의 `BASELINE_REFRESH_SUMMARY`는 같은 환경·코호트의 이전 파일로 명시합니다. baseline이 없거나 다른 환경이면 비교 결과를 기준선으로 승격하지 않습니다. refresh-drift의 실제 기본 파일 선택/오류 처리는 해당 script를 확인합니다.

## 남기는 자료

- Suite stdout prefix: `[stage-gap]`, `[zero-reason-bucket]`, `[cohort-compare]`, `[real-user-ready]`.
- Snapshot: `ai-exclusion-suite.out`, `ai-exclusion-snapshot-summary.txt`. target_service_ids_csv, fresh_top_ai_zero_count, ai_zero_count/reason_buckets, baseline/target_scope_users·zero_ai_reason_buckets, real_user_distribution_executed, dashboard/breakdown gate를 기록합니다.
- Volatility: `volatility-summary.txt`의 drift_detected_runs·changed_keys_frequency·fresh_top_ai_zero_count_frequency·ai_zero_reason_buckets_frequency를 확인합니다.
- Refresh: `baseline-refresh-summary.txt`, `baseline-report.out`, volatility/baseline/target summary와 latest_artifact_link/latest_summary_link/latest_report_link.
- Refresh drift: baseline_refresh_summary·target_refresh_summary·summary_output·latest_compare_link와 interpretation/stable/latest 변화 key.

갱신 뒤 [파일 비교·status/export/gate](recommendation-ai-exclusion-latest-status-runbook.md#파일만-읽는-helper)로 해석합니다. fresh window 변동과 stable baseline 변동을 분리하고, `READY_REAL_USER_TRAFFIC/READY_REAL_USER_COHORT`가 있어도 실제 target 분포·leader·primary/supplemental gate를 확인합니다. 높은 zero reason 빈도나 `PASS`만으로 제품 자격판정·추천 품질·정책 승격을 확정하지 않습니다.

완료 기록에는 실행 시각·커밋·환경/대상·코호트·호출 횟수·쓰기/외부 호출 범위·baseline/target 경로·stable/volatile 변화·실패/skip·다음 액션을 남깁니다. 이 절차는 실제 로그·북마크 보존 결함을 해결하거나 수정 금지 상태로 바꾸지 않습니다.
