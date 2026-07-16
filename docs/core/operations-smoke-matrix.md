# Operations Smoke Matrix

작성 기준일: 2026-07-16

## Purpose

Choose the right smoke command without guessing after deploys, restarts, ALB changes, or operational checks.

## Matrix

| Situation | First command | Mutates data | Expected duration | Reads |
| --- | --- | --- | ---: | --- |
| app deploy/rebuild, nginx reload, ALB target change | `RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh` | no | seconds | local actuator, ALB target health, public list/search/ranking, nginx user-path 5xx |
| instance/RDS/Redis restart sanity check | `bash deploy/smoke/run-prod-post-deploy-smoke.sh` | no | seconds | same as post-deploy, ALB target health runs when AWS CLI is available |
| auth/session/runtime contract verification | `bash deploy/smoke/run-prod-runtime-smoke-suite.sh` | yes, bounded smoke user | minutes | cutover preflight, runtime API smoke, auth observation |
| daily operator read-only baseline | `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh` | no | minutes | admin dashboard, collect failures, recommendation breakdowns |
| nightly handoff | `bash deploy/smoke/run-nightly-ops-handoff.sh` | bounded according to child smoke | longer | broad operational handoff artifacts |
| alert/log threshold check | `bash deploy/ops/send-log-alert.sh` | no | seconds/minutes | app/nginx log alert evaluator |

## Default Order After Deploy

1. `RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh`
2. If step 1 passes, do not treat short deploy-window `/alb-health` 502 as an incident.
3. If auth/session behavior changed, run `bash deploy/smoke/run-prod-runtime-smoke-suite.sh`.
4. If operational dashboard/read models changed, run the ops observation suite.

## Node Roles

| Node | Scheduler | Watchdog | Runtime disk cleanup | Smoke execution |
| --- | --- | --- | --- | --- |
| primary | enabled | enabled | enabled | full, including AWS ALB target health |
| secondary | disabled | enabled | enabled | local/public/nginx smoke; ALB target health may be skipped if AWS CLI is unavailable |

## Notes

- Use `docker-compose.prod.elasticache.yml` for the current production app compose file.
- `run-prod-post-deploy-smoke.sh` is read-only and safe to run repeatedly.
- `run-prod-runtime-smoke-suite.sh` creates bounded smoke data and should be used when stateful contracts matter.
- Nightly handoff is broader than post-deploy smoke; do not use it as the fast deploy gate.
