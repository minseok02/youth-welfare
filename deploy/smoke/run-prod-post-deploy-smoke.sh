#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "${ROOT_DIR}/deploy/smoke/smoke-common.sh"

PUBLIC_BASE_URL="${PUBLIC_BASE_URL:-https://youthmoa.kr}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ALB_TARGET_GROUP_ARN="${ALB_TARGET_GROUP_ARN:-arn:aws:elasticloadbalancing:ap-northeast-2:857721769929:targetgroup/youth-welfare-web-tg/5625712bc3af7438}"
EXPECTED_ALB_HEALTHY_TARGETS="${EXPECTED_ALB_HEALTHY_TARGETS:-2}"
RUN_ALB_TARGET_HEALTH="${RUN_ALB_TARGET_HEALTH:-auto}"
RUN_NGINX_5XX_CHECK="${RUN_NGINX_5XX_CHECK:-true}"
NGINX_ACCESS_LOG="${NGINX_ACCESS_LOG:-/var/log/nginx/access.log}"
NGINX_TAIL_LINES="${NGINX_TAIL_LINES:-4000}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/tmp/prod-post-deploy-smoke/$(smoke_now_ts_utc)}"
SUMMARY_FILE="${ARTIFACT_DIR}/prod-post-deploy-smoke-summary.txt"

normalize_bool() {
  case "${1,,}" in
    true|false) printf '%s' "${1,,}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 2
      ;;
  esac
}

normalize_bool_or_auto() {
  case "${1,,}" in
    true|false|auto) printf '%s' "${1,,}" ;;
    *)
      echo "unsupported boolean/auto value: ${1}" >&2
      exit 2
      ;;
  esac
}

RUN_ALB_TARGET_HEALTH="$(normalize_bool_or_auto "${RUN_ALB_TARGET_HEALTH}")"
RUN_NGINX_5XX_CHECK="$(normalize_bool "${RUN_NGINX_5XX_CHECK}")"

mkdir -p "${ARTIFACT_DIR}"
chmod 700 "${ARTIFACT_DIR}"

cleanup() {
  smoke_sanitize_artifacts "${ARTIFACT_DIR}"
}
trap cleanup EXIT

write_summary_line() {
  printf '%s\n' "$*" | tee -a "${SUMMARY_FILE}"
}

check_json_success() {
  local response_file="$1"
  local label="$2"

  python3 - "${response_file}" "${label}" <<'PY'
import json
import sys

path, label = sys.argv[1:3]
with open(path, "r", encoding="utf-8") as fp:
    payload = json.load(fp)

if payload.get("success") is not True:
    raise SystemExit(f"{label}: success flag is not true")

data = payload.get("data")
count = 0
if isinstance(data, dict) and isinstance(data.get("content"), list):
    count = len(data["content"])
elif isinstance(data, list):
    count = len(data)
elif data is not None:
    count = 1

if count <= 0:
    raise SystemExit(f"{label}: empty data")

print(count)
PY
}

curl_check() {
  local label="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  local output_file="${ARTIFACT_DIR}/${label}.json"
  local status
  local count

  smoke_print_step "post deploy smoke: ${label}"
  if [[ "${method}" == "POST" ]]; then
    status="$(curl -sS -o "${output_file}" -w '%{http_code}' -H 'Content-Type: application/json' -X POST -d "${body}" "${url}")"
  else
    status="$(curl -sS -o "${output_file}" -w '%{http_code}' -X GET "${url}")"
  fi
  smoke_assert_status 200 "${status}" "${label}" "${output_file}"
  count="$(check_json_success "${output_file}" "${label}")"
  write_summary_line "${label}=passed status=${status} count=${count}"
}

check_local_health() {
  local response_file="${ARTIFACT_DIR}/local-health.json"
  local stderr_file="${ARTIFACT_DIR}/local-health.stderr"
  local status

  smoke_print_step "post deploy smoke: local actuator"
  status="$(smoke_wait_for_health 10 1 "${APP_HEALTH_URL}" "${response_file}" "${stderr_file}")"
  write_summary_line "local_actuator=passed status=${status}"
}

check_alb_target_health() {
  local output_file="${ARTIFACT_DIR}/alb-target-health.json"
  local healthy_count

  [[ "${RUN_ALB_TARGET_HEALTH}" == "true" ]] || {
    if [[ "${RUN_ALB_TARGET_HEALTH}" == "auto" ]] && command -v aws >/dev/null 2>&1; then
      :
    else
      write_summary_line "alb_target_health=skipped mode=${RUN_ALB_TARGET_HEALTH}"
      return 0
    fi
  }

  if ! command -v aws >/dev/null 2>&1; then
    if [[ "${RUN_ALB_TARGET_HEALTH}" == "auto" ]]; then
      write_summary_line "alb_target_health=skipped mode=auto reason=missing_aws_cli"
      return 0
    fi
    echo "aws CLI is required for ALB target health check" >&2
    exit 127
  fi

  if [[ "${RUN_ALB_TARGET_HEALTH}" == "false" ]]; then
    write_summary_line "alb_target_health=skipped"
    return 0
  fi

  smoke_print_step "post deploy smoke: ALB target health"
  aws elbv2 describe-target-health \
    --region "${AWS_REGION}" \
    --target-group-arn "${ALB_TARGET_GROUP_ARN}" \
    --query 'TargetHealthDescriptions[].{Target:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason}' \
    --output json > "${output_file}"

  healthy_count="$(python3 - "${output_file}" "${EXPECTED_ALB_HEALTHY_TARGETS}" <<'PY'
import json
import sys

path, expected_raw = sys.argv[1:3]
expected = int(expected_raw)
with open(path, "r", encoding="utf-8") as fp:
    targets = json.load(fp)

healthy = [target for target in targets if target.get("State") == "healthy"]
if len(healthy) < expected or len(healthy) != len(targets):
    print(json.dumps(targets, ensure_ascii=False, indent=2), file=sys.stderr)
    raise SystemExit("ALB target health check failed")

print(len(healthy))
PY
)"

  write_summary_line "alb_target_health=passed healthy_targets=${healthy_count}"
}

check_nginx_recent_5xx() {
  local sample_file="${ARTIFACT_DIR}/nginx-access-tail.log"
  local summary_json="${ARTIFACT_DIR}/nginx-5xx-summary.json"

  [[ "${RUN_NGINX_5XX_CHECK}" == "true" ]] || {
    write_summary_line "nginx_recent_5xx=skipped"
    return 0
  }

  smoke_print_step "post deploy smoke: nginx recent 5xx"
  if [[ ! -r "${NGINX_ACCESS_LOG}" ]]; then
    write_summary_line "nginx_recent_5xx=skipped missing_log=${NGINX_ACCESS_LOG}"
    return 0
  fi

  tail -n "${NGINX_TAIL_LINES}" "${NGINX_ACCESS_LOG}" > "${sample_file}"

  python3 - "${sample_file}" "${summary_json}" <<'PY'
import json
import re
import sys

sample_path, summary_path = sys.argv[1:3]
pattern = re.compile(r'"([A-Z]+)\s+([^"\s]+)[^"]*"\s+(\d{3})\s')
alb_health_5xx = 0
user_5xx = 0
probe_5xx = 0
samples = []
probe_samples = []

with open(sample_path, "r", encoding="utf-8", errors="replace") as fp:
    for line in fp:
        match = pattern.search(line)
        if not match:
            continue
        method, path, status_raw = match.groups()
        status = int(status_raw)
        if status < 500:
            continue
        path_only = path.split("?", 1)[0]
        if path_only == "/alb-health":
            alb_health_5xx += 1
        elif path_only.startswith("/api/") or method in {"GET", "HEAD"}:
            user_5xx += 1
            if len(samples) < 10:
                samples.append(line.strip())
        else:
            probe_5xx += 1
            if len(probe_samples) < 10:
                probe_samples.append(line.strip())

summary = {
    "alb_health_5xx": alb_health_5xx,
    "user_5xx": user_5xx,
    "probe_5xx": probe_5xx,
    "sample_user_5xx": samples,
    "sample_probe_5xx": probe_samples,
}
with open(summary_path, "w", encoding="utf-8") as fp:
    json.dump(summary, fp, ensure_ascii=False, indent=2)

print(json.dumps(summary, ensure_ascii=False))
if user_5xx > 0:
    raise SystemExit("recent API/page 5xx found")
PY

  local alb_health_5xx
  alb_health_5xx="$(python3 - "${summary_json}" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
print(payload.get("alb_health_5xx", 0))
PY
)"
  local probe_5xx
  probe_5xx="$(python3 - "${summary_json}" <<'PY'
import json
import sys
with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)
print(payload.get("probe_5xx", 0))
PY
)"
  write_summary_line "nginx_recent_5xx=passed user_5xx=0 alb_health_5xx=${alb_health_5xx} probe_5xx=${probe_5xx}"
}

: > "${SUMMARY_FILE}"
write_summary_line "prod_post_deploy_smoke=started"
write_summary_line "artifact_dir=${ARTIFACT_DIR}"
write_summary_line "public_base_url=${PUBLIC_BASE_URL}"
write_summary_line "app_health_url=${APP_HEALTH_URL}"

check_local_health
check_alb_target_health
curl_check "public-policy-list" GET "${PUBLIC_BASE_URL}/api/policies?page=0&size=5"
curl_check "public-policy-search" POST "${PUBLIC_BASE_URL}/api/policies/search" '{"keyword":"\uc6d4\uc138","page":0,"size":5}'
curl_check "public-policy-ranking" GET "${PUBLIC_BASE_URL}/api/policies/ranking?page=0&size=5"
check_nginx_recent_5xx

write_summary_line "prod_post_deploy_smoke=passed"
smoke_sanitize_artifacts "${ARTIFACT_DIR}"
