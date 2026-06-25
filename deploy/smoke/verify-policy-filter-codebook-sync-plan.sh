#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLAN_DOC="${PLAN_DOC:-${ROOT_DIR}/docs/policy/policy-filter-codebook-sync-plan.md}"

python3 - "${PLAN_DOC}" <<'PY'
import sys
from pathlib import Path

doc_path = Path(sys.argv[1])
if not doc_path.exists():
    raise SystemExit(f"missing policy filter codebook sync plan: {doc_path}")

text = doc_path.read_text(encoding="utf-8")
required_contract_ids = [
    "POLICY_FILTER_CODEBOOK_CURRENT_CONTRACT",
    "POLICY_FILTER_CODEBOOK_NOT_RUNTIME_API_YET",
    "POLICY_FILTER_CODEBOOK_GENERATED_CONSTANTS_CANDIDATE",
    "POLICY_FILTER_CODEBOOK_PUBLIC_API_CANDIDATE",
    "POLICY_FILTER_CODEBOOK_REOPEN_CONDITIONS",
    "POLICY_FILTER_CODEBOOK_VERIFICATION",
]
required_terms = [
    "frontend/src/lib/policyFilterOptions.js",
    "frontend/src/lib/policyFilterOptions.test.js",
    "Gov24ServiceFieldSupport.managedLabels()",
    "Gov24UserTypeSupport.managedTokens()",
    "Gov24BenefitTypeSupport.managedTokens()",
    "Gov24PolicyFilterSupportTest",
    "OfficialCodebookReadService",
    "/api/reference/official-codes",
    "GET /api/policies/filter-codebooks",
    "build-time generated constants",
    "checked-in fallback",
]

failures = []
for contract_id in required_contract_ids:
    if contract_id not in text:
        failures.append(f"missing contract id {contract_id}")

for term in required_terms:
    if term not in text:
        failures.append(f"missing plan term {term}")

if "`GET /api/policies/filter-codebooks` 같은 공개 runtime API는 지금 만들지 않습니다." not in text:
    failures.append("missing explicit runtime API deferral decision")

if failures:
    for failure in failures:
        print(failure, file=sys.stderr)
    raise SystemExit(1)

print("policy filter codebook sync plan contract passed")
PY
