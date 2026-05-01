# 복지로 Live Detail Validation 리허설

관련 문서:

- [db-migration.md](../../db-migration.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-income-threshold-soft-signal-policy.md](./policy-normalization-income-threshold-soft-signal-policy.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

복지로 live detail 적재 기준에서

- `welfare_service_details`
- `service_facts`

를 어떤 순서로 검증할지 리허설 절차를 고정합니다.

이번 문서의 목적은 coverage를 당장 더 늘리는 게 아니라,
현재 canonical 경계가 어디까지 맞는지 재현 가능한 순서로 확인하는 것입니다.

## 결론

복지로 live detail validation 은 아래 4단계로 리허설합니다.

1. stored/raw coverage baseline 확인
2. detail payload shape / `welfare_service_details` 저장 확인
3. `service_facts` density / optional fact 분리 확인
4. residual sample 재분류 및 후속 액션 결정

즉 detail fetch coverage 와 fact extraction coverage 를 같은 지표로 보지 않습니다.

## 리허설 순서

## 1단계. stored/raw coverage baseline

먼저 확인할 것:

- 복지로 전체 서비스 수
- stored `DETAIL` raw payload 수
- missing detail backlog 수

이 단계의 질문:

- 현재 ceiling 이 extractor 문제인가
- 아니면 stored detail coverage 부족인가

대표 확인 지표:

- `welfare_services` 중 `BOKJIRO_CENTRAL/LOCAL` 총건수
- `raw_api_payloads(api_category='DETAIL')` 건수
- detail raw payload 없는 서비스 수

현재 의미:

- 이 수치가 낮으면 `service_facts` density가 낮아도 extractor만의 문제로 보면 안 됩니다.

## 2단계. detail payload shape / `welfare_service_details`

다음으로 확인할 것:

- live/stored detail payload key shape
- `welfare_service_details` 저장 경계

이 단계의 질문:

- detail payload에서 canonical `detail` 로 가져올 필드가 실제로 안정적으로 존재하는가
- explicit deadline 같은 기대 field가 실제로 있는가

대표 확인 지표:

- detail raw payload key inventory
- `welfare_service_details` row 생성/갱신 여부
- `targetDetail`, `supportDetail`, `applyMethodDetail`, `selectionCriteria` 같은 본문 필드 존재 여부

현재 판단:

- `applyEndDate/aplyEndDt/deadline/rcptEndDt` 류 explicit key는 아직 증거가 없으므로, 이 단계에서는 `BK_APPLY_END_DATE` 를 optional fact 로 유지합니다.

## 3단계. `service_facts` density

다음으로 확인할 것:

- `service_facts` total row 수
- distinct service 수
- fact code 분포

이 단계의 질문:

- detail payload가 늘어난 만큼 facts가 느는가
- 어떤 fact 는 stable 하고, 어떤 fact 는 optional/soft signal 로 남겨야 하는가

대표 확인 지표:

- `service_facts` 총 row 수
- `service_facts` distinct service 수
- `BK_AGE_ELIGIBILITY` 건수
- `BK_APPLY_END_DATE` 건수

현재 기준:

- `BK_AGE_ELIGIBILITY` 는 main fact candidate
- `BK_APPLY_END_DATE` 는 여전히 optional fact
- `threshold_like income` 은 hard fact가 아니라 optional soft signal 후보

## 4단계. residual sample 재분류

마지막으로 확인할 것:

- detail raw payload 는 있지만 fact 가 없는 서비스 집합
- 그 안에서 age/date/income/beneficiary signal 분포

이 단계의 질문:

- 남은 gap 이 extractor residual 인가
- payload 자체 signal 부재인가
- optional soft signal 로만 남겨야 하는가

대표 확인 지표:

- `detail raw payload exists && service_facts absent` 서비스 수
- `income-like`
- `beneficiary_only`
- `date-like`
- strict `age-like residual`

현재 기준:

- `beneficiary_only` 는 soft taxonomy whitelist
- `threshold_like` 는 optional soft signal
- 광범위한 age regex 확대는 보수적으로

## 이번 리허설의 pass 기준

이번 리허설은 아래를 통과하면 됩니다.

1. stored detail coverage 와 fact coverage 를 별도 지표로 재현 가능하게 기록
2. `welfare_service_details` 와 `service_facts` 의 역할을 섞지 않음
3. `BK_APPLY_END_DATE` 를 optional fact 로 유지하는 근거를 다시 확인
4. residual sample 을 `hard fact 보강` 과 `soft signal 보류` 로 다시 분류

## 이번 리허설에서 일부러 안 여는 것

이번 단계에서는 아래를 같이 열지 않습니다.

- `bokjiro-details-gap-fill` 예산 확대 실행
- `centralBudget/localBudget` observability 노출
- 새 regex 구현
- `threshold_like` soft signal 실제 스키마 구현

이 문서는 validation rehearsal 순서만 고정합니다.

## 다음 단계

이 리허설 이후에만 아래를 다시 엽니다.

1. `centralBudget/localBudget` observability 노출 여부
2. gap-fill 추가 라운드/호출 예산 전략
3. soft signal schema 실제 구현 여부

## 요약

1. 복지로 live detail validation 은 `coverage -> detail shape -> fact density -> residual sample` 순서로 본다.
2. `welfare_service_details` 검증과 `service_facts` 검증은 분리한다.
3. `BK_APPLY_END_DATE` 는 이번 리허설에서도 optional fact 전제로 본다.
4. 남은 gap 은 extractor 문제와 payload signal 부재를 구분해서 본다.
