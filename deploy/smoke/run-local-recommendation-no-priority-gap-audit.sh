#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

smoke_require_command bash
smoke_require_command python3

RUN_TS_UTC="${RUN_TS_UTC:-$(smoke_now_ts_utc)}"
ARTIFACT_ROOT="${ARTIFACT_ROOT:-${ROOT_DIR}/tmp/recommendation-no-priority-gap-audit}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ARTIFACT_ROOT}/${RUN_TS_UTC}}"
mkdir -p "${ARTIFACT_DIR}"

ALL_OUTPUT="${ARTIFACT_DIR}/all-concentration.out"
REAL_USER_OUTPUT="${ARTIFACT_DIR}/real-user-concentration.out"
SUMMARY_OUT="${ARTIFACT_DIR}/no-priority-gap-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/no-priority-gap-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/no-priority-gap-note.md"

smoke_print_step "all latest-batch concentration"
USER_COHORT=all bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${ALL_OUTPUT}"

smoke_print_step "real-user latest-batch concentration"
USER_COHORT=real_user bash "${ROOT_DIR}/deploy/smoke/run-local-recommendation-concentration-audit.sh" | tee "${REAL_USER_OUTPUT}"

python3 - "${ALL_OUTPUT}" "${REAL_USER_OUTPUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" <<'PY'
import json
import sys
from pathlib import Path

all_path = Path(sys.argv[1])
real_path = Path(sys.argv[2])
summary_path = Path(sys.argv[3])
json_path = Path(sys.argv[4])
note_path = Path(sys.argv[5])


def parse_output(path: Path):
    metrics = {}
    sections = {}
    current = None
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            current = line[1:-1]
            sections[current] = []
            continue
        if current is not None:
            sections[current].append(line)
            continue
        if "=" in line:
            key, value = line.split("=", 1)
            metrics[key.strip()] = value.strip()
    return metrics, sections


def first_section_row(sections, section_name, prefix):
    for row in sections.get(section_name, []):
        if row.startswith(prefix):
            return row
    return ""


def parse_pipe_row(row):
    if not row:
        return {}
    parts = row.split("|")
    return {
        "raw": row,
        "parts": parts,
    }


def parse_priority_profile_rows(sections):
    rows = []
    for row in sections.get("priority_profile_top1", []):
        parts = row.split("|")
        if len(parts) < 5:
            continue
        rows.append({
            "profile": parts[0],
            "title": parts[1],
            "source": parts[2],
            "category": parts[3],
            "users": parts[4],
        })
    return rows


all_metrics, all_sections = parse_output(all_path)
real_metrics, real_sections = parse_output(real_path)

all_no_priority_row = parse_pipe_row(first_section_row(all_sections, "top1_by_priority_state", "NO_PRIORITY|"))
all_has_priority_row = parse_pipe_row(first_section_row(all_sections, "top1_by_priority_state", "HAS_PRIORITY|"))
real_no_priority_row = parse_pipe_row(first_section_row(real_sections, "top1_by_priority_state", "NO_PRIORITY|"))
real_has_priority_row = parse_pipe_row(first_section_row(real_sections, "top1_by_priority_state", "HAS_PRIORITY|"))

all_priority_profiles = parse_priority_profile_rows(all_sections)
real_priority_profiles = parse_priority_profile_rows(real_sections)

all_priority_gap_detected = (
    bool(all_no_priority_row)
    and bool(all_has_priority_row)
    and len(all_no_priority_row["parts"]) > 1
    and len(all_has_priority_row["parts"]) > 1
    and all_no_priority_row["parts"][1] != all_has_priority_row["parts"][1]
)
real_priority_gap_detected = (
    bool(real_no_priority_row)
    and bool(real_has_priority_row)
    and len(real_no_priority_row["parts"]) > 1
    and len(real_has_priority_row["parts"]) > 1
    and real_no_priority_row["parts"][1] != real_has_priority_row["parts"][1]
)

datetime_mod = __import__("datetime")
summary_lines = [
    f"generated_at_utc={datetime_mod.datetime.now(datetime_mod.UTC).replace(microsecond=0).isoformat().replace('+00:00', 'Z')}",
    f"generated_at_kst={datetime_mod.datetime.now(datetime_mod.timezone(datetime_mod.timedelta(hours=9))).replace(microsecond=0).isoformat()}",
    f"all_latest_batch_users={all_metrics.get('latest_batch_users', '')}",
    f"all_priority_users={all_metrics.get('latest_batch_priority_users', '')}",
    f"all_no_priority_users={all_metrics.get('latest_batch_no_priority_users', '')}",
    f"all_top1_leader_title={all_metrics.get('top1_leader_title', '')}",
    f"all_top1_leader_share_pct={all_metrics.get('top1_leader_share_pct', '')}",
    f"all_no_priority_top1={all_no_priority_row.get('raw', '')}",
    f"all_has_priority_top1={all_has_priority_row.get('raw', '')}",
    f"all_priority_gap_detected={'true' if all_priority_gap_detected else 'false'}",
    f"all_concentration_readiness={next(iter(all_sections.get('concentration_readiness', [])), '')}",
    f"real_user_latest_batch_users={real_metrics.get('latest_batch_users', '')}",
    f"real_user_top1_leader_title={real_metrics.get('top1_leader_title', '')}",
    f"real_user_top1_leader_share_pct={real_metrics.get('top1_leader_share_pct', '')}",
    f"real_user_no_priority_top1={real_no_priority_row.get('raw', '')}",
    f"real_user_has_priority_top1={real_has_priority_row.get('raw', '')}",
    f"real_user_priority_gap_detected={'true' if real_priority_gap_detected else 'false'}",
    f"real_user_concentration_readiness={next(iter(real_sections.get('concentration_readiness', [])), '')}",
    f"recommended_next_action={'STRENGTHEN_PRIORITY_CAPTURE' if all_metrics.get('latest_batch_no_priority_users', '0').isdigit() and int(all_metrics.get('latest_batch_no_priority_users', '0')) > int(all_metrics.get('latest_batch_priority_users', '0')) else 'OBSERVE_PRIORITY_MIX'}",
    f"artifact_dir={summary_path.parent}",
]
summary_path.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "all": {
        "metrics": all_metrics,
        "noPriorityTop1": all_no_priority_row.get("raw", ""),
        "hasPriorityTop1": all_has_priority_row.get("raw", ""),
        "priorityGapDetected": all_priority_gap_detected,
        "priorityProfileTop1": all_priority_profiles[:8],
        "concentrationReadiness": next(iter(all_sections.get("concentration_readiness", [])), ""),
    },
    "realUser": {
        "metrics": real_metrics,
        "noPriorityTop1": real_no_priority_row.get("raw", ""),
        "hasPriorityTop1": real_has_priority_row.get("raw", ""),
        "priorityGapDetected": real_priority_gap_detected,
        "priorityProfileTop1": real_priority_profiles[:8],
        "concentrationReadiness": next(iter(real_sections.get("concentration_readiness", [])), ""),
    },
    "recommendedNextAction": "STRENGTHEN_PRIORITY_CAPTURE"
    if all_metrics.get("latest_batch_no_priority_users", "0").isdigit()
    and int(all_metrics.get("latest_batch_no_priority_users", "0")) > int(all_metrics.get("latest_batch_priority_users", "0"))
    else "OBSERVE_PRIORITY_MIX",
}
json_path.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# Recommendation No-Priority Gap Audit",
    "",
    "## All latest batch",
    f"- users: `{all_metrics.get('latest_batch_users', '')}`",
    f"- priority users: `{all_metrics.get('latest_batch_priority_users', '')}`",
    f"- no-priority users: `{all_metrics.get('latest_batch_no_priority_users', '')}`",
    f"- top1 leader: `{all_metrics.get('top1_leader_title', '')}` (`{all_metrics.get('top1_leader_share_pct', '')}%`)",
    f"- no-priority top1: `{all_no_priority_row.get('raw', '')}`",
    f"- has-priority top1: `{all_has_priority_row.get('raw', '')}`",
    f"- priority gap detected: `{all_priority_gap_detected}`",
    "",
    "## Real-user latest batch",
    f"- users: `{real_metrics.get('latest_batch_users', '')}`",
    f"- top1 leader: `{real_metrics.get('top1_leader_title', '')}` (`{real_metrics.get('top1_leader_share_pct', '')}%`)",
    f"- no-priority top1: `{real_no_priority_row.get('raw', '')}`",
    f"- has-priority top1: `{real_has_priority_row.get('raw', '')}`",
    f"- priority gap detected: `{real_priority_gap_detected}`",
    "",
    "## Priority profiles",
    "all latest batch 상위 priority profile top1:",
]
for row in all_priority_profiles[:5]:
    note_lines.append(f"- `{row['profile']}` -> `{row['title']}` / `{row['category']}` / `{row['users']}` users")
note_lines.extend([
    "",
    f"recommended_next_action=`{json_payload['recommendedNextAction']}`",
])
note_path.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_sanitize_artifacts "${ARTIFACT_DIR}"
smoke_update_links \
  "${ARTIFACT_DIR}" "${ARTIFACT_ROOT}/latest" \
  "${SUMMARY_OUT}" "${ARTIFACT_ROOT}/latest-no-priority-gap-summary.txt" \
  "${JSON_OUT}" "${ARTIFACT_ROOT}/latest-no-priority-gap-summary.json" \
  "${NOTE_OUT}" "${ARTIFACT_ROOT}/latest-no-priority-gap-note.md"

cat "${SUMMARY_OUT}"
