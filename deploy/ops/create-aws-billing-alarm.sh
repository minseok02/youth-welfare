#!/usr/bin/env bash
set -euo pipefail

ALARM_REGION="${BILLING_ALARM_REGION:-us-east-1}"
THRESHOLD_USD="${THRESHOLD_USD:-5}"
TOPIC_NAME="${TOPIC_NAME:-youth-welfare-ops-alerts}"

if ! command -v aws >/dev/null 2>&1; then
  echo "missing aws CLI" >&2
  exit 2
fi

topic_arn="$(aws sns create-topic \
  --region "${ALARM_REGION}" \
  --name "${TOPIC_NAME}" \
  --query 'TopicArn' \
  --output text)"

aws cloudwatch put-metric-alarm \
  --region "${ALARM_REGION}" \
  --alarm-name "youth-welfare-billing-estimated-charges-${THRESHOLD_USD}usd" \
  --alarm-description "AWS estimated monthly charges exceeded ${THRESHOLD_USD} USD for Youth Welfare account" \
  --namespace AWS/Billing \
  --metric-name EstimatedCharges \
  --dimensions "Name=Currency,Value=USD" \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --datapoints-to-alarm 1 \
  --threshold "${THRESHOLD_USD}" \
  --comparison-operator GreaterThanThreshold \
  --treat-missing-data notBreaching \
  --alarm-actions "${topic_arn}" \
  --ok-actions "${topic_arn}"

echo "created CloudWatch billing alarm for ${THRESHOLD_USD} USD"
