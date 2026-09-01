# 시스템 문서 묶음

## 목적

cross-cutting 문서가 흩어져 있어도

- 프로젝트 기본 메타 정보
- 구조와 API contract
- 데이터/마이그레이션 설계
- 후속 확장 설계 메모

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 기본 구조와 계약

- [production-ops-quickstart.md](./production-ops-quickstart.md)
- [restart-and-teardown-handoff-2026-09-01.md](./restart-and-teardown-handoff-2026-09-01.md)
- [infra-teardown-execution-checklist-2026-09-01.md](./infra-teardown-execution-checklist-2026-09-01.md)
- [stabilization-checklist.md](./stabilization-checklist.md)
- [final-ops-closeout-checklist.md](./final-ops-closeout-checklist.md)
- [final-production-operations-runbook-2026-07-16.md](./final-production-operations-runbook-2026-07-16.md)
- [security-hardening-current-state.md](./security-hardening-current-state.md)
- [local-validation-docs-index.md](./local-validation-docs-index.md)
- [testing.md](./testing.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [server-runtime-drift-checklist.md](./server-runtime-drift-checklist.md)
- [uptime-monitoring-runbook.md](./uptime-monitoring-runbook.md)
- [alb-health-502-runbook.md](./alb-health-502-runbook.md)
- [post-deploy-smoke-runbook.md](./post-deploy-smoke-runbook.md)
- [operations-smoke-matrix.md](./operations-smoke-matrix.md)
- [ops-readiness-check-2026-07-16.md](./ops-readiness-check-2026-07-16.md)
- [no-cost-ops-check-2026-07-16.md](./no-cost-ops-check-2026-07-16.md)
- [data-layer-risk-and-backup-check-2026-07-16.md](./data-layer-risk-and-backup-check-2026-07-16.md)
- [incident-first-five-minutes-runbook.md](./incident-first-five-minutes-runbook.md)
- [cost-scaling-decision-table.md](./cost-scaling-decision-table.md)
- [restore-rehearsal-prep-checklist.md](./restore-rehearsal-prep-checklist.md)
- [alb-multi-ec2-rollout-plan.md](./alb-multi-ec2-rollout-plan.md)
- [alb-demo-switch-runbook.md](./alb-demo-switch-runbook.md)
- [alb-route53-cutover-2026-07-13.md](./alb-route53-cutover-2026-07-13.md)
- [log-alert-thresholds.md](./log-alert-thresholds.md)
- [runtime-disk-cleanup-runbook.md](./runtime-disk-cleanup-runbook.md)
- [admin-dashboard-alert-surface-contract.md](./admin-dashboard-alert-surface-contract.md)
- [project-spec.md](../project-spec.md)
- [final-report-submission-checklist-2026-07-16.md](../final-report-submission-checklist-2026-07-16.md)
- [architecture.md](../architecture.md)
- [api-mapping.md](./api-mapping.md)

### 데이터/마이그레이션

- [db-migration.md](./db-migration.md)
- [db-backup-restore-rehearsal-runbook.md](./db-backup-restore-rehearsal-runbook.md)
- [user-data-separation-design.md](./user-data-separation-design.md)
- [pii-key-rotation-runbook.md](./pii-key-rotation-runbook.md)

### 후속 확장 메모

- [chatbot-plan.md](./chatbot-plan.md)
- [openai-runtime-contract.md](./openai-runtime-contract.md)
- [ops-observation-runbook.md](./ops-observation-runbook.md)
- [ops-baseline-runbook.md](./ops-baseline-runbook.md)
- [nightly-ops-handoff-cron-runbook.md](./nightly-ops-handoff-cron-runbook.md)
- [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
- [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)
- [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md)
- [notification-backlog-sample-audit-runbook.md](./notification-backlog-sample-audit-runbook.md)
- [notification-stale-target-audit-runbook.md](./notification-stale-target-audit-runbook.md)
  - stale unread cluster는 `hide-stale` admin maintenance 경로와 같이 읽는다.

## 문서 역할

### 1. 프로젝트 기본 메타

- [stabilization-checklist.md](./stabilization-checklist.md)
- [final-ops-closeout-checklist.md](./final-ops-closeout-checklist.md)
- [security-hardening-current-state.md](./security-hardening-current-state.md)
- [local-validation-docs-index.md](./local-validation-docs-index.md)
- [project-spec.md](../project-spec.md)

`stabilization-checklist.md` 는 기능 freeze 이후 CI/nightly/attention 실패만 처리하는 안정화 기준입니다.

`production-ops-quickstart.md` 는 평소 운영자가 먼저 볼 짧은 진입점입니다. smoke, 장애 첫 5분, 백업/확장 판단 문서로 이어지는 상위 요약입니다.

`restart-and-teardown-handoff-2026-09-01.md` 는 teardown 직전의 실제 AWS 상태, dirty worktree, 보존 대상 파일, 재시작 순서를 고정한 snapshot 문서입니다. 인프라 삭제 전과 장기 중단 후 재개 시에는 이 문서를 먼저 봅니다.

`infra-teardown-execution-checklist-2026-09-01.md` 는 실제 삭제 전에 무엇을 백업하고 어떤 순서로 AWS 자원을 내릴지, 삭제 후 어떤 값을 남겨야 하는지에 집중한 실행 체크리스트입니다.

`final-production-operations-runbook-2026-07-16.md` 는 최종 제출/인수 기준 운영 상태, AWS 콘솔 확인 위치, IAM/Route53/ALB 기대값, 비용/위험 경계, 주요 점검 명령을 한 문서에 모은 handoff runbook입니다.

`final-ops-closeout-checklist.md` 는 안정화 작업을 PR/배포/운영 handoff 전에 어떤 명령 순서로 닫을지 고정합니다.

`uptime-monitoring-runbook.md` 는 Healthchecks.io ping, 서버 내부 watchdog, AWS Route53/CloudWatch 알람을 운영 서버에 붙이는 순서를 정리합니다.

`alb-health-502-runbook.md` 는 `/alb-health` 502가 배포/재시작 창의 health-check noise인지 사용자 영향 5xx인지 판단하는 기준을 고정합니다.

`post-deploy-smoke-runbook.md` 는 배포 직후 read-only public API, ALB target health, local actuator, nginx 5xx를 짧게 확인하는 절차를 고정합니다.

`operations-smoke-matrix.md` 는 post-deploy smoke, runtime smoke, ops observation, nightly handoff를 언제 쓰는지 고정합니다.

`ops-readiness-check-2026-07-16.md` 는 실제 AWS 알람/SNS/Route53 상태와 primary/secondary drift 점검 결과를 기록합니다.

`no-cost-ops-check-2026-07-16.md` 는 비용 증가 없이 수행한 cron, compose default, log alert, DB/Redis, edge/header, IAM drift 점검 결과를 기록합니다.

`data-layer-risk-and-backup-check-2026-07-16.md` 는 RDS/Valkey 단일 구성 리스크, 백업 설정, snapshot inventory 권한 gap, SNS test publish 결과를 기록합니다.

`incident-first-five-minutes-runbook.md` 는 ALB/app/DB/Redis/disk 장애 첫 5분 안에 볼 명령과 즉시 판단 기준을 고정합니다.

`cost-scaling-decision-table.md` 는 EC2 1대/2대, RDS Multi-AZ, Valkey failover/snapshot, restore rehearsal 같은 비용 증가 결정을 언제 할지 고정합니다.

`restore-rehearsal-prep-checklist.md` 는 비용이 생기는 RDS restore rehearsal을 승인 전에 준비만 해두는 체크리스트입니다.

`alb-multi-ec2-rollout-plan.md` 는 현재 EC2 1대 + RDS 구조를 유지한 채 EC2 web 노드를 2대로 늘리고 ALB/공유 Redis/scheduler 단일 실행 경계를 붙이는 전환 계획입니다.

`alb-demo-switch-runbook.md` 는 평소 1대 운영과 시연용 ALB 2대 운영을 Route53 레코드 전환으로 오가는 실제 실행 절차입니다.

`alb-route53-cutover-2026-07-13.md` 는 2026-07-13에 수행한 신규 EC2 연결, ALB/ACM/Route53 전환, frontend asset 불일치 장애와 해결 기록입니다.

`log-alert-thresholds.md` 는 app/nginx 로그와 운영 dashboard 지표의 warning/critical 기준을 고정합니다.

`runtime-disk-cleanup-runbook.md` 는 20 GiB EC2 root disk에서 성능 측정 산출물, npm cache, Docker builder cache가 쌓였을 때의 정리 정책과 cron 설치 절차를 고정합니다.

`admin-dashboard-alert-surface-contract.md` 는 운영 alert evaluator가 판정 권위이고 admin dashboard는 raw triage surface라는 경계를 고정합니다.

`project-spec.md` 는

- 기술 스택
- 실행 환경
- 외부 연동
- 현재 단계

를 짧게 보는 entry spec 입니다.

### 2. 구조 설명

- [architecture.md](../architecture.md)

이 문서는 현재 모듈 구조와 계층 분리를 큰 그림으로 설명합니다.

### 3. API contract

- [api-mapping.md](./api-mapping.md)

이 문서는 프론트/외부 연동 기준 API 요청/응답 contract를 정리합니다.

### 4. DB migration / schema 메모

- [db-migration.md](./db-migration.md)
- [db-backup-restore-rehearsal-runbook.md](./db-backup-restore-rehearsal-runbook.md)

이 문서는 현재 PostgreSQL mainline 기준 schema/migration truth를 보조하는 **legacy migration / draft sidecar 메모** 를 정리합니다.
현재 실행 판단은 `db-migration.md` 단독보다 `current-state.md`, `testing.md`, 관련 runbook을 먼저 봅니다.

`db-backup-restore-rehearsal-runbook.md` 는 운영 DB 백업에서 별도 DB로 복원하고 앱 연결 전 감사/smoke를 태우는 절차를 고정합니다.

### 5. 사용자 데이터 분리 설계

- [user-data-separation-design.md](./user-data-separation-design.md)

이 문서는 `user_key`, `auth_users`, `user_profiles`, `user_pii`, `2 schema` 기준의 PII 분리 배경과 cut-over 원칙을 정리합니다.

- [pii-key-rotation-runbook.md](./pii-key-rotation-runbook.md)

이 문서는 현재 지원되는 legacy cipher rotation과 아직 금지된 직접 `AES_SECRET_KEY` 교체 경계를 정리합니다.

### 6. 챗봇 후속 설계

- [chatbot-plan.md](./chatbot-plan.md)
- [openai-runtime-contract.md](./openai-runtime-contract.md)

이 문서들은 챗봇 구조와 OpenAI runtime/fallback/privacy 계약을 같이 정리합니다.

### 7. 알림 채널 확장 설계

- [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)

이 문서는 현재 이메일 중심 알림 구조를 기준으로 `인앱 알림함 + 웹푸시` 를 어떻게 붙일지 정리한 설계 메모입니다.

### 8. 알림 채널 확장 체크리스트

- [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)

이 문서는 알림 채널 확장 작업을 단계별로 어디까지 할지 짧게 고정하는 작업 체크리스트입니다.

## 읽는 순서

### 구조/계약을 빨리 확인할 때

1. [stabilization-checklist.md](./stabilization-checklist.md)
2. [final-ops-closeout-checklist.md](./final-ops-closeout-checklist.md)
3. [security-hardening-current-state.md](./security-hardening-current-state.md)
4. [local-validation-docs-index.md](./local-validation-docs-index.md)
5. [testing.md](./testing.md)
6. [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
7. [uptime-monitoring-runbook.md](./uptime-monitoring-runbook.md)
8. [log-alert-thresholds.md](./log-alert-thresholds.md)
9. [project-spec.md](../project-spec.md)
10. [architecture.md](../architecture.md)
11. [api-mapping.md](./api-mapping.md)

### DB/데이터 구조 판단이 필요할 때

1. [db-migration.md](./db-migration.md)
2. [user-data-separation-design.md](./user-data-separation-design.md)
3. [pii-key-rotation-runbook.md](./pii-key-rotation-runbook.md)

### 후속 확장 메모를 볼 때

1. [chatbot-plan.md](./chatbot-plan.md)
2. [openai-runtime-contract.md](./openai-runtime-contract.md)
3. [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
4. [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md)
5. [notification-backlog-sample-audit-runbook.md](./notification-backlog-sample-audit-runbook.md)
6. [notification-stale-target-audit-runbook.md](./notification-stale-target-audit-runbook.md)
7. [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)
8. 필요하면 [phase-plan.md](../phase-plan.md)

## 요약

1. 안정화 단계에서는 [stabilization-checklist.md](./stabilization-checklist.md) 를 먼저 봅니다.
2. PR/배포/운영 handoff를 닫을 때는 [final-ops-closeout-checklist.md](./final-ops-closeout-checklist.md) 를 봅니다.
3. 보안 현재 상태는 [security-hardening-current-state.md](./security-hardening-current-state.md) 를 봅니다.
4. 로컬/통합 검증 진입점은 [local-validation-docs-index.md](./local-validation-docs-index.md), [testing.md](./testing.md), [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md) 를 봅니다.
5. 서버 다운 감지는 [uptime-monitoring-runbook.md](./uptime-monitoring-runbook.md) 를 봅니다.
6. EC2 web 이중화/ALB 전환 계획은 [alb-multi-ec2-rollout-plan.md](./alb-multi-ec2-rollout-plan.md) 를 보고, 실제 전환/복구는 [alb-demo-switch-runbook.md](./alb-demo-switch-runbook.md) 를 봅니다.
7. 운영 warning/critical 기준은 [log-alert-thresholds.md](./log-alert-thresholds.md) 를 보고, dashboard 화면에서 어떤 raw field로 확인하는지는 [admin-dashboard-alert-surface-contract.md](./admin-dashboard-alert-surface-contract.md) 를 봅니다.
8. 구조와 contract는 [project-spec.md](../project-spec.md), [architecture.md](../architecture.md), [api-mapping.md](./api-mapping.md) 부터 봅니다.
9. DB/schema 판단은 [db-migration.md](./db-migration.md), [user-data-separation-design.md](./user-data-separation-design.md), [pii-key-rotation-runbook.md](./pii-key-rotation-runbook.md) 를 봅니다.
10. 챗봇과 OpenAI 경계는 [chatbot-plan.md](./chatbot-plan.md), [openai-runtime-contract.md](./openai-runtime-contract.md) 를 같이 봅니다.
11. ops observation은 [ops-observation-runbook.md](./ops-observation-runbook.md) 를 봅니다.
12. 알림 채널 확장은 [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md) 부터 봅니다.
13. backlog 운영 triage는 [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md) 를 봅니다.
14. 실제 작업 범위는 [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md) 로 고정합니다.
