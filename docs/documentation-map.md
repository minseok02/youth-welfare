# Documentation Map

## 목적

문서 수가 많아도 실제로 매번 다 읽을 필요는 없습니다.

이 문서는 현재 `docs/` 를

- **지금 바로 봐야 하는 source of truth**
- **구현 배경을 남긴 design history**
- **외부 응답이 있어야 다시 열리는 blocked 트랙**
- **지금은 보류 상태인 future infra/deploy 메모**

으로 나눠서 길을 줄입니다.

## 1. 지금 바로 볼 문서

### 제품/구조

- [README.md](./README.md)
- [architecture.md](./architecture.md)
- [api-mapping.md](./api-mapping.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [phase-plan.md](./phase-plan.md)

### 현재 구현 상태 요약

- [auth-docs-index.md](./auth-docs-index.md)
- [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)
- [auth-operation-checklist.md](./auth-operation-checklist.md)
- [policy-docs-index.md](./policy-docs-index.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)
- [policy-source-onboarding-template.md](./policy-source-onboarding-template.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-post-local-closeout-track-split.md](./policy-post-local-closeout-track-split.md)

### 로컬 검증 절차

- [collect-current-state.md](./collect-current-state.md)
- [collect-operation-checklist.md](./collect-operation-checklist.md)
- [recommendation-current-state.md](./recommendation-current-state.md)
- [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
- [auth-operation-checklist.md](./auth-operation-checklist.md)
- [frontend-qa-current-state.md](./frontend-qa-current-state.md)
- [frontend-qa-checklist.md](./frontend-qa-checklist.md)
- [frontend-qa-template.md](./frontend-qa-template.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [testing.md](./testing.md)
- [collect-ops.md](./collect-ops.md)

현재 우선순위:

- 로컬 기능 검증
- 구조 검증
- 수정
- 최적화/보안
- 프론트 연동 검증
- 마지막에만 infra/deploy

## 2. design history 로 읽을 문서

아래는 현재 구현의 배경을 남긴 문서입니다.

### auth forced logout cluster

- `auth-admin-forced-logout-*`

읽는 법:

- 전체 auth 묶음은 [auth-docs-index.md](./auth-docs-index.md)
- 현재 계약 확인은 [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)
- 왜 그렇게 됐는지는 개별 design history 문서

### 교육 replay / OpenAI replay cluster

- `policy-normalization-education-*`
- `openai-replay-*`

읽는 법:

- 현재 local closeout 여부는 [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- 세부 실험 배경은 개별 문서

### canonical normalization decision cluster

- `policy-normalization-*`

읽는 법:

- 전체 policy 묶음은 [policy-docs-index.md](./policy-docs-index.md)
- current big picture 는 [policy-normalization-current-state.md](./policy-normalization-current-state.md),
  [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md),
  [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md),
  [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md),
  [policy-source-onboarding-template.md](./policy-source-onboarding-template.md),
  [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md),
  [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md),
  [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
- 세부 drift/inventory/bridge 판단은 개별 문서

## 3. external blocked 트랙

현재 바로 구현으로 못 가는 문서들입니다.

- `GOV24_*` codebook / inventory / request template
- `YOUTH_MID` stable code source / request spec
- CTR sample / 카카오 알림톡 2차

현재 해석:

- 문서 추가보다 외부 응답/승인/표본 확보가 먼저

## 4. future infra/deploy

현재는 운영 서버가 없고,
프론트 연동 검증도 아직 남아 있으므로
배포/cron/runbook 문서는 active current-state 문서로 유지하지 않습니다.

해석:

- 지금은 로컬 smoke와 코드/구조 문서만 본다
- 실제 서버가 생길 때 deploy runbook을 다시 만드는 편이 맞다

## 5. 추천 읽기 순서

### 코드와 문서 불일치가 걱정될 때

1. 실제 코드
2. [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md) 또는 현재 상태 요약 문서
3. [phase-plan.md](./phase-plan.md)
4. 필요하면 개별 design history

### 새 작업을 열 때

1. [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
2. [policy-post-local-closeout-track-split.md](./policy-post-local-closeout-track-split.md)
3. 현재 active 트랙의 current-state 문서

## 6. 요약

1. 모든 문서를 같은 우선순위로 읽지 않습니다.
2. 현재 계약 확인은 `current state` 문서와 실제 코드가 우선입니다.
3. 쪼개진 `policy` / `auth-admin-forced-logout-*` 문서는 대부분 design history 로 읽습니다.
4. `auth-docs-index.md`, `policy-docs-index.md` 가 각 문서군의 1차 진입점입니다.
5. 지금 기준에서 pure ops/runbook 문서는 삭제했고, 서버가 생기기 전까지는 local-only 문서만 유지합니다.
