#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APPROVAL_RECORD_SMOKE_SCRIPT="${APPROVAL_RECORD_SMOKE_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-admin-recommendation-review-gate-promotion-approval-record-smoke.sh}"
RECENT_WINDOW_AUDIT_SCRIPT="${RECENT_WINDOW_AUDIT_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-review-gate-recent-window-audit.sh}"
STALENESS_AUDIT_SCRIPT="${STALENESS_AUDIT_SCRIPT:-${ROOT_DIR}/deploy/smoke/run-local-recommendation-review-gate-staleness-audit.sh}"

ARTIFACT_ROOT="${ROOT_DIR}/tmp/recommendation-bounded-promotion-review"
RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
SUMMARY_OUT="${ARTIFACT_DIR}/bounded-promotion-review-summary.txt"
NOTE_OUT="${ARTIFACT_DIR}/bounded-promotion-review-note.md"
JSON_OUT="${ARTIFACT_DIR}/bounded-promotion-review.json"
APPROVAL_SMOKE_OUT="${ARTIFACT_DIR}/approval-record-smoke.out"
RECENT_WINDOW_OUT="${ARTIFACT_DIR}/recent-window-audit.out"
STALENESS_OUT="${ARTIFACT_DIR}/staleness-audit.out"

LATEST_ARTIFACT_LINK="${ARTIFACT_ROOT}/latest"
LATEST_SUMMARY_LINK="${ARTIFACT_ROOT}/latest-bounded-promotion-review-summary.txt"
LATEST_NOTE_LINK="${ARTIFACT_ROOT}/latest-bounded-promotion-review-note.md"
LATEST_JSON_LINK="${ARTIFACT_ROOT}/latest-bounded-promotion-review.json"

RECENT_WINDOW_HOURS="${RECENT_WINDOW_HOURS:-24}"
TARGET_SERVICE_ID="${TARGET_SERVICE_ID:-2622}"

mkdir -p "${ARTIFACT_DIR}"
smoke_require_command python3

smoke_print_step "approval record write/clear preflight"
ADMIN_EMAIL="${ADMIN_EMAIL:-${ADMIN_EMAIL-}}" \
ADMIN_PASSWORD="${ADMIN_PASSWORD:-${ADMIN_PASSWORD-}}" \
APP_BASE_URL="${APP_BASE_URL:-http://127.0.0.1:8082}" \
  bash "${APPROVAL_RECORD_SMOKE_SCRIPT}" | tee "${APPROVAL_SMOKE_OUT}"

smoke_print_step "recent-window bounded review signal"
RECENT_WINDOW_HOURS="${RECENT_WINDOW_HOURS}" TARGET_SERVICE_ID="${TARGET_SERVICE_ID}" \
  bash "${RECENT_WINDOW_AUDIT_SCRIPT}" | tee "${RECENT_WINDOW_OUT}"

smoke_print_step "historical staleness cross-check"
TARGET_SERVICE_ID="${TARGET_SERVICE_ID}" \
  bash "${STALENESS_AUDIT_SCRIPT}" | tee "${STALENESS_OUT}"

python3 - "${APPROVAL_SMOKE_OUT}" "${RECENT_WINDOW_OUT}" "${STALENESS_OUT}" "${SUMMARY_OUT}" "${NOTE_OUT}" "${JSON_OUT}" "${RECENT_WINDOW_HOURS}" "${TARGET_SERVICE_ID}" "$(smoke_now_iso_utc)" "$(smoke_now_iso_kst)" <<'PY'
import json
import sys
from pathlib import Path

approval_out = Path(sys.argv[1])
recent_out = Path(sys.argv[2])
staleness_out = Path(sys.argv[3])
summary_out = Path(sys.argv[4])
note_out = Path(sys.argv[5])
json_out = Path(sys.argv[6])
recent_window_hours = sys.argv[7]
target_service_id = sys.argv[8]
generated_at_utc = sys.argv[9]
generated_at_kst = sys.argv[10]

def parse_kv(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key] = value
    return result

approval = parse_kv(approval_out)
recent = parse_kv(recent_out)
staleness = parse_kv(staleness_out)

recent_blocker_class = recent.get("blocker_class", "")
recent_next_step = recent.get("operator_next_step", "")
staleness_blocker_class = staleness.get("blocker_class", "")
staleness_next_step = staleness.get("operator_next_step", "")
recent_top1_leader = recent.get("recent_top1_leader_title", "")
recent_top1_share = recent.get("recent_top1_leader_share_pct", "")
recent_real_user_users = recent.get("recent_real_user_users", "0")
recent_target_top1_real_user_users = recent.get("recent_target_top1_real_user_users", "0")
example_target_last_24h = staleness.get("example_smoke_target_top1_last_24h", "0")

approval_smoke_text = approval_out.read_text(encoding="utf-8")
approval_smoke_passed = "recommendation review gate promotion approval record smoke passed" in approval_smoke_text

if (
    approval_smoke_passed
    and recent_blocker_class == "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE"
    and staleness_blocker_class == "HISTORICAL_EXAMPLE_LATEST_BATCH_DOMINANCE"
):
    result_status = "PASS_RECENT_WINDOW_POLICY_CANDIDATE"
    result_reason = "RECENT_WINDOW_CLEAR_WITH_STALE_HISTORICAL_EXAMPLE_BASELINE"
    recommended_action = "PREPARE_EXPLICIT_POLICY_REVIEW_DECISION"
elif not approval_smoke_passed:
    result_status = "FAIL_APPROVAL_RECORD_PREFLIGHT"
    result_reason = "PROMOTION_APPROVAL_RECORD_WRITE_CLEAR_PATH_FAILED"
    recommended_action = "FIX_APPROVAL_RECORD_WRITE_PATH"
else:
    result_status = "INCONCLUSIVE_BOUNDED_PROMOTION_REVIEW"
    result_reason = "RECENT_WINDOW_OR_STALENESS_SIGNAL_DID_NOT_MATCH_EXPECTED_PROMOTION_PATTERN"
    recommended_action = "KEEP_PRIMARY_BASELINE_AND_RECHECK_SIGNALS"

summary_lines = [
    f"generated_at_utc={generated_at_utc}",
    f"generated_at_kst={generated_at_kst}",
    f"recent_window_hours={recent_window_hours}",
    f"target_service_id={target_service_id}",
    f"approval_record_smoke_passed={'true' if approval_smoke_passed else 'false'}",
    f"recent_window_blocker_class={recent_blocker_class}",
    f"recent_window_operator_next_step={recent_next_step}",
    f"recent_window_top1_leader_title={recent_top1_leader}",
    f"recent_window_top1_leader_share_pct={recent_top1_share}",
    f"recent_window_real_user_users={recent_real_user_users}",
    f"recent_window_target_top1_real_user_users={recent_target_top1_real_user_users}",
    f"staleness_blocker_class={staleness_blocker_class}",
    f"staleness_operator_next_step={staleness_next_step}",
    f"example_smoke_target_top1_last_24h={example_target_last_24h}",
    f"bounded_promotion_review_result_status={result_status}",
    f"bounded_promotion_review_result_reason={result_reason}",
    f"bounded_promotion_review_recommended_action={recommended_action}",
    f"artifact_dir={summary_out.parent}",
    f"approval_record_smoke_output={approval_out}",
    f"recent_window_audit_output={recent_out}",
    f"staleness_audit_output={staleness_out}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

note_lines = [
    "# bounded promotion review",
    "",
    f"- bounded_promotion_review_result_status: `{result_status}`",
    f"- bounded_promotion_review_result_reason: `{result_reason}`",
    f"- bounded_promotion_review_recommended_action: `{recommended_action}`",
    f"- recent_window_blocker_class: `{recent_blocker_class}`",
    f"- staleness_blocker_class: `{staleness_blocker_class}`",
    f"- recent_window_top1_leader_title: `{recent_top1_leader}`",
    f"- recent_window_top1_leader_share_pct: `{recent_top1_share}`",
    f"- recent_window_real_user_users: `{recent_real_user_users}`",
    f"- recent_window_target_top1_real_user_users: `{recent_target_top1_real_user_users}`",
    f"- example_smoke_target_top1_last_24h: `{example_target_last_24h}`",
    "",
    "이 wrapper는 explicit approval record write/clear preflight를 먼저 확인하고,",
    "그 다음 recent-window clear 여부와 historical example staleness를 같이 읽어 bounded review go/no-go만 압축해서 남깁니다.",
]
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")

json_out.write_text(
    json.dumps(
        {
            "generated_at_utc": generated_at_utc,
            "generated_at_kst": generated_at_kst,
            "recent_window_hours": int(recent_window_hours),
            "target_service_id": int(target_service_id),
            "approval_record_smoke_passed": approval_smoke_passed,
            "recent_window_blocker_class": recent_blocker_class,
            "recent_window_operator_next_step": recent_next_step,
            "recent_window_top1_leader_title": recent_top1_leader,
            "recent_window_top1_leader_share_pct": recent_top1_share,
            "recent_window_real_user_users": recent_real_user_users,
            "recent_window_target_top1_real_user_users": recent_target_top1_real_user_users,
            "staleness_blocker_class": staleness_blocker_class,
            "staleness_operator_next_step": staleness_next_step,
            "example_smoke_target_top1_last_24h": example_target_last_24h,
            "bounded_promotion_review_result_status": result_status,
            "bounded_promotion_review_result_reason": result_reason,
            "bounded_promotion_review_recommended_action": recommended_action,
            "approval_record_smoke_output": str(approval_out),
            "recent_window_audit_output": str(recent_out),
            "staleness_audit_output": str(staleness_out),
        },
        ensure_ascii=False,
        indent=2,
    )
    + "\n",
    encoding="utf-8",
)
PY

smoke_update_links \
  "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}" \
  "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}" \
  "${NOTE_OUT}" "${LATEST_NOTE_LINK}" \
  "${JSON_OUT}" "${LATEST_JSON_LINK}"

echo
cat "${SUMMARY_OUT}"
echo "summary_output=${SUMMARY_OUT}"
