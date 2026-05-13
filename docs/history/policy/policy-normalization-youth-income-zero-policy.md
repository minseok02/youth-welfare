# `YOUTH min_income/max_income = 0/0` 해석 정책

2026-04-30 기준 온통청년(`YOUTH`) source의 `min_income/max_income = 0/0` 값을
추천 retrieval 에서 어떻게 해석할지 정리한 문서입니다.

관련 문서:

- [policy-normalization-education-target-sample-inventory.md](../../history/ai/policy-normalization-education-target-sample-inventory.md)
- [recommendation-pipeline.md](../../recommendation/recommendation-pipeline.md)
- [user-data-separation-design.md](../../core/user-data-separation-design.md)

## 결론

`YOUTH min_income=0 AND max_income=0` 는 **실제 소득제한이 아니라 “미지정” sentinel** 로 해석합니다.

즉 추천 retrieval 에서는:

- `NULL/NULL`
- `0/0`

둘 다 **소득 direct filter pass-through** 로 취급합니다.

반대로 실제 gate 로 유지하지 않습니다.

## 근거

local DB snapshot 기준 `YOUTH` row 분포:

- `YOUTH total = 2299`
- `min_income=0 AND max_income=0 = 2269`
- `min_income IS NULL AND max_income IS NULL = 6`
- `min_income=0 AND max_income=10 = 0`
- `min_income=1 AND max_income=10 = 0`

즉 `0/0` 은 예외 케이스가 아니라 **거의 전체 집합** 입니다.

또 사용자 입력 계약은 이미 `incomeLevel 1~10` 으로 제한돼 있습니다.

- [SignupRequest.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/dto/request/SignupRequest.java)
- [UpdateProfileRequest.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/user/dto/request/UpdateProfileRequest.java)

따라서 `0/0` 을 “0분위 전용” 으로 해석하면:

- 실제 사용자 대부분이 `YOUTH` 정책 대부분을 retrieval 단계에서 바로 잃게 되고
- canonical `교육` target row처럼 source inventory는 있는데 result set hit는 `0` 인 왜곡이 발생합니다

## 왜 `0/0` 을 gate 로 보면 안 되는가

현재 추천 SQL은 `YOUTH` source에 대해서:

```sql
(ws.min_income IS NULL OR ws.min_income <= :incomeLevel)
AND (ws.max_income IS NULL OR ws.max_income >= :incomeLevel)
```

를 그대로 적용합니다.

여기서 `0/0` 을 실제 값으로 읽으면:

- `incomeLevel=5` 사용자
- `0 <= 5` 는 통과
- `0 >= 5` 는 실패

가 되어 전부 탈락합니다.

이 해석은 다음 문제를 만듭니다.

1. source 대부분이 candidate pool 에 못 올라옴
2. scoring/priority 실험이 no-op 처럼 보임
3. `교육`, `참여권리`, `복지문화` canonical bridge 검증 자체가 왜곡됨

## 권장 해석

retrieval 경계에서는 아래처럼 봅니다.

### pass-through 로 볼 케이스

- `min_income IS NULL AND max_income IS NULL`
- `min_income = 0 AND max_income = 0`

### 실제 gate 로 볼 케이스

- `min_income >= 1`
- `max_income >= 1`
- 또는 `min_income/max_income` 중 하나라도 실질적인 범위값이 있는 경우

즉:

- `0/0` 은 미지정
- `1~3`, `2~5` 같은 값만 실제 소득 gate

로 정리합니다.

## 적용 범위

이번 정책은 **retrieval SQL/pass-fail 경계** 에만 먼저 적용합니다.

즉:

- `WelfareServiceRepository.findCandidates*`
- `WelfareServiceRepository.findLatestCandidates*`

의 `YOUTH` income 조건에서 먼저 반영합니다.

아래는 이번 단계에서 바로 바꾸지 않습니다.

- API 응답의 `minIncome/maxIncome` 저장값 자체
- canonical read-model `incomeMinLegacy/incomeMaxLegacy`
- source raw payload 보존 방식

즉 저장값은 그대로 두고,
**query semantics만 먼저 수정** 하는 것이 1차 대응입니다.

## query semantics 권장안

개념적으로는 아래와 같습니다.

```sql
ws.source_type <> 'YOUTH'
OR (
  (
    (ws.min_income IS NULL AND ws.max_income IS NULL)
    OR (ws.min_income = 0 AND ws.max_income = 0)
  )
  OR (
    (ws.min_income IS NULL OR ws.min_income <= :incomeLevel)
    AND (ws.max_income IS NULL OR ws.max_income >= :incomeLevel)
  )
)
```

즉 `0/0` 은 retrieval filter 에서 `NULL/NULL` 과 같은 의미로 처리합니다.

## 지금 하지 않는 것

지금 단계에서 하지 않는 것:

- DB backfill로 `0/0 -> NULL/NULL` 일괄 변환
- `service_facts` 로 새로운 income hard fact 생성
- `RuleScoringService` 의 저소득 soft signal 규칙 변경

이건 retrieval semantics 안정화 후 따로 봅니다.

## 최종 정책

정리하면:

- `YOUTH 0/0 income` 은 **미지정**
- retrieval 에서는 **pass-through**
- 저장값은 그대로 두고 **query semantics만 먼저 수정**

## 다음 작업

1. `WelfareServiceRepository.findCandidates* / findLatestCandidates*` 의 `YOUTH income` 조건에 `0/0 => pass-through` 반영
2. `EducationPriorityTargetCandidateCompositionIntegrationTest` 를 green 유지한 채 representative region 에서 raw candidate hit가 실제 생기는지 확인
3. 필요하면 이후에 `0/0 -> NULL` backfill 필요 여부를 별도 검토
