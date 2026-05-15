# 추천 현재 동작 기준

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)

## 목적

이 문서는 현재 코드 기준으로 추천이 어떻게 동작하는지,
무엇이 현재 계약이고 무엇이 실험/보조 신호인지 빠르게 확인하기 위한 current-state 문서입니다.

현재 제품 해석은 `청년정책 통합포털 + 개인화 추천` 이며, 추천 재사용 전략도 군집 캐시보다 개인 캐시를 우선 검토하는 쪽으로 정리합니다.
현재 코드 기준 개인 캐시는 추천 payload 전체를 Redis에 저장하는 구조가 아니라, `non-personal refresh` 를 최근에 끝냈는지 나타내는 짧은 TTL 마커만 저장하고 실제 추천 row 는 계속 DB에서 읽는 형태입니다. 이 마커 key 는 `userKey` 뿐 아니라 현재 추천 규칙 버전(예: `educationCanonicalBonusEnabled`)도 함께 포함해, 앱 재기동으로 추천 규칙 플래그가 바뀐 뒤 이전 refresh 결과를 재사용하지 않게 합니다.

## 현재 추천 파이프라인

현재 추천 흐름은 아래 순서입니다.

1. `RetrievalService`
2. `RuleScoringService`
3. `AiScoringService`
4. `ReRankingService`
5. `RecommendationPersistenceService`

조회는 저장된 `user_recommendations` 를 읽는 구조입니다.
또한 `POST /api/recommendations/refresh?personal=false` 는 최근 same-user refresh 마커가 살아 있으면 재계산을 생략하고 최신 저장 row 를 그대로 반환합니다. 반대로 `personal=true` refresh 는 항상 실계산하며, 프로필/우선순위/탈퇴 변경 시 refresh 마커는 즉시 invalidate 됩니다.

즉 현재 개인화의 기본 단위는 군집이 아니라 사용자입니다. 군집은 현재 `youth_all` 단일 경계로만 유지하고, 실제 추천 응답 가속도 먼저 `userKey` 기준 캐시로 해결합니다. 나이대×소득분위 2D 군집은 사용자 규모와 hit-rate가 충분히 커졌을 때만 다시 검토합니다.

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
- recommendation click smoke 통과
- actual collect 이후 downstream replay 통과
- `education replay` rule-only 성공
- broad regression 통과

대표 replay 결과:

- `A_top10_target=9->9`
- `B_top10_target=1->1`
- `A_fp=same`
- `B_fp=same`
- `reason_changed=0`

즉 current local 기준으로는:

- collect
- sidecar
- recommendation downstream
- CTR click instrumentation

이 다시 이어져 있습니다.

## 현재 병목/주의점

### 1. collect 이후 snapshot 품질 의존

recommendation/replay 는 collect와 sidecar snapshot 품질에 직접 의존합니다.

### 2. real OpenAI latency / variability

`rule-only` 와 달리 real OpenAI 모드에서는:

- 응답 시간 증가
- `ai_score` drift

가 남습니다.

### 3. CTR 표본 부족

클릭 추적 경계 자체는 현재 정상입니다.

- 추천 응답의 `serviceId + logId` 로 정책 상세 진입 시
- `recommendation_logs.is_clicked=1` 이 실제 DB에 기록됨

즉 현재 병목은 click instrumentation이 아니라 실사용 클릭 표본 부족입니다.

`2026-05-15` local audit 기준:

- total recommendation logs: `1872`
- clicked logs: `18`
- overall CTR: `0.96%`
- clicked users / services: `18 / 2`
- fallback sent/clicked: `1062 / 0`
- AI sent/clicked: `810 / 18`
- weight bucket:
  - `0.40:0.60` -> `1359 sent / 15 clicked / 1.10%`
  - `0.60:0.40` -> `366 sent / 3 clicked / 0.82%`
  - `0.80:0.20` -> `147 sent / 0 clicked / 0.00%`

즉 total log 수는 이미 top stage를 넘겼지만, 클릭 표본은 아직 얇아서 현재 readiness 판정은 `DEFERRED_CLICK_SAMPLE_THIN` 입니다. 게다가 클릭이 현재 `2`개 서비스(`2622`, `3688`)에만 몰려 있어 sample diversity도 부족합니다.
따라서 recommendation 쪽의 다음 active 작업은 지금 당장 weight tuning을 여는 것이 아니라, readiness baseline을 유지한 채 표본이 더 쌓일 때까지 bounded runtime/quality smoke 결과를 계속 관찰하는 것입니다.

### 3. fresh reset 뒤 collect/replay 전제

current PostgreSQL mainline에서 canonical sidecar 자체는 integrated schema의 일부입니다.

다만 fresh reset 뒤에는 replay가 기대하는 policy snapshot이나 canonical read-model 데이터가 비어 있을 수 있으므로,
local helper/replay smoke는 integrated schema 존재 여부와 collect/replay precondition을 먼저 확인하게 보강돼 있습니다.

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
6. 운영 지표는 `GET /api/admin/dashboard/summary` 에서 collect/recommendation/notification/search/user_pii_sync 묶음으로 조회합니다.
   recommendation 섹션에는 현재 active weight, 누적 recommendation log 수, latest clicked 시각, 최근 7일 weight bucket 분포가 포함됩니다.
   collect 섹션에는 최근 실패 run 목록, search 섹션에는 최근 7일 0건 검색 수가 포함됩니다.
   `trend` 섹션에는 collect/recommendation/search 의 1일/7일/30일 추세가 포함됩니다.
7. 추천 가중치/프롬프트 재조정은 CTR readiness audit가 `READY_FOR_WEIGHT_REVIEW` 를 줄 때에만 reopen 합니다.
