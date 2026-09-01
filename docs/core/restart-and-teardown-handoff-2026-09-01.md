# Restart And Teardown Handoff 2026-09-01

## Purpose

This document fixes the exact state of the project before infrastructure teardown.

Use it when one of these is true:

- the current AWS resources will be deleted
- the service must be restarted later from preserved assets
- the repo is resumed after a long pause and you need the exact production-era settings quickly

## Snapshot On 2026-09-01

### Repository

- working repo: `/home/ubuntu/youth-welfare`
- branch: `refactor/admin-dashboard-sections`
- HEAD: `17f2676c`
- git remote: `git@github.com:minseok02/youth-welfare.git`
- working tree was **not clean**
  - modified/untracked files: `85` tracked changes plus `7` untracked files
  - broad change buckets:
    - admin dashboard/backend review surfaces
    - recommendation/chat/policy display and eligibility helpers
    - nginx/smoke script updates
    - final report documentation updates

### Live AWS State

Region: `ap-northeast-2`

- Route53 hosted zone: `youthmoa.kr` `/hostedzone/Z02264322U3S9LJ356G2Y`
- public domains: `youthmoa.kr`, `www.youthmoa.kr`
- ALB: `youth-welfare-alb`
- target group: `youth-welfare-web-tg`
- EC2 primary: `i-0b8d95e454df5e0f0` `youthmoa-prod-ec2` `running`
- EC2 secondary: `i-0e8a4cc599c1148c8` `youthmoa-prod-ec2-2` `stopped`
- RDS: `youth-welfare-prod-db` `stopped`
- ElastiCache Valkey: `youth-welfare-prod-redis-valkey` `available`
- SNS topic: `arn:aws:sns:ap-northeast-2:857721769929:youth-welfare-ops-alerts`
- IAM ops role: `arn:aws:iam::857721769929:role/youth-welfare-ops-monitor-v2-role`

### Public Service State

- `https://youthmoa.kr` and `https://www.youthmoa.kr` returned static frontend `200`
- `https://youthmoa.kr/api/policies/ranking` returned `502`
- ALB target health:
  - primary `i-0b8d95e454df5e0f0`: `unhealthy`, `502`
  - secondary `i-0e8a4cc599c1148c8`: `unused`, instance stopped

Reading:

- the public site shell is still being served by nginx
- the backed API is effectively down
- this is not a clean shutdown state; it is a partially alive suspended state

## Preserve Before Deleting Anything

### 1. Preserve The Dirty Repository First

Do not delete the instance before the current worktree is stored somewhere durable.

Minimum:

- push a commit containing the current worktree
- or archive the full repo directory including `.git`

Why:

- GitHub only has `17f2676c`
- local uncommitted work is materially ahead of that point

### 2. Preserve Runtime Configuration

Keep these files outside the instance:

- `/home/ubuntu/youth-welfare/.env`
- `/home/ubuntu/youth-welfare/.env.production`
- `/home/ubuntu/youth-welfare/.env.runtime.production`
- `/home/ubuntu/youth-welfare/.env.production.bak.20260627T133010Z`
- `/home/ubuntu/youth-welfare/.env.runtime.production.backup.20260617-124726`
- `/home/ubuntu/.config/youth-welfare/ops.env`
- `/etc/nginx/sites-available/youth-welfare`

These files contain or point to:

- PostgreSQL runtime accounts
- JWT and AES secrets
- OpenAI API key
- mail account settings
- public data API keys
- web push VAPID keys
- ops webhook or Healthchecks settings

Do not copy secrets into git. Preserve the files separately in encrypted storage.

### 3. Preserve Database Recovery Capability

At minimum keep one final manual RDS snapshot before deletion.

Known state on 2026-09-01:

- RDS identifier: `youth-welfare-prod-db`
- status: `stopped`
- engine: PostgreSQL `16.14`
- backup retention: `7` days
- latest restorable time observed: `2026-08-27T12:01:44Z`
- recent automated snapshots existed through `2026-08-26`

Recommended:

1. create a final manual snapshot
2. export a logical dump if you may need table-level restore or offline analysis later
3. store both in an encrypted location because the DB includes PII

Relevant schema areas include:

- `users`, `auth_users`, `user_profiles`, `user_consents`
- `youth_welfare_pii.user_pii`
- `support_inquiries`
- `notifications`, `user_alerts`, `web_push_subscriptions`
- `chat_sessions`, `chat_messages`, `chat_retrieval_snapshots`
- `user_recommendations`, `recommendation_logs`
- `raw_api_payloads`, `api_sync_logs`

### 4. Preserve Only The Artifacts You Actually Need

Candidate locations:

- `/var/log/youth-welfare`
- `/home/ubuntu/youth-welfare/tmp`
- `/home/ubuntu/youth-welfare/docs/final-report-assets`

Important:

- `/home/ubuntu/youth-welfare/tmp` was about `1.2G`
- `/var/log/youth-welfare` was about `7.9M`
- `tmp/` includes smoke artifacts such as `cookie.jar`, `login.json`, `signup.json`

So do not bulk-upload `tmp/` without review. Extract only the final evidence you need.

### 5. Preserve DNS And Certificate Context If The Domain Will Be Reused

The hosted zone still had:

- apex and `www` A alias records to the ALB
- ACM validation CNAMEs
- SES DKIM CNAMEs

If `youthmoa.kr` will be reused later, export the record inventory before deleting Route53 resources.

## Exact Settings Used During The Last Production Era

### Runtime Topology

- public path: `Route53 -> ALB -> EC2 -> nginx -> Spring Boot`
- backend port behind nginx: `127.0.0.1:8082`
- production compose file: `docker-compose.prod.elasticache.yml`
- local rollback/single-node compatibility compose file: `docker-compose.prod.yml`
- local dev compose file: `docker-compose.yml`

### Production Domain And CORS

- `APP_BASE_URL=https://youthmoa.kr`
- `SECURITY_CORS_ALLOWED_ORIGINS=https://youthmoa.kr,https://www.youthmoa.kr`
- `AUTH_REFRESH_COOKIE_SECURE=true`

### Scheduler Split

- primary or single-node production: `APP_SCHEDULER_ENABLED=true`
- secondary behind ALB: `APP_SCHEDULER_ENABLED=false`

### Database Layout

- main DB name: `youth_welfare`
- main engine family: PostgreSQL 16
- split schemas:
  - `public`
  - `youth_welfare_pii`
- split accounts:
  - `app_core_rw`
  - `app_pii_rw`
  - `notification_pii_ro`
  - `admin_dashboard_ro`
  - `migration_admin`
  - `recommendation_review_gate_command_rw`
  - `recommendation_persistence_command_rw`
  - `chat_session_cleanup_rw`
  - `cluster_ai_cleanup_rw`
  - `recommendation_retention_cleanup_rw`
  - `collect_execution_lock_cleanup_rw`
  - `web_push_subscription_cleanup_rw`

### Cache

- Redis/Valkey endpoint lived in `REDIS_HOST`
- production used ElastiCache Valkey
- local dev used Docker Redis

### External Integrations

- OpenAI API
- Gmail SMTP
- Youth API
- Public Data Portal API
- Web Push VAPID keys
- AWS CloudWatch, SNS, Route53 health check, ALB target health

## Fast Restart Paths

### Path A: Local-Only Restart

Use this when you only need development or report reproduction.

1. clone the repo
2. restore `.env` or rebuild it from `.env.example`
3. run `docker compose up -d` with `docker-compose.yml`
4. use the local PostgreSQL + Redis containers
5. run backend/frontend validation from the existing runbooks

Start here:

- [start.md](../start.md)
- [current-state.md](../current-state.md)
- [testing.md](./testing.md)

### Path B: Restart Using Existing AWS Resources

Use this only if the current AWS resources are kept.

1. start `youth-welfare-prod-db`
2. decide whether to keep ALB mode or single-node only
3. if ALB mode is needed, also start the secondary instance or remove it from the target-health expectation
4. restore `.env.production`, `.env.runtime.production`, and `/home/ubuntu/.config/youth-welfare/ops.env`
5. on the active node, render runtime env if needed:

```bash
SOURCE_ENV_FILE=.env.production \
TARGET_ENV_FILE=.env.runtime.production \
bash deploy/env/render-app-runtime-env.sh
```

6. start the app:

```bash
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d app
```

7. verify:

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

If the API still returns `502`, check:

- RDS is actually `available`
- app container boot logs
- nginx upstream config
- ALB target health

### Path C: Full Rebuild After Deleting AWS Resources

Use this when everything is gone and you want the closest reproduction of the previous setup.

1. recreate Route53 hosted zone or reuse the domain
2. recreate ACM cert for `youthmoa.kr` and `www.youthmoa.kr`
3. recreate EC2
4. recreate RDS PostgreSQL
5. recreate ElastiCache Valkey if you want the production-era topology
6. restore `.env.production`
7. bootstrap DB:

```bash
ENV_FILE=.env.production bash deploy/postgres/bootstrap-rds-runtime.sh
ENV_FILE=.env.production bash deploy/postgres/verify-rds-runtime-privileges.sh
```

8. preflight and deploy:

```bash
ENV_FILE=.env.production \
COMPOSE_FILE=docker-compose.prod.elasticache.yml \
PRINT_SUMMARY=true \
bash deploy/smoke/preflight-runtime-cutover-env.sh

docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d app
```

9. apply nginx config and verify edge:

```bash
PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/nginx/verify-edge-baseline.sh
```

Start with these docs:

- [deployment.md](../deployment.md)
- [final-production-operations-runbook-2026-07-16.md](./final-production-operations-runbook-2026-07-16.md)
- [db-backup-restore-rehearsal-runbook.md](./db-backup-restore-rehearsal-runbook.md)
- [openai-runtime-contract.md](./openai-runtime-contract.md)

## Recommended Git Preservation Move Before Teardown

For this repository state, the cleanest move is:

1. add the restart/teardown handoff documentation
2. commit the current dirty tree as a snapshot before infra teardown
3. push the branch

Use a message that makes the intent obvious, for example:

```text
Snapshot suspended runtime state before infra teardown
```

This is intentionally a preservation commit, not a claim that the production runtime is healthy on 2026-09-01.
