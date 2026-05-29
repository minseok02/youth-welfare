#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAYWRIGHT_LIB_DIR="$("${SCRIPT_DIR}/setup-playwright-libs.sh")"

export PLAYWRIGHT_LD_LIBRARY_PATH="${PLAYWRIGHT_LIB_DIR}"
export LD_LIBRARY_PATH="${PLAYWRIGHT_LIB_DIR}${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"

PLAYWRIGHT_ARGS=()

if [[ -n "${PLAYWRIGHT_GREP:-}" ]]; then
  PLAYWRIGHT_ARGS+=(--grep "${PLAYWRIGHT_GREP}")
fi

if [[ -n "${PLAYWRIGHT_GREP_INVERT:-}" ]]; then
  PLAYWRIGHT_ARGS+=(--grep-invert "${PLAYWRIGHT_GREP_INVERT}")
fi

npx playwright test "${PLAYWRIGHT_ARGS[@]}" "$@"
