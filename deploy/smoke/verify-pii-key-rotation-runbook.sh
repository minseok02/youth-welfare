#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNBOOK_DOC="${RUNBOOK_DOC:-${ROOT_DIR}/docs/core/pii-key-rotation-runbook.md}"

python3 - "${RUNBOOK_DOC}" <<'PY'
import sys
from pathlib import Path

doc_path = Path(sys.argv[1])
if not doc_path.exists():
    raise SystemExit(f"missing PII key rotation runbook: {doc_path}")

text = doc_path.read_text(encoding="utf-8")
required_contract_ids = [
    "PII_KEY_ROTATION_CURRENT_SCOPE",
    "PII_KEY_ROTATION_FORBIDDEN_DIRECT_AES_SECRET_SWAP",
    "PII_LEGACY_CIPHER_ROTATION_RUNBOOK",
    "PII_KEY_ROTATION_FUTURE_DUAL_KEY_CONTRACT",
    "PII_KEY_ROTATION_EVIDENCE",
]
required_terms = [
    "POST /api/admin/users/pii-encryption-rotation",
    "AES_SECRET_KEY",
    "AesEncryptUtil",
    "AES/GCM/NoPadding",
    "AES/CBC/PKCS5Padding",
    "failedCount=0",
    "run-local-pii-sync-cutover-smoke.sh",
    "AES_ACTIVE_KEY_ID",
    "AES_DECRYPT_KEYS",
    "currentSchema=youth_welfare_pii",
    "DB snapshot restore",
]

failures = []
for contract_id in required_contract_ids:
    if contract_id not in text:
        failures.append(f"missing contract id {contract_id}")

for term in required_terms:
    if term not in text:
        failures.append(f"missing runbook term {term}")

if "현재 코드에서 `AES_SECRET_KEY` 를 바로 교체하는 것은 금지합니다." not in text:
    failures.append("missing direct AES_SECRET_KEY swap prohibition")

if failures:
    for failure in failures:
        print(failure, file=sys.stderr)
    raise SystemExit(1)

print("PII key rotation runbook contract passed")
PY
