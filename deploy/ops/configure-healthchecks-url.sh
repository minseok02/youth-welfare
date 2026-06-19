#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <healthchecks-ping-url>" >&2
  exit 2
fi

PING_URL="$1"
CONFIG_DIR="${CONFIG_DIR:-${HOME:-/home/ubuntu}/.config/youth-welfare}"
OPS_ENV_FILE="${OPS_ENV_FILE:-${CONFIG_DIR}/ops.env}"

case "${PING_URL}" in
  https://hc-ping.com/*|https://healthchecks.io/ping/*)
    ;;
  *)
    echo "unexpected Healthchecks URL format" >&2
    exit 2
    ;;
esac

mkdir -p "${CONFIG_DIR}"
chmod 700 "${CONFIG_DIR}"

tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT

if [[ -r "${OPS_ENV_FILE}" ]]; then
  grep -v '^HEALTHCHECKS_PING_URL=' "${OPS_ENV_FILE}" > "${tmp}" || true
fi
printf 'HEALTHCHECKS_PING_URL=%q\n' "${PING_URL}" >> "${tmp}"
install -m 600 "${tmp}" "${OPS_ENV_FILE}"

echo "configured Healthchecks ping URL in ${OPS_ENV_FILE}"
