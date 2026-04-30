# `unifiedCategory` 응답 계약 호환 전략

이 문서는 canonical taxonomy/read-model 전환 중에도
기존 API 응답의

- `unifiedCategory`

필드를 어떻게 유지할지 정리합니다.

관련 문서:

- [policy-normalization-compat-storage-policy.md](./policy-normalization-compat-storage-policy.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-compat-other-youth-major-policy.md](./policy-normalization-compat-other-youth-major-policy.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

## 현재 응답 경계

현재 `unifiedCategory` 는 아래 응답 DTO에서 모두 직접 노출됩니다.

- `PolicySummaryResponse`
- `PolicyDetailResponse`
- `PolicyRankingResponse`
- `RecommendationResponse`

그리고 값은 모두 현재 `WelfareService.unifiedCategory` 에서 바로 옵니다.

즉 `unifiedCategory` 는 이미

- 검색/목록
- 상세
- 랭킹
- 추천

전반의 외부 계약입니다.

## 결론

**현재 phase에서는 응답 `unifiedCategory` 를 계속 legacy compat category로 유지합니다.**

구체적으로는:

1. 응답 필드 이름은 그대로 `unifiedCategory`
2. 의미도 계속 현재 compat category
3. canonical taxonomy는 이 필드를 조용히 덮어쓰지 않음
4. canonical summary는 response 보조 힌트/설명 후보로만 취급

즉 canonical 전환이 진행돼도
응답 `unifiedCategory` 는 당분간
`compat_unified_category` 의 외부 노출 창구로 봅니다.

## 왜 유지하는가

### 1. 이미 외부 계약으로 넓게 퍼져 있다

`unifiedCategory` 는 추천 응답 하나에만 있는 필드가 아닙니다.

동일한 의미를:

- 정책 검색/목록
- 정책 상세
- 랭킹
- 추천 응답

이 함께 사용하고 있습니다.

따라서 canonical major나 summary로 조용히 바꾸면,
추천만이 아니라 검색/상세/UI badge/filter까지 같이 흔들립니다.

### 2. current priority/response semantics가 compat bucket 위에 서 있다

현재 사용자 우선순위와 응답 표현은

- `주거`
- `일자리`
- `교육·직업훈련`
- `금융·생활지원`
- `참여·기회`

같은 compat bucket 의미를 전제로 합니다.

반면 canonical taxonomy는

- `교육`
- `복지문화`
- `참여권리`

처럼 다른 축을 가집니다.

둘은 아직 1:1 치환 규칙이 완전히 고정되지 않았습니다.

### 3. `compat=기타 + canonical youth_major 채움` 집합이 남아 있다

현재도:

- 응답 `unifiedCategory` 는 `기타`
- canonical `youth_major` 는 `교육/복지문화/참여권리`

인 row가 남아 있습니다.

이 집합은 현재:

- 자동 override 금지
- `교육`만 좁은 실험 후보
- 나머지는 inventory/future candidate

상태입니다.

따라서 response category까지 canonical 값으로 바꾸기엔 아직 이릅니다.

## 현재 브리지 규칙

### 1. response `unifiedCategory`

계속 아래 의미를 유지합니다.

- source: stored compat layer
- 목적: UI/필터/정렬/priority 호환

즉 현재는:

- `welfare_services.unified_category`
  또는 그와 동등한 stored compat

를 응답으로 보낸다고 간주합니다.

### 2. canonical taxonomy

현재 응답에서 canonical taxonomy는
`unifiedCategory` 를 대체하지 않습니다.

허용되는 역할:

- 내부 inventory
- read-model secondary hint
- explanation/badge 후보
- future experiment 조건

금지되는 역할:

- 응답 `unifiedCategory` 직접 치환
- search/ranking/recommendation category semantics 직접 변경

### 3. recommendation read-model

추천 내부에서는:

- `RecommendationCandidateProjection.unifiedCategoryCompat`
- canonical summary hint (`youthMajorLabel` 등)

을 같이 둘 수 있습니다.

하지만 외부 응답으로 나갈 때는
계속 compat layer를 대표값으로 봅니다.

## 현재 하지 않는 것

현재 phase에서 하지 않음:

- `RecommendationResponse.unifiedCategory` 를 canonical `youth_major` 로 치환
- `PolicySummaryResponse.unifiedCategory` 를 `service_taxonomies` summary 직독으로 전환
- `PolicyDetailResponse` / `PolicyRankingResponse` 의 category 의미 변경
- `compat=기타` 인 row를 response에서 canonical major로 자동 보정
- `unifiedCategoryCanonical` 같은 새 공개 응답 필드 추가

## 허용되는 보조 확장

추후 허용 가능한 것은 아래 정도입니다.

1. 상세/추천 explanation에 canonical badge를 보조로 추가
2. admin/debug 응답에서 canonical summary를 별도 필드로 노출
3. experiment 전용 내부 trace에 compat와 canonical을 같이 기록

단, 이 경우에도 public API의 `unifiedCategory` 의미는 바꾸지 않습니다.

## 언제 재검토할 수 있는가

아래 조건이 충족될 때만 응답 계약 재검토를 엽니다.

1. `compat_unified_category` 계산-only 전환 순서가 확정
2. `교육` 실험 외 bridge 후보에 대한 explicit 정책 정리
3. `GOV24_*`, `YOUTH_MID`, `supportConditions` source-of-truth 확보
4. 프론트/UI가 canonical secondary field를 실제로 소비할 준비 완료

즉 응답 계약 변경은
read-model 내부 전환보다 더 늦게 와야 합니다.

## 최종 정책

따라서 현재는:

- `unifiedCategory` 응답 계약을 유지한다
- 의미는 계속 legacy compat category다
- canonical taxonomy는 응답 대표값이 아니라 보조 힌트다
- response override는 명시적 bridge/전환 설계 없이는 하지 않는다

## 다음 작업

1. `WelfareServiceRepository.findCandidates*`, `RetrievalService`, `RuleScoringService`, `DefaultPriorityMatcher` 의 점진 이행 순서 설계
2. `welfare_services.unified_category` 와 `service_taxonomies.compat_unified_category_*` 의 장기 정리 순서 설계
3. 필요 시 canonical badge/explanation 을 public response가 아닌 debug/internal field부터 검토
