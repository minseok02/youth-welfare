#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-60}"
MAX_REGION_BACKFILL_DURATION_MS="${MAX_REGION_BACKFILL_DURATION_MS:-60000}"
MIN_REGION_SERVICE_PCT="${MIN_REGION_SERVICE_PCT:-85}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
smoke_resolve_admin_access_token "${ROOT_DIR}"

if [[ -z "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  : "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set /tmp/youth-welfare-admin-smoke-email}"
  : "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

HEALTH_RESPONSE="${TMP_DIR}/health.json"
HEALTH_STDERR="${TMP_DIR}/health.stderr"
LOGIN_RESPONSE="${TMP_DIR}/login.json"
BACKFILL_RESPONSE="${TMP_DIR}/backfill.json"

metric() {
  local key="$1"
  local value="$2"
  printf 'METRIC %s=%s\n' "${key}" "${value}"
}

gov24_region_coverage_row() {
  smoke_db_query "
    with gov24 as (
      select ws.id
      from welfare_services ws
      join raw_api_payloads rap
        on rap.source_type = ws.source_type
       and rap.source_id = ws.source_id
       and rap.api_category = 'LIST'
      where ws.source_type = 'GOV24'
    ),
    region_counts as (
      select service_id, count(*) as region_rows
      from service_regions
      group by service_id
    )
    select
      count(*) as total_services,
      count(*) filter (where coalesce(rc.region_rows, 0) > 0) as region_services,
      count(*) filter (where coalesce(rc.region_rows, 0) = 0) as regionless_services,
      round(100.0 * count(*) filter (where coalesce(rc.region_rows, 0) > 0) / nullif(count(*), 0), 2) as region_service_pct,
      coalesce(sum(rc.region_rows), 0) as region_rows
    from gov24 g
    left join region_counts rc on rc.service_id = g.id;
  "
}

smoke_print_step "app health"
smoke_wait_for_health "${HEALTH_RETRY_COUNT}" 2 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${HEALTH_STDERR}" >/dev/null

if [[ -n "${ADMIN_ACCESS_TOKEN:-}" ]]; then
  ADMIN_TOKEN="${ADMIN_ACCESS_TOKEN}"
else
  smoke_print_step "admin login (${ADMIN_EMAIL})"
  LOGIN_STATUS="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
      -H 'Content-Type: application/json' \
      --data-binary @- <<JSON
{
  "email": "${ADMIN_EMAIL}",
  "password": "${ADMIN_PASSWORD}"
}
JSON
  )"
  smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
  ADMIN_TOKEN="$(python3 - "${LOGIN_RESPONSE}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
  )"
fi

IFS=$'\t' read -r before_total before_region before_regionless before_pct before_rows <<<"$(gov24_region_coverage_row)"

smoke_print_step "gov24 region backfill"
start_ms="$(date +%s%3N)"
BACKFILL_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/collect/gov24-sidecars-backfill?scope=regions&limitPerSource=0" "${BACKFILL_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
end_ms="$(date +%s%3N)"
duration_ms=$((end_ms - start_ms))
smoke_assert_status 200 "${BACKFILL_STATUS}" "gov24 region backfill" "${BACKFILL_RESPONSE}"

IFS=$'\t' read -r scope limit_per_source scanned upserted missing failed <<<"$(
  python3 - "${BACKFILL_RESPONSE}" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload.get("data") or payload
print("\t".join([
    str(data.get("scope", "")),
    str(data.get("limitPerSource", "")),
    str(data.get("scannedCount", 0)),
    str(data.get("upsertedCount", 0)),
    str(data.get("missingServiceCount", 0)),
    str(data.get("failedCount", 0)),
]))
PY
)"

IFS=$'\t' read -r after_total after_region after_regionless after_pct after_rows <<<"$(gov24_region_coverage_row)"

metric "gov24_region_backfill_status" "${BACKFILL_STATUS}"
metric "gov24_region_backfill_duration_ms" "${duration_ms}"
metric "gov24_region_backfill_max_duration_ms" "${MAX_REGION_BACKFILL_DURATION_MS}"
metric "gov24_region_backfill_scope" "${scope}"
metric "gov24_region_backfill_limit_per_source" "${limit_per_source}"
metric "gov24_region_backfill_scanned" "${scanned}"
metric "gov24_region_backfill_upserted" "${upserted}"
metric "gov24_region_backfill_missing" "${missing}"
metric "gov24_region_backfill_failed" "${failed}"
metric "gov24_region_before_total_services" "${before_total}"
metric "gov24_region_before_region_services" "${before_region}"
metric "gov24_region_before_regionless_services" "${before_regionless}"
metric "gov24_region_before_region_service_pct" "${before_pct}"
metric "gov24_region_before_region_rows" "${before_rows}"
metric "gov24_region_after_total_services" "${after_total}"
metric "gov24_region_after_region_services" "${after_region}"
metric "gov24_region_after_regionless_services" "${after_regionless}"
metric "gov24_region_after_region_service_pct" "${after_pct}"
metric "gov24_region_after_region_rows" "${after_rows}"

python3 - \
  "${duration_ms}" \
  "${MAX_REGION_BACKFILL_DURATION_MS}" \
  "${scanned}" \
  "${upserted}" \
  "${missing}" \
  "${failed}" \
  "${after_pct}" \
  "${MIN_REGION_SERVICE_PCT}" <<'PY'
import sys

duration_ms = int(sys.argv[1])
max_duration_ms = int(sys.argv[2])
scanned = int(sys.argv[3])
upserted = int(sys.argv[4])
missing = int(sys.argv[5])
failed = int(sys.argv[6])
after_pct = float(sys.argv[7])
min_region_pct = float(sys.argv[8])

if duration_ms > max_duration_ms:
    raise SystemExit(
        f"gov24 region backfill too slow: duration_ms={duration_ms} max={max_duration_ms}"
    )
if scanned <= 0:
    raise SystemExit("gov24 region backfill scanned no rows")
if upserted != scanned:
    raise SystemExit(f"gov24 region backfill incomplete: scanned={scanned} upserted={upserted}")
if missing != 0 or failed != 0:
    raise SystemExit(f"gov24 region backfill had missing/failed rows: missing={missing} failed={failed}")
if after_pct < min_region_pct:
    raise SystemExit(
        f"gov24 region coverage below threshold after backfill: pct={after_pct} min={min_region_pct}"
    )
PY

echo "OK gov24_region_backfill_smoke"
