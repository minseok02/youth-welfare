# 복지로 `threshold_like` income signal 정책

복지로 detail 본문에서 발견되는
`threshold_like` income signal(`13`건)을
canonical `INCOME_*` hard fact 로 승격하지 않고,
optional soft signal 로 분리할지 정리한 문서입니다.

관련 문서:

- [db-migration.md](../../db-migration.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-beneficiary-dedupe-strategy.md](./policy-normalization-beneficiary-dedupe-strategy.md)

## 결론

현재 단계에서는 아래처럼 둡니다.

1. `threshold_like` 는 `INCOME_PCT`, `INCOME_WON` 같은 canonical hard fact로 올리지 않음
2. 필요하면 future sidecar/read-model 에서만 읽는 **optional soft signal** 로 분리
3. 이 soft signal은 retrieval hard filter에 직접 쓰지 않음
4. explanation / analyst inventory / future AI batch enrichment 후보로만 유지

즉:

- **hard eligibility 아님**
- **optional interpretive signal**

로 취급합니다.

## 왜 hard fact 로 올리지 않나

local snapshot 재분류 기준 `threshold_like` 는 `13`건이었고,
그 안에 아래가 섞여 있었습니다.

1. `신혼`, `2자녀`, `출산`, `맞벌이`, `우대형`, `일반형`, `개별심사`
   같은 분기 조건
2. 다중 `% 이하` threshold
3. 다중 `만원 이하` threshold

이걸 단일 `INCOME_PCT` 또는 `INCOME_WON` 값으로 펴면:

- 의미 손실이 크고
- 사용자 eligibility 를 과도하게 단순화하고
- 잘못된 hard filter 로 이어질 위험이 큽니다

따라서 current canonical layer에서는
숫자만 뽑아내 hard fact로 저장하지 않습니다.

## current canonical policy

### 1. collect / normalization

- `WelfareServiceMapper`
- `TextConstraintExtractor`
- `service_facts`

경로에서는 `threshold_like` 를 현재 hard fact로 적재하지 않습니다.

즉 아래는 하지 않습니다.

- `INCOME_PCT`
- `INCOME_WON`
- `LOW_INCOME_ELIGIBILITY`

같은 단일 사실값으로 직접 저장

### 2. sidecar / schema

future schema가 필요하면
hard fact table과 같은 의미계층이 아니라
별도 optional soft signal 계층으로 둡니다.

권장 성격:

- source text span 보존
- normalized bucket은 optional
- 다중 threshold / branch label 공존 허용
- retrieval hard gate 비사용

즉 `service_facts` 의 strict eligibility column처럼 소비하지 않습니다.

### 3. recommendation

현재 추천 파이프라인에서는
`threshold_like` 를 hard filter 나 priority bonus로 쓰지 않습니다.

허용되는 future 사용처:

1. explanation
2. analyst/debug inventory
3. AI enrichment input
4. optional badge candidate

## beneficiary_only 와 왜 다르게 보나

`beneficiary_only` 는
`기초생활수급자`, `차상위계층` 처럼
의미가 비교적 안정적인 label 이라
`TARGET_GROUP` soft taxonomy 로 보존할 수 있었습니다.

반면 `threshold_like` 는:

- 숫자 threshold
- branch label
- household/context 분기

가 함께 섞여 있습니다.

즉 같은 “income-like” 라도
`beneficiary_only` 와 `threshold_like` 는
같은 저장 전략을 쓰면 안 됩니다.

## 허용되는 다음 단계

다음 단계에서만 좁게 검토합니다.

1. future read-model 에 `incomeThresholdSignalsRaw` 같은 raw projection 추가
2. branch label / threshold token / source span 을 함께 남기는 optional schema 초안 작성
3. AI batch enrichment 가 이 raw signal 을 어떻게 해석할지 별도 검토

## 하지 않는 것

현재 단계에서 하지 않는 것:

1. `threshold_like` 를 `INCOME_*` hard fact 로 승격
2. retrieval SQL filter 에 직접 연결
3. `targetGroupBuckets` 같은 existing taxonomy bucket으로 억지 흡수
4. 숫자 하나만 남기고 branch/context 를 버리는 flattening

## 한 줄 요약

복지로 `threshold_like` income signal 은 현재 canonical 에서 hard eligibility fact가 아니라, raw/context 를 보존하는 optional soft signal 후보로만 유지한다.
