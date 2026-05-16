#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-60}"

smoke_resolve_admin_credentials "${ROOT_DIR}"

: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

HEALTH_RESPONSE="${TMP_DIR}/health.json"
HEALTH_STDERR="${TMP_DIR}/health.stderr"
LOGIN_RESPONSE="${TMP_DIR}/login.json"
BACKFILL_RESPONSE="${TMP_DIR}/backfill.json"

smoke_print_step "app health"
smoke_wait_for_health "${HEALTH_RETRY_COUNT}" 2 "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${HEALTH_STDERR}" >/dev/null

before_missing="$(smoke_db_query "
select count(*)
from (
  select ws.id
  from welfare_services ws
  left join service_taxonomy_summary_slots sts
    on sts.service_id = ws.id
   and sts.slot_key in ('GOV24_SERVICE_FIELD','GOV24_USER_TYPE','GOV24_BENEFIT_TYPE')
  where ws.source_type = 'GOV24'
  group by ws.id
  having count(distinct sts.slot_key) < 3
) gaps;
")"

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

smoke_print_step "gov24 sidecar backfill"
BACKFILL_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/admin/collect/gov24-sidecars-backfill?limitPerSource=0" "${BACKFILL_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${BACKFILL_STATUS}" "gov24 sidecar backfill" "${BACKFILL_RESPONSE}"

after_missing="$(smoke_db_query "
select count(*)
from (
  select ws.id
  from welfare_services ws
  left join service_taxonomy_summary_slots sts
    on sts.service_id = ws.id
   and sts.slot_key in ('GOV24_SERVICE_FIELD','GOV24_USER_TYPE','GOV24_BENEFIT_TYPE')
  where ws.source_type = 'GOV24'
  group by ws.id
  having count(distinct sts.slot_key) < 3
) gaps;
")"

if (( after_missing > before_missing )); then
  echo "gov24 sidecar backfill worsened missing summary slot coverage: before=${before_missing} after=${after_missing}" >&2
  cat "${BACKFILL_RESPONSE}" >&2
  exit 1
fi

if (( after_missing != 0 )); then
  echo "gov24 sidecar backfill left summary slot gaps: before=${before_missing} after=${after_missing}" >&2
  smoke_db_query "
  select ws.id, ws.source_id
  from welfare_services ws
  left join service_taxonomy_summary_slots sts
    on sts.service_id = ws.id
   and sts.slot_key in ('GOV24_SERVICE_FIELD','GOV24_USER_TYPE','GOV24_BENEFIT_TYPE')
  where ws.source_type = 'GOV24'
  group by ws.id, ws.source_id
  having count(distinct sts.slot_key) < 3
  order by ws.id;
  " >&2
  exit 1
fi

echo "gov24_sidecar_backfill_before_missing=${before_missing}"
echo "gov24_sidecar_backfill_after_missing=${after_missing}"
echo "app_base_url=${APP_BASE_URL}"
