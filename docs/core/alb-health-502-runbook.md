# ALB Health 502 Runbook

작성 기준일: 2026-07-16

## Purpose

Separate harmless ALB health-check noise from real user-facing 5xx incidents.

The 2026-07-16 stability check found sampled `502` lines in nginx, but the sampled lines were `GET /alb-health` from `ELB-HealthChecker/2.0` during deploy/restart windows. Both ALB targets were healthy after the windows, and no sampled user API path returned `502`.

## Read The Signal

Treat as deploy/restart noise only when all of these are true:

| Condition | Expected value |
| --- | --- |
| path | `/alb-health` |
| user agent | `ELB-HealthChecker/2.0` |
| timing | inside an app deploy/restart window |
| ALB target health after window | all expected targets `healthy` |
| user API/page 5xx in same window | `0` or explained separately |

Treat as incident when any of these is true:

| Signal | Meaning |
| --- | --- |
| user path 5xx exists | user-facing outage or partial failure |
| `/alb-health` 5xx continues outside deploy window | app/nginx/target path is unhealthy |
| ALB target remains `unhealthy` or `draining` unexpectedly | target group issue |
| one target unhealthy while the other is healthy | instance-local drift |
| public API smoke fails | edge path issue even if local app health is up |

## Commands

ALB target health:

```bash
aws elbv2 describe-target-health \
  --region ap-northeast-2 \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:857721769929:targetgroup/youth-welfare-web-tg/5625712bc3af7438 \
  --query 'TargetHealthDescriptions[].{Target:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason}' \
  --output table
```

Recent nginx 5xx split:

```bash
tail -n 4000 /var/log/nginx/access.log \
  | awk 'match($0, /"[^"]+" [0-9][0-9][0-9] /) { print }' \
  | grep ' 5[0-9][0-9] ' || true
```

Post-deploy bounded smoke:

```bash
bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

## Decision

After deploy:

1. Run `run-prod-post-deploy-smoke.sh`.
2. If it passes, `/alb-health` 502 lines from the deploy/restart window are not an incident.
3. If it fails on user-path 5xx, target health, or public read-only API, stop further deploy work and inspect app/nginx logs first.

## Notes

- Do not hide `/alb-health` 502 from raw logs. Keep it visible, but classify it correctly.
- Do not page on a short `/alb-health` 502 burst by itself when both targets return to healthy.
- Page or investigate when `/alb-health` 502 repeats outside a deploy/restart window or coincides with user-path 5xx.
