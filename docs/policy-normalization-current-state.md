# Policy Normalization Current State

관련 코드:

- [DeferredNormalizedPolicySidecarWriter.java](../backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java)
- [WelfareServiceRepository.java](../backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java)
- [CanonicalRecommendationReadModelRepository.java](../backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java)
- [RecommendationCandidateProjection.java](../backend/src/main/java/com/example/welfare/recommend/dto/RecommendationCandidateProjection.java)
- [RuleScoringService.java](../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)
- [DefaultPriorityMatcher.java](../backend/src/main/java/com/example/welfare/recommend/service/DefaultPriorityMatcher.java)

관련 문서:

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
- [policy-normalization-compat-storage-policy.md](./history/policy/policy-normalization-compat-storage-policy.md)
- [policy-post-local-closeout-track-split.md](./policy-post-local-closeout-track-split.md)

## 목적

`policy-normalization-*` 문서가 많아진 상태에서,
현재 실제 코드/로컬 검증 기준으로 무엇이 구현됐고 무엇이 아직 blocked 인지
한 문서에서 바로 볼 수 있게 정리합니다.

이 문서는 **현재 normalization source of truth 요약** 입니다.  
세부 drift, request template, 실험 기록, source 조사 메모는 개별 문서를 design history / research 로 읽습니다.

## 현재 구현된 핵심 범위

현재 코드 기준으로 이미 살아 있는 normalization 축은 아래입니다.

1. `YOUTH` canonical sidecar 저장
2. `YOUTH_MID_RAW_ALIAS` raw alias 보존
3. `youth_major` summary 정제
4. recommendation read-model projection hydrate
5. `YOUTH 0/0 income` repository pass-through
6. `교육 -> 교육·직업훈련` narrow scoring experiment

## 1. canonical sidecar 저장

현재 canonical sidecar의 중심 테이블은:

- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

입니다.

현재 로컬 실수집 검증 기준:

- `welfare_services=2363`
- `service_taxonomies=2363`
- `service_taxonomy_terms=7931`
- `service_facts=8257`

즉 `YOUTH` snapshot 기준으로 core row와 canonical sidecar 저장은 현재 로컬에서 정상동작 확인 상태입니다.

## 2. `YOUTH_MID_RAW_ALIAS` 현재 상태

현재 정책은:

- official `YOUTH_MID` token 은 canonical `YOUTH_MID`
- non-official variant 는 `YOUTH_MID_RAW_ALIAS`

로 보존합니다.

중요한 현재 구현 포인트:

- `DeferredNormalizedPolicySidecarWriter` 는 `YOUTH_MID` 와 `YOUTH_MID_RAW_ALIAS` 를 같은 refresh family로 봅니다
- 따라서 raw alias가 official token으로 정규화된 뒤 stale alias row가 남지 않게 처리합니다

즉 raw alias 보존은 유지하되, refresh 후 read-model 기준 stale row는 남기지 않는 상태입니다.

## 3. `youth_major` summary 현재 상태

현재 `youth_major` summary는 raw `category_main` 복사가 아니라:

- canonical major 1개로 collapse 가능한 경우만 채움
- multi-major면 `NULL`
- variant label은 canonical label로 normalize

규칙으로 정리돼 있습니다.

현재 writer / backfill SQL 모두 이 규칙을 따르도록 맞춰진 상태입니다.

## 4. recommendation read-model 현재 상태

현재 recommendation 쪽 canonical projection은:

- `CanonicalRecommendationReadModelRepository`
- `RecommendationCandidateProjection`

경계로 들어와 있습니다.

projection이 현재 담는 대표값:

- `unifiedCategoryCompat`
- `youthMajorLabel`
- `applyEndDate`
- `interestThemes`
- `targetGroupsRaw`
- `targetGroupBuckets`
- `beneficiaryTerms`
- `factKeys`

즉 raw sidecar를 추천 서비스가 직접 읽는 게 아니라,
추천 전용 projection을 통해 hydrate 하는 구조가 이미 코드에 있습니다.

## 5. retrieval / repository 현재 상태

현재 repository 의미에서 이미 구현된 중요한 보정은:

- `YOUTH 0/0 income => pass-through`

입니다.

즉 `min_income=0 && max_income=0` 은 실제 소득 하드 게이트가 아니라
미지정 sentinel 로 보고 candidate pool 에서 막지 않습니다.

이 보정이 들어간 뒤에야
교육 experiment target row가 실제 candidate/result set 에 들어오기 시작했습니다.

## 6. scoring / priority 현재 상태

### RuleScoringService

현재 이미 구현된 것:

- canonical projection 병행 입력
- `BENEFICIARY_SUPPORT` bucket max-one bonus
- `recommend.priority.education-canonical-bonus.enabled` flag
- `compat=기타 + youth_major=교육 + priority=EDUCATION` narrow bonus

즉 education experiment 는 현재 **문서만 있는 상태가 아니라 코드에 들어와 있습니다**.

### DefaultPriorityMatcher

현재 matcher는 canonical full taxonomy 로 넘어가지 않았습니다.

대신 좁은 bridge만 구현돼 있습니다.

- `projection.unifiedCategoryCompat`
- `projection.applyEndDate`

우선 사용

즉 현재 priority contract 는 여전히 compat layer 중심입니다.

## 7. response 계약 현재 상태

현재 recommendation/search/detail/ranking 응답의 `unifiedCategory` 는
canonical taxonomy 대표값이 아니라
계속 **legacy compat category 의미** 로 유지합니다.

즉 canonical taxonomy는:

- inventory
- explanation
- future experiment hint

용 보조 신호이고,
현재 response 대표 category를 대체하지 않습니다.

## 8. local validation 현재 상태

현재 로컬에서 실제로 다시 확인된 것은:

- `POST /api/admin/collect/youth` actual collect success
- canonical sidecar row 저장 success
- `deploy/smoke/run-local-education-priority-replay.sh` success
- replay summary:
  - `A_top10_target=4->8`
  - `B_top10_target=3->3`

즉 normalization 은 “문서 설계” 수준이 아니라
실제 collect -> sidecar -> recommendation downstream 까지 로컬 검증이 끝난 상태입니다.

## 아직 blocked 인 것

현재 코드/로컬 기준으로 아직 blocked 인 normalization 항목은 아래입니다.

### external blocked

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` full codebook
- `GOV24_SUPPORT_CONDITION` full inventory
- `YOUTH_MID` stable code mapping

공통 이유:

- provider/operator/codebook 응답이 먼저 필요

### ops-only

- 운영 host/DB/secret 기준 전환
- 운영 migration / datasource / deploy smoke

## design history 로 읽을 문서

아래는 current-state 문서가 아니라 세부 판단/조사/실험 기록입니다.

### source onboarding / schema

- `policy-source-onboarding-playbook.md`
- `policy-normalization-schema-draft.md`
- `policy-normalization-sample-spike.md`
- `policy-listing-source-schema-draft.md`
- `policy-scholarship-reference-matrix-draft.md`

### recommendation bridge / compat / drift

- `policy-normalization-recommendation-read-model.md`
- `policy-normalization-recommendation-migration-order.md`
- `policy-normalization-compat-*`
- `policy-normalization-education-*`

### blocked source acquisition

- `policy-normalization-gov24-*`
- `policy-normalization-youth-mid-*`

## 지금 이 문서를 먼저 보면 좋은 경우

1. `정규화가 지금 코드에 어디까지 들어갔는지` 빠르게 알고 싶을 때
2. `education experiment가 문서만 있는지, 실제 코드에 있는지` 헷갈릴 때
3. `blocked source` 와 `현재 구현` 을 분리해서 보고 싶을 때
4. `README` 에서 normalization 문서가 너무 많아 어디서부터 읽어야 할지 모를 때

## 짧은 결론

1. `YOUTH` canonical sidecar 저장과 recommendation bridge는 현재 코드/로컬 기준으로 이미 살아 있습니다.
2. 현재 recommendation contract 는 여전히 compat layer 중심이고, canonical taxonomy 는 projection/bonus/hint 로 병행 사용됩니다.
3. 남은 큰 normalization 미완은 `GOV24_*` 와 `YOUTH_MID stable code` 처럼 external blocked 인 항목입니다.
