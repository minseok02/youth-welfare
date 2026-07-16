# Runtime Disk Cleanup Runbook

작성 기준일: 2026-07-16

## Purpose

Keep the 20 GiB EC2 root volumes from filling up during repeated deploy, measurement, and smoke-test cycles.

This runbook exists because the 2026-07-16 stability check found the primary root disk at `85%` used. The largest reclaimable items were old `tmp/performance` artifacts, npm cache, and Docker build cache.

## Policy

Default cleanup targets:

| Target | Default action | Reason |
| --- | --- | --- |
| `${ROOT_DIR}/tmp/performance` | delete children older than `2` days | measurement artifacts grow quickly and are reproducible |
| `${ROOT_DIR}/tmp/stability` | not cleaned by default | current stability artifacts are operational evidence |
| `${HOME}/.npm` | `npm cache clean --force` | cache is reproducible and reached multiple GiB |
| Docker builder cache | prune entries older than `168h` | build cache is reproducible |
| Docker volumes | never cleaned by this script | volumes may contain stateful local data |
| running images/containers | not targeted | app runtime must stay untouched |

The script skips cleanup unless root disk usage is at or above `70%`, unless `FORCE=true` is provided.

## Manual Commands

Dry-run:

```bash
DRY_RUN=true FORCE=true bash deploy/ops/cleanup-runtime-disk-artifacts.sh
```

Real run:

```bash
DRY_RUN=false FORCE=true bash deploy/ops/cleanup-runtime-disk-artifacts.sh
```

Threshold-gated real run:

```bash
DRY_RUN=false RUN_WHEN_USED_PERCENT_GE=70 bash deploy/ops/cleanup-runtime-disk-artifacts.sh
```

Install or preview the weekly cron:

```bash
PRINT_ONLY=true bash deploy/ops/install-runtime-disk-cleanup-cron.sh
bash deploy/ops/install-runtime-disk-cleanup-cron.sh
```

Cron default:

- schedule: `20 4 * * 0`
- log: `${ROOT_DIR}/tmp/ops/runtime-disk-cleanup/cleanup-cron.log`
- lock: `${ROOT_DIR}/tmp/ops/runtime-disk-cleanup/cleanup.lock`
- cleanup threshold: `70%`

## Verification

Before and after a real cleanup, check:

```bash
df -h /
du -sh /home/ubuntu/youth-welfare/tmp/performance /home/ubuntu/.npm 2>/dev/null || true
docker system df
docker ps --filter name=youth-welfare-app --format '{{.Names}} {{.Status}}'
curl -fsS http://127.0.0.1:8082/actuator/health
```

For ALB-facing checks:

```bash
aws elbv2 describe-target-health \
  --region ap-northeast-2 \
  --target-group-arn arn:aws:elasticloadbalancing:ap-northeast-2:857721769929:targetgroup/youth-welfare-web-tg/5625712bc3af7438 \
  --query 'TargetHealthDescriptions[].{Target:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason}' \
  --output table
```

## Rollback

There is no data rollback for deleted cache/artifact files. The cleanup is intentionally limited to reproducible artifacts and caches.

If the cron must be removed:

```bash
crontab -l | sed '/# >>> youth-welfare runtime disk cleanup >>>/,/# <<< youth-welfare runtime disk cleanup <<</d' | crontab -
```

## Notes

- Do not add `docker volume prune` to this script.
- Do not clean `${ROOT_DIR}/tmp/stability` by default; stability artifacts should stay until a human decides they are no longer useful.
- First deploy/build after cache cleanup can be slower because Docker and npm caches may need to be rebuilt.
