#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"
OBSERVATION_ROOT="${OBSERVATION_ROOT:-${ROOT_DIR}/tmp/youth-regionless-audit}"
KEEP_ARTIFACTS="${KEEP_ARTIFACTS:-false}"
MAX_LOCAL_SUSPICIOUS_COUNT="${MAX_LOCAL_SUSPICIOUS_COUNT:-20}"

RUN_TS_UTC="$(smoke_now_ts_utc)"
ARTIFACT_DIR="${ARTIFACT_DIR:-${OBSERVATION_ROOT}/${RUN_TS_UTC}}"
RAW_ROWS_OUT="${ARTIFACT_DIR}/youth-regionless-raw.tsv"
SUMMARY_OUT="${ARTIFACT_DIR}/youth-regionless-summary.txt"
JSON_OUT="${ARTIFACT_DIR}/youth-regionless-summary.json"
NOTE_OUT="${ARTIFACT_DIR}/youth-regionless-note.md"

LATEST_ARTIFACT_LINK="${OBSERVATION_ROOT}/latest"
LATEST_SUMMARY_LINK="${OBSERVATION_ROOT}/latest-youth-regionless-summary.txt"
LATEST_JSON_LINK="${OBSERVATION_ROOT}/latest-youth-regionless-summary.json"
LATEST_NOTE_LINK="${OBSERVATION_ROOT}/latest-youth-regionless-note.md"

cleanup() {
  if [[ "${KEEP_ARTIFACTS}" == "true" ]]; then
    return 0
  fi
  rm -rf "${ARTIFACT_DIR}"
}
trap cleanup EXIT

KEEP_ARTIFACTS="$(smoke_normalize_bool "${KEEP_ARTIFACTS}")"

smoke_require_command curl
smoke_require_command python3
mkdir -p "${ARTIFACT_DIR}"

echo "[health]"
curl -fsS "${APP_HEALTH_URL}" >/dev/null
echo "OK app_health=${APP_HEALTH_URL}"

smoke_db_query "
with region_counts as (
  select service_id, count(*) as region_rows
  from service_regions
  group by service_id
)
select
  ws.source_id,
  replace(coalesce(ws.title, ''), E'\t', ' ') as title,
  coalesce(rc.region_rows, 0) as region_rows,
  replace(coalesce(rap.payload_json::jsonb->>'zipCd', ''), E'\t', ' ') as zip_cd,
  replace(coalesce(rap.payload_json::jsonb->>'sprvsnInstCdNm', ''), E'\t', ' ') as host_org,
  replace(coalesce(ws.category_main, ''), E'\t', ' ') as category_main
from welfare_services ws
join raw_api_payloads rap
  on rap.source_type = ws.source_type
 and rap.source_id = ws.source_id
 and rap.api_category = 'LIST'
left join region_counts rc on rc.service_id = ws.id
where ws.source_type = 'YOUTH'
order by ws.source_id;
" > "${RAW_ROWS_OUT}"

python3 - "${RAW_ROWS_OUT}" "${SUMMARY_OUT}" "${JSON_OUT}" "${NOTE_OUT}" "${ARTIFACT_DIR}" "${MAX_LOCAL_SUSPICIOUS_COUNT}" <<'PY'
import csv
import json
import sys
from collections import Counter
from pathlib import Path

raw_rows_path = Path(sys.argv[1])
summary_out = Path(sys.argv[2])
json_out = Path(sys.argv[3])
note_out = Path(sys.argv[4])
artifact_dir = sys.argv[5]
max_local_suspicious_count = int(sys.argv[6])

CENTRAL_MARKERS = [
    "고용노동부", "중소벤처기업부", "교육부", "외교부", "국토교통부", "보건복지부",
    "문화체육관광부", "행정안전부", "해양수산부", "산업통상자원부", "농림축산식품부",
    "과학기술정보통신부", "기획재정부", "환경부", "국세청", "병무청", "경찰청",
    "질병관리청", "재외동포청", "식품의약품안전처", "금융위원회", "개인정보보호위원회",
    "국가유산청", "국가보훈부", "조달청", "통계청", "법무부", "여성가족부",
]
LOCAL_MARKERS = [
    "특별시", "광역시", "특별자치시", "특별자치도", "도청", "시청", "군청", "구청", "교육청",
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
]
NATIONWIDE_SIDO_THRESHOLD = 15


def split_codes(value: str):
    return [part.strip() for part in value.split(",") if part.strip()]


def distinct_sido_count(codes):
    return len({code[:2] for code in codes if len(code) >= 2})


def classify_host_scope(host_org: str) -> str:
    if not host_org:
        return "institutional_unknown"
    if any(marker in host_org for marker in CENTRAL_MARKERS):
        return "centralish"
    if any(marker in host_org for marker in LOCAL_MARKERS):
        return "localish"
    return "institutional_unknown"


rows = []
with raw_rows_path.open(encoding="utf-8") as f:
    reader = csv.reader(f, delimiter="\t")
    for source_id, title, region_rows_raw, zip_cd, host_org, category_main in reader:
        region_rows = int(region_rows_raw)
        codes = split_codes(zip_cd)
        distinct_sido = distinct_sido_count(codes)
        nationwide_zip = distinct_sido >= NATIONWIDE_SIDO_THRESHOLD
        rows.append({
            "source_id": source_id,
            "title": title,
            "region_rows": region_rows,
            "zip_cd": zip_cd,
            "zip_code_count": len(codes),
            "zip_length": len(zip_cd),
            "distinct_sido_count": distinct_sido,
            "nationwide_zip": nationwide_zip,
            "host_org": host_org,
            "host_scope_guess": classify_host_scope(host_org),
            "category_main": category_main,
        })

total_services = len(rows)
region_services = sum(1 for row in rows if row["region_rows"] > 0)
regionless_rows = [row for row in rows if row["region_rows"] == 0]
regionless_services = len(regionless_rows)
region_rows_total = sum(row["region_rows"] for row in rows)
region_service_pct = round((region_services / total_services) * 100.0, 2) if total_services else 0.0

regionless_empty_zip_count = sum(1 for row in regionless_rows if not row["zip_cd"])
regionless_csv_zip_count = sum(1 for row in regionless_rows if row["zip_code_count"] >= 2)
regionless_long_zip_count = sum(1 for row in regionless_rows if row["zip_length"] > 20)
regionless_nationwide_zip_count = sum(1 for row in regionless_rows if row["nationwide_zip"])
regionless_non_nationwide_zip_count = regionless_services - regionless_nationwide_zip_count

regionless_centralish_host_count = sum(1 for row in regionless_rows if row["host_scope_guess"] == "centralish")
regionless_localish_host_count = sum(1 for row in regionless_rows if row["host_scope_guess"] == "localish")
regionless_institutional_unknown_host_count = sum(
    1 for row in regionless_rows if row["host_scope_guess"] == "institutional_unknown"
)
local_suspicious_count = regionless_localish_host_count

regionless_scope_counter = Counter(row["host_scope_guess"] for row in regionless_rows)
central_host_counter = Counter(row["host_org"] for row in regionless_rows if row["host_scope_guess"] == "centralish" and row["host_org"])
local_suspicious_samples = [
    {
        "source_id": row["source_id"],
        "title": row["title"],
        "host_org": row["host_org"],
        "host_scope_guess": row["host_scope_guess"],
        "zip_code_count": row["zip_code_count"],
        "distinct_sido_count": row["distinct_sido_count"],
        "category_main": row["category_main"],
    }
    for row in regionless_rows
    if row["host_scope_guess"] == "localish"
]

if local_suspicious_count > max_local_suspicious_count:
    decision_class = "LOCAL_REVIEW_REQUIRED"
    operator_reading = (
        "YOUTH regionless rows are still dominated by nationwide-style zipCd, but the locally suspicious "
        "subset is large enough that host/org review or bounded override rules should be considered."
    )
    next_action = "docs/collect/youth-regionless-audit-runbook.md"
else:
    decision_class = "NATIONWIDE_DOMINANT_BASELINE"
    operator_reading = (
        "Most YOUTH regionless rows still carry nationwide-style zipCd lists and look central/nationwide. "
        "The remaining local-suspicious subset is small enough to treat as bounded manual review, not a broad parser failure."
    )
    next_action = "bash deploy/smoke/run-local-youth-regionless-audit.sh"

summary_lines = [
    "youth_regionless_audit=passed",
    f"artifact_dir={artifact_dir}",
    f"total_services={total_services}",
    f"region_services={region_services}",
    f"regionless_services={regionless_services}",
    f"region_service_pct={region_service_pct:.2f}",
    f"region_rows_total={region_rows_total}",
    f"regionless_empty_zip_count={regionless_empty_zip_count}",
    f"regionless_csv_zip_count={regionless_csv_zip_count}",
    f"regionless_long_zip_count={regionless_long_zip_count}",
    f"regionless_nationwide_zip_count={regionless_nationwide_zip_count}",
    f"regionless_non_nationwide_zip_count={regionless_non_nationwide_zip_count}",
    f"regionless_centralish_host_count={regionless_centralish_host_count}",
    f"regionless_localish_host_count={regionless_localish_host_count}",
    f"regionless_institutional_unknown_host_count={regionless_institutional_unknown_host_count}",
    f"local_suspicious_count={local_suspicious_count}",
    f"max_local_suspicious_count={max_local_suspicious_count}",
    f"top_central_hosts={','.join(f'{host}:{count}' for host, count in central_host_counter.most_common(5)) if central_host_counter else '(none)'}",
    f"decision_class={decision_class}",
    f"operator_reading={operator_reading}",
    f"next_action={next_action}",
]
summary_out.write_text("\n".join(summary_lines) + "\n", encoding="utf-8")

json_payload = {
    "artifact_dir": artifact_dir,
    "total_services": total_services,
    "region_services": region_services,
    "regionless_services": regionless_services,
    "region_service_pct": region_service_pct,
    "region_rows_total": region_rows_total,
    "regionless_empty_zip_count": regionless_empty_zip_count,
    "regionless_csv_zip_count": regionless_csv_zip_count,
    "regionless_long_zip_count": regionless_long_zip_count,
    "regionless_nationwide_zip_count": regionless_nationwide_zip_count,
    "regionless_non_nationwide_zip_count": regionless_non_nationwide_zip_count,
    "regionless_scope_guess_distribution": dict(regionless_scope_counter),
    "regionless_centralish_host_count": regionless_centralish_host_count,
    "regionless_localish_host_count": regionless_localish_host_count,
    "regionless_institutional_unknown_host_count": regionless_institutional_unknown_host_count,
    "local_suspicious_count": local_suspicious_count,
    "max_local_suspicious_count": max_local_suspicious_count,
    "top_central_hosts": [{"host_org": host, "count": count} for host, count in central_host_counter.most_common(10)],
    "local_suspicious_samples": local_suspicious_samples[:20],
    "decision_class": decision_class,
    "operator_reading": operator_reading,
    "next_action": next_action,
}
json_out.write_text(json.dumps(json_payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

note_lines = [
    "# YOUTH Regionless Audit",
    "",
    f"- `total_services`: `{total_services}`",
    f"- `region_services`: `{region_services}`",
    f"- `regionless_services`: `{regionless_services}`",
    f"- `region_service_pct`: `{region_service_pct:.2f}`",
    f"- `regionless_empty_zip_count`: `{regionless_empty_zip_count}`",
    f"- `regionless_csv_zip_count`: `{regionless_csv_zip_count}`",
    f"- `regionless_nationwide_zip_count`: `{regionless_nationwide_zip_count}`",
    f"- `regionless_non_nationwide_zip_count`: `{regionless_non_nationwide_zip_count}`",
    f"- `regionless_centralish_host_count`: `{regionless_centralish_host_count}`",
    f"- `regionless_localish_host_count`: `{regionless_localish_host_count}`",
    f"- `regionless_institutional_unknown_host_count`: `{regionless_institutional_unknown_host_count}`",
    f"- `local_suspicious_count`: `{local_suspicious_count}`",
    f"- `decision_class`: `{decision_class}`",
    "",
    "## Operator Reading",
    "",
    operator_reading,
    "",
    "## Why This Audit Exists",
    "",
    "- `YOUTH` regionless rows are no longer mainly a missing-field problem.",
    "- Current code treats long `zipCd` lists spanning many sidos as nationwide-style input and switches to `host_org` inference.",
    "- This audit separates the dominant nationwide/central pattern from the smaller explicitly local-looking subset that may deserve bounded manual review.",
    "",
    "## Local-Suspicious Samples",
    "",
]
if local_suspicious_samples:
    for sample in local_suspicious_samples[:10]:
        note_lines.append(
            f"- `{sample['source_id']}` `{sample['title']}` / `{sample['host_org'] or '(empty)'}` / "
            f"`scope={sample['host_scope_guess']}` / `distinctSido={sample['distinct_sido_count']}`"
        )
else:
    note_lines.append("- `(none)`")
note_out.write_text("\n".join(note_lines) + "\n", encoding="utf-8")
PY

smoke_publish_dir_snapshot "${ARTIFACT_DIR}" "${LATEST_ARTIFACT_LINK}"
smoke_publish_file "${SUMMARY_OUT}" "${LATEST_SUMMARY_LINK}"
smoke_publish_file "${JSON_OUT}" "${LATEST_JSON_LINK}"
smoke_publish_file "${NOTE_OUT}" "${LATEST_NOTE_LINK}"

cat "${SUMMARY_OUT}"
echo "latest_artifact_link=${LATEST_ARTIFACT_LINK}"
echo "latest_summary_link=${LATEST_SUMMARY_LINK}"
echo "latest_json_link=${LATEST_JSON_LINK}"
echo "latest_note_link=${LATEST_NOTE_LINK}"
