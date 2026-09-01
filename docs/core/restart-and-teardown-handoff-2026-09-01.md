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
- preserved snapshot commit: `338bcd86`
- git remote: `git@github.com:minseok02/youth-welfare.git`
- preservation note:
  - before this handoff was written, the observed local state was `17f2676c` plus `85` tracked changes and `7` untracked files
  - that suspended state was preserved and pushed as commit `338bcd86`
  - current branch can now be rebuilt from git without depending on the old dirty worktree

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

### Exact AWS Resource Shape

#### EC2

- EC2-1:
  - instance id: `i-0b8d95e454df5e0f0`
  - name: `youthmoa-prod-ec2`
  - state at audit time: `running`
  - instance type: `t3.medium`
  - AMI: `ami-0765f9741eedf9c7b`
  - VPC: `vpc-093662d691f85e844`
  - subnet: `subnet-018e1db2c13f63c3c`
  - AZ: `ap-northeast-2b`
  - private IP: `172.31.25.51`
  - key pair: `youthmoa-new-key`
  - instance profile: `youth-welfare-ops-monitor-v2-role`
  - IMDS: `required`, hop limit `2`
  - root volume: `20GiB gp3`, `3000 IOPS`, `encrypted=false`, `DeleteOnTermination=true`
  - security group: `sg-01b664b3af6ad95a2` `youthmoa-ec2-sg`
- EC2-2:
  - instance id: `i-0e8a4cc599c1148c8`
  - name: `youthmoa-prod-ec2-2`
  - state at audit time: `stopped`
  - instance type: `t3.medium`
  - AMI: `ami-0e4ab31f1847c850c`
  - VPC: `vpc-093662d691f85e844`
  - subnet: `subnet-0c7127a0767ebc2e9`
  - AZ: `ap-northeast-2c`
  - key pair: `youthmoa-new-key`
  - instance profile: `youth-welfare-ops-monitor-v2-role`
  - IMDS: `required`, hop limit `2`
  - security group: `sg-01b664b3af6ad95a2` `youthmoa-ec2-sg`

#### Security Groups

- EC2 SG `sg-01b664b3af6ad95a2` `youthmoa-ec2-sg`
  - inbound `80/tcp` from ALB SG `sg-06ac84b1d37d48409`
  - inbound `22/tcp` from `222.118.115.90/32`
  - inbound `22/tcp` from prefix list `pl-00ec8fd779e5b4175`
- ALB SG `sg-06ac84b1d37d48409` `youth-welfare-alb-sg`
  - inbound `80/tcp` from `0.0.0.0/0`
  - inbound `443/tcp` from `0.0.0.0/0`
- RDS SG `sg-036bbd2c8c0ac2bef` `youthmoa-rds-sg`
  - inbound `5432/tcp` from EC2 SG `sg-01b664b3af6ad95a2`
- Redis SG `sg-08a9bcba60cfd16b0` `youthmoa-redis-sg`
  - inbound `6379/tcp` from EC2 SG `sg-01b664b3af6ad95a2`

#### ALB

- name: `youth-welfare-alb`
- scheme: `internet-facing`
- type: `application`
- VPC: `vpc-093662d691f85e844`
- subnets:
  - `subnet-018e1db2c13f63c3c`
  - `subnet-0c7127a0767ebc2e9`
- security group: `sg-06ac84b1d37d48409`
- listener `80/HTTP`: redirect to `443/HTTPS`
- listener `443/HTTPS`: forward to target group `youth-welfare-web-tg`
- target group:
  - ARN suffix: `5625712bc3af7438`
  - protocol: `HTTP`
  - port: `80`
  - target type: `instance`
  - health check path: `/alb-health`
  - health check matcher: `200`
- certificate in use by listener `443`:
  - ARN: `arn:aws:acm:ap-northeast-2:857721769929:certificate/9c6cae15-619a-47b7-9e23-e540434dd004`
  - note: `acm:DescribeCertificate` permission was not available to the ops role during this audit

#### RDS

- identifier: `youth-welfare-prod-db`
- engine: `postgres`
- engine version: `16.14`
- class: `db.t3.small`
- storage: `20GiB gp3`
- public: `false`
- encrypted: `true`
- Multi-AZ: `false`
- deletion protection: `true`
- master username: `masteradmin`
- endpoint: `youth-welfare-prod-db.cnqsmges40i1.ap-northeast-2.rds.amazonaws.com`
- subnet group: `default-vpc-093662d691f85e844`
- VPC security group: `sg-036bbd2c8c0ac2bef`

#### ElastiCache Valkey

- replication group: `youth-welfare-prod-redis-valkey`
- member cluster: `youth-welfare-prod-redis-valkey-001`
- engine: `valkey`
- engine version: `9.1.0`
- node type: `cache.t4g.micro`
- primary endpoint: `youth-welfare-prod-redis-valkey.xcwfaw.ng.0001.apn2.cache.amazonaws.com`
- node endpoint: `youth-welfare-prod-redis-valkey-001.xcwfaw.0001.apn2.cache.amazonaws.com`
- security group: `sg-08a9bcba60cfd16b0`
- subnet group: `youth-welfare-prod-redis-valkey-subnet-group`
- preferred AZ: `ap-northeast-2a`
- Multi-AZ: `disabled`
- automatic failover: `disabled`
- snapshot retention: `0`

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

### 1. Preserve The Git Snapshot First

Do not delete the instance unless the repo state at or after the preserved snapshot is stored somewhere durable.

Minimum:

- keep the pushed branch containing commit `338bcd86`
- or archive the full repo directory including `.git`

Why:

- the previously suspended dirty worktree is now represented by commit `338bcd86`
- deleting the instance is no longer blocked on recovering uncommitted local work, but it is still blocked on preserving the repo itself if the remote is not trusted as the only copy

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

### 2-1. Preserve The Frontend And Edge Layout

Keep these path conventions because the current nginx config expects them:

- frontend static root: `/var/www/youth-welfare/frontend`
- ACME challenge root: `/var/www/certbot`
- nginx site file: `/etc/nginx/sites-available/youth-welfare`
- nginx enabled symlink: `/etc/nginx/sites-enabled/youth-welfare`
- direct host TLS cert path currently in use:
  - `/etc/letsencrypt/live/youthmoa.kr/fullchain.pem`
  - `/etc/letsencrypt/live/youthmoa.kr/privkey.pem`

If you rebuild the same host-style edge, the frontend deploy step is:

```bash
cd /home/ubuntu/youth-welfare/frontend
npm ci
npm run build
sudo rsync -a --delete dist/ /var/www/youth-welfare/frontend/
sudo find /var/www/youth-welfare/frontend -type d -exec chmod 755 {} +
sudo find /var/www/youth-welfare/frontend -type f -exec chmod 644 {} +
```

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

### Current Runtime Toolchain On EC2-1

- OS family: Ubuntu `24.04`
- Docker: `29.1.3`
- Docker Compose: `2.40.3`
- nginx: `1.24.0`
- Java: `17.0.20`
- Node.js: `v24.15.0`
- npm: `11.12.1`

If you rebuild a new EC2 and want the least surprise, staying close to this toolchain is the conservative move.

### Runtime Env Coverage Audit

`env.production.example` is close, but restart should not rely on the template alone unless the preserved env files are gone.

During the 2026-09-01 audit, active runtime keys also included:

- `SECURITY_ADMIN_EMAILS`
- `RECOMMEND_RATE_LIMIT_PERSONAL_REFRESH_MAX_REQUESTS`
- `WEB_PUSH_PUBLIC_KEY`
- `WEB_PUSH_PRIVATE_KEY`
- `WEB_PUSH_SUBJECT`
- `WEB_PUSH_MAX_SUBSCRIPTIONS_PER_USER`
- `WEB_PUSH_SEND_TIMEOUT_SECONDS`
- `POLICY_RANKING_CANDIDATE_ENABLED`
- `POLICY_RANKING_SNAPSHOT_READ_ENABLED`
- `POLICY_RANKING_SNAPSHOT_REFRESH_ENABLED`
- `POLICY_RANKING_SNAPSHOT_MAX_SIZE`
- `POLICY_RANKING_SNAPSHOT_TTL_SECONDS`
- `POLICY_RANKING_SNAPSHOT_REFRESH_FIXED_DELAY_MS`
- `POLICY_RANKING_SNAPSHOT_REFRESH_INITIAL_DELAY_MS`

Most ranking flags have code defaults, but `SECURITY_ADMIN_EMAILS` and the web push keys are operationally important. Preserve the real env files first; use the template only as fallback.

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

7. if nginx or frontend was lost on the host, restore it:

```bash
cd /home/ubuntu/youth-welfare/frontend
npm ci
npm run build
sudo rsync -a --delete dist/ /var/www/youth-welfare/frontend/
sudo cp /home/ubuntu/youth-welfare/deploy/nginx/youth-welfare.conf /etc/nginx/sites-available/youth-welfare
sudo ln -sfn /etc/nginx/sites-available/youth-welfare /etc/nginx/sites-enabled/youth-welfare
sudo nginx -t
sudo systemctl reload nginx
```

8. verify:

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/nginx/verify-edge-baseline.sh
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

If the API still returns `502`, check:

- RDS is actually `available`
- app container boot logs
- nginx upstream config
- ALB target health

### Path C: Full Rebuild After Deleting AWS Resources

Use this when everything is gone and you want the closest reproduction of the previous setup.

1. recreate or keep the domain and hosted zone for `youthmoa.kr`
2. request a new ACM certificate for `youthmoa.kr` and `www.youthmoa.kr`
3. recreate ALB in `vpc-093662d691f85e844` with subnets:
   - `subnet-018e1db2c13f63c3c`
   - `subnet-0c7127a0767ebc2e9`
4. recreate EC2 primary with the closest known shape:
   - `t3.medium`
   - Ubuntu 24.04
   - AMI-compatible x86_64 image
   - subnet `subnet-018e1db2c13f63c3c`
   - SG `youthmoa-ec2-sg` equivalent
   - instance profile `youth-welfare-ops-monitor-v2-role` equivalent
   - root volume `20GiB gp3`, `3000 IOPS`, `encrypted=false`
5. recreate EC2 secondary only if you want the former HA topology:
   - `t3.medium`
   - subnet `subnet-0c7127a0767ebc2e9`
   - same SG and instance profile
   - `APP_SCHEDULER_ENABLED=false`
6. recreate RDS PostgreSQL with the closest known shape:
   - identifier `youth-welfare-prod-db`
   - engine `postgres 16`
   - class `db.t3.small`
   - storage `20GiB gp3`
   - public access `false`
   - encrypted `true`
   - backup retention `7`
   - deletion protection `true`
   - DB name `youth_welfare`
   - SG allowing `5432` only from the EC2 SG
7. recreate ElastiCache Valkey only if you want the former production topology:
   - replication group `youth-welfare-prod-redis-valkey`
   - node type `cache.t4g.micro`
   - SG allowing `6379` only from the EC2 SG
   - snapshot retention `0`
8. on the EC2 host install the base tools:

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-v2 nginx git curl jq postgresql-client
```

9. restore `.env.production`, `.env.runtime.production`, `/home/ubuntu/.config/youth-welfare/ops.env`, and nginx config if preserved
10. if preserved env files are unavailable, rebuild them from `env.production.example` plus the preserved key inventory and secret store
11. bootstrap DB:

```bash
ENV_FILE=.env.production bash deploy/postgres/bootstrap-rds-runtime.sh
ENV_FILE=.env.production bash deploy/postgres/verify-rds-runtime-privileges.sh
```

12. preflight and deploy:

```bash
ENV_FILE=.env.production \
COMPOSE_FILE=docker-compose.prod.elasticache.yml \
PRINT_SUMMARY=true \
bash deploy/smoke/preflight-runtime-cutover-env.sh

docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d app
```

13. build and publish the frontend bundle:

```bash
cd /home/ubuntu/youth-welfare/frontend
npm ci
npm run build
sudo mkdir -p /var/www/youth-welfare/frontend /var/www/certbot
sudo rsync -a --delete dist/ /var/www/youth-welfare/frontend/
sudo find /var/www/youth-welfare/frontend -type d -exec chmod 755 {} +
sudo find /var/www/youth-welfare/frontend -type f -exec chmod 644 {} +
```

14. apply nginx config and verify edge:

```bash
sudo cp /home/ubuntu/youth-welfare/deploy/nginx/youth-welfare.conf /etc/nginx/sites-available/youth-welfare
sudo ln -sfn /etc/nginx/sites-available/youth-welfare /etc/nginx/sites-enabled/youth-welfare
sudo nginx -t
sudo systemctl reload nginx

PUBLIC_BASE_URL='https://youthmoa.kr' \
bash deploy/nginx/verify-edge-baseline.sh
```

15. if ALB mode is used, recreate the target group with:
   - protocol `HTTP`
   - port `80`
   - target type `instance`
   - health check path `/alb-health`
   - matcher `200`
16. point Route53 apex and `www` alias records to the ALB
17. finish with:

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

## Audit Result On 2026-09-01

After the second pass, restart is practical **if and only if** these are preserved:

1. git repo at or after `338bcd86`
2. `.env.production` and `.env.runtime.production`
3. final RDS snapshot or dump
4. nginx site file and frontend deployment path convention
5. Route53/ALB/RDS/Redis exact shape above

The main gaps found during audit were:

- the first handoff document was stale after the preservation commit
- `env.production.example` did not cover several active runtime keys
- the earlier handoff summary did not include SG IDs, subnets, target-group health check, frontend root path, or host TLS paths

Those gaps were fixed in this document and in `env.production.example`.

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
