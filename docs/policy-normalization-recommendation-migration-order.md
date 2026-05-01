# 추천 본체 점진 이행 순서

이 문서는 canonical 정규화 구조를 추천 본체에 붙일 때

- `WelfareServiceRepository.findCandidates*`
- `RetrievalService`
- `RuleScoringService`
- `DefaultPriorityMatcher`

를 어떤 순서로 이행해야 하는지 고정합니다.

관련 문서:

- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-compat-storage-policy.md](./history/policy/policy-normalization-compat-storage-policy.md)
- [policy-normalization-unified-category-response-bridge.md](./history/policy/policy-normalization-unified-category-response-bridge.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

## 결론

추천 본체의 canonical 전환 순서는 아래 고정입니다.

1. `WelfareServiceRepository.findCandidates*`
2. `RetrievalService`
3. `RuleScoringService`
4. `DefaultPriorityMatcher`

즉:

- 후보 SQL을 먼저 안정화
- retrieval에서 canonical projection을 붙이고
- scoring은 그 다음에 bridge
- priority matcher는 가장 나중에 좁게 연결

순으로 간다.

## 왜 이 순서인가

### 1. repository는 pass/fail 경계다

`WelfareServiceRepository.findCandidates*` 는
후보 풀 자체를 결정합니다.

여기서:

- age/income pass-through 의미
- region filter
- active/upcoming status

가 먼저 잘못되면,
이후 retrieval/scoring/matcher를 아무리 바꿔도
대상 row가 후보 집합에 들어오지 않습니다.

실제로 `YOUTH 0/0 income` 문제도
scoring이 아니라 repository semantics에서 먼저 막혔습니다.

따라서 첫 단계는 항상
`candidate pool semantics` 를 안정화하는 것입니다.

### 2. retrieval은 hydrate/후처리 경계다

`RetrievalService` 는 현재:

- repository candidate query
- youth relevance filter
- age keyword fallback
- latest candidate merge

를 수행합니다.

여기에 canonical sidecar를 붙이더라도
첫 단계는
`legacy candidate list + canonical projection hydrate`
까지만 허용하는 것이 맞습니다.

즉 retrieval 단계의 역할은:

1. legacy 후보 집합은 유지
2. canonical projection을 service id 기준으로 병행 hydrate
3. high-confidence 후처리만 천천히 이관

입니다.

### 3. scoring은 raw tag와 canonical hint를 병행 소비하면 된다

`RuleScoringService` 는 pass/fail이 아니라
rank ordering 경계입니다.

따라서 retrieval이 projection을 안정적으로 공급한 뒤에야

- `interestThemes`
- `targetGroupsRaw`
- `targetGroupBuckets`
- `factKeys`

를 legacy `ServiceTag` fallback과 OR 방식으로 병행 읽을 수 있습니다.

이 순서를 지키면
“후보가 안 들어온 문제”와
“점수 브리지 문제”를 분리할 수 있습니다.

### 4. priority matcher는 category contract를 쥐고 있어서 가장 나중이다

`DefaultPriorityMatcher` 는 현재:

- `unifiedCategory`
- `applyEndDate`

의 의미를 직접 정의합니다.

여길 너무 일찍 canonical summary 직독으로 바꾸면

- priority semantics
- response category expectation
- replay validation

이 한 번에 흔들립니다.

그래서 matcher는 마지막 단계에서
`stored compat` 를 우선 읽는 좁은 bridge만 허용합니다.

## 단계별 정책

## 1단계: repository semantics 안정화

대상:

- `WelfareServiceRepository.findCandidates*`
- `findLatestCandidates*`

허용:

- source-specific sentinel semantics 정리
  - 예: `YOUTH 0/0 income => pass-through`
- age/income/region/status 의미 보정
- candidate pool coverage 확인용 integration test 추가

금지:

- sidecar join으로 후보 SQL 전면 교체
- canonical taxonomy summary를 SQL pass/fail에 직접 연결
- `unifiedCategory` 의미 변경

산출물:

- representative region / known-positive candidate composition test
- repository 단계에서 후보 풀 누락 원인 제거

## 2단계: retrieval hydrate + high-confidence 후처리

대상:

- `RetrievalService`
- `CanonicalRecommendationReadModelRepository`

허용:

- `RetrievedRecommendationCandidates`
- candidate ids 기준 projection hydrate
- high-confidence AGE 같은 후처리 보강
- legacy candidate list 유지

금지:

- retrieval이 raw sidecar row를 직접 해석해 scoring 의미까지 결정
- retrieval 단계에서 category contract 변경
- broad soft signal을 retrieval hard filter로 승격

산출물:

- `RetrievedRecommendationCandidates(candidates, projections)`
- retrieval/repository 분리 유지

## 3단계: scoring bridge

대상:

- `RuleScoringService`

허용:

- `interestThemes`, `keywordTags`, `targetGroupsRaw` 를 legacy tag와 OR 매칭
- beneficiary multi-term -> `BENEFICIARY_SUPPORT` bucket max-one bonus
- `factKeys` 를 helper 경계에서만 먼저 연결
- feature flag 아래 narrow experiment

금지:

- projection term 개수만큼 중복 가산
- legacy tag fallback 제거
- canonical signal을 hard filter처럼 사용

산출물:

- score regression tests
- projection-only / legacy-only / mixed-input regression

## 4단계: priority matcher bridge

대상:

- `PriorityMatcher`
- `DefaultPriorityMatcher`

허용:

- `RecommendationCandidateProjection.unifiedCategoryCompat`
- `RecommendationCandidateProjection.applyEndDate`

만 우선 읽는 좁은 bridge

금지:

- `youth_major`, `gov24_service_field` 직독
- matcher 안에서 canonical bridge experiment 추가
- response `unifiedCategory` 의미 변경

산출물:

- matcher regression tests
- compat layer 유지 문서화

## 단계별 입력/출력 경계

### repository

입력:

- user age/income/region

출력:

- `List<WelfareService>`

### retrieval

입력:

- repository candidate list
- service id 기반 sidecar rows

출력:

- `RetrievedRecommendationCandidates`

### scoring

입력:

- legacy `WelfareService`
- legacy `ServiceTag`
- canonical projection

출력:

- `ScoredCandidate`

### matcher

입력:

- `PriorityPreference`
- stored compat
- stored/applyEndDate summary

출력:

- priority match boolean

## 지금 하지 않는 순서

현재 금지하는 이행 순서:

1. `DefaultPriorityMatcher` 를 먼저 canonical summary direct-read로 교체
2. `RuleScoringService` 에서 raw tag fallback 없이 projection-only 전환
3. retrieval 이전에 sidecar join으로 candidate SQL 전면 교체
4. response `unifiedCategory` 의미를 canonical 쪽으로 먼저 변경

이 순서는 모두
원인 분리보다 효과를 먼저 섞는 방식이라 회귀 반경이 커집니다.

## 최종 정책

따라서 추천 본체의 canonical 이행은:

- repository semantics
- retrieval hydrate
- scoring bridge
- matcher bridge

순으로만 진행하고,
response contract 변경은 그 뒤 단계로 미룹니다.

## 다음 작업

1. 현재 문서 순서에 맞춰 `TextConstraintExtractor` 출력 모델을 `service_facts` 저장 규격 쪽으로 다시 정리
2. repository/retrieval/scoring/matcher 각 단계별 남은 legacy fallback 제거 조건 목록화
3. `welfare_services.unified_category` / compat mirror 장기 정리 순서 설계
