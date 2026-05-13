# `교육 -> 교육·직업훈련` priority 실험 검증 기준

2026-04-30 기준 `교육 -> 교육·직업훈련` narrow experiment를 실제로 켰을 때,
sample top-N 과 explanation 관점에서 무엇을 통과 기준으로 볼지 정리한 문서입니다.

관련 문서:

- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)
- [policy-normalization-education-priority-flag-scope.md](./policy-normalization-education-priority-flag-scope.md)
- [policy-normalization-education-priority-config-boundary.md](./policy-normalization-education-priority-config-boundary.md)
- [recommendation-pipeline.md](../../recommendation/recommendation-pipeline.md)

## 목적

이 실험은
`compat=기타 + youth_major=교육`
집합에 대해
`RuleScoringService` priority bonus 를 1회 더 주는 것이
실제 추천 품질에 도움이 되는지 보는 것입니다.

따라서 검증 기준도:

- 전체 추천 품질 재평가
- category 체계 개편
- AI reason 해석 변경

이 아니라,
**작은 bonus 추가가 top-N 을 유의미하게 개선하는지**
에만 맞춥니다.

## 기본 비교 방법

같은 사용자 snapshot, 같은 candidate pool 에 대해
아래 두 결과를 나란히 비교합니다.

1. flag `off`
2. flag `on`

비교 대상은 최소 2종입니다.

### sample A

- `EDUCATION` priority 를 가진 사용자
- candidate pool 안에 `compat=기타 + youth_major=교육` row 가 실제 포함되는 경우

### sample B

- 같은 사용자이지만
- candidate pool 안에 해당 row 가 없거나
- `EDUCATION` priority 가 없는 경우

즉:

- 실험이 작동해야 하는 케이스 1개 이상
- 실험이 아무 영향도 주면 안 되는 케이스 1개 이상

를 같이 봅니다.

## top-N 통과 기준

### 기대되는 변화

flag `on` 에서 아래 변화는 **허용/기대**합니다.

- `compat=기타 + youth_major=교육` row 가 top-N 안으로 진입
- 이미 top-N 에 있던 해당 row 가 더 위로 이동
- 기존 `교육·직업훈련` compat row 와 가까운 위치로 재정렬

### 기대되지 않는 변화

다음 변화는 **보류 또는 실패 신호**로 봅니다.

1. `EDUCATION` priority 가 없는 사용자에게 top-N 변화 발생
2. `compat=기타 + youth_major=교육` 와 무관한 row 가 다수 연쇄 이동
3. `JOB`, `HOUSING`, `DEADLINE` 성격 row 가 상위권에서 밀리며 전체 top-N 의미가 흐려짐
4. 동일한 canonical `교육` row 가 너무 과하게 올라가 non-education top result를 광범위하게 밀어냄

## 최소 통과 조건

실험을 “계속 볼 가치가 있다”고 판단하려면
아래를 만족해야 합니다.

1. target sample 에서만 순위 변화가 발생
2. 변화한 row 중 적어도 1개는 사람이 보기에 실제 `교육·직업훈련` priority와 잘 맞음
3. top-5 전체 의미가 무너지지 않음
4. non-target sample 에서는 top-N 이 사실상 유지됨

여기서 “사실상 유지”는:

- 순위 완전 동일이 가장 좋고
- 달라져도 실험 대상 row와 직접 연결된 최소 변화만 허용

으로 해석합니다.

## explanation 검증 기준

현재 추천 응답은 [RecommendationResponse.java](../../../backend/src/main/java/com/example/welfare/recommend/dto/RecommendationResponse.java) 기준으로

- `unifiedCategory`
- `aiReason`

를 그대로 보여줍니다.

이번 실험은 이 둘을 바꾸지 않습니다.

따라서 explanation 검증 기준은 아래입니다.

### 반드시 유지되어야 하는 것

1. `RecommendationResponse.unifiedCategory` 는 계속 legacy compat 값
2. AI reason 문자열은 실험 때문에 별도 rewrite 하지 않음
3. `교육` canonical hint 는 현재 응답 계약에 새 필드로 노출하지 않음

### 보면 안 되는 것

다음이 보이면 실험 설계가 범위를 넘은 것입니다.

- `compat=기타` row 가 응답에서 갑자기 `교육·직업훈련` 으로 표기됨
- AI reason 없이 category label만 바뀌어 설명이 더 혼란스러워짐
- narrow bonus 실험인데 response semantics 까지 같이 바뀜

## 체크리스트

실험 검증 시 최소 체크리스트:

1. flag `off` / `on` 같은 user snapshot 으로 추천 refresh 2회 실행
2. top-10 serviceId / title / unifiedCategory / finalScore 비교
3. `compat=기타 + youth_major=교육` row 의 위치 변화 확인
4. `EDUCATION` priority 없는 control sample 에서 top-10 변화 확인
5. 응답 `unifiedCategory` 가 바뀌지 않았는지 확인
6. `aiReason` 가 비정상적으로 더 어색해지지 않았는지 sample 확인

## 보류 조건

아래 중 하나라도 보이면
실험은 바로 default 후보로 올리지 않고 보류합니다.

1. non-target sample 에서도 top-N 이 흔들림
2. target sample 에서 올라온 row 가 사람이 보기엔 `교육·직업훈련` 과 잘 안 맞음
3. top-5 의 핵심 정책이 밀려 overall utility 가 떨어짐
4. response category/explanation 혼란이 생김

## 최종 정책

정리하면:

- 검증 대상은 top-N 순위 변화와 explanation 안정성
- target sample 에서만 좁게 움직여야 함
- response `unifiedCategory` / `aiReason` 의미는 유지되어야 함
- 의미 있는 개선이 없거나 비대상 sample 까지 흔들리면 보류

## 다음 작업

1. 실제 구현 시 `RuleScoringService` 내부 helper 이름과 bonus slot 위치를 최소 diff로 확정
2. `참여권리` 의 `청년참여` subset bridge 여부 결정
3. 교육 실험이 유효하면 다음에만 sample replay procedure를 실제 명령 수준으로 구체화
