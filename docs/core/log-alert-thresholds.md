# Log Alert Thresholds

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

앱 로그, nginx access log, 신규 관측 테이블을 같은 기준으로 읽기 위한 초기 알림 기준입니다. 실제 운영 트래픽이 더 쌓이면 7일 rolling baseline 기준으로 조정합니다.

## 입력

- app log artifact: `tmp/performance/app-log-observability/latest-app-log-observability-summary.txt`
- nginx artifact: `tmp/performance/nginx-log-observability/latest-nginx-log-observability-summary.txt`
- threshold tuning artifact: `tmp/performance/log-alert-threshold-tuning/latest-log-alert-threshold-tuning-summary.txt`
- DB tables: `recommendation_run_logs`, `notification_attempt_logs`

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
- interactive API latency는 endpoint별 p95에서 `POST /api/recommendations/refresh` 같은 batch/refresh endpoint를 제외하고 봅니다.
- warning: nginx `request_time p95 >= 1500ms` 가 10분 이상 지속
- critical: nginx `request_time p95 >= 3000ms` 또는 `upstream_response_time p95 >= 2500ms` 가 10분 이상 지속
- warning: `raw_error_lines > 0` 이면서 같은 stack/error prefix가 반복

기본 evaluator 환경 변수:

```bash
LOG_ALERT_WARN_5XX=1
LOG_ALERT_CRIT_5XX=5
LOG_ALERT_CRIT_ERROR_CODE_REPEAT=10
LOG_ALERT_WARN_API_P95_MS=1500
LOG_ALERT_CRIT_API_P95_MS=3000
LOG_ALERT_EXCLUDED_API_P95_PATHS='POST /api/recommendations/refresh'
LOG_ALERT_WARN_NGINX_P95_SECONDS=1.5
LOG_ALERT_CRIT_NGINX_P95_SECONDS=3.0
```

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
4. `ALERT_WEBHOOK_URL` 을 설정한 서버에서는 `bash deploy/ops/send-log-alert.sh` 로 webhook 전송까지 닫습니다.
5. 관리자 API에서 추천 run summary 확인
6. DB 또는 관리자 API에서 `notification_attempt_logs` 최근 실패 outcome 확인
7. 원인이 배포/DB migration drift이면 [server-runtime-drift-checklist.md](./server-runtime-drift-checklist.md)를 먼저 적용

cron 예시:

```cron
*/5 * * * * cd /home/ubuntu/youth-welfare && LOG_ALERT_APP_SINCE=10m LOG_ALERT_NGINX_TAIL_LINES=2000 bash deploy/ops/send-log-alert.sh >> /var/log/youth-welfare/ops/log-alert.cron.log 2>&1
```

`deploy/ops/send-log-alert.sh` 는 `~/.config/youth-welfare/ops.env` 의 `ALERT_WEBHOOK_URL` 을 재사용합니다. URL이 없으면 전송은 건너뛰고 평가 결과와 상태 파일만 남깁니다.

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
