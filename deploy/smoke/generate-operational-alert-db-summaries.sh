#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/operational-alert-db-summaries}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"

RAW_RECOMMENDATION_OUT="${ARTIFACT_DIR}/recommendation-run-metrics.tsv"
RAW_WEB_PUSH_OUT="${ARTIFACT_DIR}/web-push-metrics.tsv"
RECOMMENDATION_SUMMARY_OUT="${ARTIFACT_DIR}/recommendation-run-summary.txt"
WEB_PUSH_SUMMARY_OUT="${ARTIFACT_DIR}/web-push-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/operational-alert-db-summaries.json"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_RECOMMENDATION_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-recommendation-run-summary.txt"
LATEST_WEB_PUSH_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-web-push-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-operational-alert-db-summaries.json"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

RECOMMENDATION_TABLE_AVAILABLE="$(smoke_db_query "select case when to_regclass('public.recommendation_run_logs') is null then 'false' else 'true' end;")"
WEB_PUSH_TABLES_AVAILABLE="$(smoke_db_query "
select case
  when to_regclass('public.web_push_subscriptions') is not null
   and to_regclass('public.notification_attempt_logs') is not null
  then 'true'
  else 'false'
end;
")"

if [[ "${RECOMMENDATION_TABLE_AVAILABLE}" == "true" ]]; then
  smoke_db_query "
with recommendation_windows as (
  select
    count(*) filter (where created_at >= now() - interval '10 minutes' and outcome = 'ERROR') as recommendation_error_10m,
    count(*) filter (where created_at >= now() - interval '30 minutes' and outcome = 'NO_CANDIDATES') as recommendation_no_candidates_30m,
    count(*) filter (where created_at >= now() - interval '30 minutes') as recommendation_total_30m,
    count(*) filter (where created_at >= now() - interval '30 minutes' and outcome in ('ERROR', 'NO_CANDIDATES')) as recommendation_failed_30m,
    avg(duration_ms) filter (where created_at >= now() - interval '30 minutes') as recommendation_avg_duration_ms_30m,
    count(*) filter (where created_at >= now() - interval '1 hour') as recommendation_total_1h,
    count(*) filter (where created_at >= now() - interval '1 hour' and outcome = 'SUCCESS') as recommendation_success_1h
  from recommendation_run_logs
)
select 'recommendation_error_10m', coalesce(recommendation_error_10m, 0)::text from recommendation_windows
union all
select 'recommendation_no_candidates_30m', coalesce(recommendation_no_candidates_30m, 0)::text from recommendation_windows
union all
select 'recommendation_failure_rate_30m_pct',
       case
         when coalesce(recommendation_total_30m, 0) = 0 then '0.00'
         else to_char((recommendation_failed_30m * 100.0 / recommendation_total_30m), 'FM999999990.00')
       end
  from recommendation_windows
union all
select 'recommendation_avg_duration_ms_30m', to_char(coalesce(recommendation_avg_duration_ms_30m, 0), 'FM999999990.00') from recommendation_windows
union all
select 'recommendation_traffic_without_success_1h',
       case when coalesce(recommendation_total_1h, 0) > 0 and coalesce(recommendation_success_1h, 0) = 0 then 'true' else 'false' end
  from recommendation_windows;
" > "${RAW_RECOMMENDATION_OUT}"
  printf 'operational_alert_source_available\ttrue\n' >> "${RAW_RECOMMENDATION_OUT}"
else
  {
    printf 'operational_alert_source_available\tfalse\n'
    printf 'source_missing\trecommendation_run_logs\n'
  } > "${RAW_RECOMMENDATION_OUT}"
fi

if [[ "${WEB_PUSH_TABLES_AVAILABLE}" == "true" ]]; then
  smoke_db_query "
with subscription_summary as (
  select
    count(*) as web_push_subscription_total,
    count(*) filter (where enabled = false) as web_push_disabled_subscription_total
  from web_push_subscriptions
), attempt_summary as (
  select
    count(*) filter (
      where channel = 'web_push'
        and created_at >= now() - interval '15 minutes'
        and outcome in ('disabled', 'failed', 'exception', 'fanout_failed', 'gateway_false')
    ) as web_push_disabled_or_gateway_failure_15m,
    count(*) filter (
      where channel = 'web_push'
        and created_at >= now() - interval '1 hour'
        and outcome = 'disabled'
    ) as web_push_disabled_1h,
    count(*) filter (
      where channel = 'web_push'
        and created_at >= now() - interval '24 hours'
        and outcome = 'disabled'
    ) as web_push_disabled_24h
  from notification_attempt_logs
)
select 'web_push_subscription_total', coalesce(web_push_subscription_total, 0)::text from subscription_summary
union all
select 'web_push_disabled_ratio_pct',
       case
         when coalesce(web_push_subscription_total, 0) = 0 then '0.00'
         else to_char((web_push_disabled_subscription_total * 100.0 / web_push_subscription_total), 'FM999999990.00')
       end
  from subscription_summary
union all
select 'web_push_disabled_or_gateway_failure_15m', coalesce(web_push_disabled_or_gateway_failure_15m, 0)::text from attempt_summary
union all
select 'notification_disabled_attempt_multiplier_1h',
       case
         when coalesce(web_push_disabled_24h, 0) = 0 then '0.00'
         else to_char((web_push_disabled_1h * 24.0 / web_push_disabled_24h), 'FM999999990.00')
       end
  from attempt_summary;
" > "${RAW_WEB_PUSH_OUT}"
  printf 'operational_alert_source_available\ttrue\n' >> "${RAW_WEB_PUSH_OUT}"
else
  {
    printf 'operational_alert_source_available\tfalse\n'
    printf 'source_missing\tweb_push_subscriptions,notification_attempt_logs\n'
  } > "${RAW_WEB_PUSH_OUT}"
fi

python3 - \
  "${RAW_RECOMMENDATION_OUT}" \
  "${RAW_WEB_PUSH_OUT}" \
  "${RECOMMENDATION_SUMMARY_OUT}" \
  "${WEB_PUSH_SUMMARY_OUT}" \
  "${JSON_OUT}" \
  "${ARTIFACT_DIR}" <<'PY'
import csv
import json
import sys
from pathlib import Path

recommendation_raw = Path(sys.argv[1])
web_push_raw = Path(sys.argv[2])
recommendation_summary_out = Path(sys.argv[3])
web_push_summary_out = Path(sys.argv[4])
json_out = Path(sys.argv[5])
artifact_dir = sys.argv[6]


def read_metrics(path):
    metrics = {}
    with path.open(encoding="utf-8") as f:
        for row in csv.reader(f, delimiter="\t"):
            if not row:
                continue
            metrics[row[0]] = row[1] if len(row) > 1 else ""
    return metrics


recommendation = read_metrics(recommendation_raw)
web_push = read_metrics(web_push_raw)

recommendation_lines = [
    f"artifact_dir={artifact_dir}",
    f"operational_alert_source_available={recommendation.get('operational_alert_source_available', 'true')}",
    f"source_missing={recommendation.get('source_missing', '')}",
    f"recommendation_error_10m={recommendation.get('recommendation_error_10m', '0')}",
    f"recommendation_no_candidates_30m={recommendation.get('recommendation_no_candidates_30m', '0')}",
    f"recommendation_failure_rate_30m_pct={recommendation.get('recommendation_failure_rate_30m_pct', '0.00')}",
    f"recommendation_avg_duration_ms_30m={recommendation.get('recommendation_avg_duration_ms_30m', '0.00')}",
    f"recommendation_traffic_without_success_1h={recommendation.get('recommendation_traffic_without_success_1h', 'false')}",
]
web_push_lines = [
    f"artifact_dir={artifact_dir}",
    f"operational_alert_source_available={web_push.get('operational_alert_source_available', 'true')}",
    f"source_missing={web_push.get('source_missing', '')}",
    f"web_push_subscription_total={web_push.get('web_push_subscription_total', '0')}",
    f"web_push_disabled_ratio_pct={web_push.get('web_push_disabled_ratio_pct', '0.00')}",
    f"web_push_disabled_or_gateway_failure_15m={web_push.get('web_push_disabled_or_gateway_failure_15m', '0')}",
    f"notification_disabled_attempt_multiplier_1h={web_push.get('notification_disabled_attempt_multiplier_1h', '0.00')}",
]

recommendation_summary_out.write_text("\n".join(recommendation_lines) + "\n", encoding="utf-8")
web_push_summary_out.write_text("\n".join(web_push_lines) + "\n", encoding="utf-8")
json_out.write_text(json.dumps({
    "artifactDir": artifact_dir,
    "recommendation": recommendation,
    "webPush": web_push,
    "evaluatorUsage": {
        "RECOMMENDATION_RUN_SUMMARY": str(recommendation_summary_out),
        "WEB_PUSH_SUMMARY": str(web_push_summary_out),
    },
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${RECOMMENDATION_SUMMARY_OUT}" "${LATEST_RECOMMENDATION_SUMMARY_LINK}"
smoke_publish_file "${WEB_PUSH_SUMMARY_OUT}" "${LATEST_WEB_PUSH_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"

cat "${RECOMMENDATION_SUMMARY_OUT}"
cat "${WEB_PUSH_SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_recommendation_summary_link=${LATEST_RECOMMENDATION_SUMMARY_LINK}"
echo "latest_web_push_summary_link=${LATEST_WEB_PUSH_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
