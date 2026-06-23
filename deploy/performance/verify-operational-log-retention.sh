#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_LOG_FILE="${APP_LOG_FILE:-}"
APP_LOG_TAIL_LINES="${APP_LOG_TAIL_LINES:-20000}"
APP_LOG_COMPOSE_FILE="${APP_LOG_COMPOSE_FILE:-${ROOT_DIR}/docker-compose.prod.yml}"
APP_LOG_COMPOSE_SERVICE="${APP_LOG_COMPOSE_SERVICE:-app}"

CONFIG_FILE="${ROOT_DIR}/backend/src/main/resources/application.yml"
RECOMMENDATION_MIGRATION="${ROOT_DIR}/backend/src/main/resources/db/migration/V2026_06_23_01__add_recommendation_run_logs.sql"
NOTIFICATION_MIGRATION="${ROOT_DIR}/backend/src/main/resources/db/migration/V2026_06_23_02__add_notification_attempt_logs.sql"

status="passed"

require_contains() {
  local file="$1"
  local pattern="$2"
  local label="$3"
  if ! grep -Fq "$pattern" "$file"; then
    echo "missing_${label}=true"
    status="failed"
  else
    echo "${label}=present"
  fi
}

echo "operational_log_retention_verification=started"
require_contains "$CONFIG_FILE" "observability:" "observability_config"
require_contains "$CONFIG_FILE" "OBSERVABILITY_LOG_RETENTION_DAYS:90" "retention_days_default"
require_contains "$CONFIG_FILE" "OBSERVABILITY_LOG_RETENTION_CRON:0 40 3 * * *" "retention_cron_default"
require_contains "$RECOMMENDATION_MIGRATION" "GRANT SELECT, INSERT, DELETE ON TABLE recommendation_run_logs TO app_core_rw;" "recommendation_run_delete_grant"
require_contains "$NOTIFICATION_MIGRATION" "GRANT SELECT, INSERT, DELETE ON TABLE notification_attempt_logs TO app_core_rw;" "notification_attempt_delete_grant"

tmp_log="$(mktemp)"
trap 'rm -f "$tmp_log"' EXIT

if [[ -n "$APP_LOG_FILE" && -r "$APP_LOG_FILE" ]]; then
  tail -n "$APP_LOG_TAIL_LINES" "$APP_LOG_FILE" > "$tmp_log" || true
elif command -v docker >/dev/null 2>&1; then
  docker compose -f "$APP_LOG_COMPOSE_FILE" logs --no-color --tail="$APP_LOG_TAIL_LINES" "$APP_LOG_COMPOSE_SERVICE" > "$tmp_log" 2>/dev/null || true
else
  : > "$tmp_log"
fi

cleanup_lines="$(grep -F "[OperationalLogRetentionService]" "$tmp_log" || true)"
cleanup_completed_lines="$(printf '%s\n' "$cleanup_lines" | grep -F "completed" || true)"
cleanup_failed_lines="$(printf '%s\n' "$cleanup_lines" | grep -F "failed" || true)"

cleanup_line_count="$(printf '%s\n' "$cleanup_lines" | sed '/^$/d' | wc -l | tr -d ' ')"
cleanup_completed_line_count="$(printf '%s\n' "$cleanup_completed_lines" | sed '/^$/d' | wc -l | tr -d ' ')"
cleanup_failed_line_count="$(printf '%s\n' "$cleanup_failed_lines" | sed '/^$/d' | wc -l | tr -d ' ')"

echo "cleanup_log_lines=${cleanup_line_count}"
echo "cleanup_completed_lines=${cleanup_completed_line_count}"
echo "cleanup_failed_lines=${cleanup_failed_line_count}"

if [[ "$cleanup_failed_line_count" != "0" ]]; then
  status="failed"
fi

echo "operational_log_retention_verification=${status}"
[[ "$status" == "passed" ]]
