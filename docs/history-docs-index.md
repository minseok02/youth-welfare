# 히스토리 문서 묶음

## 목적

`history/` 와 `archive/` 아래 문서는 현재 계약 자체보다

- 왜 그런 설계를 했는지
- 어떤 실험/조사/드리프트 분석이 있었는지
- 지금은 보류된 과거 계획이 무엇인지

를 남긴 문서입니다.

이 묶음은 current-state 문서와 design history 문서를 명확히 분리해서 찾게 정리합니다.

## 지금 먼저 볼 문서

### 현재 계약을 먼저 봐야 할 때

먼저 아래 current-state / index 문서를 봅니다.

- [auth-docs-index.md](./auth-docs-index.md)
- [collect-docs-index.md](./collect-docs-index.md)
- [recommendation-docs-index.md](./recommendation-docs-index.md)
- [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)
- [policy-docs-index.md](./policy-docs-index.md)
- [local-validation-docs-index.md](./local-validation-docs-index.md)
- [system-docs-index.md](./system-docs-index.md)

그 다음 설계 배경이 필요할 때만 `history/` 로 내려갑니다.

## 문서 역할

### 1. auth design history

- `docs/history/auth/*`

주로 남아 있는 내용:

- admin forced logout 설계 순서
- Redis/JWT/helper/service 경계
- revoke / logout / withdraw 정책 배경

### 2. policy / normalization design history

- `docs/history/policy/*`

주로 남아 있는 내용:

- canonical normalization
- summary slot / bridge / schema draft
- `Gov24` blocked 근거
- `YOUTH_MID` inventory
- 복지로 detail/gap-fill 조사

#### 정책 목록 UI 관련 결정 (2026-05-04 추가)

- [policy-listing-sort-region-strategy.md](./history/policy/policy-listing-sort-region-strategy.md) — 정렬 B안 채택 (LATEST strict region-first, VIEWS/DEADLINE tiebreaker), A안 대안 포함
- [policy-card-source-sido-field.md](./history/policy/policy-card-source-sido-field.md) — 카드 source 필드 sido 추가 결정 (BOKJIRO_LOCAL 1,223개 대상, DB 조사 결과 포함)

### 3. AI / replay design history

- `docs/history/ai/*`

주로 남아 있는 내용:

- education replay
- OpenAI drift
- prompt / reason pattern 분석

### 4. archive 문서

- [archive/README.md](./archive/README.md)

현재 직접 참고하지 않는 과거 계획 문서를 보관하는 위치입니다.

## 읽는 순서

### 현재 코드와 문서가 왜 이렇게 됐는지 볼 때

1. 현재 current-state 또는 docs index
2. [phase-plan.md](./phase-plan.md)
3. 필요하면 `docs/history/...`

### 외부 blocked 근거를 찾을 때

1. [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
2. 필요하면 `docs/history/policy/policy-normalization-gov24-*`

### OpenAI / replay 배경을 찾을 때

1. [recommendation-docs-index.md](./recommendation-docs-index.md)
2. 필요하면 `docs/history/ai/*`

## 요약

1. `history/` 문서는 현재 계약보다 설계 배경 문서로 봅니다.
2. `archive/` 문서는 과거 계획 보관용입니다.
3. 먼저 current-state/index 문서를 보고, 배경이 필요할 때만 이 묶음으로 내려오는 흐름이 맞습니다.
