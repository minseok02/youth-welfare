#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PLAYWRIGHT_LIB_ROOT="${ROOT_DIR}/.tmp/playwright-libs"
PACKAGE_DIR="${PLAYWRIGHT_LIB_ROOT}/packages"
ROOTFS_DIR="${PLAYWRIGHT_LIB_ROOT}/rootfs"
LIB_DIR="${ROOTFS_DIR}/usr/lib/x86_64-linux-gnu"

required_libs=(
  "libnspr4.so"
  "libnss3.so"
  "libnssutil3.so"
  "libasound.so.2"
)

all_present=true
for lib_name in "${required_libs[@]}"; do
  if [[ ! -f "${LIB_DIR}/${lib_name}" ]]; then
    all_present=false
    break
  fi
done

if [[ "${all_present}" == "true" ]]; then
  printf '%s\n' "${LIB_DIR}"
  exit 0
fi

mkdir -p "${PACKAGE_DIR}" "${ROOTFS_DIR}"

packages=(
  "libnspr4"
  "libnss3"
  "libasound2t64"
)

pushd "${PACKAGE_DIR}" >/dev/null
apt download "${packages[@]}" >/dev/null
for deb_file in ./*.deb; do
  dpkg-deb -x "${deb_file}" "${ROOTFS_DIR}"
done
popd >/dev/null

printf '%s\n' "${LIB_DIR}"
