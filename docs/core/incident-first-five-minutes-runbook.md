# Incident First Five Minutes Runbook

작성 기준일: 2026-07-16

## Purpose

Give the operator the first commands and decisions for the first five minutes of a production incident.

This runbook is not a full root-cause guide. It answers:

- is traffic currently affected?
- which layer is failing?
- what is the safest immediate containment?

## Always Start Here

```bash
date -u
git -C /home/ubuntu/youth-welfare rev-parse --short HEAD
curl -fsS http://127.0.0.1:8082/actuator/health
RUN_ALB_TARGET_HEALTH=true bash /home/ubuntu/youth-welfare/deploy/smoke/run-prod-post-deploy-smoke.sh
```

If the post-deploy smoke passes, do not page on deploy-window `/alb-health` noise alone. Use [alb-health-502-runbook.md](./alb-health-502-runbook.md).

## ALB Target Unhealthy

First commands:

```bash
aws elbv2 describe-target-health \
  --region ap-northeast-2 \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:857721769929:targetgroup/youth-welfare-web-tg/5625712bc3af7438 \
  --query 'TargetHealthDescriptions[].{Target:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason,Description:TargetHealth.Description}' \
  --output table

docker ps --filter name=youth-welfare-app
curl -i -H 'Host: youthmoa.kr' http://127.0.0.1/alb-health
sudo tail -n 80 /var/log/nginx/error.log
sudo tail -n 120 /var/log/nginx/access.log
```

Interpretation:

| Signal | First decision |
| --- | --- |
| one target unhealthy, the other healthy | treat as instance-local drift; keep healthy target serving |
| both targets unhealthy | treat as app/nginx/shared dependency incident |
| local actuator `UP`, `/alb-health` fails | inspect nginx route/config |
| local actuator down | inspect app container and dependencies |

Immediate containment:

- If only one target is bad and traffic is still served, do not restart both nodes.
- If a recent deploy caused the issue, stop further deploy actions and keep the healthy target in service.
- If app health is down and watchdog has not recovered it, run one controlled restart on the affected node:

```bash
cd /home/ubuntu/youth-welfare
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml restart app
```

## Public API 5xx

First commands:

```bash
RUN_ALB_TARGET_HEALTH=true bash /home/ubuntu/youth-welfare/deploy/smoke/run-prod-post-deploy-smoke.sh
sudo tail -n 4000 /var/log/nginx/access.log | grep ' 5[0-9][0-9] ' | tail -n 50 || true
docker logs --since 10m youth-welfare-app 2>&1 | tail -n 200
```

Interpretation:

| Signal | First decision |
| --- | --- |
| only `/alb-health` 5xx, post-deploy smoke passes | deploy/restart-window noise |
| only non-GET/HEAD root probes such as `POST /` 501 | scanner noise; watch but do not treat as app outage |
| user path 5xx appears | user-facing incident |
| app log has DB connection errors | jump to DB section |
| app log has Redis connection errors | jump to Redis section |
| app log has application exception | keep traffic steady, collect stack/error prefix |

Immediate containment:

- Do not clear logs or prune containers.
- Do not restart both nodes unless both are failing.
- If one node is bad, verify the other target is healthy before touching it.

## DB Unavailable Or Degraded

First commands:

```bash
aws rds describe-db-instances \
  --region ap-northeast-2 \
  --db-instance-identifier youth-welfare-prod-db \
  --query 'DBInstances[0].{Status:DBInstanceStatus,MultiAZ:MultiAZ,LatestRestorableTime:LatestRestorableTime,BackupRetentionPeriod:BackupRetentionPeriod}' \
  --output table

docker logs --since 10m youth-welfare-app 2>&1 | grep -Ei 'postgres|jdbc|database|connection|timeout|refused|password' | tail -n 80 || true
```

If DB is reachable from the app host:

```bash
ENV_FILE=.env.production PRINT_SUMMARY=true bash /home/ubuntu/youth-welfare/deploy/postgres/verify-rds-runtime-privileges.sh
```

Interpretation:

| Signal | First decision |
| --- | --- |
| RDS status not `available` | AWS/RDS availability incident |
| app reports auth/permission errors | runtime credential or role drift |
| app reports connection timeout/refused | network/security group/RDS status issue |
| long query/lock symptoms only | do not restart DB; inspect DB audit next |

Immediate containment:

- Do not restore over the production DB.
- Do not run restore rehearsal during an active incident unless explicitly approved.
- If production DB is unavailable, keep user-facing diagnosis focused on RDS status and latest restorable time.

## Redis/Valkey Unavailable

First commands:

```bash
aws elasticache describe-replication-groups \
  --region ap-northeast-2 \
  --replication-group-id youth-welfare-prod-redis-valkey \
  --query 'ReplicationGroups[0].{Status:Status,AutomaticFailover:AutomaticFailover,MultiAZ:MultiAZ,SnapshotRetentionLimit:SnapshotRetentionLimit}' \
  --output table

aws cloudwatch get-metric-statistics \
  --region ap-northeast-2 \
  --namespace AWS/ElastiCache \
  --metric-name Evictions \
  --dimensions Name=CacheClusterId,Value=youth-welfare-prod-redis-valkey-001 Name=CacheNodeId,Value=0001 \
  --statistics Sum \
  --period 300 \
  --start-time "$(date -u -d '30 minutes ago' +%Y-%m-%dT%H:%M:%SZ)" \
  --end-time "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --output table

docker logs --since 10m youth-welfare-app 2>&1 | grep -Ei 'redis|valkey|lettuce|connection|timeout|refused' | tail -n 80 || true
```

Interpretation:

| Signal | First decision |
| --- | --- |
| Valkey not `available` | cache/session/rate-limit disruption |
| evictions > 0 | memory pressure or key churn |
| app only has transient Redis timeouts | monitor and keep RDS/app stable |
| auth/session failures | expect login/refresh disruption |

Immediate containment:

- Redis is not the canonical DB. Do not attempt DB restore for Redis-only failure.
- Do not flush Redis during incident triage.
- If Redis remains unavailable, document expected user impact: sessions, refresh tokens, rate limit, cache, locks.

## Disk 80%+

First commands:

```bash
df -h /
du -sh /home/ubuntu/youth-welfare/tmp/performance /home/ubuntu/youth-welfare/tmp/stability /home/ubuntu/.npm 2>/dev/null || true
docker system df
```

Safe dry-run:

```bash
cd /home/ubuntu/youth-welfare
DRY_RUN=true FORCE=true bash deploy/ops/cleanup-runtime-disk-artifacts.sh
```

Immediate containment:

```bash
DRY_RUN=false FORCE=true bash deploy/ops/cleanup-runtime-disk-artifacts.sh
```

Rules:

- Do not run `docker volume prune`.
- Do not delete `tmp/stability` unless a human explicitly decides the evidence is no longer needed.
- Recheck app and ALB health after cleanup.

## Communicate

Within five minutes, report only:

- start time in UTC
- affected layer: ALB/app/DB/Redis/disk/unknown
- user-facing impact: yes/no/unknown
- current ALB target health
- immediate action taken
- next checkpoint time
