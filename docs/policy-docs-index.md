# Policy 문서 묶음

## 목적

`policy-*` 문서가 왜 이렇게 많아졌는지와, 지금 어디부터 읽어야 하는지를 한 문서에서 정리합니다.

## 왜 문서가 많아졌나

`policy-*` 문서군은 canonical normalization, recommendation bridge, replay smoke, source onboarding 조사를 한 번에 끝낸 결과가 아닙니다.

쪼개진 이유는 세 가지입니다.

1. `온통청년`, `복지로`, `Gov24`, `추천`, `OpenAI replay` 를 동시에 바꾸지 않고 작은 local-first task로 나눴습니다.
2. 실제 로컬 검증 전후에 판단이 많이 바뀌어, 각 단계의 inventory / drift / bridge / blocked source 근거를 따로 남겼습니다.
3. 일부는 외부 응답이 있어야 다시 열 수 있는 blocked 트랙이라, 구현 문서가 아니라 "왜 지금 못 가는지" 를 남기는 기록이 됐습니다.

그래서 지금 `policy-*` 파일 다수는 현재 구현 설명서라기보다, 정규화/수집/추천 전환의 설계 히스토리와 조사 로그입니다.

## 지금 먼저 볼 문서

### 현재 코드/로컬 검증 기준

- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-post-local-closeout-track-split.md](./policy-post-local-closeout-track-split.md)

### source onboarding 큰 그림

- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)
- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-source-canonical-onboarding-priority.md](./policy-source-canonical-onboarding-priority.md)

### 추천 경계

- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
- [policy-normalization-unified-category-response-bridge.md](./history/policy/policy-normalization-unified-category-response-bridge.md)

## design history 로 읽을 문서

### normalization decision cluster

- `policy-normalization-*`

주로 남아 있는 내용:

- `youth_major`, `YOUTH_MID`, `compat_unified_category` 판단
- `Gov24` codebook/source blocked 근거
- `soft signal`, `fact merge`, `schema draft` 같은 canonical 설계 배경

### education replay / experiment cluster

- `policy-normalization-education-*`
- `openai-replay-*`

주로 남아 있는 내용:

- `교육 -> 교육·직업훈련` 실험 경계
- replay smoke 절차
- real OpenAI variability 조사

### source-specific investigation cluster

- `policy-bokjiro-*`
- `policy-listing-source-schema-draft.md`
- `policy-scholarship-reference-matrix-draft.md`

주로 남아 있는 내용:

- 복지로 detail/gap-fill 조사
- listing형 source 분리 스키마 초안
- 장학금/참조행렬 모델 초안

## blocked 로 봐야 하는 문서

아래는 지금 바로 구현을 계속하는 문서가 아니라, 외부 source/codebook 응답이 와야 다시 여는 문서입니다.

- `policy-normalization-gov24-*`
- `policy-normalization-youth-mid-*`

## 지금 기준으로 기억할 핵심

1. 현재 구현 확인은 [policy-normalization-current-state.md](./policy-normalization-current-state.md)부터 봅니다.
2. 신규 API를 어떻게 꽂을지 큰 구조는 [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)를 먼저 봅니다.
3. 실제로 새 source를 받을 때는 [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md) 순서대로 판단합니다.
4. 실제 코드에서 어디를 열지 찾으려면 [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)를 봅니다.
5. 개별 `policy-*` 문서는 대부분 design history, blocked 조사, 실험 배경입니다.
6. `policy-*` 파일 수가 많은 이유는 문서가 과한 것보다, local-first로 잘게 검증한 흔적이 누적된 결과에 가깝습니다.
