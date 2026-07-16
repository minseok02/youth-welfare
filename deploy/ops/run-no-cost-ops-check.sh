#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
ENV_FILE="${ENV_FILE:-.env.production}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
ALB_TARGET_GROUP_ARN="${ALB_TARGET_GROUP_ARN:-arn:aws:elasticloadbalancing:ap-northeast-2:857721769929:targetgroup/youth-welfare-web-tg/5625712bc3af7438}"
APP_SG_ID="${APP_SG_ID:-sg-01b664b3af6ad95a2}"
ALB_SG_ID="${ALB_SG_ID:-sg-06ac84b1d37d48409}"
EC2_INSTANCE_IDS="${EC2_INSTANCE_IDS:-i-0b8d95e454df5e0f0 i-0e8a4cc599c1148c8}"
RUN_DB_AUDIT="${RUN_DB_AUDIT:-true}"
RUN_LOG_ALERT="${RUN_LOG_ALERT:-true}"
RUN_POST_DEPLOY_SMOKE="${RUN_POST_DEPLOY_SMOKE:-true}"
RUN_SECURITY_GROUP_CHECK="${RUN_SECURITY_GROUP_CHECK:-true}"
RUN_CRON_CHECK="${RUN_CRON_CHECK:-true}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:8082/actuator/health}"

cd "${ROOT_DIR}"

ok_count=0
fail_count=0

print_step() {
  printf '\n[%s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"
}

record_ok() {
  ok_count=$((ok_count + 1))
  printf 'ok=%s\n' "$*"
}

record_fail() {
  fail_count=$((fail_count + 1))
  printf 'fail=%s\n' "$*" >&2
}

normalize_bool() {
  case "${1,,}" in
    true|false) printf '%s' "${1,,}" ;;
    *)
      echo "unsupported boolean value: ${1}" >&2
      exit 2
      ;;
  esac
}

RUN_DB_AUDIT="$(normalize_bool "${RUN_DB_AUDIT}")"
RUN_LOG_ALERT="$(normalize_bool "${RUN_LOG_ALERT}")"
RUN_POST_DEPLOY_SMOKE="$(normalize_bool "${RUN_POST_DEPLOY_SMOKE}")"
RUN_SECURITY_GROUP_CHECK="$(normalize_bool "${RUN_SECURITY_GROUP_CHECK}")"
RUN_CRON_CHECK="$(normalize_bool "${RUN_CRON_CHECK}")"

print_step "repo and local health"
git_status="$(git status --short --branch)"
git_head="$(git rev-parse --short HEAD)"
printf 'git_head=%s\n' "${git_head}"
printf '%s\n' "${git_status}"
if curl -fsS "${APP_HEALTH_URL}" >/tmp/youth-welfare-no-cost-health.json; then
  record_ok "local_actuator"
else
  record_fail "local_actuator"
fi

if [[ "${RUN_POST_DEPLOY_SMOKE}" == "true" ]]; then
  print_step "post-deploy smoke"
  if RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh; then
    record_ok "post_deploy_smoke"
  else
    record_fail "post_deploy_smoke"
  fi
fi

if [[ "${RUN_DB_AUDIT}" == "true" ]]; then
  print_step "operational DB audit"
  db_audit_out="/tmp/youth-welfare-no-cost-db-audit.out"
  if ENV_FILE="${ENV_FILE}" bash deploy/postgres/audit-operational-db-state.sh > "${db_audit_out}"; then
    rg 'ops_queue|db_runtime|policy_error_reports_open|notification_failed_like|collect_locks_active|unread_user_alerts|waiting_locks|active_queries_over_5m|operational_alert_source_available|notification_failed_like=' "${db_audit_out}" || true
    if rg -q 'policy_error_reports_open[[:space:]]+\\| 0|policy_error_reports_open=0' "${db_audit_out}" \
      && rg -q 'notification_failed_like[[:space:]]+\\| 0|notification_failed_like=0' "${db_audit_out}" \
      && rg -q 'waiting_locks[[:space:]]+\\| 0|waiting_locks=0' "${db_audit_out}" \
      && rg -q 'active_queries_over_5m[[:space:]]+\\| 0|active_queries_over_5m=0' "${db_audit_out}"; then
      record_ok "db_audit_core"
    else
      record_fail "db_audit_core"
    fi
  else
    record_fail "db_audit_command"
  fi
fi

if [[ "${RUN_LOG_ALERT}" == "true" ]]; then
  print_step "log alert"
  log_alert_out="/tmp/youth-welfare-no-cost-log-alert.out"
  if LOG_ALERT_NOTIFY_OK=false bash deploy/ops/send-log-alert.sh > "${log_alert_out}"; then
    cat "${log_alert_out}"
    if rg -q '^LOG_ALERT_STATUS=ok$' "${log_alert_out}"; then
      record_ok "log_alert_ok"
    else
      record_fail "log_alert_status"
    fi
  else
    cat "${log_alert_out}" 2>/dev/null || true
    record_fail "log_alert_command"
  fi
fi

if [[ "${RUN_SECURITY_GROUP_CHECK}" == "true" ]]; then
  print_step "security groups and direct EC2 access"
  sg_json="/tmp/youth-welfare-no-cost-security-groups.json"
  if aws ec2 describe-security-groups \
    --region "${AWS_REGION}" \
    --group-ids "${APP_SG_ID}" "${ALB_SG_ID}" \
    --output json > "${sg_json}"; then
    if python3 - "${sg_json}" "${APP_SG_ID}" "${ALB_SG_ID}" <<'PY'
import json
import sys

path, app_sg_id, alb_sg_id = sys.argv[1:4]
payload = json.load(open(path, encoding="utf-8"))
groups = {group["GroupId"]: group for group in payload["SecurityGroups"]}
app = groups[app_sg_id]
alb = groups[alb_sg_id]

def has_public_tcp(group, port):
    for rule in group.get("IpPermissions", []):
        if rule.get("IpProtocol") != "tcp":
            continue
        if int(rule.get("FromPort", -1)) > port or int(rule.get("ToPort", -1)) < port:
            continue
        if any(ip.get("CidrIp") == "0.0.0.0/0" for ip in rule.get("IpRanges", [])):
            return True
    return False

def has_source_sg_tcp(group, port, source_group):
    for rule in group.get("IpPermissions", []):
        if rule.get("IpProtocol") != "tcp":
            continue
        if int(rule.get("FromPort", -1)) > port or int(rule.get("ToPort", -1)) < port:
            continue
        if any(pair.get("GroupId") == source_group for pair in rule.get("UserIdGroupPairs", [])):
            return True
    return False

assert not has_public_tcp(app, 80), "app SG has public 80"
assert not has_public_tcp(app, 443), "app SG has public 443"
assert has_source_sg_tcp(app, 80, alb_sg_id), "app SG missing ALB SG source on 80"
assert has_public_tcp(alb, 80), "ALB SG missing public 80"
assert has_public_tcp(alb, 443), "ALB SG missing public 443"
print("security_group_shape=ok")
PY
    then
      record_ok "security_group_shape"
    else
      record_fail "security_group_shape"
    fi
  else
    record_fail "security_group_describe"
  fi

  public_ips="$(
    aws ec2 describe-instances \
      --region "${AWS_REGION}" \
      --instance-ids ${EC2_INSTANCE_IDS} \
      --query 'Reservations[].Instances[].PublicIpAddress' \
      --output text 2>/dev/null || true
  )"
  direct_failures=0
  for ip in ${public_ips}; do
    for scheme in http https; do
      status="$(
        curl -k -sS -o /dev/null -w '%{http_code}' --connect-timeout 3 --max-time 5 "${scheme}://${ip}/" 2>/dev/null || true
      )"
      printf 'direct_%s_%s_status=%s\n' "${scheme}" "${ip}" "${status:-000}"
      if [[ "${status}" != "000" ]]; then
        direct_failures=$((direct_failures + 1))
      fi
    done
  done
  if [[ "${direct_failures}" -eq 0 ]]; then
    record_ok "ec2_direct_access_blocked"
  else
    record_fail "ec2_direct_access_blocked"
  fi
fi

if [[ "${RUN_CRON_CHECK}" == "true" ]]; then
  print_step "cron and watchdog"
  if systemctl is-active cron >/dev/null; then
    record_ok "cron_active"
  else
    record_fail "cron_active"
  fi
  if crontab -l | rg -q 'deploy/ops/send-log-alert.sh'; then
    record_ok "log_alert_cron_installed"
  else
    record_fail "log_alert_cron_installed"
  fi
  if crontab -l | rg -q 'deploy/ops/app-watchdog.sh'; then
    record_ok "watchdog_cron_installed"
  else
    record_fail "watchdog_cron_installed"
  fi
  tail -n 5 /var/log/youth-welfare/ops/log-alert.log 2>/dev/null || true
  tail -n 5 /var/log/youth-welfare/ops/app-watchdog.log 2>/dev/null || true
fi

print_step "summary"
printf 'ok_count=%s\n' "${ok_count}"
printf 'fail_count=%s\n' "${fail_count}"
if [[ "${fail_count}" -gt 0 ]]; then
  exit 1
fi
