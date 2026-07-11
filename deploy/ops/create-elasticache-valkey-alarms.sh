#!/usr/bin/env bash
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
REPLICATION_GROUP_ID="${REPLICATION_GROUP_ID:-youth-welfare-prod-redis-valkey}"
CACHE_CLUSTER_ID="${CACHE_CLUSTER_ID:-${REPLICATION_GROUP_ID}-001}"
CACHE_NODE_ID="${CACHE_NODE_ID:-0001}"
TOPIC_NAME="${TOPIC_NAME:-youth-welfare-ops-alerts}"
ALERT_EMAIL="${ALERT_EMAIL:-}"

ENGINE_CPU_THRESHOLD="${ENGINE_CPU_THRESHOLD:-80}"
DB_MEMORY_THRESHOLD="${DB_MEMORY_THRESHOLD:-80}"
FREEABLE_MEMORY_THRESHOLD_BYTES="${FREEABLE_MEMORY_THRESHOLD_BYTES:-52428800}"
CURR_CONNECTIONS_THRESHOLD="${CURR_CONNECTIONS_THRESHOLD:-200}"
NEW_CONNECTIONS_THRESHOLD="${NEW_CONNECTIONS_THRESHOLD:-100}"

if ! command -v aws >/dev/null 2>&1; then
  echo "missing aws CLI" >&2
  exit 2
fi

topic_arn="$(aws sns create-topic \
  --region "${REGION}" \
  --name "${TOPIC_NAME}" \
  --query 'TopicArn' \
  --output text)"

if [[ -n "${ALERT_EMAIL}" ]]; then
  aws sns subscribe \
    --region "${REGION}" \
    --topic-arn "${topic_arn}" \
    --protocol email \
    --notification-endpoint "${ALERT_EMAIL}" >/dev/null
fi

dimensions=(
  "Name=CacheClusterId,Value=${CACHE_CLUSTER_ID}"
  "Name=CacheNodeId,Value=${CACHE_NODE_ID}"
)

put_alarm() {
  local alarm_name="$1"
  local description="$2"
  local metric_name="$3"
  local statistic="$4"
  local comparison_operator="$5"
  local threshold="$6"
  local period="${7:-300}"
  local evaluation_periods="${8:-3}"
  local datapoints_to_alarm="${9:-2}"

  aws cloudwatch put-metric-alarm \
    --region "${REGION}" \
    --alarm-name "${alarm_name}" \
    --alarm-description "${description}" \
    --namespace AWS/ElastiCache \
    --metric-name "${metric_name}" \
    --dimensions "${dimensions[@]}" \
    --statistic "${statistic}" \
    --period "${period}" \
    --evaluation-periods "${evaluation_periods}" \
    --datapoints-to-alarm "${datapoints_to_alarm}" \
    --threshold "${threshold}" \
    --comparison-operator "${comparison_operator}" \
    --treat-missing-data notBreaching \
    --alarm-actions "${topic_arn}" \
    --ok-actions "${topic_arn}"
}

put_alarm \
  "youth-welfare-elasticache-engine-cpu-high" \
  "Youth Welfare ElastiCache Valkey engine CPU is high" \
  "EngineCPUUtilization" \
  "Average" \
  "GreaterThanOrEqualToThreshold" \
  "${ENGINE_CPU_THRESHOLD}"

put_alarm \
  "youth-welfare-elasticache-memory-usage-high" \
  "Youth Welfare ElastiCache Valkey database memory usage is high" \
  "DatabaseMemoryUsagePercentage" \
  "Average" \
  "GreaterThanOrEqualToThreshold" \
  "${DB_MEMORY_THRESHOLD}"

put_alarm \
  "youth-welfare-elasticache-freeable-memory-low" \
  "Youth Welfare ElastiCache Valkey freeable memory is low" \
  "FreeableMemory" \
  "Average" \
  "LessThanOrEqualToThreshold" \
  "${FREEABLE_MEMORY_THRESHOLD_BYTES}"

put_alarm \
  "youth-welfare-elasticache-evictions-detected" \
  "Youth Welfare ElastiCache Valkey evictions detected" \
  "Evictions" \
  "Sum" \
  "GreaterThanOrEqualToThreshold" \
  "1" \
  "300" \
  "1" \
  "1"

put_alarm \
  "youth-welfare-elasticache-current-connections-high" \
  "Youth Welfare ElastiCache Valkey current connections are high" \
  "CurrConnections" \
  "Average" \
  "GreaterThanOrEqualToThreshold" \
  "${CURR_CONNECTIONS_THRESHOLD}"

put_alarm \
  "youth-welfare-elasticache-new-connections-high" \
  "Youth Welfare ElastiCache Valkey new connections are high" \
  "NewConnections" \
  "Sum" \
  "GreaterThanOrEqualToThreshold" \
  "${NEW_CONNECTIONS_THRESHOLD}"

echo "created ElastiCache Valkey CloudWatch alarms"
echo "region=${REGION}"
echo "cache_cluster_id=${CACHE_CLUSTER_ID}"
echo "cache_node_id=${CACHE_NODE_ID}"
echo "sns_topic=${topic_arn}"
if [[ -n "${ALERT_EMAIL}" ]]; then
  echo "Confirm the SNS subscription email for ${ALERT_EMAIL}."
fi
