#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}"
APP_HEALTH_URL="${APP_HEALTH_URL:-${APP_BASE_URL}/actuator/health}"
HEALTH_RETRY_COUNT="${HEALTH_RETRY_COUNT:-20}"
HEALTH_RETRY_DELAY_SECONDS="${HEALTH_RETRY_DELAY_SECONDS:-1}"
RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/policy-search-scenario-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-true}"

SUMMARY_OUT="${ARTIFACT_DIR}/policy-search-scenario-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/policy-search-scenario-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/policy-search-scenario-note.md"
HEALTH_RESPONSE="${ARTIFACT_DIR}/health.json"
LATEST_ARTIFACT_LINK="${ARTIFACT_ROOT}/latest"
LATEST_SUMMARY_LINK="${ARTIFACT_ROOT}/latest-policy-search-scenario-summary.txt"
LATEST_JSON_LINK="${ARTIFACT_ROOT}/latest-policy-search-scenario-summary.json"
LATEST_NOTE_LINK="${ARTIFACT_ROOT}/latest-policy-search-scenario-note.md"

mkdir -p "${ARTIFACT_DIR}"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" != "true" ]]; then
    rm -rf "${ARTIFACT_DIR}"
  fi
}
trap cleanup EXIT

smoke_require_command curl
smoke_require_command python3

smoke_print_step "health check"
HEALTH_STATUS="$(smoke_wait_for_health "${HEALTH_RETRY_COUNT}" "${HEALTH_RETRY_DELAY_SECONDS}" "${APP_HEALTH_URL}" "${HEALTH_RESPONSE}" "${ARTIFACT_DIR}/health.stderr")"
smoke_assert_status 200 "${HEALTH_STATUS}" "health check" "${HEALTH_RESPONSE}"

python3 - "${APP_BASE_URL}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" <<'PY'
import json
import sys
import urllib.request
from pathlib import Path

base_url = sys.argv[1]
summary_path = Path(sys.argv[2])
json_path = Path(sys.argv[3])
note_path = Path(sys.argv[4])

keyword_scenarios = [
    {"keyword": "월세", "expected_sido": "서울특별시", "expect_local_signal": True},
    {"keyword": "청약", "expected_sido": "서울특별시", "expect_local_signal": False},
    {"keyword": "면접비", "expected_sido": None, "expect_local_signal": True},
    {"keyword": "자격증", "expected_sido": None, "expect_local_signal": True},
]
region_filter_scenarios = [
    {"keyword": "월세", "sido": "서울특별시"},
    {"keyword": "청약", "sido": "서울특별시"},
]


def get_json(url: str):
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def post_json(url: str, payload: dict):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def search(keyword: str, size: int = 3, sido: str | None = None):
    payload = {
        "keyword": keyword,
        "page": 0,
        "size": size,
    }
    if sido:
        payload["sido"] = sido
    url = f"{base_url}/api/policies/search"
    return post_json(url, payload)["data"]["content"]


def detail(policy_id: int):
    url = f"{base_url}/api/policies/{policy_id}"
    return get_json(url)["data"]


keyword_results = []
missing_provider_count = 0
missing_status_count = 0
missing_region_for_local_count = 0

for scenario in keyword_scenarios:
    hits = search(scenario["keyword"])
    top = hits[0] if hits else None
    detail_payload = detail(top["id"]) if top else None
    if top and not top.get("providerName"):
        missing_provider_count += 1
    if top and not top.get("statusLabel"):
        missing_status_count += 1
    if top and scenario["expect_local_signal"] and not top.get("regionLabel"):
        missing_region_for_local_count += 1
    keyword_results.append({
        "keyword": scenario["keyword"],
        "hitCount": len(hits),
        "top": None if not top else {
            "id": top.get("id"),
            "title": top.get("title"),
            "providerName": top.get("providerName"),
            "regionLabel": top.get("regionLabel"),
            "applicationPeriod": top.get("applicationPeriod"),
            "statusLabel": top.get("statusLabel"),
        },
        "detail": None if not detail_payload else {
            "id": detail_payload.get("id"),
            "providerName": detail_payload.get("providerName"),
            "regionLabel": detail_payload.get("regionLabel"),
            "applicationPeriod": detail_payload.get("applicationPeriod"),
            "statusLabel": detail_payload.get("statusLabel"),
        },
    })

region_filter_results = []
region_filter_mismatch_count = 0
for scenario in region_filter_scenarios:
    hits = search(scenario["keyword"], sido=scenario["sido"])
    mismatches = []
    for row in hits:
        region_label = row.get("regionLabel")
        if region_label and not region_label.startswith(scenario["sido"]):
            mismatches.append({
                "id": row.get("id"),
                "title": row.get("title"),
                "regionLabel": region_label,
            })
    region_filter_mismatch_count += len(mismatches)
    region_filter_results.append({
        "keyword": scenario["keyword"],
        "sido": scenario["sido"],
        "hitCount": len(hits),
        "mismatchCount": len(mismatches),
        "mismatches": mismatches,
    })

decision = "BASELINE_HEALTHY"
if missing_provider_count or missing_status_count or missing_region_for_local_count or region_filter_mismatch_count:
    decision = "FOLLOWUP_REQUIRED"

summary_lines = [
    f"keyword_scenario_count={len(keyword_results)}",
    f"region_filter_scenario_count={len(region_filter_results)}",
    f"missing_provider_count={missing_provider_count}",
    f"missing_status_count={missing_status_count}",
    f"missing_region_for_local_count={missing_region_for_local_count}",
    f"region_filter_mismatch_count={region_filter_mismatch_count}",
    f"decision_class={decision}",
]

summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")
json_path.write_text(json.dumps({
    "keywordScenarios": keyword_results,
    "regionFilterScenarios": region_filter_results,
    "missingProviderCount": missing_provider_count,
    "missingStatusCount": missing_status_count,
    "missingRegionForLocalCount": missing_region_for_local_count,
    "regionFilterMismatchCount": region_filter_mismatch_count,
    "decisionClass": decision,
}, ensure_ascii=False, indent=2), encoding="utf-8")

note_lines = [
    "# policy search scenario audit",
    "",
    f"- decision_class: `{decision}`",
    f"- missing_provider_count: `{missing_provider_count}`",
    f"- missing_status_count: `{missing_status_count}`",
    f"- missing_region_for_local_count: `{missing_region_for_local_count}`",
    f"- region_filter_mismatch_count: `{region_filter_mismatch_count}`",
    "",
    "## keyword scenarios",
]
for row in keyword_results:
    note_lines.append(
        f"- `{row['keyword']}` -> `{row['top']['title'] if row['top'] else '(no result)'}` / "
        f"provider=`{(row['top'] or {}).get('providerName')}` / "
        f"region=`{(row['top'] or {}).get('regionLabel')}` / "
        f"period=`{(row['top'] or {}).get('applicationPeriod')}` / "
        f"status=`{(row['top'] or {}).get('statusLabel')}`"
    )
note_lines.extend(["", "## region filter scenarios"])
for row in region_filter_results:
    note_lines.append(
        f"- `{row['keyword']}` + `{row['sido']}` -> hits=`{row['hitCount']}` mismatches=`{row['mismatchCount']}`"
    )
note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

print(summary_path.read_text(encoding="utf-8"), end="")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
