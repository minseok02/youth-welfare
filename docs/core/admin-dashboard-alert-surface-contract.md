# Admin Dashboard Alert Surface Contract

## 목적

운영 alert evaluator와 admin dashboard가 같은 사건을 다르게 해석하지 않도록 경계를 고정합니다.

결론은 다음과 같습니다.

- `deploy/smoke/evaluate-operational-alert-thresholds.sh`가 `ok/warning/critical/skipped` 판정의 source of truth입니다.
- admin dashboard는 threshold 판정 엔진이 아니라 triage surface입니다.
- dashboard는 evaluator가 경고한 사건을 사람이 확인할 수 있는 raw field와 샘플을 노출해야 합니다.

## ADMIN_DASHBOARD_ALERT_SURFACE_AUTHORITY

dashboard 화면은 alert status를 직접 계산하지 않습니다.

이유는 일부 evaluator 입력이 dashboard API와 시간창/단위가 다르기 때문입니다.

- search alert는 7일 zero-result rate와 non-branch zero-result rate를 artifact에서 읽습니다.
- recommendation alert는 10분/30분/1시간 DB summary artifact를 읽습니다.
- web push alert는 `web_push_subscriptions` disabled ratio와 최근 `notification_attempt_logs`를 합친 DB summary artifact를 읽습니다.
- dashboard API는 운영자가 원인을 좁히는 windowDays 기반 raw summary와 최근 샘플을 보여 줍니다.

## ADMIN_DASHBOARD_ALERT_SURFACE_MAP

| alert_id | evaluator source | dashboard API | dashboard section | dashboard raw fields |
| --- | --- | --- | --- | --- |
| `COLLECT_FAILED_JOB_RATE` | `OPS_SUMMARY` | `GET /api/admin/dashboard/collect-failures` | `admin-collect-triage` | `failedJobsInWindow`, `partialSuccessJobsInWindow`, `circuitStatuses[].open`, `collectSourceLanes[].latestRun`, `recentSamples` |
| `SEARCH_ZERO_RESULT_RATE` | `OPS_SUMMARY`, `CHAT_OBSERVABILITY_SUMMARY` | `GET /api/admin/dashboard/search-failures` | `admin-search-triage` | `zeroResultSearchesInWindow`, `zeroResultRegions`, `zeroResultFilterPatterns`, `retryGroups`, `recoveredSearchGroups`, `recentSamples` |
| `RECOMMENDATION_RUN_FAILURE_RATE` | `RECOMMENDATION_RUN_SUMMARY` | `GET /api/admin/dashboard/recommendation-run-summary` | `admin-recommendation-run-summary` | `totalRuns`, `successRuns`, `errorRuns`, `noCandidateRuns`, `averageDurationMs`, `outcomeBreakdowns`, `recentRuns` |
| `NOTIFICATION_RETRY_BACKLOG` | `NOTIFICATION_BACKLOG_SUMMARY` | `GET /api/admin/dashboard/summary`, `GET /api/admin/dashboard/notification-attempt-summary` | notification metric cards, `admin-notification-attempt-summary` | `retryableFailedNotifications`, `terminalFailedNotifications`, `failedAttempts`, `disabledAttempts`, `breakdowns`, `recentFailures` |
| `WEB_PUSH_DISABLED_RATIO` | `WEB_PUSH_SUMMARY` | `GET /api/admin/dashboard/notification-attempt-summary` | `admin-notification-attempt-summary` | `disabledAttempts`, `breakdowns[].channel`, `breakdowns[].outcome`, `recentFailures[].endpointHost`, `recentFailures[].errorType` |

## ADMIN_DASHBOARD_ALERT_SURFACE_LIMITS

Dashboard-only fields must not be used as the final alert status when evaluator fields are available.

- `zeroResultSearchesInWindow` is a count, not the evaluator's zero-result rate.
- `recommendationRunSummary.windowDays` is a day window, not the evaluator's 10m/30m/1h windows.
- `notificationAttemptSummary.disabledAttempts` is an attempt count, not the web push subscription disabled ratio.
- `collectFailures.failedJobsInWindow` and `collectFailures.circuitStatuses` are enough for dashboard triage, but evaluator still uses `collect_lane_count` from ops summary for failed job rate.

## ADMIN_DASHBOARD_ALERT_SURFACE_VERIFICATION

Use these checks when changing dashboard alert-related sections.

```bash
bash deploy/smoke/verify-admin-dashboard-alert-surface.sh
cd backend && ./gradlew test --tests com.example.welfare.global.config.AdminDashboardAlertSurfaceContractTest
```

If an evaluator `alert_id` is added, this document must map it to either a dashboard triage section or explicitly say that the dashboard does not expose it.
