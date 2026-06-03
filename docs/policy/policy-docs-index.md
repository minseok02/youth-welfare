# Policy 문서 묶음

## 목적

`policy-*` 문서가 왜 이렇게 많아졌는지와, 지금 어디부터 읽어야 하는지를 한 문서에서 정리합니다.

## 왜 문서가 많아졌나

`policy-*` 문서군은 canonical normalization, recommendation bridge, replay smoke, source onboarding 조사를 한 번에 끝낸 결과가 아닙니다.

쪼개진 이유는 세 가지입니다.

1. `온통청년`, `복지로`, `Gov24`, `추천`, `OpenAI replay` 를 동시에 바꾸지 않고 작은 local-first task로 나눴습니다.
2. 실제 로컬 검증 전후에 판단이 많이 바뀌어, 각 단계의 inventory / drift / bridge / blocked source 근거를 따로 남겼습니다.
3. 일부는 외부 응답이 있어야 다시 열 수 있는 blocked 트랙이라, 구현 문서가 아니라 "왜 지금 못 가는지" 를 남기는 기록이 됐습니다. 다만 `Gov24 서비스분야/사용자구분/지원유형` 은 현재 active 문서 기준으로 더 이상 “공개 codebook 대기”가 아니라 label-first canonical 승격 설계 대상으로 읽습니다.

그래서 지금 `policy-*` 파일 다수는 현재 구현 설명서라기보다, 정규화/수집/추천 전환의 설계 히스토리와 조사 로그입니다.

## 지금 먼저 볼 문서

### 현재 코드/로컬 검증 기준

- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-gov24-lane-closeout.md](./policy-gov24-lane-closeout.md)
- [policy-status-filter-design.md](./policy-status-filter-design.md) ← statusFilter 설계 및 온통청년 마감 처리
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-gov24-benefit-type-grouping-draft.md](./policy-gov24-benefit-type-grouping-draft.md)
- [policy-gov24-public-data-source-findings.md](./policy-gov24-public-data-source-findings.md)
- [policy-user-provided-official-codebooks.md](./policy-user-provided-official-codebooks.md)
- [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)
- [policy-admin-runtime-runbook.md](./policy-admin-runtime-runbook.md)
- [policy-quality-summary-runbook.md](./policy-quality-summary-runbook.md)
- [policy-post-local-closeout-track-split.md](./policy-post-local-closeout-track-split.md)

현재 practical runtime wrapper:

- `bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- `bash deploy/smoke/run-local-policy-quality-summary.sh`
- `bash deploy/smoke/run-local-gov24-quality-audit.sh`

### source onboarding 큰 그림

- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)
- [policy-source-onboarding-template.md](./policy-source-onboarding-template.md)
- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-source-canonical-onboarding-priority.md](./policy-source-canonical-onboarding-priority.md)

### 추천 경계

- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
- [policy-normalization-unified-category-response-bridge.md](../history/policy/policy-normalization-unified-category-response-bridge.md)

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

## blocked / deferred track 문서

아래는 지금 바로 구현을 계속하는 문서가 아니라,

- 외부 source/codebook 응답이 와야 다시 여는 blocked 문서
- 또는 runtime closeout 뒤 남은 deferred 판단을 정리한 문서

입니다.

- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- `policy-normalization-gov24-*`
- `policy-normalization-youth-mid-*`

## 지금 기준으로 기억할 핵심

1. 현재 구현 확인은 [policy-normalization-current-state.md](./policy-normalization-current-state.md)부터 봅니다.
2. `Gov24` 의 bounded 제품 확장 closeout은 [policy-gov24-lane-closeout.md](./policy-gov24-lane-closeout.md) 를 먼저 봅니다. 여기서 public filter 3축, soft scoring, deferred 범위를 한 번에 읽습니다.
3. `Gov24` 의 blocked/deferred 경계와 reopen 조건은 [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)를 먼저 봅니다.
4. `Gov24` 를 실제로 붙일 때는 [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md) 로 이번 턴 범위를 먼저 고정합니다.
5. `Gov24` runtime collect가 붙은 뒤 coverage/shape/null-heavy 샘플을 다시 볼 때는 [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md) 을 먼저 봅니다.
6. `Gov24` support fact gap이 어떤 code 군집 때문인지 볼 때는 [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md) 를 봅니다.
7. `Gov24 serviceField/userType/benefitType` 에 대해 지금 바로 확정 가능한 internal rule만 보려면 [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md) 를 봅니다.
8. `Gov24 benefitType` 을 UX group이나 대표 샘플 기준으로 다시 볼 때는 [policy-gov24-benefit-type-grouping-draft.md](./policy-gov24-benefit-type-grouping-draft.md) 를 봅니다. 이 문서는 seed source가 아니라 grouping/QA 참고표입니다.
9. 공공데이터포털에서 실제로 무엇을 확보했고, `Gov24` 3축이 왜 codebook보다 live inventory로 읽히는지 다시 보려면 [policy-gov24-public-data-source-findings.md](./policy-gov24-public-data-source-findings.md) 를 먼저 봅니다.
10. 사용자 제공 `행정표준 코드북` 묶음을 어디에 쓸 수 있는지 다시 보려면 [policy-user-provided-official-codebooks.md](./policy-user-provided-official-codebooks.md) 를 먼저 봅니다. 작은 `xlsx` 는 앱 리소스 JSON으로, 큰 `txt` 는 metadata + sample 형태로 고정했습니다.
11. `Gov24` 를 다음 active track으로 다시 열 때, 무엇을 canonical term으로 올리고 무엇을 deferred 로 남길지 보려면 [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md) 을 먼저 봅니다.
12. 정책 admin bounded runtime 경로(`reference-urls/rebuild`, `embeddings/rebuild`, `retrieval-evaluations/gate`, `category-audit`)를 한 장에서 다시 열 때는 [policy-admin-runtime-runbook.md](./policy-admin-runtime-runbook.md) 을 먼저 봅니다. 이 문서가 current one-page runtime runbook 입니다.
13. retrieval/category 상태를 daily operator 관점으로 compact하게 다시 읽고 싶을 때는 먼저 `bash deploy/smoke/run-local-policy-quality-observation-suite.sh` 를 쓰고, stable artifact `tmp/policy-quality-observation/latest-policy-quality-observation-summary.txt`, `latest-policy-quality-observation-note.md`, `latest-policy-quality-observation.json` 을 먼저 봅니다.
14. retrieval/category raw baseline 숫자와 category 분포까지 같이 기록하려면 [policy-quality-summary-runbook.md](./policy-quality-summary-runbook.md) 을 먼저 보고, `dataset_key / scenario_count / gate / category summary` 를 같이 남깁니다. 이 문서가 current one-shot summary smoke runbook 입니다.
15. 신규 API를 어떻게 꽂을지 큰 구조는 [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)를 먼저 봅니다.
16. 실제로 새 source를 받을 때는 [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md) 순서대로 판단합니다.
17. 실제 코드에서 어디를 열지 찾으려면 [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)를 봅니다.
18. 실제 새 source note를 만들 때는 [policy-source-onboarding-template.md](./policy-source-onboarding-template.md)를 복사해서 씁니다.
19. `phase-plan.md` 나 개별 `policy-*` history 문서는 현재 계약이 아니라 설계/전환 이력일 수 있으므로, 실행 판단은 위 current-state/runbook 문서를 먼저 봅니다.
20. 실행 결과를 남길 때는 숫자 요약만 적지 말고 wrapper/command, query override, `data.*` 핵심 필드, baseline과 달라진 점까지 같이 적습니다.
21. `policy-*` 파일 수가 많은 이유는 문서가 과한 것보다, local-first로 잘게 검증한 흔적이 누적된 결과에 가깝습니다.
