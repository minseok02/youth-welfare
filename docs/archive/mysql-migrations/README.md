# Archived MySQL Migrations

This directory keeps pre-PostgreSQL migration drafts for historical reference only.

Current runtime database truth is PostgreSQL:

- fresh schema: `backend/src/main/resources/db/schema.sql`
- RDS/runtime drift patches: `deploy/postgres/patches/*.sql`
- local full stack DB: `docker-compose.yml` with `ALLOW_LOCAL_DOCKER_DB=true`
- production DB: RDS PostgreSQL via `docker-compose.prod.yml`

Files in this archive are not active runtime resources and must not be applied to PostgreSQL.
