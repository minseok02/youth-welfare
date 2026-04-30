# canonical `youth_major` -> legacy priority bucket bridge table 필요 여부

2026-04-30 기준으로,

- `compat=기타 + canonical youth_major 채움` inventory
- row-level bridge candidate review

까지 끝낸 뒤, explicit bridge table을 지금 도입해야 하는지 정리한 문서입니다.

관련 문서:

- [policy-normalization-compat-other-youth-major-policy.md](./policy-normalization-compat-other-youth-major-policy.md)
- [policy-normalization-compat-other-youth-major-inventory.md](./policy-normalization-compat-other-youth-major-inventory.md)
- [policy-normalization-compat-other-youth-major-bridge-review.md](./policy-normalization-compat-other-youth-major-bridge-review.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)

## 결론

**지금은 explicit bridge table을 도입하지 않습니다.**

현재 단계의 정책은:

1. priority/scoring은 계속 `compat_unified_category` 기준 유지
2. canonical `youth_major` 는 secondary hint 유지
3. bridge table은 실제 실험/전환 착수 시점까지 보류

즉:

- `DefaultPriorityMatcher`
- legacy priority bonus
- response category

어느 곳도 아직 `youth_major -> priority bucket` 매핑표를 직접 읽지 않습니다.

## 왜 지금은 불필요한가

### 1. 현재 제품이 이미 stable compat layer를 갖고 있다

현재 priority는:

- `주거`
- `일자리`
- `교육·직업훈련`
- `금융·생활지원`
- `참여·기회`

같은 기존 bucket을 기준으로 충분히 동작합니다.

즉 bridge table이 없어서 당장 막힌 기능이 있는 상태가 아닙니다.

### 2. bridge 필요성이 candidate별로 다르다

row-level review 결과:

- `교육 -> 교육·직업훈련`
  - 가장 유력한 후보
- `참여권리 -> 참여·기회`
  - subset bridge만 조건부 후보
- `복지문화 -> 금융·생활지원`
  - 현 시점 보류

즉 현재는 “하나의 완성된 bridge table”을 만들기보다
후보마다 성격이 달라, 단일 정책으로 묶는 편이 더 위험합니다.

### 3. bridge table을 만들면 사실상 우선순위 의미가 바뀐다

explicit table은 단순 문서가 아니라

- read-model 계산값
- `DefaultPriorityMatcher`
- recommendation scoring 의미

를 바꾸는 계약입니다.

이걸 지금 넣으면 canonical 정규화 성공이 곧바로 제품 우선순위 변경으로 연결됩니다.

아직은:

- effect size 측정
- row-level QA
- subset rule

이 준비되지 않았습니다.

## 허용되는 현재 상태

### 유지

- `compat_unified_category`
- canonical `youth_major` summary
- candidate review 문서

### 보류

- `youth_major -> priority bucket` explicit table
- matcher direct-read
- response category override

## 언제 필요해지는가

아래 중 하나가 생기면 bridge table을 다시 검토합니다.

1. `교육 -> 교육·직업훈련` 단일 후보를 실제 실험 대상으로 승격하기로 결정할 때
2. `참여권리` 중 `청년참여` subset만 별도 bridge하기로 결정할 때
3. `compat_unified_category` 를 저장 필드가 아니라 read-model 계산값으로 바꾸려 할 때
4. canonical summary를 priority layer가 직접 읽는 2차 전환에 들어갈 때

즉 bridge table은 “정리 차원에서 미리 만들어 두는 것”이 아니라,
실제 전환/실험이 시작될 때 도입하는 artifact로 보는 편이 맞습니다.

## 현재 판단

### `교육`

- bridge table이 필요해진다면 가장 먼저 후보가 될 수 있음
- 하지만 지금은 table보다 “실험 필요 여부” 결정이 먼저

### `참여권리`

- 전집합 bridge는 불가
- 필요해도 subset rule이 먼저
- 즉 table보다 subset definition이 먼저

### `복지문화`

- 현재는 bridge보다 비승격 정책 유지가 맞음

## 최종 정책

따라서 현재 phase에서는:

- **explicit bridge table을 만들지 않는다**
- 대신 candidate review 결과만 문서로 유지한다
- bridge table이 필요해지는 첫 시나리오는
  `교육 -> 교육·직업훈련` 실험 착수 시점이다

## 다음 작업

1. `교육 -> 교육·직업훈련` 단일 후보를 실제 실험 대상으로 볼지 결정
2. `참여권리` 의 `청년참여` subset만 분리 bridge 후보로 둘지 결정
3. `compat_unified_category` 를 저장 필드로 유지할지, read-model 계산값으로 바꿀지 재검토
