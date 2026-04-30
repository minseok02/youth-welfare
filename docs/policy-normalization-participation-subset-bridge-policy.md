# `참여권리` 의 `청년참여` subset bridge 정책

`compat=기타 + youth_major=참여권리` 집합 중
`category_sub=청년참여` subset만
current priority bucket `참여·기회` 로 별도 bridge 후보로 둘지 정리한 문서입니다.

관련 문서:

- [policy-normalization-compat-other-youth-major-bridge-review.md](./policy-normalization-compat-other-youth-major-bridge-review.md)
- [policy-normalization-compat-other-youth-major-policy.md](./policy-normalization-compat-other-youth-major-policy.md)
- [policy-normalization-priority-bridge-table-policy.md](./policy-normalization-priority-bridge-table-policy.md)
- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)

## 결론

현재 단계에서는 아래처럼 둡니다.

1. `참여권리` 전체를 `참여·기회` 로 승격하지 않음
2. `청년참여` subset도 **지금 당장 실험/구현 대상으로 올리지 않음**
3. 다만 future bridge 후보 inventory로는 유지
4. 우선순위는 계속 `교육 -> 교육·직업훈련` 실험 다음 순번

즉 `청년참여` 는
**조건부 future candidate** 이지만,
이번 단계의 active experiment로는 승격하지 않습니다.

## 왜 전체 승격을 안 하나

이미 row-level review에서 확인한 대로
`참여권리` 안에는 아래가 섞여 있습니다.

- `청년참여`
- `정책인프라구축`
- `청년국제교류`

이 중 `청년참여` 는 현재 priority bucket `참여·기회` 와 가깝지만,
`정책인프라구축`, `플랫폼/센터 운영`, `인프라성 정책` 은
사용자 action opportunity와 같은 의미로 보기 어렵습니다.

따라서 전체 `참여권리` 승격은 계속 금지합니다.

## 왜 `청년참여` subset도 바로 올리지 않나

### 1. 지금 active bridge 실험은 이미 `교육` 이다

현재 canonical-to-priority narrow experiment는
`교육 -> 교육·직업훈련` 하나만 열어 두었습니다.

이 상태에서 `청년참여 -> 참여·기회` 까지 동시에 열면:

- 실험 해석이 분산되고
- `compat=기타` 집합에서 두 개의 별도 예외 규칙이 생기고
- control drift 해석도 복잡해집니다

즉 현재 단계에서는 실험 축을 늘리지 않는 편이 맞습니다.

### 2. `청년참여` 자체도 UX 의미가 완전히 균질하진 않다

대표 sample은 `홍보파트너 모집`, `청년마을만들기 사업 공모`, `청년자율공간 참여 사업자 모집`
처럼 비교적 `참여 opportunity` 에 가깝습니다.

하지만 같은 `청년참여` label 안에서도:

- 모집/공모
- 파트너/서포터즈
- 공간 참여

처럼 사용자 행동 비용과 기대 결과가 조금씩 다릅니다.

즉 `교육` 축보다도 current priority bucket에 주는 의미가 조금 더 broad 합니다.

### 3. 지금 제품 계약은 `compat` 우선 유지다

현재 priority / response category는
계속 `compat_unified_category` 를 기준으로 둡니다.

`청년참여` subset 하나만 예외 bridge로 열기 시작하면,
`복지문화` 나 다른 subset도 같은 방식으로 열고 싶어질 가능성이 큽니다.

지금은 이 패턴을 늘리기보다,
`교육` 실험의 효과/부작용을 먼저 충분히 본 뒤
2번째 candidate로 검토하는 편이 더 안전합니다.

## 현재 정책

### 1. priority / scoring

- `청년참여` subset bonus를 새로 만들지 않음
- `DefaultPriorityMatcher` 예외 규칙 추가 안 함
- `RuleScoringService` narrow bonus 추가 안 함

### 2. read-model

- `youthMajor=참여권리`
- raw/category_sub=`청년참여`

정보는 inventory / explanation 후보로만 유지 가능
- 현재 priority bridge 신호로 직접 소비하지 않음

### 3. response / UI

- `compat=기타` 를 `청년참여` 를 이유로 `참여·기회` 로 치환하지 않음
- 필요하면 future badge/explanation 후보 정도로만 검토

## 허용되는 다음 단계

아래 순서만 허용합니다.

1. `교육 -> 교육·직업훈련` 실험 효과/부작용을 먼저 관찰
2. 그 뒤에도 second bridge candidate가 필요하면
   `청년참여` subset만 별도 inventory / replay sample로 다시 좁힘
3. 그때도 전집합 bridge table이 아니라
   narrow experiment 후보로만 연다

즉 `청년참여` 는
**2순위 좁은 후보** 이지,
현재 즉시 구현 대상은 아닙니다.

## 하지 않는 것

현재 단계에서 하지 않는 것:

1. `참여권리` 전집합 승격
2. `청년참여` subset narrow bonus 구현
3. `참여·기회` 응답 category 치환
4. explicit bridge table에 `청년참여 -> 참여·기회` 를 먼저 추가

## 한 줄 요약

`청년참여` subset은 future bridge 후보로는 남기되, 지금 단계에서는 교육 실험 다음 순번의 조건부 후보로만 유지하고 바로 구현하지 않는다.
