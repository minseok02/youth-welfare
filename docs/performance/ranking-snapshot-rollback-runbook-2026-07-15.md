# Ranking Snapshot Rollback Runbook 2026-07-15

## Purpose

This runbook disables the Redis precomputed ranking snapshot if `/api/policies/ranking?size=20` shows incorrect results, elevated errors, or sustained latency regression.

The code default remains safe: when snapshot read is disabled, ranking falls back to the full request-time ranking path.

## Current Normal State

| Node | Instance | Scheduler | Snapshot read | Snapshot refresh |
| --- | --- | --- | --- | --- |
| primary | `i-0b8d95e454df5e0f0` | true | true | true |
| secondary | `i-0e8a4cc599c1148c8` | false | true | false |

The request-time candidate mode must stay disabled:

```env
POLICY_RANKING_CANDIDATE_ENABLED=false
```

## Fast Rollback

Use this when ranking snapshot read should stop immediately but the app can keep serving.

On both nodes, set:

```env
POLICY_RANKING_CANDIDATE_ENABLED=false
POLICY_RANKING_SNAPSHOT_READ_ENABLED=false
POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=false
POLICY_RANKING_SNAPSHOT_MAX_SIZE=20
POLICY_RANKING_SNAPSHOT_TTL_SECONDS=300
POLICY_RANKING_SNAPSHOT_REFRESH_FIXED_DELAY_MS=300000
POLICY_RANKING_SNAPSHOT_REFRESH_INITIAL_DELAY_MS=1000
```

Then restart the app service:

```bash
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d --force-recreate app
```

Wait for health:

```bash
for i in $(seq 1 40); do
  h="$(docker inspect -f '{{.State.Health.Status}}' youth-welfare-app 2>/dev/null || true)"
  code="$(curl -fsS -o /dev/null -w '%{http_code}' http://127.0.0.1:8082/actuator/health || true)"
  echo "health_attempt=$i container=$h http=$code"
  [ "$h" = healthy ] && [ "$code" = 200 ] && break
  sleep 5
done
```

## Backup Restore Rollback

Use this if the desired state is exactly the pre-formalization `.env.runtime.production`.

Backups created during formalization:

- primary: `.env.runtime.production.bak-snapshot-20260715T160904Z`
- secondary: `.env.runtime.production.bak-snapshot-20260715T160805Z`

On each node:

```bash
cp .env.runtime.production.bak-snapshot-<timestamp> .env.runtime.production
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d --force-recreate app
```

## Verification

After rollback:

```bash
docker exec youth-welfare-app /bin/sh -c 'printf "candidate=%s\nread=%s\nrefresh=%s\n" "$POLICY_RANKING_CANDIDATE_ENABLED" "$POLICY_RANKING_SNAPSHOT_READ_ENABLED" "$POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED"'
curl -fsS http://127.0.0.1:8082/actuator/health
curl -k -fsS -o /dev/null -w 'ranking_status=%{http_code} time=%{time_total}\n' 'https://youthmoa.kr/api/policies/ranking?size=20'
```

Confirm ALB target health:

```bash
aws elbv2 describe-target-health \
  --region ap-northeast-2 \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:857721769929:targetgroup/youth-welfare-web-tg/5625712bc3af7438 \
  --query 'TargetHealthDescriptions[].{id:Target.Id,state:TargetHealth.State,reason:TargetHealth.Reason}' \
  --output table
```

Expected:

- both targets `healthy`
- ranking endpoint returns `200`
- ranking latency may return to the old full-ranking cold profile

## Re-Enable

Re-enable only after correctness is confirmed against a current full-ranking response.

Primary:

```env
POLICY_RANKING_CANDIDATE_ENABLED=false
POLICY_RANKING_SNAPSHOT_READ_ENABLED=true
POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=true
POLICY_RANKING_SNAPSHOT_MAX_SIZE=20
```

Secondary:

```env
POLICY_RANKING_CANDIDATE_ENABLED=false
POLICY_RANKING_SNAPSHOT_READ_ENABLED=true
POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED=false
POLICY_RANKING_SNAPSHOT_MAX_SIZE=20
```

The secondary must keep scheduler and snapshot refresh disabled to avoid duplicate scheduled work.

