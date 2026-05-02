# 추천 현재 동작 기준

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)

## 목적

이 문서는 현재 코드 기준으로 추천이 어떻게 동작하는지,
무엇이 현재 계약이고 무엇이 실험/보조 신호인지 빠르게 확인하기 위한 current-state 문서입니다.

## 현재 추천 파이프라인

현재 추천 흐름은 아래 순서입니다.

1. `RetrievalService`
2. `RuleScoringService`
3. `AiScoringService`
4. `ReRankingService`
5. `RecommendationPersistenceService`

조회는 저장된 `user_recommendations` 를 읽는 구조입니다.

## 현재 retrieval 기준

기본 축:

- 상태 `ACTIVE/UPCOMING`
- 나이
- 지역
- 소득

주의:

- `min_income=0 && max_income=0` 은 source와 무관하게 미지정 sentinel 로 보고 pass-through
- 복지로 계열은 소득 구조화 값이 약해서 사실상 pass-through가 많음

즉 현재 candidate pool 은 “정확한 hard gate 전부” 가 아니라
`구조화된 것은 필터`, 나머지는 `태그/후속 scoring 보조` 에 가깝습니다.

## 현재 scoring 기준

### rule score

현재 핵심 신호:

- interest match
- keyword match
- target group match
- deadline soon

제거된 것:

- `onlineApply`
- 단순 `sourceType=YOUTH`
- 구조화되지 않은 큰 지원금 액수

### priority weight

현재 우선순위는 `rule_base_score * maxApplicableWeight` 구조입니다.

즉 여러 priority가 동시에 맞아도 최고 배율 하나만 적용합니다.

## 현재 canonical bridge

현재 recommendation 은 canonical full migration 상태가 아닙니다.

대신 아래가 이미 들어와 있습니다.

- `CanonicalRecommendationReadModelRepository`
- `RecommendationCandidateProjection`
- `unifiedCategoryCompat`
- `youthMajorLabel`
- `factKeys`
- target group bucket
- `projection.youthRelevant` 우선 사용, heuristic은 projection 부재 시 fallback
- `projection.audienceRelevanceBonus`, `projection.specialTargetBuckets` 우선 사용, legacy text heuristic은 fallback
- priority 매칭은 `projection.priorityBuckets` 우선 사용, compat 문자열 비교는 fallback
- education narrow experiment도 `projection.educationPriorityBoostEligible` 우선 사용, raw compat+youthMajor 조합은 fallback
- recommendation response의 `unifiedCategory` 는 여전히 compat contract지만, 응답 생성 시 projection compat 값을 우선 사용
- policy/search/detail/ranking/bookmark 응답도 `unifiedCategory` 의미는 compat contract를 유지하되, 값은 projection compat를 우선 사용

즉 canonical sidecar는 현재 recommendation 의 보조 입력입니다.

## 현재 response 계약

`RecommendationResponse.unifiedCategory` 는
아직 canonical taxonomy 대표값이 아니라
계속 legacy compat category 의미로 유지합니다.

즉 current contract는:

- response 대표 category = compat
- canonical taxonomy = hint / projection / experiment signal

입니다.

## 현재 education 실험 상태

이미 코드에 들어간 상태:

- `recommend.priority.education-canonical-bonus.enabled`
- `compat=기타 + youth_major=교육 + priority=EDUCATION`
- `RuleScoringService` narrow bonus

즉 education experiment는 설계 문서만 있는 것이 아니라
현재 코드/로컬 검증 기준으로 살아 있습니다.

## 현재 AI score 해석

현재 `ai_score` 는 deterministic truth가 아닙니다.

현재 제품 해석:

- `rule-only` replay = hard verification baseline
- `real-openai` replay = diagnostic / exploratory
- same prompt / seed / fingerprint 에도 drift 가능

즉 `ai_score exact equality` 는 현재 제품 보장 범위가 아닙니다.

## 현재 로컬 검증 기준

현재 로컬에서 다시 확인된 것은:

- auth/runtime smoke 통과
- actual collect 이후 downstream replay 통과
- `education replay` rule-only 성공
- broad regression 통과

대표 replay 결과:

- `A_top10_target=4->8`
- `B_top10_target=2->2`

즉 current local 기준으로는:

- collect
- sidecar
- recommendation downstream

이 다시 이어져 있습니다.

## 현재 병목/주의점

### 1. collect 이후 snapshot 품질 의존

recommendation/replay 는 collect와 sidecar snapshot 품질에 직접 의존합니다.

### 2. real OpenAI latency / variability

`rule-only` 와 달리 real OpenAI 모드에서는:

- 응답 시간 증가
- `ai_score` drift

가 남습니다.

### 3. fresh reset 뒤 sidecar 공백

runtime bootstrap이 자동으로 sidecar를 다 복구하는 건 아닙니다.

다만 local replay smoke는 helper로 self-heal 되게 보강돼 있습니다.

## 지금 정상으로 보는 것

아래는 현재 정상 범주입니다.

- `rule-only` replay 기준 target row 개선
- control sample count 유지
- `real-openai` 에서 score drift가 있어도 input trace는 동일

## 지금 장애로 보는 것

- collect/snapshot 없이 replay 자체가 불가능
- canonical sidecar row가 비어 projection이 깨짐
- rule-only baseline 에서도 target improvement가 사라짐
- retrieval/scoring/re-ranking contract가 regression 으로 무너짐

## 요약

1. 현재 recommendation contract는 아직 compat 중심입니다.
2. canonical sidecar는 projection/bonus/hint 로 병행 사용됩니다.
3. education experiment는 이미 코드에 들어가 있고 local replay로 검증됐습니다.
4. `ai_score` exact match는 현재 제품 보장 범위가 아닙니다.
5. notification 후보 선택은 현재 `[A, A, B?]` 슬롯 배치입니다.
