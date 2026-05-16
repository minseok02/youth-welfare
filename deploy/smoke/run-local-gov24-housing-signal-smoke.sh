#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-gov24.housing.signal}"
SMOKE_NAME="${SMOKE_NAME:-주거신호점검}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SMOKE_INTEREST_FIELD="${SMOKE_INTEREST_FIELD:-주거}"
SMOKE_PRIORITY_CODE="${SMOKE_PRIORITY_CODE:-HOUSING}"
TARGET_SERVICE_ID="${TARGET_SERVICE_ID:-4689}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
COOKIE_JAR="${ARTIFACT_DIR}/user.cookie"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SIGNUP_RESPONSE="${ARTIFACT_DIR}/signup.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/login.json"
PROFILE_RESPONSE="${ARTIFACT_DIR}/profile.json"
PRIORITIES_RESPONSE="${ARTIFACT_DIR}/priorities.json"
REFRESH_RESPONSE="${ARTIFACT_DIR}/refresh.json"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

extract_json() {
  local response_file="$1"
  local expression="$2"
  python3 - "$response_file" "$expression" <<'PY'
import json
import sys

response_file, expression = sys.argv[1], sys.argv[2]
with open(response_file, "r", encoding="utf-8") as fp:
    payload = json.load(fp)

value = eval(expression, {"__builtins__": {}, "len": len, "sum": sum, "str": str}, {"payload": payload})
if value is None:
    print("")
elif isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)
PY
}

query_batch_metrics() {
  local email="$1"
  local service_id="$2"
  smoke_db_query "
    with target_user as (
      select u.user_key
      from users u
      where u.email = '${email}'
    ),
    target_batch as (
      select user_key, max(recommended_at) as recommended_at
      from user_recommendations
      where user_key = (select user_key from target_user)
      group by user_key
    ),
    ranked as (
      select
        ur.user_key,
        ur.recommended_at,
        ws.id as service_id,
        ws.source_type,
        ws.title,
        coalesce(ur.ai_status, 'NULL_STATUS') as ai_status,
        ur.rule_weighted_score,
        ur.ai_score,
        ur.final_score,
        row_number() over (
          partition by ur.user_key
          order by ur.final_score desc, ur.id asc
        ) as final_rank,
        row_number() over (
          partition by ur.user_key
          order by ur.rule_weighted_score desc nulls last, ur.id asc
        ) as rule_rank
      from user_recommendations ur
      join target_batch tb
        on tb.user_key = ur.user_key
       and tb.recommended_at = ur.recommended_at
      join welfare_services ws
        on ws.id = ur.service_id
    )
    select 'target_user_key', coalesce((select user_key from target_user), '')
    union all
    select 'target_recommended_at', coalesce((select recommended_at::text from target_batch), '')
    union all
    select 'fresh_batch_rows', coalesce((select count(*)::text from ranked), '0')
    union all
    select 'refresh_count', coalesce((select count(*)::text from ranked), '0')
    union all
    select 'top2_source_distribution', coalesce((
      select string_agg(source_type || ':' || cnt, ',' order by source_type)
      from (
        select source_type, count(*) as cnt
        from ranked
        where final_rank <= 2
        group by source_type
      ) s
    ), 'empty')
    union all
    select 'gov24_rows', coalesce((select count(*)::text from ranked where source_type = 'GOV24'), '0')
    union all
    select 'gov24_top10_rows', coalesce((select count(*)::text from ranked where source_type = 'GOV24' and final_rank <= 10), '0')
    union all
    select 'gov24_top2_rows', coalesce((select count(*)::text from ranked where source_type = 'GOV24' and final_rank <= 2), '0')
    union all
    select 'gov24_ai_status_distribution', coalesce((
      select string_agg(ai_status || ':' || cnt, ',' order by ai_status)
      from (
        select ai_status, count(*) as cnt
        from ranked
        where source_type = 'GOV24'
        group by ai_status
      ) s
    ), 'empty')
    union all
    select 'target_service_row', coalesce((
      select 'f' || final_rank || ':r' || rule_rank || ':' || source_type || ':' || service_id || ':' || title || ':' ||
             ai_status || ':' ||
             coalesce(rule_weighted_score::text, 'null') || ':' ||
             coalesce(ai_score::text, 'null') || ':' ||
             coalesce(final_score::text, 'null')
      from ranked
      where service_id = ${service_id}
      order by final_rank
      limit 1
    ), 'missing')
    union all
    select 'top2_rows_detail', coalesce((
      select string_agg(
        'f' || final_rank || ':r' || rule_rank || ':' || source_type || ':' || service_id || ':' || title || ':' ||
        coalesce(rule_weighted_score::text, 'null') || ':' ||
        coalesce(ai_score::text, 'null') || ':' ||
        coalesce(final_score::text, 'null'),
        ' || ' order by final_rank
      )
      from ranked
      where final_rank <= 2
    ), 'empty')
    union all
    select 'gov24_top10_rows_detail', coalesce((
      select string_agg(
        'f' || final_rank || ':r' || rule_rank || ':' || service_id || ':' || title || ':' ||
        ai_status || ':' ||
        coalesce(rule_weighted_score::text, 'null') || ':' ||
        coalesce(ai_score::text, 'null') || ':' ||
        coalesce(final_score::text, 'null'),
        ' || ' order by final_rank, service_id
      )
      from ranked
      where source_type = 'GOV24' and final_rank <= 10
    ), 'empty');
  "
}

smoke_require_command curl
smoke_require_command python3

SMOKE_EMAIL="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

smoke_print_step "signup ${SMOKE_EMAIL}"
smoke_seed_verified_email "${SMOKE_EMAIL}"
SIGNUP_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${SIGNUP_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\",
      \"name\": \"${SMOKE_NAME}\",
      \"birthDate\": \"${SMOKE_BIRTH_DATE}\",
      \"sido\": \"${SMOKE_SIDO}\",
      \"sgg\": \"${SMOKE_SGG}\",
      \"incomeLevel\": ${SMOKE_INCOME_LEVEL},
      \"employmentStatus\": \"${SMOKE_EMPLOYMENT_STATUS}\",
      \"householdType\": \"${SMOKE_HOUSEHOLD_TYPE}\"
    }"
)"
smoke_assert_status 200 "${SIGNUP_STATUS}" "signup" "${SIGNUP_RESPONSE}"

smoke_print_step "login"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -c "${COOKIE_JAR}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${SMOKE_EMAIL}\",
      \"password\": \"${SMOKE_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "login" "${LOGIN_RESPONSE}"
ACCESS_TOKEN="$(extract_json "${LOGIN_RESPONSE}" "payload['data']['accessToken']")"

smoke_print_step "profile update (${SMOKE_INTEREST_FIELD})"
PROFILE_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me" "${PROFILE_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"regionCode\": \"\",
      \"sido\": \"${SMOKE_SIDO}\",
      \"sgg\": \"${SMOKE_SGG}\",
      \"incomeLevel\": ${SMOKE_INCOME_LEVEL},
      \"householdType\": \"${SMOKE_HOUSEHOLD_TYPE}\",
      \"employmentStatus\": \"${SMOKE_EMPLOYMENT_STATUS}\",
      \"notificationYn\": false,
      \"notificationMinScore\": 0.5,
      \"displayCount\": 30,
      \"interestFields\": [\"${SMOKE_INTEREST_FIELD}\"],
      \"targetTypes\": []
    }"
)"
smoke_assert_status 200 "${PROFILE_STATUS}" "profile update" "${PROFILE_RESPONSE}"

smoke_print_step "priority update (${SMOKE_PRIORITY_CODE})"
PRIORITIES_STATUS="$(
  smoke_http_status PUT "${APP_BASE_URL}/api/users/me/priorities" "${PRIORITIES_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H 'Content-Type: application/json' \
    -d "{\"priorityCodes\":[\"${SMOKE_PRIORITY_CODE}\"]}"
)"
smoke_assert_status 200 "${PRIORITIES_STATUS}" "priority update" "${PRIORITIES_RESPONSE}"

smoke_print_step "recommendations refresh"
REFRESH_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${REFRESH_RESPONSE}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}"
)"
smoke_assert_status 200 "${REFRESH_STATUS}" "recommendations refresh" "${REFRESH_RESPONSE}"

METRICS="$(query_batch_metrics "${SMOKE_EMAIL}" "${TARGET_SERVICE_ID}")"
echo "${METRICS}" | while IFS=$'\t' read -r metric value; do
  [[ -n "${metric}" ]] || continue
  echo "METRIC ${metric}=${value}"
done

