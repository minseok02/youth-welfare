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

- [stabilization-checklist.md](./stabilization-checklist.md)
- [final-ops-closeout-checklist.md](./final-ops-closeout-checklist.md)
- [security-hardening-current-state.md](./security-hardening-current-state.md)
- [local-validation-docs-index.md](./local-validation-docs-index.md)
- [testing.md](./testing.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [server-runtime-drift-checklist.md](./server-runtime-drift-checklist.md)
- [uptime-monitoring-runbook.md](./uptime-monitoring-runbook.md)
- [log-alert-thresholds.md](./log-alert-thresholds.md)
- [admin-dashboard-alert-surface-contract.md](./admin-dashboard-alert-surface-contract.md)
- [project-spec.md](../project-spec.md)
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

`final-ops-closeout-checklist.md` 는 안정화 작업을 PR/배포/운영 handoff 전에 어떤 명령 순서로 닫을지 고정합니다.

`uptime-monitoring-runbook.md` 는 Healthchecks.io ping, 서버 내부 watchdog, AWS Route53/CloudWatch 알람을 운영 서버에 붙이는 순서를 정리합니다.

`log-alert-thresholds.md` 는 app/nginx 로그와 운영 dashboard 지표의 warning/critical 기준을 고정합니다.

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
6. 운영 warning/critical 기준은 [log-alert-thresholds.md](./log-alert-thresholds.md) 를 보고, dashboard 화면에서 어떤 raw field로 확인하는지는 [admin-dashboard-alert-surface-contract.md](./admin-dashboard-alert-surface-contract.md) 를 봅니다.
7. 구조와 contract는 [project-spec.md](../project-spec.md), [architecture.md](../architecture.md), [api-mapping.md](./api-mapping.md) 부터 봅니다.
8. DB/schema 판단은 [db-migration.md](./db-migration.md), [user-data-separation-design.md](./user-data-separation-design.md), [pii-key-rotation-runbook.md](./pii-key-rotation-runbook.md) 를 봅니다.
9. 챗봇과 OpenAI 경계는 [chatbot-plan.md](./chatbot-plan.md), [openai-runtime-contract.md](./openai-runtime-contract.md) 를 같이 봅니다.
10. ops observation은 [ops-observation-runbook.md](./ops-observation-runbook.md) 를 봅니다.
11. 알림 채널 확장은 [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md) 부터 봅니다.
12. backlog 운영 triage는 [notification-backlog-audit-runbook.md](./notification-backlog-audit-runbook.md) 를 봅니다.
13. 실제 작업 범위는 [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md) 로 고정합니다.
