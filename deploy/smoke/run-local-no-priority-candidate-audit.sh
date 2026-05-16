#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-Password123!}"
SMOKE_EMAIL_PREFIX="${SMOKE_EMAIL_PREFIX:-recommend.nopriority.audit}"
SMOKE_NAME="${SMOKE_NAME:-무우선순위감사}"
SMOKE_BIRTH_DATE="${SMOKE_BIRTH_DATE:-2001-04-30}"
SMOKE_SIDO="${SMOKE_SIDO:-인천광역시}"
SMOKE_SGG="${SMOKE_SGG:-중구}"
SMOKE_INCOME_LEVEL="${SMOKE_INCOME_LEVEL:-5}"
SMOKE_EMPLOYMENT_STATUS="${SMOKE_EMPLOYMENT_STATUS:-미취업}"
SMOKE_HOUSEHOLD_TYPE="${SMOKE_HOUSEHOLD_TYPE:-1인 가구}"
SAMPLE_COUNT="${SAMPLE_COUNT:-8}"
TOP_K="${TOP_K:-5}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
SUMMARY_TSV="${ARTIFACT_DIR}/summary.tsv"

cleanup() {
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

extract_access_token() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
}

extract_recommendation_count() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(len(payload.get("data") or []))
PY
}

query_topk_rows() {
  local email="$1"
  local top_k="$2"
  smoke_db_query "
    WITH target_user AS (
      SELECT user_key
      FROM users
      WHERE email = '${email}'
    ),
    latest_batch AS (
      SELECT MAX(recommended_at) AS recommended_at
      FROM user_recommendations
      WHERE user_key = (SELECT user_key FROM target_user)
    ),
    ranked AS (
      SELECT ur.user_key,
             ROW_NUMBER() OVER (
               ORDER BY ur.final_score DESC, ur.id DESC
             ) AS rank_idx,
             ur.service_id,
             ws.title,
             ws.source_type,
             COALESCE(ws.unified_category, '기타') AS category,
             COALESCE(ur.rule_weighted_score::text, '') AS rule_weighted_score,
             COALESCE(ur.ai_score::text, '') AS ai_score,
             COALESCE(ur.final_score::text, '') AS final_score
      FROM user_recommendations ur
      JOIN welfare_services ws
        ON ws.id = ur.service_id
      WHERE ur.user_key = (SELECT user_key FROM target_user)
        AND ur.recommended_at = (SELECT recommended_at FROM latest_batch)
    )
    SELECT user_key,
           rank_idx,
           service_id,
           title,
           source_type,
           category,
           COALESCE(rule_weighted_score, ''),
           COALESCE(ai_score, ''),
           COALESCE(final_score, '')
    FROM ranked
    WHERE rank_idx <= ${top_k}
    ORDER BY rank_idx;
  "
}

summarize_results() {
  local summary_file="$1"
  python3 - "${summary_file}" <<'PY'
import csv
import sys
from collections import Counter, defaultdict

rows = []
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    reader = csv.DictReader(fp, delimiter="\t")
    rows = list(reader)

if not rows:
    raise SystemExit("no rows captured")

sample_users = len({row["email"] for row in rows})
top1_rows = [row for row in rows if row["rank_idx"] == "1"]

top1_counter = Counter()
source_counter = Counter()
category_counter = Counter()
rank_source_counter = Counter()
rank_category_counter = Counter()
service_stats = defaultdict(lambda: {"count": 0, "rule_sum": 0.0, "ai_sum": 0.0, "final_sum": 0.0, "ai_count": 0})

for row in rows:
    service_key = (
        row["service_id"],
        row["title"],
        row["source"],
        row["category"],
    )
    source_counter[row["source"]] += 1
    category_counter[row["category"]] += 1
    rank_source_counter[(row["rank_idx"], row["source"])] += 1
    rank_category_counter[(row["rank_idx"], row["category"])] += 1

    stats = service_stats[service_key]
    stats["count"] += 1
    stats["rule_sum"] += float(row["rule_weighted_score"] or 0.0)
    if row["ai_score"]:
      stats["ai_sum"] += float(row["ai_score"])
      stats["ai_count"] += 1
    stats["final_sum"] += float(row["final_score"] or 0.0)

for row in top1_rows:
    key = (
        row["service_id"],
        row["title"],
        row["source"],
        row["category"],
    )
    top1_counter[key] += 1

leader, leader_count = top1_counter.most_common(1)[0]
leader_share = round(leader_count * 100.0 / len(top1_rows), 2)

print(f"sample_users={sample_users}")
print(f"top_k={len(rows) // sample_users}")
print(f"top1_leader_service_id={leader[0]}")
print(f"top1_leader_title={leader[1]}")
print(f"top1_leader_source={leader[2]}")
print(f"top1_leader_category={leader[3]}")
print(f"top1_leader_users={leader_count}")
print(f"top1_leader_share_pct={leader_share}")
print("[top1_distribution]")
for (service_id, title, source, category), count in top1_counter.most_common():
    print(f"{service_id}|{title}|{source}|{category}|{count}")

print("[topk_source_distribution]")
for source, count in source_counter.most_common():
    print(f"{source}|{count}")

print("[topk_category_distribution]")
for category, count in category_counter.most_common():
    print(f"{category}|{count}")

print("[source_by_rank]")
for (rank_idx, source), count in sorted(rank_source_counter.items(), key=lambda item: (int(item[0][0]), -item[1], item[0][1])):
    print(f"{rank_idx}|{source}|{count}")

print("[category_by_rank]")
for (rank_idx, category), count in sorted(rank_category_counter.items(), key=lambda item: (int(item[0][0]), -item[1], item[0][1])):
    print(f"{rank_idx}|{category}|{count}")

print("[topk_service_distribution]")
for (service_id, title, source, category), stats in sorted(service_stats.items(), key=lambda item: (-item[1]["count"], item[0][0]))[:20]:
    avg_rule = round(stats["rule_sum"] / stats["count"], 2)
    avg_ai = round(stats["ai_sum"] / stats["ai_count"], 2) if stats["ai_count"] else "NULL"
    avg_final = round(stats["final_sum"] / stats["count"], 5)
    print(f"{service_id}|{title}|{source}|{category}|{stats['count']}|{avg_rule}|{avg_ai}|{avg_final}")
PY
}

smoke_require_command curl
smoke_require_command python3

printf 'sample_index\temail\tuser_key\trank_idx\trecommendation_count\tservice_id\ttitle\tsource\tcategory\trule_weighted_score\tai_score\tfinal_score\n' > "${SUMMARY_TSV}"

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

for sample_index in $(seq 1 "${SAMPLE_COUNT}"); do
  email="$(smoke_build_email "${SMOKE_EMAIL_PREFIX}")"
  signup_response="${ARTIFACT_DIR}/signup-${sample_index}.json"
  login_response="${ARTIFACT_DIR}/login-${sample_index}.json"
  refresh_response="${ARTIFACT_DIR}/refresh-${sample_index}.json"

  smoke_print_step "sample ${sample_index}/${SAMPLE_COUNT} signup ${email}"
  smoke_seed_verified_email "${email}"
  signup_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/signup" "${signup_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
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
  smoke_assert_status 200 "${signup_status}" "signup sample ${sample_index}" "${signup_response}"

  login_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${login_response}" \
      -H 'Content-Type: application/json' \
      -d "{
        \"email\": \"${email}\",
        \"password\": \"${SMOKE_PASSWORD}\"
      }"
  )"
  smoke_assert_status 200 "${login_status}" "login sample ${sample_index}" "${login_response}"
  access_token="$(extract_access_token "${login_response}")"

  refresh_status="$(
    smoke_http_status POST "${APP_BASE_URL}/api/recommendations/refresh" "${refresh_response}" \
      -H "Authorization: Bearer ${access_token}"
  )"
  smoke_assert_status 200 "${refresh_status}" "recommendations refresh sample ${sample_index}" "${refresh_response}"
  recommendation_count="$(extract_recommendation_count "${refresh_response}")"

  topk_rows="$(query_topk_rows "${email}" "${TOP_K}")"
  if [[ -z "${topk_rows}" ]]; then
    echo "missing topk rows for ${email}" >&2
    exit 1
  fi

  while IFS=$'\t' read -r user_key rank_idx service_id title source category rule_weighted_score ai_score final_score; do
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "${sample_index}" \
      "${email}" \
      "${user_key}" \
      "${rank_idx}" \
      "${recommendation_count}" \
      "${service_id}" \
      "${title}" \
      "${source}" \
      "${category}" \
      "${rule_weighted_score}" \
      "${ai_score}" \
      "${final_score}" \
      >> "${SUMMARY_TSV}"
  done <<< "${topk_rows}"
done

echo
echo "no-priority candidate audit passed"
echo "app_base_url=${APP_BASE_URL}"
echo "sample_count=${SAMPLE_COUNT}"
echo "top_k=${TOP_K}"
summarize_results "${SUMMARY_TSV}"
