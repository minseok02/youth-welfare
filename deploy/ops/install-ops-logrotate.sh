#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-/home/ubuntu/youth-welfare}"
SOURCE_FILE="${ROOT_DIR}/deploy/ops/youth-welfare-ops.logrotate"
TARGET_FILE="${TARGET_FILE:-/etc/logrotate.d/youth-welfare-ops}"

if [[ ! -r "${SOURCE_FILE}" ]]; then
  echo "missing logrotate source file: ${SOURCE_FILE}" >&2
  exit 2
fi
if ! command -v logrotate >/dev/null 2>&1; then
  echo "missing logrotate" >&2
  exit 2
fi

sudo install -m 644 "${SOURCE_FILE}" "${TARGET_FILE}"
sudo logrotate -d "${TARGET_FILE}" >/dev/null

echo "installed ${TARGET_FILE}"
