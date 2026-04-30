# `교육` replay control drift 분석

2026-04-30 기준
`deploy/smoke/run-local-education-priority-replay.sh`
latest artifact(`/tmp/tmp.pKVwRY4dlt`)를 바탕으로
sample B(control)의 drift를 `finalScore` 기준으로 분해한 결과입니다.

관련 문서:

- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)
- [policy-normalization-education-priority-validation-criteria.md](./policy-normalization-education-priority-validation-criteria.md)
- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)

## 결론

- current local snapshot에서는 control sample drift를 `strict fail` 기본값으로 두지 않는다
- 이유는 `sample B`에서 **target row top-10 count는 `0 -> 0`으로 유지**되지만,
  일부 비대상 row의 `finalScore`와 top-10 내부 순서가 소폭 흔들리기 때문이다
- latest full replay script artifact(`/tmp/tmp.aoUkRUpDdB`)에서는
  `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 가 같이 남았고,
  여기서 `rule_weighted_score` 는 동일한데 `final_score` 만 달라지는 snapshot이 확인됐다
- 따라서 이 흔들림은 현재 구현상
  `ReRankingService`의 **request-local rule score normalization**
  영향으로 보는 쪽이 더 강해졌다

즉:

- `sample A`에서 canonical `교육` row가 bonus를 받아 올라오는지 확인하는 것은
  지금 스모크의 핵심 회귀 조건이다
- `sample B`는 기본적으로
  “교육 target row가 top-10으로 새로 올라오지 않았는가”
  까지만 hard condition으로 두고,
  exact top-10 id / `finalScore` 불변은 artifact review 대상으로 남긴다

## latest sample B 수치

sample:

- `regionCode=28110`
- age `25`
- `incomeLevel=5`
- `interestFields=["교육"]`
- `priorityCodes=["HOUSING","JOB"]`

latest replay:

- top-10 service id set:
  - `off`: `[356,399,405,403,404,407,375,408,422,413]`
  - `on`: `[403,356,405,399,407,375,404,408,422,413]`
- set 자체는 동일
- `compat=기타 + youth_major=교육` target row top-10 count:
  - `off`: `0`
  - `on`: `0`

즉 control sample에서 생긴 변화는
**target row 유입**이 아니라
기존 top-10 내부의 순서/점수 흔들림이다.

## top-10 주요 score delta

대표 delta:

- `356 검단신도시 워라밸빌리지 특화구역 청년주거단지 조성`
  - `0.96 -> 0.92` (`-0.04`)
- `399 청년 주택임차보증금 이자 지원 대출연장`
  - `0.94 -> 0.88` (`-0.06`)
- `403 인천시 청년월세 지원사업`
  - `0.92 -> 0.96` (`+0.04`)
- `404 (중구) 2026년 중구 청년 이사비 지원사업`
  - `0.90 -> 0.84` (`-0.06`)
- `407 천원 복비(주택 중개보수 지원)`
  - `0.88 -> 0.86` (`-0.02`)

특징:

- `sample B` top-10 내부에는 canonical `교육` target row가 여전히 없음
- 그런데 housing row의 `finalScore`가 `±0.02 ~ ±0.06` 수준으로 바뀐다
- 동시에 top-20 바깥의 `교육` row도 `-0.04` 정도의 delta를 같이 가진다

즉 drift는 특정 `교육` row만 국소적으로 튀는 패턴이 아니라
후보군 전체 score scale이 함께 다시 잡히는 패턴에 가깝다.

## 왜 strict fail 기본값이 아닌가

현재 구현을 보면:

- `RuleScoringService`는 flag on일 때
  `compat=기타 + youth_major=교육 + priority=EDUCATION`
  에만 추가 priority match를 준다
- `sample B`는 `priorityCodes=["HOUSING","JOB"]` 이라
  helper가 직접 true가 될 조건이 아니다
- 그럼에도 `finalScore`가 흔들리는 이유는
  [ReRankingService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/ReRankingService.java) 가
  request마다 `ruleMax`를 다시 구해 `normalize(ruleWeightedScore, 0, ruleMax)` 하기 때문이다

latest full replay score snapshot 기준으로는:

- 주거 row 상위권의 `rule_weighted_score` 는 그대로 `30.00`
- 교육/기타 row 상위권의 `rule_weighted_score` 도 그대로 `25.00`
- 그런데 `final_score` 는 `0.96 -> 0.88`, `0.94 -> 0.88`, `0.76 -> 0.72` 식으로 바뀐다

따라서 한쪽 후보군의 weighted score 분포가 조금만 바뀌어도:

- 다른 후보의 normalized rule score가 같이 다시 계산될 수 있고
- exact top-10 id / 순서 / `finalScore` 불변을 기본 자동화 조건으로 두면
  스모크가 너무 민감해진다

## 현재 운영 기준

스크립트 기본 모드:

- `sample A top-10 target row count 증가`는 hard assert
- `sample B target row top-10 count 증가 없음`은 기대 상태
- `sample B` exact top-10 / `finalScore` drift는 warning으로 남김

strict control 검증이 필요하면:

```bash
STRICT_CONTROL_ASSERT=true deploy/smoke/run-local-education-priority-replay.sh
```

이 모드는 “control drift도 실패로 보고 원인을 바로 파야 하는 상황”에만 쓴다.

## 다음 판단 경계

다음에 볼 것은 두 가지다.

1. full replay 문맥에서 왜 `rule_weighted_score` 는 같은데
   `final_score` 만 달라지는지 추가 원인(후보군 구성/정렬 tie/정규화 입력) 추적
2. local smoke에서는 warning 유지,
   CI/수동 검증에서는 strict mode를 추가할지

지금 단계에서는 1번이 더 우선이다.
