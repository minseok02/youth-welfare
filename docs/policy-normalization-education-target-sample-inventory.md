# `교육 -> 교육·직업훈련` 실험 target sample inventory

2026-04-30 기준 local DB / local app replay 결과를 바탕으로,
`compat=기타 + youth_major=교육` target row가 실제 추천 결과 집합에 들어오는 sample이 있는지 정리한 문서입니다.

관련 문서:

- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)
- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)
- [policy-normalization-compat-other-youth-major-inventory.md](./policy-normalization-compat-other-youth-major-inventory.md)

## 결론

현재 local snapshot에서는 **known positive replay sample이 아직 없습니다**.

즉:

- DB inventory 상 `compat=기타 + youth_major=교육` row는 존재합니다
- age/pass 조건까지 만족하는 region pool도 존재합니다
- 하지만 실제 local replay에서 target row가 결과 집합에 들어온 sample은 아직 찾지 못했습니다

따라서 다음 디버깅 경계는 `RuleScoringService` 가 아니라
`RetrievalService -> candidate pool -> youth filter -> final saved recommendations` 경계입니다.

## 1. DB inventory 요약

local DB 기준 target row는 아래와 같았습니다.

- `compat=기타 + youth_major=교육 total = 102`
- `status ACTIVE/UPCOMING = 102`
- `search_youth_relevant = 79`

문제는 age gate입니다.

- `min_age=0 AND max_age=0` row가 많습니다
- retrieval SQL은 `NULL` 이 아니라 literal `0` 을 그대로 age filter에 쓰므로, 성인 사용자는 이 row를 대부분 통과하지 못합니다

예:

- `31200`: `target_total=38`, `age25_pass=9`, `age32_pass=10`, `zero_zero=27`
- `50110`: `target_total=34`, `age25_pass=9`, `age32_pass=10`, `zero_zero=23`
- `29155`: `target_total=27`, `age25_pass=9`, `age32_pass=10`, `zero_zero=16`
- `28110~28720`: 각 region `target_total=33`, `age25_pass=12`, `age32_pass=12`, `zero_zero=15`

즉 region만 많이 찍는다고 target sample이 자동으로 생기지 않고,
`zero/zero age` row를 제외한 age-pass pool이 실제 candidate/result set에 들어오는지 별도로 봐야 합니다.

## 2. 실제 replay scan

실제 local app(`OPENAI_API_KEY=invalid-for-rule-only-replay`, `flag off`)에서 아래 조합을 직접 태웠습니다.

공통 user snapshot:

- `priorityCodes=["EDUCATION"]`
- `interestFields=["교육"]`
- `incomeLevel=5`
- `employmentStatus=미취업`
- `displayCount=30`

scan 축:

- age `25` (`birthDate=2001-01-01`)
- age `32` (`birthDate=1994-01-01`)
- region
  - `28110`
  - `28140`
  - `28177`
  - `28185`
  - `28200`
  - `28237`
  - `28245`
  - `28260`
  - `28710`
  - `28720`
  - `31200`
  - `50110`
  - `29155`
  - `41220`
  - `46230`
  - `41461`

총 `16 regions × 2 ages = 32` 조합을 돌렸고 결과는 전부 같았습니다.

- response count: `14~16`
- `target_hits = 0`

즉, tested region 전부에서 `compat=기타 + youth_major=교육` row는 결과 집합에 한 건도 들어오지 않았습니다.

## 3. 관찰 포인트

### 3-1. region은 맞는데 target row가 안 나옴

예를 들어 `31200`, `50110`, `29155` 는 DB inventory 상 age-pass target row가 `9~10`건씩 있습니다.

하지만 실제 replay 결과의 `기타` category row는 계속 아래 계열만 반복됐습니다.

- `2300 청년내일저축계좌`
- `2396 청년내일채움공제`
- `2388 예술체육 비전장학금`

즉 target row가 “없어서”가 아니라,
현재 retrieval/result path에서 다른 `기타` row가 일관되게 우선 노출되고 있습니다.

### 3-2. sample miss의 성격이 바뀜

이전에는 `region 11680` 같은 단일 sample miss 수준이었습니다.

이번 scan 이후 결론은 더 강합니다.

- 단순히 sample을 한두 개 더 바꿔보는 문제는 아닙니다
- 현재 local snapshot에서는 **known positive sample이 없다** 가 더 정확합니다

## 4. 지금 바로 하지 말아야 할 것

- `RuleScoringService` bonus를 더 키우는 것
- `DefaultPriorityMatcher` 에 canonical override를 더 넣는 것
- region sample만 계속 늘려서 수동 replay를 반복하는 것

이건 target row가 candidate/result set에 실제로 들어오는지 확인되기 전에는 의미가 약합니다.

## 5. 다음 디버깅 경계

다음 작업은 아래 순서가 맞습니다.

1. 특정 region(`31200`, `50110`, `28110` 중 하나)을 골라 age-pass target row id set을 고정
2. 같은 user snapshot으로 `RetrievalService` candidate list에 그 row가 들어오는지 확인
3. 들어오면 `RuleScoringService` / `special target mismatch` / final rerank 단계에서 빠지는지 확인
4. 안 들어오면 repository query / youth filter / age field 품질 문제로 분리

즉 다음 task 이름은 대략 이 수준이 맞습니다.

- `education canonical target row retrieval candidate composition inspect`

## 검증 메모

이번 inventory는 아래 실측에 기반합니다.

- local MySQL target inventory / region distribution query
- local rule-only app replay
- `32`개 region/age 조합 scan
- 각 replay 결과와 region target id set의 intersection 확인
