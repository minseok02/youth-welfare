# `교육 -> 교육·직업훈련` priority 실험 flag 범위

2026-04-30 기준 `교육 -> 교육·직업훈련` narrow experiment를 실제로 켜야 할 때,
어떤 flag를 쓰고 어디까지 on/off 범위를 허용할지 정리한 문서입니다.

관련 문서:

- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)
- [policy-normalization-recommendation-read-model.md](../../policy/policy-normalization-recommendation-read-model.md)
- [policy-normalization-priority-bridge-table-policy.md](../policy/policy-normalization-priority-bridge-table-policy.md)
- [policy-normalization-education-priority-config-boundary.md](./policy-normalization-education-priority-config-boundary.md)

## 결론

flag는 **전역 boolean 1개**로 둡니다.

권장 key:

`recommend.priority.education-canonical-bonus.enabled`

기본값:

`false`

즉:

- 기본 배포 상태는 항상 off
- 켤 때도 전체 서비스에서 같은 규칙으로 켬
- 사용자별 / 비율별 / 환경별 다중 분기는 지금 넣지 않음

## 왜 boolean 하나로 고정하는가

이번 실험은 이미 범위가 충분히 좁습니다.

실험 대상은 아래 4개를 동시에 만족하는 경우뿐입니다.

1. `compat_unified_category = 기타`
2. `RecommendationCandidateProjection.youthMajorLabel = 교육`
3. 사용자 priority 에 `EDUCATION` 포함
4. 추천 scoring 경로에서 priority bonus 계산 중

즉 row와 user 조건이 이미 좁기 때문에,
여기에 rollout percent 나 user allowlist까지 추가하면:

- 추천 회귀 원인 분리 어려움
- 로그/설명 복잡도 증가
- 문서와 운영 판단 경계가 흔들림

이번 단계에서는 **조건 집합 자체가 실험 범위**이고,
flag는 그 실험 전체를 켜고 끄는 스위치만 맡는 편이 맞습니다.

## 허용 범위

flag `on` 일 때도 실제 bonus가 적용되는 범위는 아래로 제한합니다.

### candidate scope

- `compat_unified_category = 기타`
- canonical `youth_major = 교육`

### user scope

- 사용자 priority 목록에 `EDUCATION` 포함

### scoring scope

- `RuleScoringService` 의 priority bonus 경계만

### non-goals

다음은 flag가 켜져도 바꾸지 않습니다.

- `DefaultPriorityMatcher` 기본 match semantics
- API 응답 category label
- 저장된 `compat_unified_category`
- 검색 category filter
- beneficiary / interest / target group bonus 규칙

## 구현 권장 형태

현재 저장소 설정 스타일에 맞춰,
가장 단순한 `@Value` boolean read로 시작하는 것을 권장합니다.

예:

- `@Value("${recommend.priority.education-canonical-bonus.enabled:false}")`

이번 단계에서는 별도 `@ConfigurationProperties` 계층이나
실험 전용 complex config object까지 만들 필요는 없습니다.

## 환경별 운영 원칙

### local

- 기본 `false`
- 수동 검증 시에만 local override

### prod-like rehearsal

- default `false`
- 실험 확인이 필요할 때만 임시 override

### production

- default `false`
- narrow validation 전까지 상시 off 유지

## 지금 하지 않는 것

현재 단계에서 하지 않는 것:

- percentage rollout
- user allowlist rollout
- priority rank별 다른 실험
- `교육` 외 다른 canonical major 동시 실험
- response/UI category override

## 이유

이 실험의 목적은
`canonical 교육 신호가 priority bonus에만 좁게 도움 되는지`
보는 것입니다.

즉 실험 단위는:

- 하나의 bridge candidate
- 하나의 scoring slot
- 하나의 boolean flag

여야 합니다.

여기서 실험 제어 장치를 더 늘리면,
오히려 “무엇 때문에 순위가 달라졌는가”가 다시 흐려집니다.

## 최종 정책

정리하면:

- flag는 `recommend.priority.education-canonical-bonus.enabled`
- 기본값은 `false`
- on/off 범위는 전역 boolean만
- 실제 영향 범위는 이미 좁은 row/user/scoring 조건으로 제한

## 다음 작업

1. `RuleScoringService` 안에서 이 flag를 어떤 helper 경계에서 읽을지 결정
2. 실험 켠 상태의 sample top-N 변화 검증 기준 정의
3. `참여권리` 의 `청년참여` subset bridge는 별도 트랙으로 유지
