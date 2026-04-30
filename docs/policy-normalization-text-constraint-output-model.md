# `TextConstraintExtractor` 출력 모델 재설계

이 문서는 현재 `TextConstraintExtractor` 를

- `KEYWORD` 토큰 추출 유틸
- `ConstraintSummary` 보조 유틸

에서

- `service_facts` 저장 규격 친화 extractor

로 재설계할 때의 목표 출력 모델을 고정합니다.

관련 문서:

- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
- [policy-normalization-fact-merge-rules.md](./policy-normalization-fact-merge-rules.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [db-migration.md](./db-migration.md)

## 현재 상태

현재 `TextConstraintExtractor` 는 아래 두 종류를 섞어 제공합니다.

1. `extract(...)`
   - `COND_AGE_MIN_*`
   - `COND_AGE_MAX_*`
   - `COND_INCOME_PCT_LE_*`
   - `COND_INCOME_WON_LE_*`
   - `COND_RENT_WON_LE_*`
   같은 문자열 토큰
2. `summarize(...)`
   - `ConstraintSummary`
   - `minAge/maxAge/income.../applyEndDate`

이 구조는 초기 `service_tags.KEYWORD` 용도로는 유용했지만,
지금 canonical sidecar에서는 목적이 흐려집니다.

## 문제

### 1. 토큰 형식이 `service_facts` 와 직접 맞물리지 않는다

지금 토큰은

- `COND_AGE_MIN_19`
- `COND_INCOME_PCT_LE_150`

처럼 legacy keyword 저장용 문자열입니다.

하지만 `service_facts` 는

- `fact_group`
- `fact_code`
- `fact_merge_key`
- `operator`
- typed value
- `source_field`
- `authority`
- `confidence`
- `raw_value`
- `evidence_text`

를 필요로 합니다.

즉 지금 토큰만으로는 persistence 직전 단계에서
다시 한 번 parsing/해석을 해야 합니다.

### 2. `ConstraintSummary` 는 singleton view이고 evidence 보존이 약하다

`ConstraintSummary` 는:

- 최솟값/최댓값
- 일부 amount/pct
- `applyEndDate`

정도만 남깁니다.

하지만 canonical fact 저장에서는

- 어떤 source field 에서 나왔는지
- 어떤 원문 evidence 에서 나왔는지
- merge key 가 무엇인지
- singleton slot 인지 set-like slot 인지

를 같이 들고 가야 합니다.

### 3. extractor가 legacy KEYWORD와 canonical fact 두 경계를 동시에 안고 있다

현재 구조는

- legacy retrieval age fallback
- canonical sidecar fact 저장

두 용도를 동시에 만족시키려 합니다.

이러면 다음 구현에서
extractor 출력보다 “그 출력의 재해석 로직”이 더 커집니다.

## 결론

`TextConstraintExtractor` 의 다음 출력 모델은
문자열 토큰 집합이 아니라
**typed fact candidate 목록** 이어야 합니다.

즉 extractor 는 앞으로:

1. legacy `KEYWORD` 토큰 생성기
2. canonical `FactCandidate` 생성기

중 canonical 쪽을 주 계약으로 삼고,
legacy 토큰은 필요하면 adapter/helper에서 파생하는 구조로 옮깁니다.

## 목표 출력 모델

권장 출력 record:

```java
record ExtractedFactCandidate(
    String factGroup,
    String factCode,
    String factMergeKey,
    NormalizedPolicyAggregate.Operator operator,
    NormalizedPolicyAggregate.ValueType valueType,
    Integer intValue,
    Integer rangeMinInt,
    Integer rangeMaxInt,
    LocalDate dateValue,
    String unit,
    String sourceField,
    NormalizedPolicyAggregate.Authority authority,
    BigDecimal confidence,
    String rawValue,
    String evidenceText
) {}
```

핵심은 `NormalizedPolicyAggregate.Fact` 와 거의 같은 shape를 갖되,
extractor 단계에서는 아직 aggregate 전체와 결합하지 않은
중간 candidate 로 두는 것입니다.

## 권장 필드 규칙

### 1. `factGroup`

초기 허용:

- `AGE`
- `INCOME`
- `RENT_CAP`
- `APPLY_END_DATE`

지금 extractor 범위에서는
우선 이 4개만 다룹니다.

### 2. `factCode`

phase-specific code가 아니라
semantic code를 씁니다.

예:

- `BOKJIRO_RULE_AGE`
- `BOKJIRO_RULE_APPLY_END_DATE`
- `BOKJIRO_RULE_INCOME_PERCENT`
- `BOKJIRO_RULE_INCOME_AMOUNT`
- `BOKJIRO_RULE_RENT_CAP`

### 3. `factMergeKey`

`service_facts` 저장 규칙과 바로 맞아야 합니다.

예:

- `BK_AGE_ELIGIBILITY`
- `BK_APPLY_END_DATE`
- `BK_INCOME_LIMIT_PERCENT`
- `BK_INCOME_LIMIT_AMOUNT`
- `BK_RENT_CAP`

즉 extractor 출력이 이미
`fact_merge_key` 기준 singleton slot을 표현해야 합니다.

### 4. `operator`

초기 매핑:

- age range -> `RANGE`
- age min only -> `GTE`
- age max only -> `LTE`
- income percent max -> `LTE`
- income amount max -> `LTE`
- rent cap -> `LTE`
- deadline -> `LTE`

### 5. typed value

초기 규칙:

- age range -> `rangeMinInt`, `rangeMaxInt`
- age min/max only -> `intValue`
- pct/won/rent -> `intValue`
- deadline -> `dateValue`

즉 extractor 출력에는
문자열 재파싱이 다시 필요하지 않게 합니다.

### 6. `sourceField`

extractor는 원문 텍스트의 논리 출처를 호출자가 넘겨받아야 합니다.

예:

- `targetDetail`
- `selectionCriteria`
- `servDgst`
- `applyMethodDetail`
- `supportDetail`

현재처럼 단순 `String... texts` 만 받으면
텍스트는 알 수 있어도 source priority를 잃습니다.

따라서 다음 단계 인터페이스는
`text + sourceField` pair를 받아야 합니다.

### 7. `authority` / `confidence`

초기 규칙:

- 전부 `RULE_DERIVED`
- confidence 는 pattern/sourceField에 따라 다르게 부여

예시:

- `targetDetail` exact age range -> `0.90`
- `selectionCriteria` -> `0.90`
- `servDgst` -> `0.80`
- `applyMethodDetail` / `supportDetail` -> `0.70`

deadline은 현재 optional fact 정책을 유지하므로
date-like token을 잡아도 sourceField와 evidence가 더 중요합니다.

### 8. `rawValue` / `evidenceText`

둘 다 유지합니다.

- `rawValue`
  - 숫자/날짜 원문 조각
- `evidenceText`
  - 주변 문맥 포함 짧은 원문

이 둘이 있어야

- merge 충돌 triage
- false positive 검토
- 운영 샘플 재확인

이 쉬워집니다.

## 권장 인터페이스

현재:

```java
extract(String... texts)
summarize(String... texts)
extractApplyEndDate(String... texts)
```

다음:

```java
record SourceText(String sourceField, String text) {}

List<ExtractedFactCandidate> extractFacts(SourceText... sourceTexts)
```

보조 helper는 허용:

- `extractAgeFacts(...)`
- `extractIncomeFacts(...)`
- `extractDeadlineFacts(...)`

하지만 public contract는
`fact candidate list`
하나로 수렴시키는 쪽이 맞습니다.

## legacy 호환

legacy `KEYWORD` age fallback 이 당장 필요하므로,
현재 즉시 삭제하지는 않습니다.

권장 순서:

1. extractor 주 출력은 `ExtractedFactCandidate` 로 전환
2. 필요 시 별도 adapter에서 legacy `COND_*` 토큰 파생
3. retrieval age fallback 이 canonical fact 또는 projection으로 대체되면 토큰 경로 제거

즉 legacy token은
extractor의 본체 계약이 아니라
임시 adapter 결과가 되어야 합니다.

## 지금 하지 않는 것

현재 단계에서 바로 넣지 않는 것:

- employment/education/household/special-group text fact 출력
- AI enriched fact 출력
- extractor 안에서 `NormalizedPolicyAggregate.Fact` 직접 생성
- `supportConditions` official code와 text extractor 결과 merge

이번 재설계는
우선 `AGE / INCOME / RENT_CAP / APPLY_END_DATE`
의 typed candidate 모델에 집중합니다.

## 다음 구현 순서

1. `SourceText(sourceField, text)` 입력 타입 추가
2. `ExtractedFactCandidate` 출력 타입 추가
3. age/income/rent/deadline pattern을 fact candidate로 직접 매핑
4. 기존 `summarize()` 호출부를 mapper/helper 경계에서 새 candidate 소비 방식으로 치환
5. legacy `COND_*` 토큰은 필요 시 adapter helper로만 유지

## 최종 정책

따라서 `TextConstraintExtractor` 는
더 이상 “KEYWORD 토큰 문자열 생성기”가 주 계약이 아니라,
`service_facts` 저장 규격과 거의 같은 shape의
typed fact candidate extractor로 재설계합니다.
