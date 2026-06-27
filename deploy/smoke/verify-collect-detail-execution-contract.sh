#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONTRACT_DOC="${CONTRACT_DOC:-${ROOT_DIR}/docs/collect/collect-detail-execution-contract.md}"

python3 - "${CONTRACT_DOC}" <<'PY'
import sys
from pathlib import Path

doc_path = Path(sys.argv[1])
if not doc_path.exists():
    raise SystemExit(f"missing collect detail execution contract doc: {doc_path}")

text = doc_path.read_text(encoding="utf-8")
required_contract_ids = [
    "COLLECT_DETAIL_PHASE_LIST_SNAPSHOT",
    "COLLECT_DETAIL_PHASE_FORCED_DETAIL",
    "COLLECT_DETAIL_PHASE_ROTATION_DETAIL",
    "COLLECT_DETAIL_DUPLICATE_LANE_ALLOWED",
    "COLLECT_DETAIL_CALL_BUDGETS",
]
required_sources = [
    "YOUTH",
    "BOKJIRO_CENTRAL",
    "BOKJIRO_LOCAL",
    "GOV24",
    "GOV24_DETAIL",
    "GOV24_SUPPORT_CONDITIONS",
    "BOKJIRO_DETAIL",
    "BOKJIRO_DETAIL_REFRESH",
]
required_config_keys = [
    "collect.list.diff.force-detail.max-candidates-per-run=50",
    "collect.list.rotation.bokjiro-detail-max-calls-per-run=100",
    "collect.list.rotation.gov24-detail-max-calls-per-run=50",
    "collect.list.rotation.gov24-support-conditions-max-calls-per-run=50",
    "collect.list.rotation.bokjiro-refresh-max-calls-per-run=50",
    "collect.youth.detail.max-calls-per-run=50",
]
required_order_terms = [
    "모든 list source 실행이 끝난 뒤",
    "forced detail phase가 모두 끝난 뒤",
    "같은 detail lane이 한 run 결과에 두 번 보이는 것은 허용된 상태",
    "api_sync_logs.requested_count",
]

failures = []
for contract_id in required_contract_ids:
    if contract_id not in text:
        failures.append(f"missing contract id {contract_id}")

for source in required_sources:
    if f"`{source}`" not in text:
        failures.append(f"missing source {source}")

for config_key in required_config_keys:
    if config_key not in text:
        failures.append(f"missing config key {config_key}")

for term in required_order_terms:
    if term not in text:
        failures.append(f"missing order/call-budget term {term}")

if failures:
    for failure in failures:
        print(failure, file=sys.stderr)
    raise SystemExit(1)

print("collect detail execution contract passed")
PY
