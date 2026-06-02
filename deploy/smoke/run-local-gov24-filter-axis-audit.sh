#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

SERVICE_FIELDS=(
  "생활안정"
  "농림축산어업"
  "보육·교육"
  "보건·의료"
  "임신·출산"
  "고용·창업"
  "문화·환경"
  "보호·돌봄"
  "행정·안전"
  "주거·자립"
)

USER_TYPES=(
  "개인"
  "가구"
  "법인/시설/단체"
  "소상공인"
)

BENEFIT_TYPES=(
  "현금"
  "현물"
  "기타"
  "현금(감면)"
  "이용권"
  "서비스(의료)"
  "시설이용"
  "기타(교육)"
  "현금(보험)"
  "현금(장학금)"
  "현금(융자)"
  "기타(상담)"
  "서비스(돌봄)"
  "서비스(일자리)"
  "의료지원"
  "상담/법률지원"
  "기술지원"
  "문화/여가지원"
  "민원"
  "봉사/기부"
)

sql_literal() {
  local value="$1"
  printf "'%s'" "${value//\'/\'\'}"
}

urlencode() {
  python3 - "$1" <<'PY'
import sys
import urllib.parse

print(urllib.parse.quote(sys.argv[1]))
PY
}

api_total_elements() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload.get("data") or {}
if isinstance(data, dict):
    total = data.get("totalElements")
    if total is not None:
        print(total)
    else:
        print(len(data.get("content") or []))
else:
    print(len(data or []))
PY
}

db_count_for_axis() {
  local axis="$1"
  local value="$2"
  local literal
  literal="$(sql_literal "${value}")"

  local filter_sql
  case "${axis}" in
    serviceField)
      filter_sql="
        EXISTS (
          SELECT 1
          FROM service_taxonomy_terms stt
          WHERE stt.service_id = ws.id
            AND stt.term_group = 'GOV24_SERVICE_FIELD'
            AND stt.term_label = ${literal}
        )
        OR (
          NOT EXISTS (
            SELECT 1
            FROM service_taxonomy_terms stt1
            WHERE stt1.service_id = ws.id
              AND stt1.term_group = 'GOV24_SERVICE_FIELD'
          )
          AND EXISTS (
            SELECT 1
            FROM service_taxonomy_summary_slots stss
            WHERE stss.service_id = ws.id
              AND stss.slot_key = 'GOV24_SERVICE_FIELD'
              AND stss.slot_label = ${literal}
          )
        )
      "
      ;;
    userType)
      filter_sql="
        EXISTS (
          SELECT 1
          FROM service_taxonomy_terms stt
          WHERE stt.service_id = ws.id
            AND stt.term_group = 'GOV24_USER_TYPE_TOKEN'
            AND stt.term_label = ${literal}
        )
        OR (
          NOT EXISTS (
            SELECT 1
            FROM service_taxonomy_terms stt1
            WHERE stt1.service_id = ws.id
              AND stt1.term_group = 'GOV24_USER_TYPE_TOKEN'
          )
          AND EXISTS (
            SELECT 1
            FROM service_taxonomies stx
            CROSS JOIN LATERAL regexp_split_to_table(coalesce(stx.gov24_user_type_label, ''), '\\|\\|') AS token_parts(bucket_label)
            WHERE stx.service_id = ws.id
              AND btrim(token_parts.bucket_label) = ${literal}
          )
        )
      "
      ;;
    benefitType)
      filter_sql="
        EXISTS (
          SELECT 1
          FROM service_taxonomy_terms stt
          WHERE stt.service_id = ws.id
            AND stt.term_group = 'GOV24_BENEFIT_TYPE_TOKEN'
            AND stt.term_label = ${literal}
        )
        OR (
          NOT EXISTS (
            SELECT 1
            FROM service_taxonomy_terms stt1
            WHERE stt1.service_id = ws.id
              AND stt1.term_group = 'GOV24_BENEFIT_TYPE_TOKEN'
          )
          AND EXISTS (
            SELECT 1
            FROM service_taxonomies stx
            CROSS JOIN LATERAL regexp_split_to_table(coalesce(stx.gov24_benefit_type_label, ''), '\\|\\|') AS token_parts(bucket_label)
            WHERE stx.service_id = ws.id
              AND btrim(token_parts.bucket_label) = ${literal}
          )
        )
      "
      ;;
    *)
      echo "unknown axis: ${axis}" >&2
      exit 1
      ;;
  esac

  smoke_db_query "
    SELECT COUNT(*)
    FROM welfare_services ws
    WHERE ws.source_type = 'GOV24'
      AND ws.status IN ('ACTIVE', 'UPCOMING')
      AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURRENT_DATE)
      AND (${filter_sql});
  "
}

audit_axis_value() {
  local axis="$1"
  local param_name="$2"
  local value="$3"
  local response_file="${ARTIFACT_DIR}/${axis}-$(printf '%s' "${value}" | sha256sum | awk '{print $1}').json"
  local encoded_value
  local status
  local db_count
  local api_count

  encoded_value="$(urlencode "${value}")"
  status="$(
    smoke_http_status GET "${APP_BASE_URL}/api/policies?sourceType=GOV24&${param_name}=${encoded_value}&size=1" "${response_file}"
  )"
  smoke_assert_status 200 "${status}" "gov24 ${axis} filter ${value}" "${response_file}"

  db_count="$(db_count_for_axis "${axis}" "${value}")"
  api_count="$(api_total_elements "${response_file}")"

  printf 'AXIS %s value=%s db_count=%s api_count=%s\n' "${axis}" "${value}" "${db_count}" "${api_count}"
  if [[ "${db_count}" != "${api_count}" ]]; then
    echo "Gov24 ${axis} filter count mismatch for ${value}: db=${db_count} api=${api_count}" >&2
    cat "${response_file}" >&2
    exit 1
  fi
}

assert_invalid_filter_rejected() {
  local param_name="$1"
  local response_file="${ARTIFACT_DIR}/invalid-${param_name}.json"
  local status

  status="$(
    smoke_http_status GET "${APP_BASE_URL}/api/policies?sourceType=GOV24&${param_name}=__invalid_gov24_filter__&size=1" "${response_file}"
  )"
  if [[ "${status}" != "400" ]]; then
    echo "Expected invalid ${param_name} to return 400, got ${status}" >&2
    cat "${response_file}" >&2
    exit 1
  fi
  printf 'INVALID_FILTER %s status=%s\n' "${param_name}" "${status}"
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

checked_count=0

smoke_print_step "Gov24 serviceField filters"
for value in "${SERVICE_FIELDS[@]}"; do
  audit_axis_value "serviceField" "gov24ServiceField" "${value}"
  checked_count=$((checked_count + 1))
done

smoke_print_step "Gov24 userType filters"
for value in "${USER_TYPES[@]}"; do
  audit_axis_value "userType" "gov24UserType" "${value}"
  checked_count=$((checked_count + 1))
done

smoke_print_step "Gov24 benefitType filters"
for value in "${BENEFIT_TYPES[@]}"; do
  audit_axis_value "benefitType" "gov24BenefitType" "${value}"
  checked_count=$((checked_count + 1))
done

smoke_print_step "invalid filter rejection"
assert_invalid_filter_rejected "gov24ServiceField"
assert_invalid_filter_rejected "gov24UserType"
assert_invalid_filter_rejected "gov24BenefitType"

echo "METRIC gov24_filter_axis_checked_values=${checked_count}"
echo "gov24_filter_axis_audit=passed"
echo "app_base_url=${APP_BASE_URL}"
if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
  echo "artifact_dir=${ARTIFACT_DIR}"
fi
