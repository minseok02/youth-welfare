#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAYWRIGHT_LIB_DIR="$("${SCRIPT_DIR}/setup-playwright-libs.sh")"

export PLAYWRIGHT_LD_LIBRARY_PATH="${PLAYWRIGHT_LIB_DIR}"
export LD_LIBRARY_PATH="${PLAYWRIGHT_LIB_DIR}${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"

npx playwright test "$@"
