# 문서 시작점

루트에는 작업 시작용 공통 문서만 두고, 주제 문서는 폴더별로 묶었습니다.

처음에는 [start.md](./start.md) 부터 보면 됩니다.

그 다음 필요한 폴더나 파일로 내려가면 됩니다.

## active 문서와 legacy/history 문서 경계

- 현재 구현/검증/운영 판단의 기준은 `start.md`, `current-state.md`, `phase-plan.md`, 각 문서군의 `*-current-state.md`, 그리고 실제 코드입니다.
- 다만 `phase-plan.md` 는 active 기준선과 긴 이력 로그가 함께 있는 문서라, 아래쪽 오래된 `MySQL`, `draft sidecar`, `service_taxonomy_summary_slots` 기록은 과거 실험/전환 이력으로 읽어야 합니다.
- 현재 실행 순서나 계약이 헷갈리면 `current-state.md`, `testing.md`, 각 문서군의 `*-current-state.md`, 실제 코드를 `phase-plan.md` 보다 먼저 봅니다.
- `history-docs-index.md`, `history/`, `archive/` 아래 문서는 설계 배경과 과거 판단을 남긴 참고 자료입니다.
- active 문서와 legacy/history 문서가 충돌하면 active 문서와 실제 코드를 우선합니다.

- [start.md](./start.md)
  작업 시작용 메인 파일입니다.

- [project-spec.md](./project-spec.md)
  프로젝트 기본 정보, 사용 기술, 버전, 환경을 봅니다.

- [current-state.md](./current-state.md)
  지금 단계가 무엇인지, 무엇을 먼저 해야 하는지 봅니다.

- [work-guide.md](./work-guide.md)
  작업을 어떤 순서와 규칙으로 진행할지 봅니다.

- [phase-plan.md](./phase-plan.md)
  진행 상황, 완료 항목, 다음 작업을 봅니다.

- [github-workflow.md](./github-workflow.md)
  브랜치, 커밋, staging, PR 규칙을 봅니다.

필요하면 아래 문서를 추가로 봅니다.

## 폴더 구조

- [core/README.md](core/README.md)
  공통 구조, 검증, 요구사항, API smoke 문서입니다.

- [auth/README.md](auth/README.md)
  인증, 세션, 강제 로그아웃 문서입니다.

- [collect/README.md](collect/README.md)
  수집 실행, 운영, 장애 대응 문서입니다.

- [recommendation/README.md](recommendation/README.md)
  추천 파이프라인, 현재 상태, 검증 문서입니다.
  현재 recommendation active 기준은 `latest-overview -> PR lifecycle order -> REAL_USER recheck` 순서로 읽는 편이 맞습니다.
  current blocker 해석은 full latest batch review gate를 primary historical baseline으로, recent-window gate를 supplemental current-live signal로 같이 읽는 쪽이 맞습니다.

- [policy/README.md](policy/README.md)
  정책 정규화, source 온보딩, 상태 관리 문서입니다.

- [frontend/README.md](frontend/README.md)
  프론트 QA와 검수 문서입니다.

- [postgres/README.md](postgres/README.md)
  PostgreSQL 전환 플레이북과 phase 스펙입니다.

- [history-docs-index.md](./history-docs-index.md)
  설계 배경과 과거 판단 문서 진입점입니다. 현재 실행 기준 문서는 아닙니다.

- [archive/README.md](archive/README.md)
  더 이상 active하지 않은 과거 계획 문서 보관 위치입니다.

## 기본 기준 문서

- [architecture.md](./architecture.md)
- [srs-v2.10.md](core/srs-v2.10.md)
- [testing.md](core/testing.md)
- [local-validation-docs-index.md](core/local-validation-docs-index.md)
- [system-docs-index.md](core/system-docs-index.md)

필요하면 설계 배경은 아래에서 따로 봅니다.

- [history-docs-index.md](./history-docs-index.md)

## 현재 상태 문서

- [auth-docs-index.md](auth/auth-docs-index.md)
- [collect-docs-index.md](collect/collect-docs-index.md)
- [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- [policy-docs-index.md](policy/policy-docs-index.md)
- [local-validation-docs-index.md](core/local-validation-docs-index.md)
- [system-docs-index.md](core/system-docs-index.md)
- [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md)
- [collect-current-state.md](collect/collect-current-state.md)
- [recommendation-current-state.md](recommendation/recommendation-current-state.md)
- [policy-normalization-current-state.md](policy/policy-normalization-current-state.md)
- [policy-next-active-track-priority.md](policy/policy-next-active-track-priority.md)
- [policy-local-closeout-pending-inventory.md](policy/policy-local-closeout-pending-inventory.md)
- [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)

## 작업용 문서

- [api-mapping.md](core/api-mapping.md)
- [recommendation-pipeline.md](recommendation/recommendation-pipeline.md)
- [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](policy/policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](policy/policy-source-code-entrypoints.md)
- [policy-source-onboarding-template.md](policy/policy-source-onboarding-template.md)

## 기록 / 참고

- [troubleshooting-log.md](core/troubleshooting-log.md)
- [documentation-map.md](./documentation-map.md)
- [history-docs-index.md](./history-docs-index.md)
