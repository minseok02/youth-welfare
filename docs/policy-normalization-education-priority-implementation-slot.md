# `교육 -> 교육·직업훈련` priority 실험 helper / slot 계획

2026-04-30 기준 `교육 -> 교육·직업훈련` narrow experiment를 실제 코드에 넣을 때,
`RuleScoringService` 내부에서 helper 이름과 bonus slot 위치를 어떻게 잡을지 정리한 문서입니다.

관련 문서:

- [policy-normalization-education-priority-config-boundary.md](./policy-normalization-education-priority-config-boundary.md)
- [policy-normalization-education-priority-validation-criteria.md](./policy-normalization-education-priority-validation-criteria.md)
- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)

## 결론

권장 helper 이름은:

- `matchesEducationCanonicalPriorityExperiment(...)`

입니다.

삽입 위치는:

- [RuleScoringService.java](../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)
  `applyPriorityWeight(...)`
  내부 `priorities.stream().filter(...)`
  경계입니다.

즉 최소 diff 형태는 개념적으로 아래와 같습니다.

```java
.filter(p -> priorityMatcher.matches(p, service, projection)
        || matchesEducationCanonicalPriorityExperiment(p, projection))
```

여기서 helper는:

- `EDUCATION` priority 인지
- global flag on 인지
- `projection.unifiedCategoryCompat() = 기타`
- `projection.youthMajorLabel() = 교육`

만 확인합니다.

## 왜 이 이름인가

`isEducationCanonicalBonusEnabled(...)` 도 가능하지만,
이번 helper는 단순 flag read만 하지 않고
실제 experiment applicability 전체를 판정합니다.

즉 의미상:

- `is...Enabled` 보다는
- `matches...Experiment`

가 더 정확합니다.

helper가 판정하는 것은
“이 priority / projection 조합이 현재 교육 실험 대상인가”이지,
단순 boolean toggle 그 자체가 아니기 때문입니다.

## 왜 이 slot인가

현재 [RuleScoringService.java](../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java) 의
priority 가중치 계산은 아래 경계로 닫혀 있습니다.

1. `PriorityMatcher` 로 매칭 판정
2. 매칭된 priority 중 `maxWeight` 선택
3. `base * maxWeight`

여기서 가장 작은 diff는
**1번 필터 조건만 좁게 확장**하는 것입니다.

즉:

- `calcBaseScore(...)` 는 그대로 둠
- special target / beneficiary / deadline bonus 는 그대로 둠
- `PriorityMatcher` contract 는 그대로 둠
- `applyPriorityWeight(...)` 의 match 후보만 좁게 늘림

이 방식이면 실험이 실제로 바꾸는 것은
`priority weight candidate set`
하나뿐입니다.

## 지금 넣지 않는 위치

현재 단계에서 넣지 않는 위치:

1. `calcBaseScore(...)`
2. `DefaultPriorityMatcher`
3. `RecommendationFacade`
4. `CanonicalRecommendationReadModelRepository`

이유:

- `calcBaseScore(...)` 에 넣으면 priority 가 아니라 rule bonus semantics 로 섞임
- `DefaultPriorityMatcher` 에 넣으면 category contract 변경이 됨
- `Facade` 에 넣으면 retrieval/scoring 경계 밖으로 실험이 새어 나감
- repository 에 넣으면 read-model 자체가 실험-aware 가 됨

## 구현 스케치

helper 시그니처 권장안:

```java
private boolean matchesEducationCanonicalPriorityExperiment(
        PriorityPreference priority,
        RecommendationCandidateProjection projection
)
```

판정 순서 권장안:

1. flag off → false
2. `priority.code() != EDUCATION` → false
3. `projection == null` → false
4. `projection.unifiedCategoryCompat() != 기타` → false
5. `projection.youthMajorLabel() != 교육` → false
6. 나머지 → true

이 순서면:

- cheap guard 먼저
- null-safe
- diff 최소

를 동시에 만족합니다.

## 최종 정책

정리하면:

- helper 이름은 `matchesEducationCanonicalPriorityExperiment(...)`
- slot 위치는 `applyPriorityWeight(...)` 의 `filter(...)` 경계
- 구현은 `priorityMatcher.matches(...) || helper(...)` 형태
- 다른 scoring slot / matcher / repository 는 건드리지 않음

## 다음 작업

1. `교육 -> 교육·직업훈련` 실험 sample replay 절차를 실제 명령 수준으로 구체화
2. `참여권리` 의 `청년참여` subset bridge 여부 결정
3. 실제 구현 PR에서 helper / filter diff만 최소 변경으로 넣기
