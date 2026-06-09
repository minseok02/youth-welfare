# Documentation Map

## 목적

문서 수가 많아도 실제로 매번 다 읽을 필요는 없습니다.

이 문서는 현재 `docs/` 를

- **지금 바로 봐야 하는 source of truth**
- **구현 배경을 남긴 design history**
- **외부 응답이 있어야 다시 열리는 blocked 트랙**
- **지금은 보류 상태인 future infra/deploy 메모**

으로 나눠서 길을 줄입니다.

문서가 서로 충돌하면 우선순위는 아래 순서로 해석합니다.

1. 실제 코드
2. `current-state` / 각 문서군의 `*-current-state` / 현재 검증 문서
3. `phase-plan` 의 최신 상단 항목
4. 설계 배경 문서
5. `history/`, `archive/`, legacy 메모

보정:

- `phase-plan.md` 는 active closeout 기록과 긴 이력 로그가 함께 있는 문서입니다.
- 따라서 `phase-plan.md` 안에서도 최신 상단 항목을 우선 읽고, 아래쪽 오래된 `MySQL`, `draft sidecar`, `service_taxonomy_summary_slots` 기록은 과거 전환 이력으로 읽습니다.

## 1. 지금 바로 볼 문서

### 제품/구조

- [start.md](./start.md)
- [project-spec.md](./project-spec.md)
- [README.md](./README.md)
- [current-state.md](./current-state.md)
- [stabilization-checklist.md](core/stabilization-checklist.md)
- [work-guide.md](./work-guide.md)
- [architecture.md](./architecture.md)
- [api-mapping.md](core/api-mapping.md)
- [recommendation-pipeline.md](recommendation/recommendation-pipeline.md)
- [local-validation-docs-index.md](core/local-validation-docs-index.md)
- [system-docs-index.md](core/system-docs-index.md)
- [phase-plan.md](./phase-plan.md)

읽는 법:

- 이 묶음이 현재 구현/검증/작업 기준입니다.
- `history-docs-index.md`, `history/`, `archive/` 는 여기와 같은 우선순위로 읽지 않습니다.

### 현재 구현 상태 요약

- [auth-docs-index.md](auth/auth-docs-index.md)
- [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md)
- [auth-operation-checklist.md](auth/auth-operation-checklist.md)
- [collect-docs-index.md](collect/collect-docs-index.md)
- [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- [policy-docs-index.md](policy/policy-docs-index.md)
- [performance-docs-index.md](performance/performance-docs-index.md)
- [policy-normalization-current-state.md](policy/policy-normalization-current-state.md)
- [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
- [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](policy/policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](policy/policy-source-code-entrypoints.md)
- [policy-source-onboarding-template.md](policy/policy-source-onboarding-template.md)
- [policy-local-closeout-pending-inventory.md](policy/policy-local-closeout-pending-inventory.md)
- [policy-post-local-closeout-track-split.md](policy/policy-post-local-closeout-track-split.md)

### 로컬 검증 절차

- [collect-docs-index.md](collect/collect-docs-index.md)
- [collect-current-state.md](collect/collect-current-state.md)
- [collect-operation-checklist.md](collect/collect-operation-checklist.md)
- [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- [recommendation-current-state.md](recommendation/recommendation-current-state.md)
- [recommendation-operation-checklist.md](recommendation/recommendation-operation-checklist.md)
- [auth-operation-checklist.md](auth/auth-operation-checklist.md)
- [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- [frontend-qa-current-state.md](frontend/frontend-qa-current-state.md)
- [frontend-qa-checklist.md](frontend/frontend-qa-checklist.md)
- [frontend-qa-template.md](frontend/frontend-qa-template.md)
- [local-validation-docs-index.md](core/local-validation-docs-index.md)
- [performance-docs-index.md](performance/performance-docs-index.md)
- [runtime-api-smoke-commands.md](core/runtime-api-smoke-commands.md)
- [testing.md](core/testing.md)
- [collect-ops.md](collect/collect-ops.md)

현재 우선순위:

- 로컬 기능 검증
- 구조 검증
- 수정
- 최적화/보안
- 프론트 연동 검증
- 마지막에만 infra/deploy

## 2. design history 로 읽을 문서

아래는 현재 구현의 배경을 남긴 문서입니다.

먼저 entrypoint가 필요하면 [history-docs-index.md](./history-docs-index.md) 를 봅니다.

해석:

- 이 문서군은 "왜 이렇게 됐는지"를 설명합니다.
- 현재 실행 기준이나 계약 확인은 active 문서와 코드에서 다시 확인합니다.

### auth forced logout cluster

- `auth-admin-forced-logout-*`

읽는 법:

- 전체 auth 묶음은 [auth-docs-index.md](auth/auth-docs-index.md)
- 현재 계약 확인은 [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md)
- 왜 그렇게 됐는지는 개별 design history 문서

### 교육 replay / OpenAI replay cluster

- `policy-normalization-education-*`
- `openai-replay-*`

읽는 법:

- 현재 local closeout 여부는 [policy-local-closeout-pending-inventory.md](policy/policy-local-closeout-pending-inventory.md)
- 세부 실험 배경은 개별 문서

### canonical normalization decision cluster

- `policy-normalization-*`

읽는 법:

- 전체 policy 묶음은 [policy-docs-index.md](policy/policy-docs-index.md)
- current big picture 는 [policy-normalization-current-state.md](policy/policy-normalization-current-state.md),
  [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md),
  [policy-source-onboarding-checklist.md](policy/policy-source-onboarding-checklist.md),
  [policy-source-code-entrypoints.md](policy/policy-source-code-entrypoints.md),
  [policy-source-onboarding-template.md](policy/policy-source-onboarding-template.md),
  [policy-source-onboarding-playbook.md](policy/policy-source-onboarding-playbook.md),
  [policy-normalization-recommendation-read-model.md](policy/policy-normalization-recommendation-read-model.md),
  [policy-normalization-recommendation-migration-order.md](policy/policy-normalization-recommendation-migration-order.md)
- 세부 drift/inventory/bridge 판단은 개별 문서

## 3. external blocked 트랙

현재 바로 구현으로 못 가는 문서들입니다.

- [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
- `GOV24_*` codebook / inventory / request template
- `YOUTH_MID` stable code source / request spec
- CTR sample / 카카오 알림톡 2차

현재 해석:

- 문서 추가보다 외부 응답/승인/표본 확보가 먼저

## 4. future infra/deploy

현재 active main track은 여전히 로컬 검증과 bounded runtime 기준선 유지다.
다만 `EC2 + RDS` 전환을 실제로 준비할 때 바로 쓸 수 있도록 배포 문서를 다시 추가했다.

- [deployment.md](./deployment.md)

해석:

- 평소에는 로컬 smoke와 코드/구조 문서를 먼저 본다.
- `EC2 + RDS` 를 실제로 준비하거나 실행할 때만 `deployment.md` 를 연다.

## 5. 추천 읽기 순서

### 코드와 문서 불일치가 걱정될 때

1. 실제 코드
2. [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md) 또는 현재 상태 요약 문서
3. [phase-plan.md](./phase-plan.md)
4. 필요하면 개별 design history

### 새 작업을 열 때

1. [policy-next-active-track-priority.md](policy/policy-next-active-track-priority.md)
2. [policy-post-local-closeout-track-split.md](policy/policy-post-local-closeout-track-split.md)
3. `Gov24` runtime closeout 이후 남은 blocked/deferred 판단이 필요하면 [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
4. 현재 active 트랙의 current-state 문서

## 6. 요약

1. 모든 문서를 같은 우선순위로 읽지 않습니다.
2. 현재 계약 확인은 `current state` 문서와 실제 코드가 우선입니다.
3. 쪼개진 `policy` / `auth-admin-forced-logout-*` 문서는 대부분 design history 로 읽습니다.
4. `auth-docs-index.md`, `collect-docs-index.md`, `recommendation-docs-index.md`, `frontend-qa-docs-index.md`, `policy-docs-index.md`, `local-validation-docs-index.md`, `system-docs-index.md` 가 active 문서군의 1차 진입점이고, `history-docs-index.md` 는 배경 문서군의 진입점입니다.
5. `Gov24` 는 runtime collect/runtime audit까지는 이미 closeout 되었고, 남은 hard import/backfill과 deferred code 승격 판단은 [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md) 에서 먼저 확인합니다.
6. 지금 기준에서 pure ops/runbook 문서는 삭제했고, 서버가 생기기 전까지는 local-only 문서만 유지합니다.
