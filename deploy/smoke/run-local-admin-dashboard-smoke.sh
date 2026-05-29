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
SUMMARY_WINDOW_DAYS="${SUMMARY_WINDOW_DAYS:-7}"
TREND_WINDOW_DAYS_CSV="${TREND_WINDOW_DAYS_CSV:-1,7,30}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-15}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"

ARTIFACT_DIR="${ARTIFACT_DIR:-$(mktemp -d)}"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LOGIN_RESPONSE="${ARTIFACT_DIR}/admin-login.json"
DASHBOARD_RESPONSE="${ARTIFACT_DIR}/dashboard-summary.json"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"
mkdir -p "${ARTIFACT_DIR}"

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

assert_dashboard_contract() {
  local response_file="$1"
  local expected_summary_window_days="$2"
  local expected_trend_windows_csv="$3"
  python3 - "$response_file" "$expected_summary_window_days" "$expected_trend_windows_csv" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

data = payload["data"]
expected_summary_window = int(sys.argv[2])
expected_trend_windows = [int(value) for value in sys.argv[3].split(",") if value]

assert data["generatedAt"], "generatedAt missing"
assert isinstance(data["collect"]["failedJobsLast24h"], int), "collect.failedJobsLast24h must be int"
assert isinstance(data["recommendation"]["totalLogs"], int), "recommendation.totalLogs must be int"
assert data["collect"]["windowDays"] == expected_summary_window, "unexpected collect windowDays"
assert data["recommendation"]["windowDays"] == expected_summary_window, "unexpected recommendation windowDays"
assert isinstance(data["recommendation"]["topWeightStage"], bool), "recommendation.topWeightStage must be bool"
traffic_mix = data["recommendation"]["trafficMixInWindow"]
assert isinstance(traffic_mix["exampleLogsInWindow"], int), "recommendation.trafficMixInWindow.exampleLogsInWindow must be int"
assert isinstance(traffic_mix["boundedLocalLogsInWindow"], int), "recommendation.trafficMixInWindow.boundedLocalLogsInWindow must be int"
assert isinstance(traffic_mix["localRealNonExampleSeedLogsInWindow"], int), "recommendation.trafficMixInWindow.localRealNonExampleSeedLogsInWindow must be int"
assert isinstance(traffic_mix["realUserLogsInWindow"], int), "recommendation.trafficMixInWindow.realUserLogsInWindow must be int"
assert isinstance(traffic_mix["realNonExampleLogsInWindow"], int), "recommendation.trafficMixInWindow.realNonExampleLogsInWindow must be int"
assert isinstance(traffic_mix["exampleUsersInWindow"], int), "recommendation.trafficMixInWindow.exampleUsersInWindow must be int"
assert isinstance(traffic_mix["boundedLocalUsersInWindow"], int), "recommendation.trafficMixInWindow.boundedLocalUsersInWindow must be int"
assert isinstance(traffic_mix["localRealNonExampleSeedUsersInWindow"], int), "recommendation.trafficMixInWindow.localRealNonExampleSeedUsersInWindow must be int"
assert isinstance(traffic_mix["realUserUsersInWindow"], int), "recommendation.trafficMixInWindow.realUserUsersInWindow must be int"
assert isinstance(traffic_mix["realNonExampleUsersInWindow"], int), "recommendation.trafficMixInWindow.realNonExampleUsersInWindow must be int"
assert isinstance(traffic_mix["exampleClickedUsersInWindow"], int), "recommendation.trafficMixInWindow.exampleClickedUsersInWindow must be int"
assert isinstance(traffic_mix["boundedLocalClickedUsersInWindow"], int), "recommendation.trafficMixInWindow.boundedLocalClickedUsersInWindow must be int"
assert isinstance(traffic_mix["localRealNonExampleSeedClickedUsersInWindow"], int), "recommendation.trafficMixInWindow.localRealNonExampleSeedClickedUsersInWindow must be int"
assert isinstance(traffic_mix["realUserClickedUsersInWindow"], int), "recommendation.trafficMixInWindow.realUserClickedUsersInWindow must be int"
assert isinstance(traffic_mix["realNonExampleClickedUsersInWindow"], int), "recommendation.trafficMixInWindow.realNonExampleClickedUsersInWindow must be int"
assert isinstance(data["recommendation"]["realUserTrafficGateInWindow"], str) and data["recommendation"]["realUserTrafficGateInWindow"], "recommendation.realUserTrafficGateInWindow must be non-empty string"
assert isinstance(data["recommendation"]["recommendationReviewGate"], str) and data["recommendation"]["recommendationReviewGate"], "recommendation.recommendationReviewGate must be non-empty string"
assert isinstance(data["recommendation"]["recentWindowRecommendationReviewReading"], str) and data["recommendation"]["recentWindowRecommendationReviewReading"], "recommendation.recentWindowRecommendationReviewReading must be non-empty string"
assert isinstance(data["recommendation"]["historicalExampleDominanceDetected"], bool), "recommendation.historicalExampleDominanceDetected must be bool"
assert isinstance(data["recommendation"]["reviewGatePolicyCandidateStatus"], str) and data["recommendation"]["reviewGatePolicyCandidateStatus"], "recommendation.reviewGatePolicyCandidateStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyCandidateReason"], str) and data["recommendation"]["reviewGatePolicyCandidateReason"], "recommendation.reviewGatePolicyCandidateReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionStatus"], "recommendation.reviewGatePolicyPromotionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReason"], "recommendation.reviewGatePolicyPromotionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionActionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionActionStatus"], "recommendation.reviewGatePolicyPromotionActionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionActionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionActionReason"], "recommendation.reviewGatePolicyPromotionActionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReadinessStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReadinessStatus"], "recommendation.reviewGatePolicyPromotionReadinessStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReadinessReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReadinessReason"], "recommendation.reviewGatePolicyPromotionReadinessReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionExecutionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionExecutionStatus"], "recommendation.reviewGatePolicyPromotionExecutionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionExecutionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionExecutionReason"], "recommendation.reviewGatePolicyPromotionExecutionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalCriteriaStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalCriteriaStatus"], "recommendation.reviewGatePolicyPromotionApprovalCriteriaStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalCriteriaReason"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalCriteriaReason"], "recommendation.reviewGatePolicyPromotionApprovalCriteriaReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalStatus"], "recommendation.reviewGatePolicyPromotionApprovalStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalReason"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalReason"], "recommendation.reviewGatePolicyPromotionApprovalReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalDecisionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalDecisionStatus"], "recommendation.reviewGatePolicyPromotionApprovalDecisionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalDecisionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalDecisionReason"], "recommendation.reviewGatePolicyPromotionApprovalDecisionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalRecordStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalRecordStatus"], "recommendation.reviewGatePolicyPromotionApprovalRecordStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionApprovalRecordReason"], str) and data["recommendation"]["reviewGatePolicyPromotionApprovalRecordReason"], "recommendation.reviewGatePolicyPromotionApprovalRecordReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunStatus"], "recommendation.reviewGatePolicyPromotionReviewRunStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunReason"], "recommendation.reviewGatePolicyPromotionReviewRunReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunCriteriaStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunCriteriaStatus"], "recommendation.reviewGatePolicyPromotionReviewRunCriteriaStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunCriteriaReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunCriteriaReason"], "recommendation.reviewGatePolicyPromotionReviewRunCriteriaReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunDecisionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunDecisionStatus"], "recommendation.reviewGatePolicyPromotionReviewRunDecisionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunDecisionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunDecisionReason"], "recommendation.reviewGatePolicyPromotionReviewRunDecisionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalCriteriaReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalCriteriaReason"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalCriteriaReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalDecisionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalDecisionStatus"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalDecisionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalDecisionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalDecisionReason"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalDecisionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalStatus"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalReason"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordStatus"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordReason"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus must be non-empty string"
assert isinstance(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason"], str) and data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason"], "recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason must be non-empty string"
staleness = data["recommendation"]["reviewGateStaleness"]
assert isinstance(staleness["targetServiceId"], int), "recommendation.reviewGateStaleness.targetServiceId must be int"
assert isinstance(staleness["primaryReferenceMode"], str) and staleness["primaryReferenceMode"], "recommendation.reviewGateStaleness.primaryReferenceMode must be non-empty string"
assert isinstance(staleness["recentWindowHours"], int), "recommendation.reviewGateStaleness.recentWindowHours must be int"
assert isinstance(staleness["exampleLatestUsers"], int), "recommendation.reviewGateStaleness.exampleLatestUsers must be int"
assert isinstance(staleness["exampleLatestUsersLast24h"], int), "recommendation.reviewGateStaleness.exampleLatestUsersLast24h must be int"
assert isinstance(staleness["exampleTargetTop1Users"], int), "recommendation.reviewGateStaleness.exampleTargetTop1Users must be int"
assert isinstance(staleness["exampleTargetTop1Last24h"], int), "recommendation.reviewGateStaleness.exampleTargetTop1Last24h must be int"
assert isinstance(staleness["realUserLatestUsers"], int), "recommendation.reviewGateStaleness.realUserLatestUsers must be int"
assert isinstance(staleness["realUserLatestUsersLast24h"], int), "recommendation.reviewGateStaleness.realUserLatestUsersLast24h must be int"
assert isinstance(staleness["realUserTargetTop1Users"], int), "recommendation.reviewGateStaleness.realUserTargetTop1Users must be int"
assert isinstance(staleness["realUserTargetTop1Last24h"], int), "recommendation.reviewGateStaleness.realUserTargetTop1Last24h must be int"
concentration = data["recommendation"]["latestBatchConcentration"]
assert isinstance(concentration["latestBatchRows"], int), "recommendation.latestBatchConcentration.latestBatchRows must be int"
assert isinstance(concentration["latestBatchUsers"], int), "recommendation.latestBatchConcentration.latestBatchUsers must be int"
assert isinstance(concentration["latestBatchDistinctServices"], int), "recommendation.latestBatchConcentration.latestBatchDistinctServices must be int"
assert isinstance(concentration["top1LeaderUsers"], int), "recommendation.latestBatchConcentration.top1LeaderUsers must be int"
assert concentration["concentrationReadiness"], "recommendation.latestBatchConcentration.concentrationReadiness missing"
assert concentration["realUserCohortGate"], "recommendation.latestBatchConcentration.realUserCohortGate missing"
assert concentration["signalQuality"], "recommendation.latestBatchConcentration.signalQuality missing"
assert concentration["top1LeaderSignalSummary"], "recommendation.latestBatchConcentration.top1LeaderSignalSummary missing"
leader_user_mix = concentration["top1LeaderUserMix"]
assert isinstance(leader_user_mix["exampleUsers"], int), "recommendation.latestBatchConcentration.top1LeaderUserMix.exampleUsers must be int"
assert isinstance(leader_user_mix["boundedLocalUsers"], int), "recommendation.latestBatchConcentration.top1LeaderUserMix.boundedLocalUsers must be int"
assert isinstance(leader_user_mix["localRealNonExampleSeedUsers"], int), "recommendation.latestBatchConcentration.top1LeaderUserMix.localRealNonExampleSeedUsers must be int"
assert isinstance(leader_user_mix["realUserUsers"], int), "recommendation.latestBatchConcentration.top1LeaderUserMix.realUserUsers must be int"
recent = data["recommendation"]["recentWindowLatestBatch"]
assert isinstance(recent["recentWindowHours"], int), "recommendation.recentWindowLatestBatch.recentWindowHours must be int"
assert isinstance(recent["targetServiceId"], int), "recommendation.recentWindowLatestBatch.targetServiceId must be int"
assert isinstance(recent["latestBatchUsers"], int), "recommendation.recentWindowLatestBatch.latestBatchUsers must be int"
assert isinstance(recent["realUserUsers"], int), "recommendation.recentWindowLatestBatch.realUserUsers must be int"
assert isinstance(recent["targetTop1Users"], int), "recommendation.recentWindowLatestBatch.targetTop1Users must be int"
assert isinstance(recent["targetTop1RealUserUsers"], int), "recommendation.recentWindowLatestBatch.targetTop1RealUserUsers must be int"
assert isinstance(recent["top1LeaderRealUserUsers"], int), "recommendation.recentWindowLatestBatch.top1LeaderRealUserUsers must be int"
if concentration["latestBatchUsers"] > 0:
    assert concentration["top1LeaderServiceId"] is not None, "recommendation.latestBatchConcentration.top1LeaderServiceId missing"
    assert concentration["top1LeaderTitle"], "recommendation.latestBatchConcentration.top1LeaderTitle missing"
if recent["latestBatchUsers"] > 0 and recent["top1LeaderServiceId"] is not None:
    assert recent["top1LeaderTitle"], "recommendation.recentWindowLatestBatch.top1LeaderTitle missing"
if data["recommendation"]["topWeightStage"]:
    assert data["recommendation"]["nextWeightKey"] is None, "top stage should not have nextWeightKey"
    assert data["recommendation"]["nextWeightMinLogCount"] is None, "top stage should not have nextWeightMinLogCount"
    assert data["recommendation"]["remainingLogsUntilNextWeight"] is None, "top stage should not have remaining logs"
else:
    assert isinstance(data["recommendation"]["nextWeightKey"], str) and data["recommendation"]["nextWeightKey"], "nextWeightKey missing"
    assert isinstance(data["recommendation"]["nextWeightMinLogCount"], int), "nextWeightMinLogCount must be int"
    assert isinstance(data["recommendation"]["remainingLogsUntilNextWeight"], int), "remainingLogsUntilNextWeight must be int"
    assert data["recommendation"]["remainingLogsUntilNextWeight"] >= 0, "remainingLogsUntilNextWeight must be >= 0"
assert data["notification"]["windowDays"] == expected_summary_window, "unexpected notification windowDays"
assert data["search"]["windowDays"] == expected_summary_window, "unexpected search windowDays"
assert isinstance(data["notification"]["sentInWindow"], int), "notification.sentInWindow must be int"
assert isinstance(data["search"]["zeroResultSearchesInWindow"], int), "search.zeroResultSearchesInWindow must be int"
assert isinstance(data["userPiiSync"]["pendingCount"], int), "userPiiSync.pendingCount must be int"
assert isinstance(data["userPiiSync"]["failedCount"], int), "userPiiSync.failedCount must be int"
assert isinstance(data["userPiiSync"]["syncedCount"], int), "userPiiSync.syncedCount must be int"

collect_windows = [point["windowDays"] for point in data["trend"]["collect"]]
recommendation_windows = [point["windowDays"] for point in data["trend"]["recommendation"]]
search_windows = [point["windowDays"] for point in data["trend"]["search"]]

assert collect_windows == expected_trend_windows, f"unexpected collect trend windows: {collect_windows}"
assert recommendation_windows == expected_trend_windows, f"unexpected recommendation trend windows: {recommendation_windows}"
assert search_windows == expected_trend_windows, f"unexpected search trend windows: {search_windows}"

print(data["generatedAt"])
print(data["collect"]["failedJobsLast24h"])
print(data["recommendation"]["totalLogs"])
print(data["recommendation"]["nextWeightKey"] or "")
print("" if data["recommendation"]["remainingLogsUntilNextWeight"] is None else data["recommendation"]["remainingLogsUntilNextWeight"])
print(data["recommendation"]["trafficMixInWindow"]["exampleLogsInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["boundedLocalLogsInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["localRealNonExampleSeedLogsInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["realUserLogsInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["realNonExampleLogsInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["boundedLocalUsersInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["localRealNonExampleSeedUsersInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["realUserUsersInWindow"])
print(data["recommendation"]["trafficMixInWindow"]["realNonExampleUsersInWindow"])
print(data["recommendation"]["realUserTrafficGateInWindow"])
print(data["recommendation"]["recommendationReviewGate"])
print(concentration["latestBatchRows"])
print(concentration["latestBatchUsers"])
print(concentration["top1LeaderServiceId"] or "")
print(concentration["top1LeaderSharePct"])
print(leader_user_mix["exampleUsers"])
print(leader_user_mix["boundedLocalUsers"])
print(leader_user_mix["localRealNonExampleSeedUsers"])
print(leader_user_mix["realUserUsers"])
print(concentration["top1LeaderSignalSummary"])
print(concentration["concentrationReadiness"])
print(concentration["realUserCohortGate"])
print(concentration["signalQuality"])
print(data["notification"]["sentInWindow"])
print(data["search"]["zeroResultSearchesInWindow"])
print(data["userPiiSync"]["pendingCount"])
print(data["userPiiSync"]["failedCount"])
print(data["userPiiSync"]["syncedCount"])
print(data["collect"]["windowDays"])
print(",".join(str(v) for v in collect_windows))
print(data["recommendation"]["recentWindowRecommendationReviewReading"])
print(str(data["recommendation"]["historicalExampleDominanceDetected"]).lower())
print(data["recommendation"]["reviewGatePolicyCandidateStatus"])
print(data["recommendation"]["reviewGatePolicyCandidateReason"])
print(data["recommendation"]["reviewGatePolicyPromotionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionActionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionActionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReadinessStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReadinessReason"])
print(data["recommendation"]["reviewGatePolicyPromotionExecutionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionExecutionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalCriteriaStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalCriteriaReason"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalReason"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalDecisionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalDecisionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalRecordStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionApprovalRecordReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunCriteriaStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunCriteriaReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunDecisionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunDecisionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalCriteriaReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalDecisionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalDecisionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus"])
print(data["recommendation"]["reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason"])
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

smoke_print_step "ensure admin account"
smoke_ensure_admin_account "${APP_BASE_URL}" "${ADMIN_EMAIL}" "${ADMIN_PASSWORD}"

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

smoke_print_step "dashboard summary"
TREND_QUERY_STRING="$(python3 - "${TREND_WINDOW_DAYS_CSV}" <<'PY'
import sys
from urllib.parse import urlencode

values = [value.strip() for value in sys.argv[1].split(",") if value.strip()]
print(urlencode([("trendWindowDays", value) for value in values]))
PY
)"
DASHBOARD_STATUS="$(
  smoke_http_status GET "${APP_BASE_URL}/api/admin/dashboard/summary?summaryWindowDays=${SUMMARY_WINDOW_DAYS}&${TREND_QUERY_STRING}" "${DASHBOARD_RESPONSE}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}"
)"
smoke_assert_status 200 "${DASHBOARD_STATUS}" "dashboard summary" "${DASHBOARD_RESPONSE}"

DASHBOARD_ASSERT_OUTPUT="$(
  assert_dashboard_contract "${DASHBOARD_RESPONSE}" "${SUMMARY_WINDOW_DAYS}" "${TREND_WINDOW_DAYS_CSV}"
)"
mapfile -t DASHBOARD_VALUES <<< "${DASHBOARD_ASSERT_OUTPUT}"

echo
echo "admin dashboard smoke passed"
echo "app_base_url=${APP_BASE_URL}"
echo "admin_email=${ADMIN_EMAIL}"
echo "admin_roles=${ADMIN_ROLES}"
echo "generated_at=${DASHBOARD_VALUES[0]}"
echo "collect_failed_jobs_last24h=${DASHBOARD_VALUES[1]}"
echo "recommendation_total_logs=${DASHBOARD_VALUES[2]}"
echo "recommendation_next_weight_key=${DASHBOARD_VALUES[3]}"
echo "recommendation_remaining_logs_until_next_weight=${DASHBOARD_VALUES[4]}"
echo "recommendation_example_logs_in_window=${DASHBOARD_VALUES[5]}"
echo "recommendation_bounded_local_logs_in_window=${DASHBOARD_VALUES[6]}"
echo "recommendation_local_real_non_example_seed_logs_in_window=${DASHBOARD_VALUES[7]}"
echo "recommendation_real_user_logs_in_window=${DASHBOARD_VALUES[8]}"
echo "recommendation_real_non_example_logs_in_window=${DASHBOARD_VALUES[9]}"
echo "recommendation_bounded_local_users_in_window=${DASHBOARD_VALUES[10]}"
echo "recommendation_local_real_non_example_seed_users_in_window=${DASHBOARD_VALUES[11]}"
echo "recommendation_real_user_users_in_window=${DASHBOARD_VALUES[12]}"
echo "recommendation_real_non_example_users_in_window=${DASHBOARD_VALUES[13]}"
echo "recommendation_real_user_traffic_gate_in_window=${DASHBOARD_VALUES[14]}"
echo "recommendation_review_gate=${DASHBOARD_VALUES[15]}"
echo "recommendation_latest_batch_rows=${DASHBOARD_VALUES[16]}"
echo "recommendation_latest_batch_users=${DASHBOARD_VALUES[17]}"
echo "recommendation_top1_leader_service_id=${DASHBOARD_VALUES[18]}"
echo "recommendation_top1_leader_share_pct=${DASHBOARD_VALUES[19]}"
echo "recommendation_top1_leader_example_users=${DASHBOARD_VALUES[20]}"
echo "recommendation_top1_leader_bounded_local_users=${DASHBOARD_VALUES[21]}"
echo "recommendation_top1_leader_local_real_non_example_seed_users=${DASHBOARD_VALUES[22]}"
echo "recommendation_top1_leader_real_user_users=${DASHBOARD_VALUES[23]}"
echo "recommendation_top1_leader_signal_summary=${DASHBOARD_VALUES[24]}"
echo "recommendation_concentration_readiness=${DASHBOARD_VALUES[25]}"
echo "recommendation_latest_batch_real_user_cohort_gate=${DASHBOARD_VALUES[26]}"
echo "recommendation_latest_batch_signal_quality=${DASHBOARD_VALUES[27]}"
echo "notification_sent_in_window=${DASHBOARD_VALUES[28]}"
echo "search_zero_result_searches_in_window=${DASHBOARD_VALUES[29]}"
echo "user_pii_sync_pending_count=${DASHBOARD_VALUES[30]}"
echo "user_pii_sync_failed_count=${DASHBOARD_VALUES[31]}"
echo "user_pii_sync_synced_count=${DASHBOARD_VALUES[32]}"
echo "summary_window_days=${DASHBOARD_VALUES[33]}"
echo "collect_trend_windows=${DASHBOARD_VALUES[34]}"
echo "recommendation_recent_window_review_reading=${DASHBOARD_VALUES[35]}"
echo "recommendation_historical_example_dominance_detected=${DASHBOARD_VALUES[36]}"
echo "recommendation_review_gate_policy_candidate_status=${DASHBOARD_VALUES[37]}"
echo "recommendation_review_gate_policy_candidate_reason=${DASHBOARD_VALUES[38]}"
echo "recommendation_review_gate_policy_promotion_status=${DASHBOARD_VALUES[39]}"
echo "recommendation_review_gate_policy_promotion_reason=${DASHBOARD_VALUES[40]}"
echo "recommendation_review_gate_policy_promotion_action_status=${DASHBOARD_VALUES[41]}"
echo "recommendation_review_gate_policy_promotion_action_reason=${DASHBOARD_VALUES[42]}"
echo "recommendation_review_gate_policy_promotion_readiness_status=${DASHBOARD_VALUES[43]}"
echo "recommendation_review_gate_policy_promotion_readiness_reason=${DASHBOARD_VALUES[44]}"
echo "recommendation_review_gate_policy_promotion_execution_status=${DASHBOARD_VALUES[45]}"
echo "recommendation_review_gate_policy_promotion_execution_reason=${DASHBOARD_VALUES[46]}"
echo "recommendation_review_gate_policy_promotion_approval_criteria_status=${DASHBOARD_VALUES[47]}"
echo "recommendation_review_gate_policy_promotion_approval_criteria_reason=${DASHBOARD_VALUES[48]}"
echo "recommendation_review_gate_policy_promotion_approval_status=${DASHBOARD_VALUES[49]}"
echo "recommendation_review_gate_policy_promotion_approval_reason=${DASHBOARD_VALUES[50]}"
echo "recommendation_review_gate_policy_promotion_approval_decision_status=${DASHBOARD_VALUES[51]}"
echo "recommendation_review_gate_policy_promotion_approval_decision_reason=${DASHBOARD_VALUES[52]}"
echo "recommendation_review_gate_policy_promotion_approval_record_status=${DASHBOARD_VALUES[53]}"
echo "recommendation_review_gate_policy_promotion_approval_record_reason=${DASHBOARD_VALUES[54]}"
echo "recommendation_review_gate_policy_promotion_review_run_status=${DASHBOARD_VALUES[55]}"
echo "recommendation_review_gate_policy_promotion_review_run_reason=${DASHBOARD_VALUES[56]}"
echo "recommendation_review_gate_policy_promotion_review_run_criteria_status=${DASHBOARD_VALUES[57]}"
echo "recommendation_review_gate_policy_promotion_review_run_criteria_reason=${DASHBOARD_VALUES[58]}"
echo "recommendation_review_gate_policy_promotion_review_run_decision_status=${DASHBOARD_VALUES[59]}"
echo "recommendation_review_gate_policy_promotion_review_run_decision_reason=${DASHBOARD_VALUES[60]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_criteria_status=${DASHBOARD_VALUES[61]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_criteria_reason=${DASHBOARD_VALUES[62]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_decision_status=${DASHBOARD_VALUES[63]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_decision_reason=${DASHBOARD_VALUES[64]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_status=${DASHBOARD_VALUES[65]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_reason=${DASHBOARD_VALUES[66]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_criteria_status=${DASHBOARD_VALUES[67]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_criteria_reason=${DASHBOARD_VALUES[68]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_status=${DASHBOARD_VALUES[69]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_reason=${DASHBOARD_VALUES[70]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_transition_status=${DASHBOARD_VALUES[71]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_transition_reason=${DASHBOARD_VALUES[72]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_write_status=${DASHBOARD_VALUES[73]}"
echo "recommendation_review_gate_policy_promotion_review_run_approval_record_write_reason=${DASHBOARD_VALUES[74]}"
echo "recommendation_review_gate_primary_reference_mode=${DASHBOARD_VALUES[75]}"
echo "recommendation_review_gate_example_target_top1_users=${DASHBOARD_VALUES[76]}"
echo "recommendation_review_gate_example_target_top1_last_24h=${DASHBOARD_VALUES[77]}"
echo "recommendation_review_gate_real_user_latest_users=${DASHBOARD_VALUES[78]}"
echo "recommendation_review_gate_real_user_target_top1_users=${DASHBOARD_VALUES[79]}"
echo "recommendation_recent_window_hours=${DASHBOARD_VALUES[80]}"
echo "recommendation_recent_window_target_service_id=${DASHBOARD_VALUES[81]}"
echo "recommendation_recent_window_latest_batch_users=${DASHBOARD_VALUES[82]}"
echo "recommendation_recent_window_real_user_users=${DASHBOARD_VALUES[83]}"
echo "recommendation_recent_window_top1_leader_service_id=${DASHBOARD_VALUES[84]}"
echo "recommendation_recent_window_top1_leader_real_user_users=${DASHBOARD_VALUES[85]}"
echo "recommendation_recent_window_target_top1_users=${DASHBOARD_VALUES[86]}"
echo "recommendation_recent_window_target_top1_real_user_users=${DASHBOARD_VALUES[87]}"
echo "requested_summary_window_days=${SUMMARY_WINDOW_DAYS}"
echo "requested_trend_window_days=${TREND_WINDOW_DAYS_CSV}"
if [[ -n "${CONTAINER_ADMIN_ALLOWLIST}" ]]; then
  echo "container_security_admin_emails=${CONTAINER_ADMIN_ALLOWLIST}"
fi
