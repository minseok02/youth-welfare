#!/usr/bin/env bash
set -euo pipefail

DOMAIN="${DOMAIN:-youthmoa.kr}"
PATH_TO_CHECK="${PATH_TO_CHECK:-/}"
PORT="${PORT:-443}"
PROTOCOL="${PROTOCOL:-HTTPS}"
ALARM_REGION="${ROUTE53_ALARM_REGION:-us-east-1}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
TOPIC_NAME="${TOPIC_NAME:-youth-welfare-ops-alerts}"

if ! command -v aws >/dev/null 2>&1; then
  echo "missing aws CLI" >&2
  exit 2
fi
if [[ -z "${ALERT_EMAIL}" ]]; then
  echo "ALERT_EMAIL is required" >&2
  exit 2
fi

caller_ref="youth-welfare-${DOMAIN}-$(date +%Y%m%d%H%M%S)"
config="{
  \"Type\": \"${PROTOCOL}\",
  \"FullyQualifiedDomainName\": \"${DOMAIN}\",
  \"Port\": ${PORT},
  \"ResourcePath\": \"${PATH_TO_CHECK}\",
  \"RequestInterval\": 30,
  \"FailureThreshold\": 3,
  \"MeasureLatency\": false,
  \"EnableSNI\": true
}"

health_check_id="$(aws route53 create-health-check \
  --caller-reference "${caller_ref}" \
  --health-check-config "${config}" \
  --query 'HealthCheck.Id' \
  --output text)"

aws route53 change-tags-for-resource \
  --resource-type healthcheck \
  --resource-id "${health_check_id}" \
  --add-tags Key=Name,Value=youth-welfare-uptime Key=Service,Value=youth-welfare >/dev/null

topic_arn="$(aws sns create-topic \
  --region "${ALARM_REGION}" \
  --name "${TOPIC_NAME}" \
  --query 'TopicArn' \
  --output text)"

aws sns subscribe \
  --region "${ALARM_REGION}" \
  --topic-arn "${topic_arn}" \
  --protocol email \
  --notification-endpoint "${ALERT_EMAIL}" >/dev/null

aws cloudwatch put-metric-alarm \
  --region "${ALARM_REGION}" \
  --alarm-name "youth-welfare-route53-uptime-unhealthy" \
  --alarm-description "Youth Welfare public uptime health check failed" \
  --namespace AWS/Route53 \
  --metric-name HealthCheckStatus \
  --dimensions "Name=HealthCheckId,Value=${health_check_id}" \
  --statistic Minimum \
  --period 60 \
  --evaluation-periods 3 \
  --datapoints-to-alarm 3 \
  --threshold 1 \
  --comparison-operator LessThanThreshold \
  --treat-missing-data notBreaching \
  --alarm-actions "${topic_arn}" \
  --ok-actions "${topic_arn}"

echo "created Route53 health check ${health_check_id} and CloudWatch alarm"
echo "Confirm the SNS subscription email for ${ALERT_EMAIL}."
