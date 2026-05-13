# 정책형 Source Canonical 온보딩 우선순위

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-sample-spike.md](../history/policy/policy-normalization-sample-spike.md)
- [policy-normalization-bridge-rules.md](../history/policy/policy-normalization-bridge-rules.md)
- [policy-listing-source-schema-draft.md](../history/policy/policy-listing-source-schema-draft.md)
- [phase-plan.md](../phase-plan.md)

## 목적

`정부지원일자리정보`, `구직자취업역량 강화프로그램`, `Gov24/보조금24` 같은 정책형 source를
한 번에 다 붙이지 않고,

- 무엇을 먼저 canonical onboarding 할지
- live validation 을 어떤 순서로 할지

를 고정합니다.

## 결론

현재 phase의 정책형 source canonical onboarding 우선순위는 아래 순서로 둡니다.

1. `Gov24/보조금24`
2. `정부지원일자리정보`
3. `구직자취업역량 강화프로그램`

핵심 이유는 간단합니다.

- `Gov24/보조금24` 는 official `core/detail/facts` 강도가 가장 높다
- `정부지원일자리정보` 는 `일자리 사업/프로그램 row` 로 canonical 적합도가 높다
- `구직자취업역량 강화프로그램` 은 정책형이지만, `일자리` 와 `교육·직업훈련` 경계 해석이 더 필요하다

## 우선순위 판단 축

이번 우선순위는 아래 네 축으로 정합니다.

1. canonical 적합도
2. official facts 강도
3. current compat bridge 필요도
4. live validation 비용

### 1. `Gov24/보조금24`

판단:

- canonical 적합도: 가장 높음
- official facts 강도: 가장 높음
- bridge 필요도: 있음
- live validation 비용: 중간

이유:

- `serviceList`, `serviceDetail`, `supportConditions` 로 `core/detail/facts` 가 분리돼 있다
- 연령/소득/가구/취업/학력 같은 hard filter 후보를 official field로 받을 수 있다
- 현재 canonical 구조의 `facts` 레이어를 실제로 가장 잘 검증할 수 있다
- 단, `청년 taxonomy` 와 `compat_unified_category` 는 source가 직접 주지 않으므로 bridge 규칙은 계속 필요하다

현재 phase의 목표:

- `core/detail/facts` ingest 경계 검증
- `supportConditions -> service_facts` 저장 검증
- `service_field / user_type / benefit_type` metadata inventory는 separate track 유지
- `compat_unified_category` / `youth taxonomy` 는 official이 아니라 `SYSTEM_DERIVED` bridge 로만 유지

### 2. `정부지원일자리정보`

판단:

- canonical 적합도: 높음
- official facts 강도: 중간
- bridge 필요도: 낮음~중간
- live validation 비용: 중간

이유:

- row grain 자체가 “사업/프로그램” 이면 `welfare_services` 와 잘 맞는다
- `compat_unified_category=일자리` 로 내려갈 가능성이 높아 현재 추천 계약과도 비교적 잘 맞는다
- 다만 Gov24처럼 구조화된 official conditions 강도가 확실히 보장된 건 아니므로, live payload 기준 확인이 먼저 필요하다

현재 phase의 목표:

- 사업/프로그램 row와 기관/reference row 분리
- `일자리` canonical 적합도 확인
- eligibility / provision / 운영기관 필드가 `facts/taxonomy` 로 얼마나 안정적으로 내려오는지 점검

### 3. `구직자취업역량 강화프로그램`

판단:

- canonical 적합도: 중간~높음
- official facts 강도: 중간
- bridge 필요도: 중간
- live validation 비용: 중간~높음

이유:

- 정책형 source 후보이긴 하지만, `일자리` 와 `교육·직업훈련` 중 어디에 더 가깝게 읽을지 해석 여지가 있다
- 프로그램/훈련/상담/역량강화 성격이 섞이면 current compat category와 bridge 규칙이 먼저 필요하다
- 즉 source 자체는 붙일 수 있어도, recommendation/read-model 호환 판단 비용이 앞 둘보다 더 든다

현재 phase의 목표:

- row title이 아니라 프로그램 목적/지원내용 중심으로 `일자리 vs 교육·직업훈련` 해석
- `service_facts` 보다 `taxonomy/compat bridge` 설계 검증을 우선

## live validation 순서

## 1단계. `Gov24/보조금24`

먼저 확인할 것:

- `serviceList -> serviceDetail -> supportConditions` raw chain 확보
- 대표 sample 20~50건으로 `core/detail/facts` 저장 검증
- `supportConditions` 가 current `service_facts` 규격에 얼마나 그대로 들어오는지 확인
- `compat_unified_category` derived bridge 가 현재 recommendation/read-model 계약과 얼마나 덜 충돌하는지 확인

통과 기준:

- age/income/household/employment facts 중 적어도 하나 이상이 안정적으로 적재
- `supportConditions` representative subset seed와 실제 live field 의미가 크게 어긋나지 않음
- `Gov24` row가 `welfare_services + sidecars` 기준으로 current canonical의 기준 source 역할을 할 수 있음

## 2단계. `정부지원일자리정보`

먼저 확인할 것:

- row grain이 실제로 사업/프로그램인지
- detail row가 별도 reference/listing 성격으로 섞여 있지 않은지
- `일자리` 정책형 row를 `welfare_services` 에 넣어도 CTR/북마크 의미가 깨지지 않는지

통과 기준:

- listing/reference row 분리가 명확함
- `compat_unified_category=일자리` 기본 해석이 과도하게 흔들리지 않음
- title keyword 보정이 아니라 official field / detail 설명 기반으로 적재 가능함

## 3단계. `구직자취업역량 강화프로그램`

먼저 확인할 것:

- row가 제도형/프로그램형인지, 운영 일정/회차 inventory인지
- `일자리` 와 `교육·직업훈련` bridge를 current compat layer에서 어느 쪽으로 더 읽는지
- recommendation lane에 넣었을 때 현재 priority/read-model 과 크게 충돌하지 않는지

통과 기준:

- canonical 적재는 가능하되 `compat` 해석 기준이 문서/테스트로 먼저 고정됨
- 프로그램형 row와 event/listing row가 섞이지 않음

## 이번 phase에서 일부러 안 여는 것

이번 우선순위 정리에서는 아래를 같이 열지 않습니다.

- listing형 `채용정보/채용행사/공채속보` 재분류 재논의
- `GOV24_*` full label import SQL
- `YOUTH_MID` stable code inventory reopen
- 추천 lane 직접 연결

이 항목들은 source onboarding 우선순위가 아니라 별도 schema/metadata/read-model 트랙입니다.

## 요약

1. 정책형 source canonical onboarding 1순위는 `Gov24/보조금24` 입니다.
2. 그 다음은 `정부지원일자리정보` 입니다.
3. `구직자취업역량 강화프로그램` 은 붙일 수는 있지만, compat 해석 비용 때문에 3순위로 둡니다.
4. live validation 순서도 같은 순서로 갑니다.
5. 이번 단계의 목적은 “다 붙이기”가 아니라 “canonical 적합도가 가장 높은 source부터 기준선 만들기” 입니다.
