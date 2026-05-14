#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATCH_DIR="${PATCH_DIR:-${ROOT_DIR}/deploy/postgres/patches}"
DB_CONTAINER="${DB_CONTAINER:-youth-welfare-db}"
DB_NAME="${DB_NAME:-youth_welfare}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker command not found" >&2
  exit 1
fi

if ! docker inspect "${DB_CONTAINER}" >/dev/null 2>&1; then
  echo "db container not found: ${DB_CONTAINER}" >&2
  exit 1
fi

if [[ "$(docker inspect -f '{{.State.Running}}' "${DB_CONTAINER}")" != "true" ]]; then
  echo "db container is not running: ${DB_CONTAINER}" >&2
  exit 1
fi

if [[ ! -d "${PATCH_DIR}" ]]; then
  echo "patch directory not found: ${PATCH_DIR}" >&2
  exit 1
fi

shopt -s nullglob
patches=("${PATCH_DIR}"/*.sql)
shopt -u nullglob

if [[ ${#patches[@]} -eq 0 ]]; then
  echo "no patch files found in ${PATCH_DIR}" >&2
  exit 1
fi

for patch in "${patches[@]}"; do
  echo "applying $(basename "${patch}")"
  docker exec -i "${DB_CONTAINER}" psql -v ON_ERROR_STOP=1 -U postgres -d "${DB_NAME}" < "${patch}"
done

echo "local runtime schema patch applied"
