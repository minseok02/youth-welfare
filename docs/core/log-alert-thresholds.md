# Log Alert Thresholds

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

앱 로그, nginx access log, 신규 관측 테이블을 같은 기준으로 읽기 위한 초기 알림 기준입니다. 실제 운영 트래픽이 더 쌓이면 7일 rolling baseline 기준으로 조정합니다.

## 입력

- app log artifact: `tmp/performance/app-log-observability/latest-app-log-observability-summary.txt`
- nginx artifact: `tmp/performance/nginx-log-observability/latest-nginx-log-observability-summary.txt`
- threshold tuning artifact: `tmp/performance/log-alert-threshold-tuning/latest-log-alert-threshold-tuning-summary.txt`
- ops observation artifact: `tmp/ops-observation/latest-ops-observation-summary.txt`
- notification backlog artifact: `tmp/notification-backlog-audit/latest-notification-backlog-summary.txt`
- DB tables: `recommendation_run_logs`, `notification_attempt_logs`
- DB read models: `web_push_subscriptions`, `chat_retrieval_snapshots`
- DB audit artifact: `deploy/postgres/audit-operational-db-state.sh`

app/nginx raw tail sample은 artifact 파일에 쓰기 전에 `smoke_redact_stream_for_log` 를 통과합니다.
성능/log alert artifact는 정상 publish 전뿐 아니라 exit trap에서도 `smoke_sanitize_artifacts` 를 다시 적용합니다.

## 운영 dashboard alert 기준

아래 `alert_id`는 운영 dashboard, nightly handoff, smoke artifact에서 같은 기준으로 읽습니다.
문서 누락은 `bash deploy/smoke/verify-operational-alert-thresholds.sh` 와 `OperationalAlertThresholdContractTest`가 잡습니다.
실제 artifact 평가는 `bash deploy/smoke/evaluate-operational-alert-thresholds.sh` 로 실행합니다.
dashboard raw triage surface와 evaluator 판정 권위의 경계는 [admin-dashboard-alert-surface-contract.md](./admin-dashboard-alert-surface-contract.md)에 고정합니다.

| alert_id | source | primary metric | warning | critical | first action |
| --- | --- | --- | --- | --- | --- |
| `COLLECT_FAILED_JOB_RATE` | `run-local-ops-observation-suite.sh`, admin collect failures | `collect_failed_jobs_in_window`, `collect_partial_success_jobs_in_window`, `open_collect_circuits`, `collect_lane_count` | warning: failed/partial job이 1개 이상이거나 failed job rate가 5% 이상 | critical: `open_collect_circuits > 0`, failed jobs가 3개 이상, 또는 failed job rate가 20% 이상 | collect failures child artifact를 먼저 열고 lane/source별 최근 실패 원인을 확인 |
| `SEARCH_ZERO_RESULT_RATE` | `run-local-chat-observability-audit.sh`, app log `policy_search` action | `chat_observability_window_7d_zero_result_rate_pct`, `chat_observability_window_7d_zero_result_non_branch_rate_pct` | warning: 7일 zero-result rate가 20% 이상이거나 non-branch zero-result rate가 15% 이상 | critical: 7일 zero-result rate가 40% 이상이거나 non-branch zero-result rate가 25% 이상 | 최근 sample과 retrieval snapshot을 열어 지역/키워드/branch fallback 누락을 확인 |
| `RECOMMENDATION_RUN_FAILURE_RATE` | `recommendation_run_logs`, admin recommendation run summary | `outcome`, `duration_ms`, `saved_count` | warning: 30분 창 `ERROR` 또는 `NO_CANDIDATES`가 1건 이상, 실패율 5% 이상, 또는 평균 `duration_ms >= 5000` | critical: 10분 창 `ERROR >= 3`, 30분 실패율 20% 이상, 또는 traffic이 있는데 1시간 동안 `SUCCESS`가 없음 | run log의 error prefix와 추천 후보/AI/cache 경계를 분리해서 확인 |
| `NOTIFICATION_RETRY_BACKLOG` | `run-local-notification-backlog-audit.sh`, `notification_attempt_logs` | `retryable_failed_total`, `retryable_failed_due_now`, `terminal_failed_total`, `earliest_retry_at` | warning: `retryable_failed_due_now > 0`, `retryable_failed_total >= 10`, 또는 earliest retry가 30분 이상 지연 | critical: `terminal_failed_total > 0`, `retryable_failed_total >= 100`, `retryable_failed_due_now >= 10`, 또는 earliest retry가 2시간 이상 지연 | retry runner, SMTP/Kakao provider, terminal failure reason을 순서대로 확인 |
| `WEB_PUSH_DISABLED_RATIO` | `web_push_subscriptions`, `notification_attempt_logs` | disabled subscription ratio, `notification_attempt_logs.outcome='disabled'`, endpoint host failure count | warning: subscription total이 20개 이상이고 disabled ratio가 10% 이상, 또는 disabled outcome이 직전 24시간 평균의 3배 이상 | critical: subscription total이 20개 이상이고 disabled ratio가 25% 이상, 또는 15분 창 disabled/gateway failure가 10건 이상 | VAPID key, `/sw.js` MIME, endpoint host별 만료/거부 응답을 확인 |
| `DB_AUDIT_INTEGRITY` | `audit-operational-db-state.sh` | `auth_without_users`, `profiles_without_users`, `pii_without_users`, `active_users_without_pii`, `chat_snapshots_nonnull_orphan_session`, `waiting_locks`, `active_queries_over_5m` | warning: `user_pii_sync_pending_or_failed > 0`, `notification_failed_like > 0`, 또는 `active_queries_over_5m >= 1` | critical: user projection drift 합계 > 0, `active_users_without_pii > 0`, `chat_snapshots_nonnull_orphan_session > 0`, 또는 `waiting_locks >= 1` | DB audit artifact를 열어 FK/PII sync/lock 원인을 분리하고 필요 시 migration drift checklist를 적용 |

비율 계산은 분모가 0이면 `0%`로 읽고, 표본이 너무 작으면 count 조건을 우선합니다.
warning은 운영자가 같은 날 확인할 신호이고, critical은 배포/스케줄/외부 provider 상태를 즉시 확인할 신호입니다.

## 보존 정책

- `recommendation_run_logs`, `notification_attempt_logs` 는 기본 90일 보존입니다.
- 앱 설정: `observability.log-retention.days`
- 환경 변수: `OBSERVABILITY_LOG_RETENTION_DAYS`
- 기본 스케줄: `observability.log-retention.cron=0 40 3 * * *`, `observability.log-retention.zone=Asia/Seoul`
- cleanup 로그 prefix: `[OperationalLogRetentionService]`
- 검증 명령: `bash deploy/performance/verify-operational-log-retention.sh`

## API 요청

- warning: 5분 창 `api_status_5xx > 0`
- critical: 5분 창 `api_status_5xx >= 5` 또는 같은 `errorCode` 가 10분 창 `>= 10`
- ALB health check path `/alb-health` 는 user API 5xx와 분리해서 읽는다.
- warning: 배포/재시작 창 밖에서 `/alb-health` 5xx가 반복되거나 한 target만 health check 실패가 이어짐
- critical: non-`/alb-health` user path 5xx가 발생하거나 ALB target이 unhealthy 상태로 지속
- interactive API latency는 endpoint별 p95에서 `POST /api/recommendations/refresh` 같은 batch/refresh endpoint를 제외하고 봅니다.
- warning: nginx `request_time p95 >= 1500ms` 가 10분 이상 지속
- critical: nginx `request_time p95 >= 3000ms` 또는 `upstream_response_time p95 >= 2500ms` 가 10분 이상 지속
- warning: `raw_error_lines > 0` 이면서 같은 stack/error prefix가 반복

기본 evaluator 환경 변수:

```bash
LOG_ALERT_WARN_5XX=1
LOG_ALERT_CRIT_5XX=5
LOG_ALERT_CRIT_ERROR_CODE_REPEAT=10
LOG_ALERT_REPEAT_ERROR_CODE_EXCLUDES='A006'
LOG_ALERT_WARN_API_P95_MS=1500
LOG_ALERT_CRIT_API_P95_MS=3000
LOG_ALERT_MIN_API_P95_COUNT=3
LOG_ALERT_EXCLUDED_API_P95_PATHS='POST /api/recommendations/refresh'
LOG_ALERT_EXCLUDED_API_P95_PREFIXES='GET /api/admin/,POST /api/admin/'
LOG_ALERT_WARN_NGINX_P95_SECONDS=1.5
LOG_ALERT_CRIT_NGINX_P95_SECONDS=3.0
```

`A006` 는 미인증/토큰 없음 계열 스캐너 요청에서 자주 반복되므로 기본 반복 error-code critical 판정에서는 제외하고, evaluator output의 `observation=` line으로 남깁니다. 인증 공격성 판단은 `[AuthAudit]` rate 기준으로 따로 봅니다.

nginx 5xx도 post-deploy smoke와 같은 기준으로 분류합니다. `/api/*` 5xx와 `GET`/`HEAD` page 5xx만 user-facing warning/critical 후보로 보고, `/alb-health` 5xx와 non-GET/HEAD root probe 5xx는 `observation=` line에 남깁니다.

interactive API p95는 기본 `LOG_ALERT_MIN_API_P95_COUNT=3` 이상일 때만 warning/critical로 승격합니다. 배포 직후 단일 ranking 요청처럼 샘플 수가 너무 적은 latency는 `observation=` line으로만 남깁니다.

admin dashboard/API는 운영자가 수동 점검할 때 latency sample을 왜곡할 수 있으므로 기본 p95 warning/critical에서는 제외하고 `excluded_latency=` 로 남깁니다.

24시간 기준 재튜닝:

```bash
LOG_ALERT_TUNE_WINDOW=24h \
NGINX_LOG_TUNE_TAIL_LINES=50000 \
bash deploy/performance/tune-log-alert-thresholds.sh
```

2026-06-23 운영 서버 24시간 tuning 기준:

- interactive API p95: `138ms`
- nginx request p95: `0.008s`
- nginx upstream p95: `0.070s`
- 권장 latency threshold: 기본값 유지
- `POST /api/recommendations/refresh` p95: `9955ms`, interactive p95 판정에서 제외하고 batch latency로 별도 관찰
- `P003` 120건은 수동 검증 트래픽으로 만든 rate-limit 노이즈였으므로 threshold 완화 대상에서 제외

## 인증/관리자

- warning: `[AuthAudit] outcome=failed` 가 5분 창 `>= 20`
- critical: `[AuthAudit]` 또는 `[AdminAudit]` rate limit 계열 outcome이 5분 창 `>= 10`
- critical: `[AdminAudit]` mutation 실패가 10분 창 `>= 3`

## 추천 실행

- warning: `recommendation_run_logs.outcome='NO_CANDIDATES'` 가 30분 창 `> 0`
- critical: `recommendation_run_logs.outcome='ERROR'` 가 10분 창 `>= 3`
- warning: 30분 창 평균 `duration_ms >= 5000`
- warning: 30분 창 `SUCCESS` 실행의 `saved_count = 0` 비율 `>= 20%`
- 확인 API: `GET /api/admin/dashboard/recommendation-run-summary?summaryWindowDays=1`

## 알림 발송

- warning: `notification_attempt_logs.outcome in ('gateway_false','failed','exception','fanout_failed')` 가 15분 창 `>= 3`
- critical: 같은 조건이 15분 창 `>= 10`
- warning: `notification_attempt_logs.outcome='disabled'` 가 1시간 창에서 직전 24시간 평균의 3배 이상
- warning: web push `endpoint_host` 단위 실패가 15분 창 `>= 5`

## 사용자 행동 로그

- warning: `[UserAction] action=policy_error_report` 가 1시간 창 `>= 5`
- warning: `[UserAction] action=policy_search` 결과 없음 또는 오류성 로그가 같은 keyword/region으로 반복

## 운영 절차

1. `bash deploy/performance/run-local-app-log-observability-baseline.sh`
2. `bash deploy/performance/run-local-nginx-log-observability-baseline.sh`
3. `bash deploy/performance/evaluate-log-alert-thresholds.sh`
4. `bash deploy/smoke/run-local-ops-observation-suite.sh`
5. `bash deploy/smoke/run-local-notification-backlog-audit.sh`
6. `bash deploy/smoke/evaluate-operational-alert-thresholds.sh`
7. `ALERT_WEBHOOK_URL` 을 설정한 서버에서는 `bash deploy/ops/send-log-alert.sh` 로 webhook 전송까지 닫습니다. child baseline output, evaluator stdout, webhook payload는 redaction stream을 거칩니다.
8. 관리자 API에서 추천 run summary 확인
9. DB 또는 관리자 API에서 `notification_attempt_logs` 최근 실패 outcome 확인
10. 원인이 배포/DB migration drift이면 [server-runtime-drift-checklist.md](./server-runtime-drift-checklist.md)를 먼저 적용

배포 직후 user-facing edge/API 상태는 먼저 read-only smoke로 닫습니다.

```bash
bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

이 smoke가 통과하면 배포/재시작 창의 짧은 `/alb-health` 502만으로는 장애로 보지 않습니다.
자세한 판단 기준은 [alb-health-502-runbook.md](./alb-health-502-runbook.md)를 봅니다.

운영 dashboard evaluator 기본 입력:

```bash
OPS_SUMMARY=tmp/ops-observation/latest-ops-observation-summary.txt \
NOTIFICATION_BACKLOG_SUMMARY=tmp/notification-backlog-audit/latest-notification-backlog-summary.txt \
CHAT_OBSERVABILITY_SUMMARY=tmp/chat-observability-audit/latest-chat-observability-summary.txt \
bash deploy/smoke/evaluate-operational-alert-thresholds.sh
```

`RECOMMENDATION_RUN_FAILURE_RATE`, `WEB_PUSH_DISABLED_RATIO`, `DB_AUDIT_INTEGRITY` 는 DB/API 요약 artifact가 있을 때만 평가합니다.
없으면 `skipped=...` 로 출력하고 `warning` 으로 승격하지 않습니다.

cron 예시:

```cron
*/5 * * * * APP_ROOT=/path/to/youth-welfare && cd "$APP_ROOT" && LOG_ALERT_APP_SINCE=10m LOG_ALERT_NGINX_TAIL_LINES=2000 bash deploy/ops/send-log-alert.sh >> /var/log/youth-welfare/ops/log-alert.cron.log 2>&1
```

`deploy/ops/send-log-alert.sh` 는 `~/.config/youth-welfare/ops.env` 의 `ALERT_WEBHOOK_URL` 을 재사용합니다. URL이 없으면 전송은 건너뛰고 평가 결과와 상태 파일만 남깁니다. webhook message는 evaluator output을 `smoke_redact_stream_for_log` 로 redaction한 값만 사용합니다.

webhook 연결 순서:

```bash
printf 'ALERT_WEBHOOK_URL=%s\n' '<ops webhook url>' >> ~/.config/youth-welfare/ops.env
LOG_ALERT_NOTIFY_OK=true bash deploy/ops/send-log-alert.sh
tail -n 5 /var/log/youth-welfare/ops/log-alert.log
```

cron 동작 확인:

```bash
systemctl is-active cron
crontab -l | grep 'deploy/ops/send-log-alert.sh'
tail -n 20 /var/log/youth-welfare/ops/log-alert.cron.log
```

## 관리자 화면 경계

- 관리자 dashboard에는 DB 기반 운영 로그인 `recommendation_run_logs`, `notification_attempt_logs` 요약을 노출합니다.
- app/nginx 파일 artifact는 host filesystem 산출물이고, 운영 app 컨테이너는 read-only이며 해당 path를 mount하지 않습니다.
- 따라서 파일 기반 alert/latency artifact는 dashboard API가 직접 읽지 않고 ops script/webhook과 runbook으로 운영합니다.
- dashboard는 threshold 판정 엔진이 아니라 triage surface입니다. alert status source of truth는 `evaluate-operational-alert-thresholds.sh` 입니다.

## Evaluator DB summary helper

`RECOMMENDATION_RUN_FAILURE_RATE`와 `WEB_PUSH_DISABLED_RATIO`를 파일 artifact 없이 평가하려면 먼저 아래 helper를 실행합니다.

```bash
bash deploy/smoke/generate-operational-alert-db-summaries.sh
RECOMMENDATION_RUN_SUMMARY=tmp/operational-alert-db-summaries/latest-recommendation-run-summary.txt \
WEB_PUSH_SUMMARY=tmp/operational-alert-db-summaries/latest-web-push-summary.txt \
OPERATIONAL_DB_AUDIT_SUMMARY=/var/log/youth-welfare/nightly-ops-handoff/artifacts/<UTC timestamp>/operational-db-audit.out \
  bash deploy/smoke/evaluate-operational-alert-thresholds.sh
```

이 helper는 `recommendation_run_logs`, `web_push_subscriptions`, `notification_attempt_logs`를 읽어 evaluator가 요구하는 key-value summary를 생성합니다.
기존 로컬 PostgreSQL volume에서 `operational_alert_source_available=false` 또는 `source_missing=...` 이 나오면 volume 삭제보다 먼저 `bash deploy/postgres/apply-local-runtime-schema-patch.sh` 로 runtime drift patch를 적용합니다.
