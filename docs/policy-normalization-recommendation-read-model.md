# Canonical Recommendation Read-Model 경계 초안

## 목적

canonical sidecar(`service_taxonomies`, `service_taxonomy_terms`, `service_facts`)를 추천 파이프라인에 붙일 때,

- persistence raw term 보존
- retrieval/filter 친화 projection
- scoring용 dedupe bucket
- response/UI용 설명 데이터

를 한 번에 뒤섞지 않도록 read-model 경계를 먼저 고정한다.

## 결론

추천 파이프라인은 raw sidecar 테이블을 직접 읽지 않는다.  
대신 `canonical recommendation read-model` projection을 하나 두고, 여기서만:

1. taxonomy/facts를 모은다
2. beneficiary raw term을 `BENEFICIARY_SUPPORT` bucket으로 dedupe 한다
3. retrieval/scoring에 필요한 구조만 내보낸다

## 권장 레이어

### 1. persistence layer

원본:

- `welfare_services`
- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

역할:

- source별 raw canonical signal 보존
- read-model 계산의 source of truth

여기서는 collapse 금지.

### 2. read-model projection layer

예상 이름:

- `CanonicalRecommendationServiceView`
- 또는 `RecommendationCandidateProjection`

역할:

- recommendation retrieval/scoring이 직접 쓰는 중간 projection
- service 1건당 1 projection

예상 필드:

```java
record RecommendationCandidateProjection(
    Long serviceId,
    String sourceType,
    String unifiedCategoryCompat,
    String title,
    String summary,
    Integer minAge,
    Integer maxAge,
    Integer incomeMinLegacy,
    Integer incomeMaxLegacy,
    LocalDate applyEndDate,
    boolean youthRelevant,
    Set<String> interestThemes,
    Set<String> targetGroupsRaw,
    Set<String> targetGroupBuckets,
    Set<String> lifeStages,
    Set<String> keywordTags,
    Set<String> beneficiaryTerms,
    Set<String> factKeys
) {}
```

핵심:

- `targetGroupsRaw`
  - raw taxonomy term 그대로
- `beneficiaryTerms`
  - `기초생활수급자`, `차상위계층`
- `targetGroupBuckets`
  - scoring용 dedupe bucket
  - 예: `BENEFICIARY_SUPPORT`

## beneficiary bucket 규칙

입력:

- `service_taxonomy_terms.term_group='TARGET_GROUP'`
- `source_field='targetDetail/selectionCriteria'`
- raw labels:
  - `기초생활수급자`
  - `차상위계층`

projection 규칙:

- raw label은 `beneficiaryTerms` 에 그대로 유지
- 두 값 중 하나라도 있으면 `targetGroupBuckets` 에 `BENEFICIARY_SUPPORT` 추가
- 두 값이 동시에 있어도 bucket은 1개만 생성

즉:

```text
raw terms = [기초생활수급자, 차상위계층]
beneficiaryTerms = [기초생활수급자, 차상위계층]
targetGroupBuckets = [BENEFICIARY_SUPPORT]
```

## retrieval 경계

현재 [RetrievalService.java](../backend/src/main/java/com/example/welfare/recommend/service/RetrievalService.java)는:

- DB SQL 후보 조회
- youth filter
- age keyword fallback

만 수행한다.

canonical 전환 후 권장 흐름:

1. `WelfareServiceRepository.findCandidates*`
   - 기존 candidate row 조회
2. `CanonicalRecommendationReadModelRepository`
   - candidate service ids 기준 sidecar projection 로드
3. `RetrievalService`
   - structured facts/buckets 기반 후처리 필터

즉 retrieval은:

- base entity query
- sidecar projection hydrate

2단계 구조가 된다.

## scoring 경계

현재 [RuleScoringService.java](../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)는 `ServiceTag` raw를 읽는다.

canonical 전환 후 권장:

- `interestThemes` -> 관심분야 매칭
- `targetGroupsRaw` -> 기존 broad target group 매칭
- `targetGroupBuckets` -> beneficiary dedupe bonus
- `factKeys` / structured facts -> hard/soft eligibility signal

beneficiary 관련 규칙:

- raw beneficiary term 개수와 무관하게
- `BENEFICIARY_SUPPORT` bucket match bonus는 서비스당 최대 1회

priority 가중치 경계:

- [DefaultPriorityMatcher.java](../backend/src/main/java/com/example/welfare/recommend/service/DefaultPriorityMatcher.java)는 1차 전환에서 `RecommendationCandidateProjection.unifiedCategoryCompat`, `applyEndDate` 만 병행 입력으로 읽는다
- `unifiedCategoryCompat` 자체도 현재는 read-model 계산값이 아니라 저장된 compat layer를 읽는 것으로 본다. 저장/계산 경계는 [policy-normalization-compat-storage-policy.md](./policy-normalization-compat-storage-policy.md)를 따른다
- canonical taxonomy summary code/label(`youth_major_code`, `gov24_service_field_code`)은 아직 priority matcher가 직접 해석하지 않는다
- 즉 priority는 당분간 `compat_unified_category` 기반 호환 레이어를 유지하고, taxonomy summary code 직독은 후속 inventory/매핑표 결정 이후로 미룬다
- 특히 `compat=기타 + youth_major 채움` 집합도 현재 단계에서는 `unifiedCategoryCompat` 를 canonical major로 override 하지 않는다. canonical `youth_major` 는 projection 안에 보조 힌트로만 실어 두고, priority/scoring category bonus는 계속 compat layer만 기준으로 계산한다
- explicit `youth_major -> priority bucket` bridge table도 아직 도입하지 않는다. 그런 table은 실제 실험/전환이 시작될 때만 추가하고, 현재 read-model은 raw compat layer와 canonical hint를 나란히 보존하는 데서 멈춘다
- `교육 -> 교육·직업훈련` 실험이 필요하더라도 삽입 위치는 `DefaultPriorityMatcher` 가 아니라 `RuleScoringService` 의 narrow priority bonus 경계다. matcher 는 category contract를 유지하고, scoring layer만 feature flag 아래서 additive bonus를 열 수 있게 둔다

## response/UI 경계

추천 응답에서 explanation/badge 용으로는 raw label을 유지한다.

예:

- badge:
  - `기초생활수급자`
  - `차상위계층`
- score computation:
  - `BENEFICIARY_SUPPORT`

즉 score와 UI는 같은 source를 보지 않는다.

## 왜 repository에서 바로 collapse 하지 않는가

repository SQL에서 미리 한 label로 collapse 하면:

- raw explanation 손실
- source drift 분석 어려움
- future bucket 정책 변경 비용 증가

그래서:

- DB 저장은 raw
- projection은 raw + deduped bucket 둘 다
- scoring은 deduped bucket

으로 분리한다.

## 후속 구현 순서

1. `RecommendationCandidateProjection` DTO 초안 추가
2. `CanonicalRecommendationReadModelRepository` 초안 추가
3. `service_id IN (...)` 기준 sidecar projection 로드 쿼리 추가
4. beneficiary raw term -> `BENEFICIARY_SUPPORT` bucket 변환 유틸 추가
5. `RuleScoringService` 가 raw `ServiceTag` 대신 projection을 병행 읽도록 전환

추천 본체 전체의 더 큰 이행 순서는
[policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
를 따른다.

## 비목표

- 이번 단계에서 retrieval SQL을 sidecar join으로 전면 교체하지 않음
- 이번 단계에서 `RuleScoringService` 코드를 바로 바꾸지 않음
- beneficiary를 hard fact 로 승격하지 않음
