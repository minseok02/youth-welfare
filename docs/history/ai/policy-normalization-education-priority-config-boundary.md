# `교육 -> 교육·직업훈련` priority 실험 config 경계

2026-04-30 기준 `교육 -> 교육·직업훈련` narrow experiment를 실제 코드에 넣을 때,
`RuleScoringService` 안에서 flag를 어디서 어떻게 읽을지 정리한 문서입니다.

관련 문서:

- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)
- [policy-normalization-education-priority-flag-scope.md](./policy-normalization-education-priority-flag-scope.md)
- [policy-normalization-recommendation-read-model.md](../../policy-normalization-recommendation-read-model.md)
- [policy-normalization-education-priority-implementation-slot.md](./policy-normalization-education-priority-implementation-slot.md)

## 결론

config 경계는 **`RuleScoringService` 내부의 private helper 1개 + `@Value` boolean 주입**으로 둡니다.

권장 형태:

1. `RuleScoringService` 에
   - `@Value("${recommend.priority.education-canonical-bonus.enabled:false}")`
   - `private boolean educationCanonicalBonusEnabled;`
2. `applyPriorityWeight(...)` 또는 그 바로 아래 helper 에서만 읽음
3. `DefaultPriorityMatcher`, `RecommendationFacade`, repository 계층으로 flag read 를 퍼뜨리지 않음

즉:

- flag read 책임은 `RuleScoringService`
- helper 책임은 `narrow experiment applicability 판단`
- matcher/repository 는 계속 모름

## 왜 `RuleScoringService` 안에서 끝내는가

이 실험은 현재:

- narrow candidate scope
- narrow user scope
- narrow bonus scope

만 건드립니다.

따라서 flag 제어도 같은 계층에 있어야 합니다.

만약 flag를:

- `DefaultPriorityMatcher`
- `RecommendationFacade`
- 별도 `ExperimentPolicyService`
- read-model repository

까지 퍼뜨리면,
실험이 코드 구조상 더 큰 기능처럼 굳어질 수 있습니다.

지금 필요한 건 rollout framework 가 아니라,
**priority bonus 1개를 선택적으로 더하는 가장 작은 토글**입니다.

## helper 경계

권장 helper 예시는 이 정도입니다.

- `isEducationCanonicalBonusEnabled(...)`
- 또는 `matchesEducationCanonicalExperiment(...)`

이 helper는 아래 조건만 함께 판단합니다.

1. global flag on
2. `PriorityPreference.code() = EDUCATION`
3. `projection.unifiedCategoryCompat() = 기타`
4. `projection.youthMajorLabel() = 교육`

즉 helper는:

- config read
- experiment eligibility read

를 같이 묶고,
실제 bonus 숫자 계산은 기존 priority bonus 흐름에 그대로 맡깁니다.

## 왜 별도 config class를 만들지 않는가

현재 저장소는 작은 토글에 대해
복잡한 실험용 config object를 남발하는 구조가 아닙니다.

실제 패턴도:

- `@Value("${openai.model:gpt-4o-mini}")`
- `@Value("${chat.rate-limit.max-requests:5}")`
- `@Value("${auth.password-reset.expiration-minutes:30}")`

처럼 작은 설정은 바로 주입하는 형태가 많습니다.

이번 실험도:

- key 1개
- boolean 1개
- read site 1곳

이므로 별도 `@ConfigurationProperties` 객체까지 만들 이유가 약합니다.

## 지금 하지 않는 것

현재 단계에서 하지 않는 것:

- `EducationPriorityExperimentProperties` 같은 별도 config class
- `ExperimentPolicyService` 같은 공용 서비스
- repository / matcher / facade 레벨 flag branching
- multi-flag 조합

## 이유

bridge candidate 첫 실험은
아직 장기 운영 정책이 아니라 **제한적 검증**입니다.

설정 객체와 서비스 계층을 먼저 키우면:

- 실험을 정식 기능처럼 굳혀 버리고
- 나중에 제거/수정 비용이 커지고
- “현재 좁은 실험”이라는 사실이 코드에서 흐려집니다.

따라서 지금은:

- 가장 작은 주입
- 가장 작은 helper
- 가장 좁은 read site

로 끝내는 편이 맞습니다.

## 최종 정책

정리하면:

- flag key는 `recommend.priority.education-canonical-bonus.enabled`
- read location은 `RuleScoringService` 내부
- 구현 경계는 `@Value` + private helper
- matcher/repository/facade 로는 전파하지 않음

## 다음 작업

1. 실험 켠 상태에서 sample top-N 변화 검증 기준 정의
2. `참여권리` 의 `청년참여` subset bridge 여부 결정
3. 실제 구현 시 helper 이름과 bonus slot 위치를 최소 diff로 넣기
