# `교육 -> 교육·직업훈련` priority 실험 후보 결정

2026-04-30 기준 canonical `youth_major` bridge candidate review 후,
`교육 -> 교육·직업훈련` 을 실제 실험 대상으로 볼지 정리한 문서입니다.

관련 문서:

- [policy-normalization-compat-other-youth-major-bridge-review.md](./policy-normalization-compat-other-youth-major-bridge-review.md)
- [policy-normalization-priority-bridge-table-policy.md](./policy-normalization-priority-bridge-table-policy.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)

## 결론

**예, 첫 실험 후보로는 적절합니다.**  
다만 조건은 분명합니다.

1. 기본 동작을 바로 바꾸지 않는다
2. `explicit bridge table` 전면 도입이 아니라 실험 범위만 좁게 연다
3. 실험 대상은 `compat=기타 + youth_major=교육` 집합으로 한정한다

즉:

- production default priority는 그대로 `compat_unified_category`
- canonical `교육` 신호는 실험 플래그 아래에서만 사용

으로 정리합니다.

## 왜 `교육` 이 첫 후보인가

row-level review에서 `교육` 집합(`102건`)은 다음 세 축이 중심이었습니다.

- `미래역량강화` `71`
- `교육비지원` `16`
- `온·오프라인교육` 계열 `11`

sample도:

- `대학일자리플러스센터 운영`
- `지역특화 청년 무역전문가 양성사업`
- `장학금 지원`
- `학자금대출 이자 지원`
- `취업 아카데미 운영`

처럼 현재 `교육·직업훈련` priority bucket과 크게 어긋나지 않았습니다.

`참여권리`, `복지문화` 와 달리:

- 운영 인프라 성격 혼입이 상대적으로 적고
- 건강/문화/생활지원처럼 다른 bucket과 충돌하는 항목도 적습니다.

## 왜 바로 기본값으로 승격하지 않는가

그래도 즉시 기본값 변경은 하지 않습니다.

이유:

1. 지금 priority 계약은 여전히 `compat_unified_category`
2. `교육비지원` 과 `직업훈련` 이 UX상 같은 우선순위로 소비되는지 검증이 아직 없음
3. canonical major를 기본값으로 쓰기 시작하면
   - matcher
   - scoring
   - 응답 category
   의미가 함께 바뀔 수 있음

따라서 이건 **기본 동작 변경**이 아니라
**제어된 read-model 실험**으로 다뤄야 합니다.

## 권장 실험 범위

실험 범위는 최대한 좁게 잡습니다.

### 대상 row

- `compat_unified_category = 기타`
- `youth_major_label = 교육`

만 포함

즉 이미 `compat=교육·직업훈련` 으로 분류된 기존 row는 건드리지 않습니다.

### 영향 지점

1. `DefaultPriorityMatcher`
2. `RuleScoringService` priority bonus

중 하나만 먼저

권장:

- 먼저 `priority match bonus` 에만 제한적으로 연결
- response category override는 하지 않음

### 비영향 지점

- 검색 category filter
- API 응답 category label
- 저장된 `compat_unified_category`

는 그대로 유지

## 실험 형태

권장 형태:

1. feature flag 또는 read-model experiment flag
2. `compat=기타 + youth_major=교육` 이고
3. 사용자 priority에 `EDUCATION` 이 있을 때만
4. 기존 `교육·직업훈련` match와 같은 bonus를 추가

즉 실험은:

- narrow audience
- narrow candidate set
- narrow scoring change

3개를 동시에 만족해야 합니다.

## 실험 전에 필요한 것

1. 어떤 레이어에서 실험할지 결정
   - `DefaultPriorityMatcher` level
   - 또는 `RuleScoringService` level

2. 실험 on/off가 가능해야 함
   - feature flag
   - config flag
   - experiment-only branch

3. 관찰 포인트 정의
   - `EDUCATION` priority 사용자에서 top-N 변화
   - `compat=기타 + youth_major=교육` row 유입 수
   - 추천 explanation 혼란 여부

## 지금 하지 않는 것

현재 단계에서 하지 않는 것:

- 전체 `youth_major -> priority` bridge table 생성
- `교육` 집합 전부를 response category `교육·직업훈련` 으로 바꾸기
- `compat_unified_category` 저장값 재계산
- `참여권리`, `복지문화` 까지 함께 실험

## 최종 정책

정리하면:

- `교육 -> 교육·직업훈련` 은 **첫 실험 후보로 승인**
- 하지만 **기본 동작 변경은 보류**
- **narrow experiment only**

## 다음 작업

1. 실험이 필요하다면 `DefaultPriorityMatcher` vs `RuleScoringService` 중 어디에 좁게 넣을지 결정
2. 실험 flag 단위 정의
3. `참여권리` 의 `청년참여` subset bridge는 별도 트랙으로 유지
