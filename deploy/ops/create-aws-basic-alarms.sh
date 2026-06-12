#!/usr/bin/env bash
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
INSTANCE_ID="${INSTANCE_ID:-}"
ALERT_EMAIL="${ALERT_EMAIL:-}"
TOPIC_NAME="${TOPIC_NAME:-youth-welfare-ops-alerts}"

if ! command -v aws >/dev/null 2>&1; then
  echo "missing aws CLI" >&2
  exit 2
fi

if [[ -z "${INSTANCE_ID}" ]]; then
  token="$(curl -sS --max-time 2 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)"
  if [[ -n "${token}" ]]; then
    INSTANCE_ID="$(curl -sS --max-time 2 -H "X-aws-ec2-metadata-token: ${token}" http://169.254.169.254/latest/meta-data/instance-id 2>/dev/null || true)"
  fi
fi

if [[ -z "${ALERT_EMAIL}" ]]; then
  echo "ALERT_EMAIL is required" >&2
  exit 2
fi
if [[ -z "${INSTANCE_ID}" ]]; then
  echo "INSTANCE_ID is required" >&2
  exit 2
fi

topic_arn="$(aws sns create-topic \
  --region "${REGION}" \
  --name "${TOPIC_NAME}" \
  --query 'TopicArn' \
  --output text)"

aws sns subscribe \
  --region "${REGION}" \
  --topic-arn "${topic_arn}" \
  --protocol email \
  --notification-endpoint "${ALERT_EMAIL}" >/dev/null

aws cloudwatch put-metric-alarm \
  --region "${REGION}" \
  --alarm-name "youth-welfare-ec2-status-check-failed" \
  --alarm-description "Youth Welfare EC2 status check failed" \
  --namespace AWS/EC2 \
  --metric-name StatusCheckFailed \
  --dimensions "Name=InstanceId,Value=${INSTANCE_ID}" \
  --statistic Maximum \
  --period 60 \
  --evaluation-periods 2 \
  --datapoints-to-alarm 2 \
  --threshold 1 \
  --comparison-operator GreaterThanOrEqualToThreshold \
  --treat-missing-data notBreaching \
  --alarm-actions "${topic_arn}" \
  --ok-actions "${topic_arn}"

echo "created SNS topic and EC2 status alarm"
echo "Confirm the SNS subscription email for ${ALERT_EMAIL}."
