#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
REPO_ENV_FILE="${ROOT_DIR}/.env"
RECONCILE_DB_SCRIPT="${ROOT_DIR}/deploy/mysql/reconcile-local-runtime-db-accounts.sh"
APPLY_CANONICAL_DRAFT_SCRIPT="${ROOT_DIR}/deploy/mysql/apply-local-policy-sidecar-draft.sh"

APP_PID=""

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf "%s" "${value}"
}

unquote() {
  local value="$1"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf "%s" "${value}"
}

load_env_file() {
  local line key value

  [[ -f "${REPO_ENV_FILE}" ]] || return 0

  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "$(trim "${line}")" ]] && continue
    [[ "$(trim "${line}")" == \#* ]] && continue
    [[ "${line}" == *=* ]] || continue

    key="$(trim "${line%%=*}")"
    value="${line#*=}"
    value="$(unquote "${value}")"

    if [[ "${key}" == export\ * ]]; then
      key="$(trim "${key#export }")"
    fi
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    [[ -n "${!key+x}" ]] && continue
    export "${key}=${value}"
  done < "${REPO_ENV_FILE}"
}

cleanup() {
  stop_app || true
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

load_env_file

APP_PORT="${APP_PORT:-18082}"
APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:${APP_PORT}}"
APP_HEALTH_TIMEOUT_SECONDS="${APP_HEALTH_TIMEOUT_SECONDS:-120}"
ENSURE_DOCKER_SERVICES="${ENSURE_DOCKER_SERVICES:-true}"
MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-youth-welfare-db}"
RECONCILE_LOCAL_DB_ACCOUNTS="${RECONCILE_LOCAL_DB_ACCOUNTS:-true}"
AUTO_APPLY_LOCAL_CANONICAL_DRAFT="${AUTO_APPLY_LOCAL_CANONICAL_DRAFT:-true}"
CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY="${CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY:-false}"

DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:3307/youth_welfare?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul}"
APP_PII_DB_URL="${APP_PII_DB_URL:-jdbc:mysql://127.0.0.1:3307/youth_welfare_pii?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul}"
NOTIFICATION_PII_DB_URL="${NOTIFICATION_PII_DB_URL:-${APP_PII_DB_URL}}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
AES_SECRET_KEY="${AES_SECRET_KEY:-0123456789abcdef0123456789abcdef}"
USE_REAL_OPENAI_FOR_REPLAY="${USE_REAL_OPENAI_FOR_REPLAY:-false}"
OPENAI_API_KEY="${OPENAI_API_KEY:-invalid-for-rule-only-replay}"
if [[ "${USE_REAL_OPENAI_FOR_REPLAY}" != "true" ]]; then
  OPENAI_API_KEY="invalid-for-rule-only-replay"
fi
DB_USERNAME="${DB_USERNAME:-app_core_rw}"
DB_PASSWORD="${DB_PASSWORD:-welfare1234!}"
DB_MIGRATION_USERNAME="${DB_MIGRATION_USERNAME:-migration_admin}"
DB_MIGRATION_PASSWORD="${DB_MIGRATION_PASSWORD:-${DB_PASSWORD}}"
DB_APP_PII_USERNAME="${DB_APP_PII_USERNAME:-app_pii_rw}"
DB_APP_PII_PASSWORD="${DB_APP_PII_PASSWORD:-${DB_PASSWORD}}"
DB_NOTIFICATION_PII_RO_USERNAME="${DB_NOTIFICATION_PII_RO_USERNAME:-notification_pii_ro}"
DB_NOTIFICATION_PII_RO_PASSWORD="${DB_NOTIFICATION_PII_RO_PASSWORD:-${DB_PASSWORD}}"
DB_QUERY_USERNAME="${DB_QUERY_USERNAME:-${DB_MIGRATION_USERNAME}}"
DB_QUERY_PASSWORD="${DB_QUERY_PASSWORD:-${DB_MIGRATION_PASSWORD}}"

if [[ "${DB_USERNAME}" == "root" ]]; then
  DB_USERNAME="app_core_rw"
fi
if [[ "${DB_QUERY_USERNAME}" == "root" ]]; then
  DB_QUERY_USERNAME="${DB_MIGRATION_USERNAME}"
fi

if [[ "${DB_URL}" == jdbc:mysql://db:* ]]; then
  DB_URL="jdbc:mysql://127.0.0.1:3307/youth_welfare?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
fi
if [[ -z "${APP_PII_DB_URL}" || "${APP_PII_DB_URL}" == jdbc:mysql://db:* ]]; then
  APP_PII_DB_URL="jdbc:mysql://127.0.0.1:3307/youth_welfare_pii?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"
fi
if [[ -z "${NOTIFICATION_PII_DB_URL}" || "${NOTIFICATION_PII_DB_URL}" == jdbc:mysql://db:* ]]; then
  NOTIFICATION_PII_DB_URL="${APP_PII_DB_URL}"
fi
if [[ "${REDIS_HOST}" == "redis" ]]; then
  REDIS_HOST="127.0.0.1"
fi

SAMPLE_REGION_CODE="${SAMPLE_REGION_CODE:-28110}"
SAMPLE_SIDO="${SAMPLE_SIDO:-인천광역시}"
SAMPLE_SGG="${SAMPLE_SGG:-중구}"
SAMPLE_BIRTH_DATE="${SAMPLE_BIRTH_DATE:-2001-04-30}"
SAMPLE_INCOME_LEVEL="${SAMPLE_INCOME_LEVEL:-5}"
SAMPLE_EMPLOYMENT_STATUS="${SAMPLE_EMPLOYMENT_STATUS:-미취업}"
SAMPLE_HOUSEHOLD_TYPE="${SAMPLE_HOUSEHOLD_TYPE:-1인 가구}"
SAMPLE_INTEREST_FIELD="${SAMPLE_INTEREST_FIELD:-교육}"
SAMPLE_A_EMAIL="${SAMPLE_A_EMAIL:-education.replay.afterincome.a@example.com}"
SAMPLE_B_EMAIL="${SAMPLE_B_EMAIL:-education.replay.afterincome.b@example.com}"
SAMPLE_PASSWORD="${SAMPLE_PASSWORD:-Password123!}"
SAMPLE_A_PRIORITY_CODES="${SAMPLE_A_PRIORITY_CODES:-[\"EDUCATION\",\"JOB\"]}"
SAMPLE_B_PRIORITY_CODES="${SAMPLE_B_PRIORITY_CODES:-[\"HOUSING\",\"JOB\"]}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"
STRICT_CONTROL_ASSERT="${STRICT_CONTROL_ASSERT:-false}"
RECOMMEND_AI_REPLAY_TRACE_ENABLED="${RECOMMEND_AI_REPLAY_TRACE_ENABLED:-true}"
RECOMMEND_AI_REPLAY_SEED="${RECOMMEND_AI_REPLAY_SEED:-424242}"
REPLAY_SUMMARY_APPEND_FILE="${REPLAY_SUMMARY_APPEND_FILE:-}"
REPLAY_SUMMARY_TS="${REPLAY_SUMMARY_TS:-}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
OPENAI_MODE_FILE="${ARTIFACT_DIR}/openai-mode.txt"
APP_LOG_OFF="${ARTIFACT_DIR}/bootrun-off.log"
APP_LOG_ON="${ARTIFACT_DIR}/bootrun-on.log"
RESP_A_OFF="${ARTIFACT_DIR}/edu-a-off.json"
RESP_A_ON="${ARTIFACT_DIR}/edu-a-on.json"
RESP_B_OFF="${ARTIFACT_DIR}/edu-b-off.json"
RESP_B_ON="${ARTIFACT_DIR}/edu-b-on.json"
COOKIE_A="${ARTIFACT_DIR}/edu-a.cookie"
COOKIE_B="${ARTIFACT_DIR}/edu-b.cookie"
HEALTH_FILE="${ARTIFACT_DIR}/health.json"
META_FILE="${ARTIFACT_DIR}/response-service-meta.tsv"
SUMMARY_SLOT_METRICS_FILE="${ARTIFACT_DIR}/summary-slot-metrics.tsv"
USER_KEY_A_FILE="${ARTIFACT_DIR}/edu-a.userkey"
USER_KEY_B_FILE="${ARTIFACT_DIR}/edu-b.userkey"
SCORES_A_OFF="${ARTIFACT_DIR}/edu-a-off-scores.tsv"
SCORES_A_ON="${ARTIFACT_DIR}/edu-a-on-scores.tsv"
SCORES_B_OFF="${ARTIFACT_DIR}/edu-b-off-scores.tsv"
SCORES_B_ON="${ARTIFACT_DIR}/edu-b-on-scores.tsv"
REASON_DIFF_A="${ARTIFACT_DIR}/edu-a-ai-reason-diff.tsv"
REASON_DIFF_B="${ARTIFACT_DIR}/edu-b-ai-reason-diff.tsv"
REASON_PATTERN_SUMMARY_FILE="${ARTIFACT_DIR}/ai-reason-pattern-summary.tsv"
AI_TRACE_OFF_RAW="${ARTIFACT_DIR}/ai-trace-off.log"
AI_TRACE_ON_RAW="${ARTIFACT_DIR}/ai-trace-on.log"
AI_TRACE_A_OFF="${ARTIFACT_DIR}/edu-a-off-ai-trace.log"
AI_TRACE_B_OFF="${ARTIFACT_DIR}/edu-b-off-ai-trace.log"
AI_TRACE_A_ON="${ARTIFACT_DIR}/edu-a-on-ai-trace.log"
AI_TRACE_B_ON="${ARTIFACT_DIR}/edu-b-on-ai-trace.log"
AI_RESPONSE_TRACE_OFF_RAW="${ARTIFACT_DIR}/ai-trace-response-off.log"
AI_RESPONSE_TRACE_ON_RAW="${ARTIFACT_DIR}/ai-trace-response-on.log"
AI_RESPONSE_TRACE_A_OFF="${ARTIFACT_DIR}/edu-a-off-ai-response-trace.log"
AI_RESPONSE_TRACE_B_OFF="${ARTIFACT_DIR}/edu-b-off-ai-response-trace.log"
AI_RESPONSE_TRACE_A_ON="${ARTIFACT_DIR}/edu-a-on-ai-response-trace.log"
AI_RESPONSE_TRACE_B_ON="${ARTIFACT_DIR}/edu-b-on-ai-response-trace.log"

mysql_exec() {
  local sql="$1"
  if command -v mysql >/dev/null 2>&1; then
    MYSQL_PWD="${DB_QUERY_PASSWORD}" mysql \
      --default-character-set=utf8mb4 \
      --batch \
      --skip-column-names \
      -h 127.0.0.1 \
      -P 3307 \
      -u "${DB_QUERY_USERNAME}" \
      youth_welfare \
      -e "${sql}"
    return 0
  fi

  docker exec -e MYSQL_PWD="${DB_QUERY_PASSWORD}" -i "${MYSQL_CONTAINER_NAME}" \
    mysql --default-character-set=utf8mb4 --batch --skip-column-names \
    -u"${DB_QUERY_USERNAME}" youth_welfare -e "${sql}"
}

clear_cluster_ai_cache() {
  mysql_exec "
    SET NAMES utf8mb4;
    DELETE FROM cluster_ai_results;
  " >/dev/null
}

table_exists() {
  local table_name="$1"
  local result
  result="$(
    mysql_exec "
      SET NAMES utf8mb4;
      SELECT COUNT(*)
      FROM information_schema.tables
      WHERE table_schema = 'youth_welfare'
        AND table_name = '${table_name}';
    "
  )"
  [[ "${result}" == "1" ]]
}

ensure_local_canonical_draft() {
  local target_count

  if [[ "${AUTO_APPLY_LOCAL_CANONICAL_DRAFT}" != "true" ]]; then
    return 0
  fi

  if ! table_exists "service_taxonomies" || ! table_exists "service_taxonomy_summary_slots"; then
    "${APPLY_CANONICAL_DRAFT_SCRIPT}" >/dev/null
    return 0
  fi

  target_count="$(
    mysql_exec "
      SET NAMES utf8mb4;
      SELECT COUNT(*)
      FROM welfare_services ws
      JOIN service_taxonomies st ON st.service_id = ws.id
      LEFT JOIN (
        SELECT service_id, MAX(slot_label) AS slot_label
        FROM service_taxonomy_summary_slots
        WHERE slot_key = 'YOUTH_MAJOR'
        GROUP BY service_id
      ) stss_youth_major ON stss_youth_major.service_id = ws.id
      WHERE ws.unified_category = '기타'
        AND COALESCE(stss_youth_major.slot_label, st.youth_major_label) = '교육';
    "
  )"
  if [[ "${target_count}" == "0" ]]; then
    APPLY_SEED_WHEN_EMPTY_ONLY=false "${APPLY_CANONICAL_DRAFT_SCRIPT}" >/dev/null
  fi
}

require_replay_data_preconditions() {
  local welfare_service_count target_count

  if ! table_exists "welfare_services"; then
    echo "education replay precondition unmet: welfare_services table missing in local youth_welfare db" >&2
    exit 1
  fi

  if ! table_exists "service_taxonomies"; then
    echo "education replay precondition unmet: service_taxonomies table missing; local canonical schema/backfill not loaded" >&2
    exit 1
  fi

  if ! table_exists "service_taxonomy_summary_slots"; then
    echo "education replay precondition unmet: service_taxonomy_summary_slots table missing; local summary slot schema/backfill not loaded" >&2
    exit 1
  fi

  welfare_service_count="$(
    mysql_exec "
      SET NAMES utf8mb4;
      SELECT COUNT(*) FROM welfare_services;
    "
  )"
  if [[ "${welfare_service_count}" == "0" ]]; then
    echo "education replay precondition unmet: welfare_services is empty; load local policy snapshot before replay" >&2
    exit 1
  fi

  target_count="$(
    mysql_exec "
      SET NAMES utf8mb4;
      SELECT COUNT(*)
      FROM welfare_services ws
      JOIN service_taxonomies st ON st.service_id = ws.id
      LEFT JOIN (
        SELECT service_id, MAX(slot_label) AS slot_label
        FROM service_taxonomy_summary_slots
        WHERE slot_key = 'YOUTH_MAJOR'
        GROUP BY service_id
      ) stss_youth_major ON stss_youth_major.service_id = ws.id
      WHERE ws.unified_category = '기타'
        AND COALESCE(stss_youth_major.slot_label, st.youth_major_label) = '교육';
    "
  )"
  if [[ "${target_count}" == "0" ]]; then
    echo "education replay precondition unmet: no compat=기타 + youth_major=교육 target rows in local snapshot" >&2
    exit 1
  fi
}

lookup_user_key_by_email() {
  local email="$1"
  mysql_exec "
    SET NAMES utf8mb4;
    SELECT user_key
    FROM users
    WHERE email = '${email}'
    LIMIT 1;
  "
}

capture_recommendation_snapshot() {
  local user_key="$1"
  local output_file="$2"
  mysql_exec "
    SET NAMES utf8mb4;
    SELECT ur.service_id,
           ur.rule_weighted_score,
           COALESCE(ur.ai_score, 'NULL'),
           COALESCE(REPLACE(REPLACE(ur.ai_reason, '\t', ' '), '\n', ' '), 'NULL'),
           COALESCE(ur.rule_weight_used, 'NULL'),
           COALESCE(ur.ai_weight_used, 'NULL'),
           ur.final_score,
           ws.title,
           ws.unified_category
    FROM user_recommendations ur
    JOIN welfare_services ws ON ws.id = ur.service_id
    WHERE ur.user_key = '${user_key}'
    ORDER BY ur.final_score DESC, ur.id DESC
    LIMIT 30;
  " > "${output_file}"
}

wait_for_app_health() {
  local waited=0
  local sleep_seconds=2
  local status_code

  while (( waited < APP_HEALTH_TIMEOUT_SECONDS )); do
    status_code="$(curl -fsS -o "${HEALTH_FILE}" -w "%{http_code}" "${APP_BASE_URL}/actuator/health" 2>/dev/null || true)"
    if [[ "${status_code}" == "200" ]]; then
      if python3 - "${HEALTH_FILE}" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    data = json.load(fp)
sys.exit(0 if data.get("status") == "UP" else 1)
PY
      then
        return 0
      fi
    fi
    sleep "${sleep_seconds}"
    waited=$((waited + sleep_seconds))
  done

  echo "app health did not reach UP within ${APP_HEALTH_TIMEOUT_SECONDS}s" >&2
  [[ -f "${HEALTH_FILE}" ]] && cat "${HEALTH_FILE}" >&2 || true
  [[ -n "${APP_PID}" ]] && tail -n 200 "${APP_LOG_OFF}" >&2 2>/dev/null || true
  exit 1
}

wait_for_app_down() {
  local waited=0
  local sleep_seconds=1

  while (( waited < 30 )); do
    if ! curl -fsS "${APP_BASE_URL}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${sleep_seconds}"
    waited=$((waited + sleep_seconds))
  done

  echo "app did not shut down within 30s" >&2
  return 1
}

write_openai_mode() {
  if [[ "${USE_REAL_OPENAI_FOR_REPLAY}" == "true" ]]; then
    printf "real-openai\n" > "${OPENAI_MODE_FILE}"
  else
    printf "rule-only-invalid-key\n" > "${OPENAI_MODE_FILE}"
  fi
}

capture_ai_traces() {
  local log_file="$1"
  local raw_output="$2"
  local sample_a_output="$3"
  local sample_b_output="$4"
  local marker="$5"

  python3 - <<'PY' "${log_file}" "${raw_output}" "${sample_a_output}" "${sample_b_output}" "${marker}"
from pathlib import Path
import sys

log_file, raw_output, sample_a_output, sample_b_output, marker = sys.argv[1:]
lines = [
    line for line in Path(log_file).read_text(encoding="utf-8", errors="replace").splitlines()
    if marker in line
]
Path(raw_output).write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
Path(sample_a_output).write_text((lines[0] + "\n") if len(lines) >= 1 else "", encoding="utf-8")
Path(sample_b_output).write_text((lines[1] + "\n") if len(lines) >= 2 else "", encoding="utf-8")
PY
}

start_app() {
  local flag_value="$1"
  local log_file="$2"

  stop_app || true
  rm -f "${HEALTH_FILE}"

  (
    cd "${BACKEND_DIR}"
    DB_URL="${DB_URL}" \
    DB_USERNAME="${DB_USERNAME}" \
    DB_PASSWORD="${DB_PASSWORD}" \
    APP_PII_DB_URL="${APP_PII_DB_URL}" \
    NOTIFICATION_PII_DB_URL="${NOTIFICATION_PII_DB_URL}" \
    DB_APP_PII_USERNAME="${DB_APP_PII_USERNAME}" \
    DB_APP_PII_PASSWORD="${DB_APP_PII_PASSWORD}" \
    DB_NOTIFICATION_PII_RO_USERNAME="${DB_NOTIFICATION_PII_RO_USERNAME}" \
    DB_NOTIFICATION_PII_RO_PASSWORD="${DB_NOTIFICATION_PII_RO_PASSWORD}" \
    REDIS_HOST="${REDIS_HOST}" \
    REDIS_PORT="${REDIS_PORT}" \
    AES_SECRET_KEY="${AES_SECRET_KEY}" \
    OPENAI_API_KEY="${OPENAI_API_KEY}" \
    RECOMMEND_AI_REPLAY_TRACE_ENABLED="${RECOMMEND_AI_REPLAY_TRACE_ENABLED}" \
    RECOMMEND_AI_REPLAY_SEED="${RECOMMEND_AI_REPLAY_SEED}" \
    RECOMMEND_PRIORITY_EDUCATION_CANONICAL_BONUS_ENABLED="${flag_value}" \
    SERVER_PORT="${APP_PORT}" \
    ./gradlew bootRun --no-daemon
  ) >"${log_file}" 2>&1 &

  APP_PID=$!
  wait_for_app_health
}

stop_app() {
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    kill "${APP_PID}" 2>/dev/null || true
    wait "${APP_PID}" 2>/dev/null || true
  fi
  APP_PID=""
  wait_for_app_down || true
}

signup_if_needed() {
  local email="$1"
  local name="$2"
  local status
  status="$(curl -sS -o "${ARTIFACT_DIR}/signup.out" -w "%{http_code}" \
    -H "Content-Type: application/json" \
    -X POST "${APP_BASE_URL}/api/auth/signup" \
    -d "{\"email\":\"${email}\",\"password\":\"${SAMPLE_PASSWORD}\",\"name\":\"${name}\",\"birthDate\":\"${SAMPLE_BIRTH_DATE}\",\"sido\":\"${SAMPLE_SIDO}\",\"sgg\":\"${SAMPLE_SGG}\",\"incomeLevel\":${SAMPLE_INCOME_LEVEL},\"employmentStatus\":\"${SAMPLE_EMPLOYMENT_STATUS}\",\"householdType\":\"${SAMPLE_HOUSEHOLD_TYPE}\"}")"
  if [[ "${status}" != "200" && "${status}" != "409" ]]; then
    echo "signup failed for ${email}: ${status}" >&2
    cat "${ARTIFACT_DIR}/signup.out" >&2
    exit 1
  fi
}

login_and_token() {
  local email="$1"
  local cookie_file="$2"
  curl -sS -c "${cookie_file}" \
    -H "Content-Type: application/json" \
    -X POST "${APP_BASE_URL}/api/auth/login" \
    -d "{\"email\":\"${email}\",\"password\":\"${SAMPLE_PASSWORD}\"}" > "${ARTIFACT_DIR}/login.json"
  python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["data"]["accessToken"])' "${ARTIFACT_DIR}/login.json"
}

update_profile() {
  local access_token="$1"
  curl -sS \
    -H "Authorization: Bearer ${access_token}" \
    -H "Content-Type: application/json" \
    -X PUT "${APP_BASE_URL}/api/users/me" \
    -d "{\"regionCode\":\"${SAMPLE_REGION_CODE}\",\"sido\":\"${SAMPLE_SIDO}\",\"sgg\":\"${SAMPLE_SGG}\",\"incomeLevel\":${SAMPLE_INCOME_LEVEL},\"householdType\":\"${SAMPLE_HOUSEHOLD_TYPE}\",\"employmentStatus\":\"${SAMPLE_EMPLOYMENT_STATUS}\",\"notificationYn\":false,\"notificationMinScore\":0.5,\"displayCount\":30,\"interestFields\":[\"${SAMPLE_INTEREST_FIELD}\"],\"targetTypes\":[]}" \
    > "${ARTIFACT_DIR}/profile.out"
}

update_priorities() {
  local access_token="$1"
  local priority_codes="$2"
  curl -sS \
    -H "Authorization: Bearer ${access_token}" \
    -H "Content-Type: application/json" \
    -X PUT "${APP_BASE_URL}/api/users/me/priorities" \
    -d "{\"priorityCodes\":${priority_codes}}" \
    > "${ARTIFACT_DIR}/priorities.out"
}

refresh_recommendations() {
  local access_token="$1"
  local output_file="$2"
  curl -sS \
    -H "Authorization: Bearer ${access_token}" \
    -X POST "${APP_BASE_URL}/api/recommendations/refresh" \
    > "${output_file}"
}

collect_service_meta() {
  local service_ids
  service_ids="$(
    python3 - <<'PY' "${RESP_A_OFF}" "${RESP_A_ON}" "${RESP_B_OFF}" "${RESP_B_ON}"
import json
import sys
ids = []
for path in sys.argv[1:]:
    with open(path, "r", encoding="utf-8") as fp:
        ids.extend(row["serviceId"] for row in json.load(fp)["data"])
uniq = sorted(set(ids))
print(",".join(str(v) for v in uniq))
PY
  )"

  mysql_exec "
    SET NAMES utf8mb4;
    SELECT ws.id,
           ws.title,
           ws.unified_category,
           COALESCE(stss_youth_major.slot_label, st.youth_major_label, ''),
           COALESCE(stss_youth_mid.slot_label, st.youth_mid_label, ''),
           COALESCE(stss_provision_method.slot_label, st.provision_method_label, ws.apply_method_name, ''),
           COALESCE(stss_gov24_service_field.slot_label, st.gov24_service_field_label, ''),
           COALESCE(stss_gov24_user_type.slot_label, st.gov24_user_type_label, ''),
           COALESCE(stss_gov24_benefit_type.slot_label, st.gov24_benefit_type_label, '')
    FROM welfare_services ws
    LEFT JOIN service_taxonomies st ON st.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'YOUTH_MAJOR'
      GROUP BY service_id
    ) stss_youth_major ON stss_youth_major.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'YOUTH_MID'
      GROUP BY service_id
    ) stss_youth_mid ON stss_youth_mid.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'PROVISION_METHOD'
      GROUP BY service_id
    ) stss_provision_method ON stss_provision_method.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'GOV24_SERVICE_FIELD'
      GROUP BY service_id
    ) stss_gov24_service_field ON stss_gov24_service_field.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'GOV24_USER_TYPE'
      GROUP BY service_id
    ) stss_gov24_user_type ON stss_gov24_user_type.service_id = ws.id
    LEFT JOIN (
      SELECT service_id, MAX(slot_label) AS slot_label
      FROM service_taxonomy_summary_slots
      WHERE slot_key = 'GOV24_BENEFIT_TYPE'
      GROUP BY service_id
    ) stss_gov24_benefit_type ON stss_gov24_benefit_type.service_id = ws.id
    WHERE ws.id IN (${service_ids})
    ORDER BY ws.id;
  " > "${META_FILE}"
}

collect_summary_slot_metrics() {
  mysql_exec "
    SET NAMES utf8mb4;
    SELECT 'slot_rows', COUNT(*) FROM service_taxonomy_summary_slots
    UNION ALL
    SELECT 'slot_services', COUNT(DISTINCT service_id) FROM service_taxonomy_summary_slots
    UNION ALL
    SELECT 'slot_education_rows', COUNT(*)
    FROM service_taxonomy_summary_slots stss
    JOIN welfare_services ws ON ws.id = stss.service_id
    WHERE stss.slot_key = 'YOUTH_MAJOR'
      AND stss.slot_label = '교육'
      AND ws.unified_category = '기타'
    UNION ALL
    SELECT 'slot_education_services', COUNT(DISTINCT stss.service_id)
    FROM service_taxonomy_summary_slots stss
    JOIN welfare_services ws ON ws.id = stss.service_id
    WHERE stss.slot_key = 'YOUTH_MAJOR'
      AND stss.slot_label = '교육'
      AND ws.unified_category = '기타'
    UNION ALL
    SELECT 'slot_services_YOUTH_MAJOR', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MAJOR'
    UNION ALL
    SELECT 'slot_services_YOUTH_MID', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MID'
    UNION ALL
    SELECT 'slot_services_GOV24_SERVICE_FIELD', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_SERVICE_FIELD'
    UNION ALL
    SELECT 'slot_services_GOV24_USER_TYPE', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_USER_TYPE'
    UNION ALL
    SELECT 'slot_services_GOV24_BENEFIT_TYPE', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_BENEFIT_TYPE'
    UNION ALL
    SELECT 'slot_services_PROVISION_METHOD', COUNT(DISTINCT service_id)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'PROVISION_METHOD'
    UNION ALL
    SELECT 'slot_rows_PROVISION_METHOD', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'PROVISION_METHOD'
    UNION ALL
    SELECT 'slot_rows_YOUTH_MID', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MID'
    UNION ALL
    SELECT 'slot_rows_GOV24_SERVICE_FIELD', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_SERVICE_FIELD'
    UNION ALL
    SELECT 'slot_rows_GOV24_USER_TYPE', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_USER_TYPE'
    UNION ALL
    SELECT 'slot_rows_GOV24_BENEFIT_TYPE', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'GOV24_BENEFIT_TYPE'
    UNION ALL
    SELECT 'slot_rows_YOUTH_MAJOR', COUNT(*)
    FROM service_taxonomy_summary_slots
    WHERE slot_key = 'YOUTH_MAJOR'
      ;
  " > "${SUMMARY_SLOT_METRICS_FILE}"
}

print_summary() {
  python3 - <<'PY' "${RESP_A_OFF}" "${RESP_A_ON}" "${RESP_B_OFF}" "${RESP_B_ON}" "${META_FILE}" "${SUMMARY_SLOT_METRICS_FILE}" "${STRICT_CONTROL_ASSERT}" "${AI_RESPONSE_TRACE_A_OFF}" "${AI_RESPONSE_TRACE_A_ON}" "${AI_RESPONSE_TRACE_B_OFF}" "${AI_RESPONSE_TRACE_B_ON}" "${OPENAI_MODE_FILE}" "${ARTIFACT_DIR}" "${REPLAY_SUMMARY_APPEND_FILE}" "${REPLAY_SUMMARY_TS}" "${SCORES_A_OFF}" "${SCORES_A_ON}" "${SCORES_B_OFF}" "${SCORES_B_ON}" "${REASON_DIFF_A}" "${REASON_DIFF_B}"
import json
import re
import sys
from pathlib import Path
from datetime import datetime

resp_a_off, resp_a_on, resp_b_off, resp_b_on, meta_path, slot_metrics_path, strict_control_assert, trace_a_off, trace_a_on, trace_b_off, trace_b_on, openai_mode_file, artifact_dir, summary_append_file, replay_summary_ts, scores_a_off, scores_a_on, scores_b_off, scores_b_on, reason_diff_a, reason_diff_b = sys.argv[1:]

def load_rows(path):
    with open(path, "r", encoding="utf-8") as fp:
        return json.load(fp)["data"]

meta = {}
for line in Path(meta_path).read_text(encoding="utf-8").splitlines():
    (
        service_id,
        title,
        compat,
        youth_major,
        youth_mid,
        provision_method,
        gov24_service_field,
        gov24_user_type,
        gov24_benefit_type,
    ) = line.split("\t")
    meta[int(service_id)] = {
        "title": title,
        "compat": compat,
        "youth_major": youth_major,
        "youth_mid": youth_mid,
        "provision_method": provision_method,
        "gov24_service_field": gov24_service_field,
        "gov24_user_type": gov24_user_type,
        "gov24_benefit_type": gov24_benefit_type,
    }

slot_metrics = {}
for line in Path(slot_metrics_path).read_text(encoding="utf-8").splitlines():
    key, value = line.split("\t")
    slot_metrics[key] = int(value)

fp_pattern = re.compile(r"systemFingerprint=([^ ]+)")

def fingerprint_of(path):
    text = Path(path).read_text(encoding="utf-8")
    match = fp_pattern.search(text)
    return match.group(1) if match else "missing"

def summarize(label, rows):
    target_positions = []
    for idx, row in enumerate(rows, 1):
        service_id = row["serviceId"]
        info = meta.get(service_id, {})
        if info.get("compat") == "기타" and info.get("youth_major") == "교육":
            target_positions.append((idx, service_id, row["title"], row["finalScore"]))
    top10 = rows[:10]
    top10_target_count = sum(
        1
        for row in top10
        if meta.get(row["serviceId"], {}).get("compat") == "기타"
        and meta.get(row["serviceId"], {}).get("youth_major") == "교육"
    )
    print(label, "count", len(rows), "target_count", len(target_positions), "top10_target_count", top10_target_count)
    for i, row in enumerate(top10, 1):
        print(label, i, row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
    print(label, "top10_target_positions", target_positions[:10])
    return {
        "count": len(rows),
        "target_count": len(target_positions),
        "top10_target_count": top10_target_count,
    }

def load_score_rows(path):
    rows = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        service_id, rule_weighted_score, ai_score, ai_reason, rule_weight_used, ai_weight_used, final_score, title, unified_category = line.split("\t")
        rows[int(service_id)] = {
            "service_id": int(service_id),
            "rule_weighted_score": rule_weighted_score,
            "ai_score": ai_score,
            "ai_reason": ai_reason,
            "rule_weight_used": rule_weight_used,
            "ai_weight_used": ai_weight_used,
            "final_score": final_score,
            "title": title,
            "unified_category": unified_category,
        }
    return rows

def write_reason_diff(off_path, on_path, output_path):
    off_rows = load_score_rows(off_path)
    on_rows = load_score_rows(on_path)
    service_ids = sorted(set(off_rows.keys()) | set(on_rows.keys()))
    changed = []
    text_changed = 0
    membership_changed = 0
    for service_id in service_ids:
        off_row = off_rows.get(service_id)
        on_row = on_rows.get(service_id)
        if off_row is None or on_row is None:
            membership_changed += 1
            changed.append({
                "change_type": "entered" if off_row is None else "exited",
                "service_id": service_id,
                "title": (on_row or off_row)["title"],
                "off_reason": off_row["ai_reason"] if off_row else "MISSING",
                "on_reason": on_row["ai_reason"] if on_row else "MISSING",
                "off_ai_score": off_row["ai_score"] if off_row else "MISSING",
                "on_ai_score": on_row["ai_score"] if on_row else "MISSING",
                "off_final_score": off_row["final_score"] if off_row else "MISSING",
                "on_final_score": on_row["final_score"] if on_row else "MISSING",
            })
            continue
        off_reason = off_row["ai_reason"] if off_row else "MISSING"
        on_reason = on_row["ai_reason"] if on_row else "MISSING"
        if off_reason == on_reason:
            continue
        text_changed += 1
        changed.append({
            "change_type": "text_changed",
            "service_id": service_id,
            "title": (on_row or off_row)["title"],
            "off_reason": off_reason,
            "on_reason": on_reason,
            "off_ai_score": off_row["ai_score"] if off_row else "MISSING",
            "on_ai_score": on_row["ai_score"] if on_row else "MISSING",
            "off_final_score": off_row["final_score"] if off_row else "MISSING",
            "on_final_score": on_row["final_score"] if on_row else "MISSING",
        })
    with Path(output_path).open("w", encoding="utf-8") as fp:
        fp.write("change_type\tservice_id\ttitle\toff_ai_score\ton_ai_score\toff_final_score\ton_final_score\toff_reason\ton_reason\n")
        for row in changed:
            fp.write(
                f"{row['change_type']}\t{row['service_id']}\t{row['title']}\t{row['off_ai_score']}\t{row['on_ai_score']}\t"
                f"{row['off_final_score']}\t{row['on_final_score']}\t{row['off_reason']}\t{row['on_reason']}\n"
            )
    return {
        "total_changed": len(changed),
        "text_changed": text_changed,
        "membership_changed": membership_changed,
    }

def summarize_reason_patterns(diff_path):
    phrase_catalog = [
        ("직접적인 도움", "direct_help"),
        ("실질적인 도움이", "practical_help"),
        ("특정 분야에 국한", "narrow_scope"),
        ("관심이 있는", "interest_fit"),
        ("연관성이 낮", "low_relevance"),
        ("주거비 부담", "housing_burden"),
        ("큰 도움이", "strong_help"),
        ("취업 기회", "job_opportunity"),
        ("실무 경험", "practical_experience"),
        ("창의", "creativity"),
    ]
    rows = []
    counts = {}
    for phrase, key in phrase_catalog:
        counts[key] = 0
    with Path(diff_path).open("r", encoding="utf-8") as fp:
        for row in fp.read().splitlines()[1:]:
            if not row.strip():
                continue
            cols = row.split("\t")
            if not cols or cols[0] != "text_changed":
                continue
            combined = " || ".join(cols[-2:])
            for phrase, key in phrase_catalog:
                if phrase in combined:
                    counts[key] += 1
    return counts

def format_top_patterns(prefix, counts):
    ordered = sorted(
        ((key, value) for key, value in counts.items() if value > 0),
        key=lambda item: (-item[1], item[0])
    )[:4]
    if not ordered:
        return f"{prefix}=none"
    summary = ",".join(f"{key}:{value}" for key, value in ordered)
    return f"{prefix}={summary}"

a_off = summarize("A_OFF", load_rows(resp_a_off))
a_on = summarize("A_ON", load_rows(resp_a_on))
b_off = summarize("B_OFF", load_rows(resp_b_off))
b_on = summarize("B_ON", load_rows(resp_b_on))
a_reason_diff = write_reason_diff(scores_a_off, scores_a_on, reason_diff_a)
b_reason_diff = write_reason_diff(scores_b_off, scores_b_on, reason_diff_b)
a_reason_patterns = summarize_reason_patterns(reason_diff_a)
b_reason_patterns = summarize_reason_patterns(reason_diff_b)

with Path(reason_pattern_summary_file := Path(artifact_dir) / "ai-reason-pattern-summary.tsv").open("w", encoding="utf-8") as fp:
    fp.write("sample\tpattern_key\tcount\n")
    for sample, counts in (("A", a_reason_patterns), ("B", b_reason_patterns)):
        for key, value in sorted(counts.items()):
            fp.write(f"{sample}\t{key}\t{value}\n")

a_off_fp = fingerprint_of(trace_a_off)
a_on_fp = fingerprint_of(trace_a_on)
b_off_fp = fingerprint_of(trace_b_off)
b_on_fp = fingerprint_of(trace_b_on)
a_fp_relation = "same" if a_off_fp == a_on_fp else "different"
b_fp_relation = "same" if b_off_fp == b_on_fp else "different"
mode = Path(openai_mode_file).read_text(encoding="utf-8").strip() or "unknown"
ts_value = replay_summary_ts or datetime.now().astimezone().isoformat(timespec="seconds")

print("A_FINGERPRINT", a_off_fp, a_on_fp, a_fp_relation)
print("B_FINGERPRINT", b_off_fp, b_on_fp, b_fp_relation)
print(
    "SUMMARY_SLOT_METRIC",
    f"slot_rows={slot_metrics.get('slot_rows', 0)}",
    f"slot_services={slot_metrics.get('slot_services', 0)}",
    f"slot_education_rows={slot_metrics.get('slot_education_rows', 0)}",
    f"slot_education_services={slot_metrics.get('slot_education_services', 0)}",
    f"slot_services_YOUTH_MAJOR={slot_metrics.get('slot_services_YOUTH_MAJOR', 0)}",
    f"slot_services_YOUTH_MID={slot_metrics.get('slot_services_YOUTH_MID', 0)}",
    f"slot_services_GOV24_SERVICE_FIELD={slot_metrics.get('slot_services_GOV24_SERVICE_FIELD', 0)}",
    f"slot_services_GOV24_USER_TYPE={slot_metrics.get('slot_services_GOV24_USER_TYPE', 0)}",
    f"slot_services_GOV24_BENEFIT_TYPE={slot_metrics.get('slot_services_GOV24_BENEFIT_TYPE', 0)}",
    f"slot_services_PROVISION_METHOD={slot_metrics.get('slot_services_PROVISION_METHOD', 0)}",
)
print(
    "SUMMARY_METRIC",
    f"A_top10_target={a_off['top10_target_count']}->{a_on['top10_target_count']}",
    f"B_top10_target={b_off['top10_target_count']}->{b_on['top10_target_count']}",
    f"A_target_total={a_off['target_count']}->{a_on['target_count']}",
    f"B_target_total={b_off['target_count']}->{b_on['target_count']}",
    f"A_fp={a_fp_relation}",
    f"B_fp={b_fp_relation}",
)
print(
    "SUMMARY_REASON_METRIC",
    f"A_reason_changed={a_reason_diff['total_changed']}",
    f"B_reason_changed={b_reason_diff['total_changed']}",
    f"A_reason_text_changed={a_reason_diff['text_changed']}",
    f"B_reason_text_changed={b_reason_diff['text_changed']}",
    f"A_reason_membership_changed={a_reason_diff['membership_changed']}",
    f"B_reason_membership_changed={b_reason_diff['membership_changed']}",
)
print(
    "SUMMARY_REASON_PATTERN",
    format_top_patterns("A_top_patterns", a_reason_patterns),
    format_top_patterns("B_top_patterns", b_reason_patterns),
)

summary_line = (
    f"ts={ts_value} "
    f"mode={mode} "
    f"A_top10_target={a_off['top10_target_count']}->{a_on['top10_target_count']} "
    f"B_top10_target={b_off['top10_target_count']}->{b_on['top10_target_count']} "
    f"A_target_total={a_off['target_count']}->{a_on['target_count']} "
    f"B_target_total={b_off['target_count']}->{b_on['target_count']} "
    f"A_fp={a_fp_relation} "
    f"B_fp={b_fp_relation} "
    f"A_reason_changed={a_reason_diff['total_changed']} "
    f"B_reason_changed={b_reason_diff['total_changed']} "
    f"A_reason_text_changed={a_reason_diff['text_changed']} "
    f"B_reason_text_changed={b_reason_diff['text_changed']} "
    f"A_reason_membership_changed={a_reason_diff['membership_changed']} "
    f"B_reason_membership_changed={b_reason_diff['membership_changed']} "
    f"{format_top_patterns('A_top_patterns', a_reason_patterns).replace('=', '=')} "
    f"{format_top_patterns('B_top_patterns', b_reason_patterns).replace('=', '=')} "
    f"slot_rows={slot_metrics.get('slot_rows', 0)} "
    f"slot_services={slot_metrics.get('slot_services', 0)} "
    f"slot_education_services={slot_metrics.get('slot_education_services', 0)} "
    f"slot_services_YOUTH_MAJOR={slot_metrics.get('slot_services_YOUTH_MAJOR', 0)} "
    f"slot_services_YOUTH_MID={slot_metrics.get('slot_services_YOUTH_MID', 0)} "
    f"slot_services_GOV24_SERVICE_FIELD={slot_metrics.get('slot_services_GOV24_SERVICE_FIELD', 0)} "
    f"slot_services_GOV24_USER_TYPE={slot_metrics.get('slot_services_GOV24_USER_TYPE', 0)} "
    f"slot_services_GOV24_BENEFIT_TYPE={slot_metrics.get('slot_services_GOV24_BENEFIT_TYPE', 0)} "
    f"slot_services_PROVISION_METHOD={slot_metrics.get('slot_services_PROVISION_METHOD', 0)} "
    f"artifact_dir={artifact_dir}"
)

if summary_append_file:
    summary_path = Path(summary_append_file)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    with summary_path.open("a", encoding="utf-8") as fp:
        fp.write(summary_line + "\n")
    print("SUMMARY_APPEND_FILE", summary_append_file)

print("SUMMARY_APPEND_LINE", summary_line)

if a_on["top10_target_count"] <= a_off["top10_target_count"]:
    message = (
        "sample A did not improve target row top10 count "
        f"({a_off['top10_target_count']} -> {a_on['top10_target_count']})"
    )
    if mode == "real-openai":
        print(f"WARNING: {message}")
    else:
        raise SystemExit(message)
if b_on["top10_target_count"] > b_off["top10_target_count"]:
    message = (
        "sample B target row top10 count increased unexpectedly "
        f"({b_off['top10_target_count']} -> {b_on['top10_target_count']})"
    )
    if strict_control_assert.lower() == "true":
        raise SystemExit(message)
    print(f"WARNING: {message}")
PY
}

load_env_file
require_command curl
require_command python3
write_openai_mode

if [[ "${ENSURE_DOCKER_SERVICES}" == "true" ]]; then
  require_command docker
  if [[ "${RECONCILE_LOCAL_DB_ACCOUNTS}" == "true" ]]; then
    "${RECONCILE_DB_SCRIPT}" >/dev/null
  fi
  (cd "${ROOT_DIR}" && docker compose up -d db redis >/dev/null)
fi

ensure_local_canonical_draft
require_replay_data_preconditions

signup_or_prepare_samples() {
  local token_a token_b user_key_a user_key_b
  if [[ "${CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY}" == "true" ]] && table_exists "cluster_ai_results"; then
    clear_cluster_ai_cache
  fi
  signup_if_needed "${SAMPLE_A_EMAIL}" "Education Replay Sample A"
  signup_if_needed "${SAMPLE_B_EMAIL}" "Education Replay Sample B"
  token_a="$(login_and_token "${SAMPLE_A_EMAIL}" "${COOKIE_A}")"
  token_b="$(login_and_token "${SAMPLE_B_EMAIL}" "${COOKIE_B}")"
  user_key_a="$(lookup_user_key_by_email "${SAMPLE_A_EMAIL}")"
  user_key_b="$(lookup_user_key_by_email "${SAMPLE_B_EMAIL}")"
  printf "%s\n" "${user_key_a}" > "${USER_KEY_A_FILE}"
  printf "%s\n" "${user_key_b}" > "${USER_KEY_B_FILE}"
  update_profile "${token_a}"
  update_profile "${token_b}"
  update_priorities "${token_a}" "${SAMPLE_A_PRIORITY_CODES}"
  update_priorities "${token_b}" "${SAMPLE_B_PRIORITY_CODES}"
  refresh_recommendations "${token_a}" "${RESP_A_OFF}"
  capture_recommendation_snapshot "${user_key_a}" "${SCORES_A_OFF}"
  refresh_recommendations "${token_b}" "${RESP_B_OFF}"
  capture_recommendation_snapshot "${user_key_b}" "${SCORES_B_OFF}"
  capture_ai_traces "${APP_LOG_OFF}" "${AI_TRACE_OFF_RAW}" "${AI_TRACE_A_OFF}" "${AI_TRACE_B_OFF}" "[RealtimeAiGateway][replay-trace]"
  capture_ai_traces "${APP_LOG_OFF}" "${AI_RESPONSE_TRACE_OFF_RAW}" "${AI_RESPONSE_TRACE_A_OFF}" "${AI_RESPONSE_TRACE_B_OFF}" "[RealtimeAiGateway][replay-trace-response]"
}

run_on_phase() {
  local token_a token_b user_key_a user_key_b
  if [[ "${CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY}" == "true" ]] && table_exists "cluster_ai_results"; then
    clear_cluster_ai_cache
  fi
  token_a="$(login_and_token "${SAMPLE_A_EMAIL}" "${COOKIE_A}")"
  token_b="$(login_and_token "${SAMPLE_B_EMAIL}" "${COOKIE_B}")"
  user_key_a="$(cat "${USER_KEY_A_FILE}")"
  user_key_b="$(cat "${USER_KEY_B_FILE}")"
  refresh_recommendations "${token_a}" "${RESP_A_ON}"
  capture_recommendation_snapshot "${user_key_a}" "${SCORES_A_ON}"
  refresh_recommendations "${token_b}" "${RESP_B_ON}"
  capture_recommendation_snapshot "${user_key_b}" "${SCORES_B_ON}"
  capture_ai_traces "${APP_LOG_ON}" "${AI_TRACE_ON_RAW}" "${AI_TRACE_A_ON}" "${AI_TRACE_B_ON}" "[RealtimeAiGateway][replay-trace]"
  capture_ai_traces "${APP_LOG_ON}" "${AI_RESPONSE_TRACE_ON_RAW}" "${AI_RESPONSE_TRACE_A_ON}" "${AI_RESPONSE_TRACE_B_ON}" "[RealtimeAiGateway][replay-trace-response]"
}

start_app "false" "${APP_LOG_OFF}"
signup_or_prepare_samples
stop_app

start_app "true" "${APP_LOG_ON}"
run_on_phase
stop_app

collect_service_meta
collect_summary_slot_metrics
print_summary

echo
echo "education priority replay smoke passed"
echo "artifacts: ${ARTIFACT_DIR}"
