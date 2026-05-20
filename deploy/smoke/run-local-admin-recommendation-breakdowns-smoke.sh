#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-youth-welfare-app}"

smoke_resolve_admin_credentials "${ROOT_DIR}"
: "${ADMIN_EMAIL:?ADMIN_EMAIL is empty; export ADMIN_EMAIL or set SECURITY_ADMIN_EMAILS/.env or /tmp/youth-welfare-admin-smoke-email}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD is empty; export ADMIN_PASSWORD or set /tmp/youth-welfare-admin-smoke-password}"
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-14}"
BREAKDOWN_LIMIT="${BREAKDOWN_LIMIT:-3}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
BREAKDOWN_RESPONSE="${ARTIFACT_DIR}/recommendation-breakdowns.json"

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

extract_jwt_roles() {
  local response_file="$1"
  python3 - "$response_file" <<'PY'
import base64
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    token = json.load(fp)["data"]["accessToken"]

payload = token.split(".")[1]
payload += "=" * (-len(payload) % 4)
decoded = json.loads(base64.urlsafe_b64decode(payload))
print(",".join(decoded.get("roles") or []))
PY
}

extract_container_admin_allowlist() {
  if ! command -v docker >/dev/null 2>&1; then
    return 0
  fi

  if ! docker ps --format '{{.Names}}' | grep -Fxq "${APP_CONTAINER_NAME}"; then
    return 0
  fi

  docker exec "${APP_CONTAINER_NAME}" /bin/sh -lc 'printf "%s" "${SECURITY_ADMIN_EMAILS:-}"' 2>/dev/null || true
}

assert_breakdown_contract() {
  local response_file="$1"
  local expected_summary_window_days="$2"
  local expected_limit="$3"
  python3 - "$response_file" "$expected_summary_window_days" "$expected_limit" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload["data"]
expected_window = int(sys.argv[2])
expected_limit = int(sys.argv[3])

assert data["generatedAt"], "generatedAt missing"
assert data["windowDays"] == expected_window, "unexpected windowDays"
assert isinstance(data["sentLogsInWindow"], int), "sentLogsInWindow must be int"
assert isinstance(data["clickedLogsInWindow"], int), "clickedLogsInWindow must be int"
assert isinstance(data["fallbackLogsInWindow"], int), "fallbackLogsInWindow must be int"

traffic_mix = data["trafficMixInWindow"]
for key in (
    "exampleLogsInWindow",
    "boundedLocalLogsInWindow",
    "localRealNonExampleSeedLogsInWindow",
    "realUserLogsInWindow",
    "realNonExampleLogsInWindow",
    "exampleUsersInWindow",
    "boundedLocalUsersInWindow",
    "localRealNonExampleSeedUsersInWindow",
    "realUserUsersInWindow",
    "realNonExampleUsersInWindow",
    "exampleClickedUsersInWindow",
    "boundedLocalClickedUsersInWindow",
    "localRealNonExampleSeedClickedUsersInWindow",
    "realUserClickedUsersInWindow",
    "realNonExampleClickedUsersInWindow",
):
    assert isinstance(traffic_mix[key], int), f"{key} must be int"

assert isinstance(data["realUserTrafficGateInWindow"], str) and data["realUserTrafficGateInWindow"], "realUserTrafficGateInWindow must be non-empty string"
assert isinstance(data["recommendationReviewGate"], str) and data["recommendationReviewGate"], "recommendationReviewGate must be non-empty string"
assert isinstance(data["recentWindowRecommendationReviewReading"], str) and data["recentWindowRecommendationReviewReading"], "recentWindowRecommendationReviewReading must be non-empty string"
assert isinstance(data["historicalExampleDominanceDetected"], bool), "historicalExampleDominanceDetected must be bool"
assert isinstance(data["reviewGatePolicyCandidateStatus"], str) and data["reviewGatePolicyCandidateStatus"], "reviewGatePolicyCandidateStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyCandidateReason"], str) and data["reviewGatePolicyCandidateReason"], "reviewGatePolicyCandidateReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionStatus"], str) and data["reviewGatePolicyPromotionStatus"], "reviewGatePolicyPromotionStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReason"], str) and data["reviewGatePolicyPromotionReason"], "reviewGatePolicyPromotionReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionActionStatus"], str) and data["reviewGatePolicyPromotionActionStatus"], "reviewGatePolicyPromotionActionStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionActionReason"], str) and data["reviewGatePolicyPromotionActionReason"], "reviewGatePolicyPromotionActionReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReadinessStatus"], str) and data["reviewGatePolicyPromotionReadinessStatus"], "reviewGatePolicyPromotionReadinessStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReadinessReason"], str) and data["reviewGatePolicyPromotionReadinessReason"], "reviewGatePolicyPromotionReadinessReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionExecutionStatus"], str) and data["reviewGatePolicyPromotionExecutionStatus"], "reviewGatePolicyPromotionExecutionStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionExecutionReason"], str) and data["reviewGatePolicyPromotionExecutionReason"], "reviewGatePolicyPromotionExecutionReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalCriteriaStatus"], str) and data["reviewGatePolicyPromotionApprovalCriteriaStatus"], "reviewGatePolicyPromotionApprovalCriteriaStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalCriteriaReason"], str) and data["reviewGatePolicyPromotionApprovalCriteriaReason"], "reviewGatePolicyPromotionApprovalCriteriaReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalStatus"], str) and data["reviewGatePolicyPromotionApprovalStatus"], "reviewGatePolicyPromotionApprovalStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalReason"], str) and data["reviewGatePolicyPromotionApprovalReason"], "reviewGatePolicyPromotionApprovalReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalDecisionStatus"], str) and data["reviewGatePolicyPromotionApprovalDecisionStatus"], "reviewGatePolicyPromotionApprovalDecisionStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalDecisionReason"], str) and data["reviewGatePolicyPromotionApprovalDecisionReason"], "reviewGatePolicyPromotionApprovalDecisionReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalRecordStatus"], str) and data["reviewGatePolicyPromotionApprovalRecordStatus"], "reviewGatePolicyPromotionApprovalRecordStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionApprovalRecordReason"], str) and data["reviewGatePolicyPromotionApprovalRecordReason"], "reviewGatePolicyPromotionApprovalRecordReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReviewRunStatus"], str) and data["reviewGatePolicyPromotionReviewRunStatus"], "reviewGatePolicyPromotionReviewRunStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReviewRunReason"], str) and data["reviewGatePolicyPromotionReviewRunReason"], "reviewGatePolicyPromotionReviewRunReason must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReviewRunCriteriaStatus"], str) and data["reviewGatePolicyPromotionReviewRunCriteriaStatus"], "reviewGatePolicyPromotionReviewRunCriteriaStatus must be non-empty string"
assert isinstance(data["reviewGatePolicyPromotionReviewRunCriteriaReason"], str) and data["reviewGatePolicyPromotionReviewRunCriteriaReason"], "reviewGatePolicyPromotionReviewRunCriteriaReason must be non-empty string"
staleness = data["reviewGateStaleness"]
assert isinstance(staleness["targetServiceId"], int), "reviewGateStaleness.targetServiceId must be int"
assert isinstance(staleness["primaryReferenceMode"], str) and staleness["primaryReferenceMode"], "reviewGateStaleness.primaryReferenceMode must be non-empty string"
assert isinstance(staleness["recentWindowHours"], int), "reviewGateStaleness.recentWindowHours must be int"
assert isinstance(staleness["exampleLatestUsers"], int), "reviewGateStaleness.exampleLatestUsers must be int"
assert isinstance(staleness["exampleLatestUsersLast24h"], int), "reviewGateStaleness.exampleLatestUsersLast24h must be int"
assert isinstance(staleness["exampleTargetTop1Users"], int), "reviewGateStaleness.exampleTargetTop1Users must be int"
assert isinstance(staleness["exampleTargetTop1Last24h"], int), "reviewGateStaleness.exampleTargetTop1Last24h must be int"
assert isinstance(staleness["realUserLatestUsers"], int), "reviewGateStaleness.realUserLatestUsers must be int"
assert isinstance(staleness["realUserLatestUsersLast24h"], int), "reviewGateStaleness.realUserLatestUsersLast24h must be int"
assert isinstance(staleness["realUserTargetTop1Users"], int), "reviewGateStaleness.realUserTargetTop1Users must be int"
assert isinstance(staleness["realUserTargetTop1Last24h"], int), "reviewGateStaleness.realUserTargetTop1Last24h must be int"
concentration = data["latestBatchConcentration"]
assert isinstance(concentration["latestBatchRows"], int), "latestBatchConcentration.latestBatchRows must be int"
assert isinstance(concentration["latestBatchUsers"], int), "latestBatchConcentration.latestBatchUsers must be int"
assert isinstance(concentration["latestBatchDistinctServices"], int), "latestBatchConcentration.latestBatchDistinctServices must be int"
assert isinstance(concentration["top1LeaderUsers"], int), "latestBatchConcentration.top1LeaderUsers must be int"
assert concentration["concentrationReadiness"], "latestBatchConcentration.concentrationReadiness missing"
assert concentration["realUserCohortGate"], "latestBatchConcentration.realUserCohortGate missing"
assert concentration["signalQuality"], "latestBatchConcentration.signalQuality missing"
assert concentration["top1LeaderSignalSummary"], "latestBatchConcentration.top1LeaderSignalSummary missing"
leader_user_mix = concentration["top1LeaderUserMix"]
assert isinstance(leader_user_mix["exampleUsers"], int), "latestBatchConcentration.top1LeaderUserMix.exampleUsers must be int"
assert isinstance(leader_user_mix["boundedLocalUsers"], int), "latestBatchConcentration.top1LeaderUserMix.boundedLocalUsers must be int"
assert isinstance(leader_user_mix["localRealNonExampleSeedUsers"], int), "latestBatchConcentration.top1LeaderUserMix.localRealNonExampleSeedUsers must be int"
assert isinstance(leader_user_mix["realUserUsers"], int), "latestBatchConcentration.top1LeaderUserMix.realUserUsers must be int"
recent = data["recentWindowLatestBatch"]
assert isinstance(recent["recentWindowHours"], int), "recentWindowLatestBatch.recentWindowHours must be int"
assert isinstance(recent["targetServiceId"], int), "recentWindowLatestBatch.targetServiceId must be int"
assert isinstance(recent["latestBatchUsers"], int), "recentWindowLatestBatch.latestBatchUsers must be int"
assert isinstance(recent["realUserUsers"], int), "recentWindowLatestBatch.realUserUsers must be int"
assert isinstance(recent["targetTop1Users"], int), "recentWindowLatestBatch.targetTop1Users must be int"
assert isinstance(recent["targetTop1RealUserUsers"], int), "recentWindowLatestBatch.targetTop1RealUserUsers must be int"
assert isinstance(recent["top1LeaderRealUserUsers"], int), "recentWindowLatestBatch.top1LeaderRealUserUsers must be int"
if concentration["latestBatchUsers"] > 0:
    assert concentration["top1LeaderServiceId"] is not None, "latestBatchConcentration.top1LeaderServiceId missing"
    assert concentration["top1LeaderTitle"], "latestBatchConcentration.top1LeaderTitle missing"
if recent["latestBatchUsers"] > 0 and recent["top1LeaderServiceId"] is not None:
    assert recent["top1LeaderTitle"], "recentWindowLatestBatch.top1LeaderTitle missing"

for collection_key in (
    "topRepeatedServices",
    "top1Services",
    "sourceBreakdowns",
    "categoryBreakdowns",
    "weightBreakdowns",
    "recentFallbackSamples",
    "recentClickedSamples",
    "repeatExposureGroups",
):
    assert isinstance(data[collection_key], list), f"{collection_key} must be list"
    assert len(data[collection_key]) <= expected_limit, f"{collection_key} exceeds limit"

if data["topRepeatedServices"]:
    first = data["topRepeatedServices"][0]
    assert isinstance(first["serviceId"], int), "topRepeatedServices[0].serviceId must be int"
    assert isinstance(first["rowCount"], int), "topRepeatedServices[0].rowCount must be int"
    assert isinstance(first["distinctUsers"], int), "topRepeatedServices[0].distinctUsers must be int"
    user_mix = first["userMix"]
    assert isinstance(user_mix["exampleUsers"], int), "topRepeatedServices[0].userMix.exampleUsers must be int"
    assert isinstance(user_mix["localRealNonExampleSeedUsers"], int), "topRepeatedServices[0].userMix.localRealNonExampleSeedUsers must be int"
    assert isinstance(user_mix["realUserUsers"], int), "topRepeatedServices[0].userMix.realUserUsers must be int"

if data["top1Services"]:
    first = data["top1Services"][0]
    assert isinstance(first["serviceId"], int), "top1Services[0].serviceId must be int"
    assert isinstance(first["usersAsTop1"], int), "top1Services[0].usersAsTop1 must be int"
    user_mix = first["userMix"]
    assert isinstance(user_mix["exampleUsers"], int), "top1Services[0].userMix.exampleUsers must be int"
    assert isinstance(user_mix["localRealNonExampleSeedUsers"], int), "top1Services[0].userMix.localRealNonExampleSeedUsers must be int"
    assert isinstance(user_mix["realUserUsers"], int), "top1Services[0].userMix.realUserUsers must be int"

valid_cohorts = {"EXAMPLE_SMOKE", "BOUNDED_LOCAL", "LOCAL_REAL_NON_EXAMPLE_SEED", "REAL_USER"}
non_example_present = (
    traffic_mix["boundedLocalUsersInWindow"] > 0
    or traffic_mix["realNonExampleUsersInWindow"] > 0
)

fallback_cohort = "NONE"
clicked_cohort = "NONE"
repeat_cohort = "NONE"

if data["recentFallbackSamples"]:
    first = data["recentFallbackSamples"][0]
    assert first["fallback"] is True, "recentFallbackSamples[0].fallback must be true"
    assert first["userCohort"] in valid_cohorts, "recentFallbackSamples[0].userCohort invalid"
    if not non_example_present:
        assert first["userCohort"] == "EXAMPLE_SMOKE", "fallback sample should be EXAMPLE_SMOKE when non-example cohort is absent"
    fallback_cohort = first["userCohort"]

if data["recentClickedSamples"]:
    first = data["recentClickedSamples"][0]
    assert first["clicked"] is True, "recentClickedSamples[0].clicked must be true"
    assert first["userCohort"] in valid_cohorts, "recentClickedSamples[0].userCohort invalid"
    if not non_example_present:
        assert first["userCohort"] == "EXAMPLE_SMOKE", "clicked sample should be EXAMPLE_SMOKE when non-example cohort is absent"
    clicked_cohort = first["userCohort"]

if data["repeatExposureGroups"]:
    first = data["repeatExposureGroups"][0]
    assert first["exposureCount"] > 1, "repeatExposureGroups[0].exposureCount must be > 1"
    assert first["userCohort"] in valid_cohorts, "repeatExposureGroups[0].userCohort invalid"
    if not non_example_present:
        assert first["userCohort"] == "EXAMPLE_SMOKE", "repeat exposure sample should be EXAMPLE_SMOKE when non-example cohort is absent"
    repeat_cohort = first["userCohort"]

print(data["generatedAt"])
print(data["sentLogsInWindow"])
print(data["clickedLogsInWindow"])
print(data["fallbackLogsInWindow"])
print(traffic_mix["exampleLogsInWindow"])
print(traffic_mix["boundedLocalUsersInWindow"])
print(traffic_mix["localRealNonExampleSeedUsersInWindow"])
print(traffic_mix["realUserUsersInWindow"])
print(traffic_mix["realNonExampleUsersInWindow"])
print(data["realUserTrafficGateInWindow"])
print(data["recommendationReviewGate"])
print(concentration["latestBatchRows"])
print(concentration["latestBatchUsers"])
print(concentration["top1LeaderServiceId"] or "")
print(concentration["top1LeaderSharePct"])
print(concentration["concentrationReadiness"])
print(concentration["realUserCohortGate"])
print(concentration["signalQuality"])
print(leader_user_mix["exampleUsers"])
print(leader_user_mix["boundedLocalUsers"])
print(leader_user_mix["localRealNonExampleSeedUsers"])
print(leader_user_mix["realUserUsers"])
print(concentration["top1LeaderSignalSummary"])
print(data["topRepeatedServices"][0]["serviceId"] if data["topRepeatedServices"] else "")
print(data["top1Services"][0]["serviceId"] if data["top1Services"] else "")
print(data["topRepeatedServices"][0]["userMix"]["exampleUsers"] if data["topRepeatedServices"] else 0)
print(data["topRepeatedServices"][0]["userMix"]["localRealNonExampleSeedUsers"] if data["topRepeatedServices"] else 0)
print(data["topRepeatedServices"][0]["userMix"]["realUserUsers"] if data["topRepeatedServices"] else 0)
print(data["top1Services"][0]["userMix"]["exampleUsers"] if data["top1Services"] else 0)
print(data["top1Services"][0]["userMix"]["localRealNonExampleSeedUsers"] if data["top1Services"] else 0)
print(data["top1Services"][0]["userMix"]["realUserUsers"] if data["top1Services"] else 0)
print(fallback_cohort)
print(clicked_cohort)
print(repeat_cohort)
print(len(data["topRepeatedServices"]))
print(len(data["top1Services"]))
print(len(data["sourceBreakdowns"]))
print(len(data["categoryBreakdowns"]))
print(len(data["weightBreakdowns"]))
print(data["recentWindowRecommendationReviewReading"])
print(str(data["historicalExampleDominanceDetected"]).lower())
print(data["reviewGatePolicyCandidateStatus"])
print(data["reviewGatePolicyCandidateReason"])
print(data["reviewGatePolicyPromotionStatus"])
print(data["reviewGatePolicyPromotionReason"])
print(data["reviewGatePolicyPromotionActionStatus"])
print(data["reviewGatePolicyPromotionActionReason"])
print(data["reviewGatePolicyPromotionReadinessStatus"])
print(data["reviewGatePolicyPromotionReadinessReason"])
print(data["reviewGatePolicyPromotionExecutionStatus"])
print(data["reviewGatePolicyPromotionExecutionReason"])
print(data["reviewGatePolicyPromotionApprovalCriteriaStatus"])
print(data["reviewGatePolicyPromotionApprovalCriteriaReason"])
print(data["reviewGatePolicyPromotionApprovalStatus"])
print(data["reviewGatePolicyPromotionApprovalReason"])
print(data["reviewGatePolicyPromotionApprovalDecisionStatus"])
print(data["reviewGatePolicyPromotionApprovalDecisionReason"])
print(data["reviewGatePolicyPromotionApprovalRecordStatus"])
print(data["reviewGatePolicyPromotionApprovalRecordReason"])
print(data["reviewGatePolicyPromotionReviewRunStatus"])
print(data["reviewGatePolicyPromotionReviewRunReason"])
print(data["reviewGatePolicyPromotionReviewRunCriteriaStatus"])
print(data["reviewGatePolicyPromotionReviewRunCriteriaReason"])
print(staleness["primaryReferenceMode"])
print(staleness["exampleTargetTop1Users"])
print(staleness["exampleTargetTop1Last24h"])
print(staleness["realUserLatestUsers"])
print(staleness["realUserTargetTop1Users"])
print(recent["recentWindowHours"])
print(recent["targetServiceId"])
print(recent["latestBatchUsers"])
print(recent["realUserUsers"])
print(recent["top1LeaderServiceId"] or "")
print(recent["top1LeaderRealUserUsers"])
print(recent["targetTop1Users"])
print(recent["targetTop1RealUserUsers"])
PY
}

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

CONTAINER_ADMIN_ALLOWLIST="$(extract_container_admin_allowlist)"

smoke_print_step "admin login (${ADMIN_EMAIL})"
LOGIN_STATUS="$(
  smoke_http_status POST "${APP_BASE_URL}/api/auth/login" "${LOGIN_RESPONSE}" \
    -H 'Content-Type: application/json' \
    -d "{
      \"email\": \"${ADMIN_EMAIL}\",
      \"password\": \"${ADMIN_PASSWORD}\"
    }"
)"
smoke_assert_status 200 "${LOGIN_STATUS}" "admin login" "${LOGIN_RESPONSE}"
ADMIN_TOKEN="$(extract_access_token "${LOGIN_RESPONSE}")"
ADMIN_ROLES="$(extract_jwt_roles "${LOGIN_RESPONSE}")"

if [[ ",${ADMIN_ROLES}," != *",ROLE_ADMIN,"* ]]; then
  echo "admin login succeeded but ROLE_ADMIN is missing for ${ADMIN_EMAIL}" >&2
  if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
    echo "current ${APP_CONTAINER_NAME} SECURITY_ADMIN_EMAILS=${CONTAINER_ADMIN_ALLOWLIST}" >&2
  else
    echo "current ${APP_CONTAINER_NAME} SECURITY_ADMIN_EMAILS is empty or container was not inspectable" >&2
  fi
  echo "for local docker validation, restart app with:" >&2
  echo "  SECURITY_ADMIN_EMAILS=${ADMIN_EMAIL} docker compose up -d --force-recreate app" >&2
  exit 1
fi

smoke_print_step "recommendation breakdowns"
BREAKDOWN_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/recommendation-breakdowns?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&limit=${BREAKDOWN_LIMIT}" "${BREAKDOWN_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${BREAKDOWN_STATUS}" "recommendation breakdowns" "${BREAKDOWN_RESPONSE}"

BREAKDOWN_ASSERT_OUTPUT="$(
  assert_breakdown_contract "${BREAKDOWN_RESPONSE}" "${SUMMARY_WINDOW_DAYS}" "${BREAKDOWN_LIMIT}"
)"
mapfile -t BREAKDOWN_VALUES <<< "${BREAKDOWN_ASSERT_OUTPUT}"

echo
echo "admin recommendation breakdowns smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "admin_roles=${ADMIN_ROLES}"
echo "generated_at=${BREAKDOWN_VALUES[0]}"
echo "sent_logs_in_window=${BREAKDOWN_VALUES[1]}"
echo "clicked_logs_in_window=${BREAKDOWN_VALUES[2]}"
echo "fallback_logs_in_window=${BREAKDOWN_VALUES[3]}"
echo "example_logs_in_window=${BREAKDOWN_VALUES[4]}"
echo "bounded_local_users_in_window=${BREAKDOWN_VALUES[5]}"
echo "local_real_non_example_seed_users_in_window=${BREAKDOWN_VALUES[6]}"
echo "real_user_users_in_window=${BREAKDOWN_VALUES[7]}"
echo "real_non_example_users_in_window=${BREAKDOWN_VALUES[8]}"
echo "real_user_traffic_gate_in_window=${BREAKDOWN_VALUES[9]}"
echo "recommendation_review_gate=${BREAKDOWN_VALUES[10]}"
echo "latest_batch_rows=${BREAKDOWN_VALUES[11]}"
echo "latest_batch_users=${BREAKDOWN_VALUES[12]}"
echo "top1_leader_service_id=${BREAKDOWN_VALUES[13]}"
echo "top1_leader_share_pct=${BREAKDOWN_VALUES[14]}"
echo "concentration_readiness=${BREAKDOWN_VALUES[15]}"
echo "latest_batch_real_user_cohort_gate=${BREAKDOWN_VALUES[16]}"
echo "latest_batch_signal_quality=${BREAKDOWN_VALUES[17]}"
echo "top1_leader_example_users=${BREAKDOWN_VALUES[18]}"
echo "top1_leader_bounded_local_users=${BREAKDOWN_VALUES[19]}"
echo "top1_leader_local_real_non_example_seed_users=${BREAKDOWN_VALUES[20]}"
echo "top1_leader_real_user_users=${BREAKDOWN_VALUES[21]}"
echo "top1_leader_signal_summary=${BREAKDOWN_VALUES[22]}"
echo "top_repeated_leader_service_id=${BREAKDOWN_VALUES[23]}"
echo "top1_distribution_leader_service_id=${BREAKDOWN_VALUES[24]}"
echo "top_repeated_leader_example_users=${BREAKDOWN_VALUES[25]}"
echo "top_repeated_leader_local_real_non_example_seed_users=${BREAKDOWN_VALUES[26]}"
echo "top_repeated_leader_real_user_users=${BREAKDOWN_VALUES[27]}"
echo "top1_distribution_leader_example_users=${BREAKDOWN_VALUES[28]}"
echo "top1_distribution_leader_local_real_non_example_seed_users=${BREAKDOWN_VALUES[29]}"
echo "top1_distribution_leader_real_user_users=${BREAKDOWN_VALUES[30]}"
echo "recent_fallback_sample_user_cohort=${BREAKDOWN_VALUES[31]}"
echo "recent_clicked_sample_user_cohort=${BREAKDOWN_VALUES[32]}"
echo "repeat_exposure_user_cohort=${BREAKDOWN_VALUES[33]}"
echo "top_repeated_services_count=${BREAKDOWN_VALUES[34]}"
echo "top1_services_count=${BREAKDOWN_VALUES[35]}"
echo "source_breakdown_count=${BREAKDOWN_VALUES[36]}"
echo "category_breakdown_count=${BREAKDOWN_VALUES[37]}"
echo "weight_breakdown_count=${BREAKDOWN_VALUES[38]}"
echo "recent_window_recommendation_review_reading=${BREAKDOWN_VALUES[39]}"
echo "historical_example_dominance_detected=${BREAKDOWN_VALUES[40]}"
echo "review_gate_policy_candidate_status=${BREAKDOWN_VALUES[41]}"
echo "review_gate_policy_candidate_reason=${BREAKDOWN_VALUES[42]}"
echo "review_gate_policy_promotion_status=${BREAKDOWN_VALUES[43]}"
echo "review_gate_policy_promotion_reason=${BREAKDOWN_VALUES[44]}"
echo "review_gate_policy_promotion_action_status=${BREAKDOWN_VALUES[45]}"
echo "review_gate_policy_promotion_action_reason=${BREAKDOWN_VALUES[46]}"
echo "review_gate_policy_promotion_readiness_status=${BREAKDOWN_VALUES[47]}"
echo "review_gate_policy_promotion_readiness_reason=${BREAKDOWN_VALUES[48]}"
echo "review_gate_policy_promotion_execution_status=${BREAKDOWN_VALUES[49]}"
echo "review_gate_policy_promotion_execution_reason=${BREAKDOWN_VALUES[50]}"
echo "review_gate_policy_promotion_approval_criteria_status=${BREAKDOWN_VALUES[51]}"
echo "review_gate_policy_promotion_approval_criteria_reason=${BREAKDOWN_VALUES[52]}"
echo "review_gate_policy_promotion_approval_status=${BREAKDOWN_VALUES[53]}"
echo "review_gate_policy_promotion_approval_reason=${BREAKDOWN_VALUES[54]}"
echo "review_gate_policy_promotion_approval_decision_status=${BREAKDOWN_VALUES[55]}"
echo "review_gate_policy_promotion_approval_decision_reason=${BREAKDOWN_VALUES[56]}"
echo "review_gate_policy_promotion_approval_record_status=${BREAKDOWN_VALUES[57]}"
echo "review_gate_policy_promotion_approval_record_reason=${BREAKDOWN_VALUES[58]}"
echo "review_gate_policy_promotion_review_run_status=${BREAKDOWN_VALUES[59]}"
echo "review_gate_policy_promotion_review_run_reason=${BREAKDOWN_VALUES[60]}"
echo "review_gate_policy_promotion_review_run_criteria_status=${BREAKDOWN_VALUES[61]}"
echo "review_gate_policy_promotion_review_run_criteria_reason=${BREAKDOWN_VALUES[62]}"
echo "review_gate_primary_reference_mode=${BREAKDOWN_VALUES[63]}"
echo "review_gate_example_target_top1_users=${BREAKDOWN_VALUES[64]}"
echo "review_gate_example_target_top1_last_24h=${BREAKDOWN_VALUES[65]}"
echo "review_gate_real_user_latest_users=${BREAKDOWN_VALUES[66]}"
echo "review_gate_real_user_target_top1_users=${BREAKDOWN_VALUES[67]}"
echo "recent_window_hours=${BREAKDOWN_VALUES[68]}"
echo "recent_window_target_service_id=${BREAKDOWN_VALUES[69]}"
echo "recent_window_latest_batch_users=${BREAKDOWN_VALUES[70]}"
echo "recent_window_real_user_users=${BREAKDOWN_VALUES[71]}"
echo "recent_window_top1_leader_service_id=${BREAKDOWN_VALUES[72]}"
echo "recent_window_top1_leader_real_user_users=${BREAKDOWN_VALUES[73]}"
echo "recent_window_target_top1_users=${BREAKDOWN_VALUES[74]}"
echo "recent_window_target_top1_real_user_users=${BREAKDOWN_VALUES[75]}"
echo "summary_window_days=${SUMMARY_WINDOW_DAYS}"
echo "breakdown_limit=${BREAKDOWN_LIMIT}"
if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
  echo "container_security_admin_emails=${CONTAINER_ADMIN_ALLOWLIST}"
fi
