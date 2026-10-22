# 구조와 운영 문서 목차

[환경 구분](environment-guide.md)에서 로컬·서버·로컬 앱과 원격 DB를 구분한 뒤 필요한 계약과 절차를 선택합니다. 문서의 존재나 과거 green 기록은 현재 운영 상태의 증거가 아닙니다.

## 구조와 계약

| 목적 | 문서 |
| --- | --- |
| 기술·버전 | [project-spec.md](../project-spec.md) |
| 모듈·공통 탐색·배포 구성 | [architecture.md](../architecture.md) |
| API와 외부 데이터 매핑 | [api-mapping.md](api-mapping.md) |
| 요구사항과 기대 동작 | [srs-v2.10.md](srs-v2.10.md) |
| 공개·인증·관리자 접근 | [feature-access-matrix.md](feature-access-matrix.md) |
| OpenAI 호출·fallback·privacy | [openai-runtime-contract.md](openai-runtime-contract.md) |
| 보안 현재 계약 | [security-hardening-current-state.md](security-hardening-current-state.md) |

## 데이터와 안전 절차

- [DB 초기화와 변경](db-migration.md)
- [백업 복구 리허설](db-backup-restore-rehearsal-runbook.md)
- [PII 키 교체](pii-key-rotation-runbook.md)
- [사용자 데이터 분리 설계](user-data-separation-design.md): 유효한 PII/권한 원칙과 과거 이행 기록을 구분합니다.

## 로컬 검증

[로컬 검증 목차](local-validation-docs-index.md), [테스트 실행 기준](testing.md), [런타임 명령](runtime-api-smoke-commands.md)을 확인합니다. 런타임 smoke에는 조회와 실제 데이터 변경이 섞여 있으므로 테스트 이름만으로 실행하지 않습니다.

## 서버 배포와 운영

- 배포·runtime env·RDS: [deployment.md](../deployment.md)
- 환경/schema/credential 문제: [server-runtime-drift-checklist.md](server-runtime-drift-checklist.md)
- 일상·실패 triage·배포 후 확인·인계: [운영 점검](final-ops-closeout-checklist.md)
- 관측 결과 해석: [ops-baseline-runbook.md](ops-baseline-runbook.md), [ops-observation-runbook.md](ops-observation-runbook.md)
- 주기 실행과 가용성: [nightly-ops-handoff-cron-runbook.md](nightly-ops-handoff-cron-runbook.md), [uptime-monitoring-runbook.md](uptime-monitoring-runbook.md)
- 경보 기준과 표시 계약: [log-alert-thresholds.md](log-alert-thresholds.md), [admin-dashboard-alert-surface-contract.md](admin-dashboard-alert-surface-contract.md)
- 알림 backlog: [총량](notification-backlog-audit-runbook.md), [표본](notification-backlog-sample-audit-runbook.md), [stale target](notification-stale-target-audit-runbook.md). 실제 `hide-stale` 처리는 별도 상태 변경입니다.
- 메일 provider 전환: [mail-ses-migration-runbook.md](mail-ses-migration-runbook.md). 실제 발송과 계정 변경이 포함됩니다.

## 과거 설계와 후속 제안

[챗봇 설계](chatbot-plan.md), [알림 확장 설계](notification-channel-expansion-plan.md), [기존 채널 단계표](notification-channel-expansion-checklist.md), [MVP 확장 판단](mvp-grow-when-needed-playbook.md), [PostgreSQL 전환 계획](../postgres/postgres-refactor-spec-index.md)은 현재 구현과 대조한 뒤 미해결 제안만 검토합니다. 인앱·웹푸시·공통 탐색 등 이미 구현된 범위를 다시 미구현으로 분류하지 않습니다.

[deep-audit-checklist.md](deep-audit-checklist.md)는 과거 점검과 유효한 보안 설명을 보존합니다. 기록의 완료 표시를 현재 검증으로 재사용하지 않습니다. 전체 기록의 위치는 [history-docs-index.md](../history-docs-index.md)를 확인합니다.
