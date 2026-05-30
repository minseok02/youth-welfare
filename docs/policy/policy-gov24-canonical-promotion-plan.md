# `Gov24` canonical promotion plan

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)

## 목적

이 문서는 `Gov24 canonical deferred` 를 다시 열 때

- 무엇을 지금 active 설계 대상으로 볼지
- 무엇을 계속 raw-only 또는 deferred 로 둘지
- `service_facts` 와 `service_taxonomy_terms` 중 어디로 올릴지

를 한 장에서 고정합니다.

이번 문서는 **runtime collect 재개** 나 **hard import/backfill SQL** 문서가 아닙니다.

## 결론

현재 `2026-05-18` 기준 다음 Gov24 active lane은

**`serviceField/userType/benefitType` 의 label-first canonical 승격 설계**

입니다.

즉 지금 여는 것은:

1. raw exact label 유지 원칙
2. token allowlist 경계
3. canonical 저장층을 `service_taxonomy_terms` 로 제한하는 판단

까지입니다.

추가로 `2026-05-19` 공식 문서 재확인 기준:

- 공공데이터포털 공식 Swagger에서 직접 확인되는 필드명은 영문 `serviceField/userType/benefitType` 가 아니라
  `서비스분야/사용자구분/지원유형` 이다.
- 이 세 필드는 공개 Swagger에서 모두 `string` 으로만 정의되어 있고,
  별도 enum / codebook / closed-set inventory 는 공개돼 있지 않다.
- 즉 이번 active lane은 “공식 code를 더 기다렸다가 code-first 로 가는 트랙”이 아니라,
  **공개 공식 문서상 string label로만 존재하는 값을 내부 canonical term으로 어디까지 승격할지 정하는 트랙**이다.

이번 단계에서 여전히 열지 않는 것은:

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` stable code SQL
2. `service_facts` 로의 직접 승격
3. recommendation matcher hard condition / AI prompt 소비
4. `YOUTH_MID` bridge
5. `supportConditions` 사업체/업종/창업 상태 full-scope 승격

## 왜 지금 이 경계가 맞는가

현재 Gov24는 이미 아래가 닫혀 있습니다.

- runtime collect
- raw exact label summary
- `사용자구분 / 지원유형` token split parser
- admin diagnostics
- admin recommendation facet

즉 남은 질문은

- “값이 있나?”
- “parser가 맞나?”

가 아니라

- “이 값을 공통 canonical 층으로 올릴지?”
- “올린다면 어느 저장층까지 올릴지?”

입니다.

여기서 바로 `service_facts` 나 scoring으로 가면 범위가 과합니다.

## 승격 원칙

## 1. raw exact label은 계속 보존한다

아래 summary label은 계속 raw exact 값을 유지합니다.

- `gov24ServiceFieldLabel`
- `gov24UserTypeLabel`
- `gov24BenefitTypeLabel`

즉 canonical 승격이 시작되어도

- `개인||가구`
- `현금(융자)`
- `보육·교육`

같은 원문 표현은 그대로 남깁니다.

## 2. canonical 저장은 `service_taxonomy_terms` 우선이다

이번 축은 eligibility fact보다 **분류/타겟/제공형태 성격**이 더 강합니다.

따라서 1차 승격 저장층은 `service_facts` 보다

- `service_taxonomy_terms`

가 맞습니다.

이유:

- `사용자구분`, `지원유형` 은 정책 설명 분류이지 개인 적격성 판정 fact가 아니다.
- `service_facts` 로 올리면 추천 matcher/facet/scoring 쪽으로 오해 섞인 재사용이 빨라질 수 있다.
- 반면 `service_taxonomy_terms` 는 label-first canonical summary와 admin/internal facet에 더 잘 맞는다.

## 3. allowlist token만 올린다

raw combo 전체를 term으로 만들지 않습니다.

승격 대상은 split token allowlist 입니다.

### `서비스분야`

이 축은 raw exact label `10개` 자체가 managed inventory 입니다.

승격안:

- term group: `GOV24_SERVICE_FIELD`
- value: exact raw label 그대로

즉 `서비스분야` 는 token split 없이 **exact-label canonical term** 으로 봅니다.

### `사용자구분`

승격 대상 token:

- `개인`
- `가구`
- `법인/시설/단체`
- `소상공인`

승격안:

- term group: `GOV24_USER_TYPE_TOKEN`
- value: split token

raw combo (`개인||가구`) 자체는 summary/raw 에만 남기고,
canonical term은 additive multi-term 으로만 봅니다.

### `지원유형`

승격 대상 token은 현재 `20개` allowlist 입니다.

- `현금`
- `현물`
- `기타`
- `현금(감면)`
- `이용권`
- `서비스(의료)`
- `시설이용`
- `기타(교육)`
- `현금(보험)`
- `현금(장학금)`
- `현금(융자)`
- `기타(상담)`
- `서비스(돌봄)`
- `서비스(일자리)`
- `의료지원`
- `상담/법률지원`
- `기술지원`
- `문화/여가지원`
- `민원`
- `봉사/기부`

승격안:

- term group: `GOV24_BENEFIT_TYPE_TOKEN`
- value: split token

복합 raw string 전체는 summary/raw 에만 남깁니다.

## 4. `service_facts` 는 이번 단계에서 열지 않는다

아래는 이번 active lane의 범위 밖입니다.

- `GOV24_SERVICE_FIELD` fact
- `GOV24_USER_TYPE` fact
- `GOV24_BENEFIT_TYPE` fact

이유:

- 이 축은 추천 적격성 fact보다 **설명용 canonical term** 성격이 강하다.
- `supportConditions` 가 이미 Gov24 fact 축을 담당하고 있다.
- `service_facts` 로 열면 recommendation matcher가 오용할 가능성이 크다.

## 5. public/filter/scoring 소비는 계속 보류한다

이번 승격은 internal canonical 정리일 뿐입니다.

즉 아래는 계속 열지 않습니다.

- recommendation scoring
- matcher hard condition
- AI prompt 추가 입력

이번 단계의 소비처는 우선:

- canonical summary 정리
- admin facet/read-model 안정화

까지만 봅니다.

예외적으로 `2026-05-31` 기준 아래 bounded public filter는 열었다.

- `/api/policies`
- `/api/policies/search`
- query param: `gov24ServiceField`
- 허용값: managed exact label `10개`
- query contract: `GOV24_SERVICE_FIELD` term 우선, term이 없는 legacy row만 summary slot fallback
- query param: `gov24UserType`
- 허용값: managed token `4개`
- query contract: `GOV24_USER_TYPE_TOKEN` term 우선, term이 없는 legacy row만 `gov24_user_type_label` `||` split fallback
- query param: `gov24BenefitType`
- 허용값: managed token `20개`
- query contract: `GOV24_BENEFIT_TYPE_TOKEN` term 우선, term이 없는 legacy row만 `gov24_benefit_type_label` `||` split fallback

즉 현재 public 소비는 “Gov24 전체 public filter/scoring 개방”이 아니라
`serviceField` exact-label + `userType` additive token + `benefitType` additive token 세 축만 read-only discovery filter로 좁게 연 상태다.

## 지금 active 로 볼 구현 범위

다음 Gov24 코드 트랙은 아래 정도가 상한입니다.

1. `GOV24_SERVICE_FIELD` exact-label term 승격
2. `GOV24_USER_TYPE_TOKEN` allowlist token term 승격
3. `GOV24_BENEFIT_TYPE_TOKEN` allowlist token term 승격
4. canonical summary/read-model/admin facet 를 새 term group 기준으로 읽게 정리
5. raw exact label summary는 그대로 유지

## 계속 deferred 로 둘 범위

아래는 이번 lane 이후에도 deferred 유지입니다.

1. `GOV24_*` stable code SQL
2. `GOV24_SUPPORT_CONDITION` full-scope business/industry/startup code 승격
3. `Gov24 -> YOUTH_MID` 연결
4. recommendation matcher hard condition / ranking hard gate 소비

## reopen 완료 판정

이번 Gov24 lane이 닫혔다고 볼 기준은 아래입니다.

1. exact raw label 유지 원칙이 문서와 코드에 같이 반영됨
2. term 승격 대상이 `서비스분야 10`, `사용자구분 4`, `지원유형 20` allowlist로 고정됨
3. canonical 저장층이 `service_taxonomy_terms` 로 제한됨
4. `service_facts` / scoring 미개방 경계가 문서에 명시됨

`2026-05-19` local closeout 기준 위 조건은 현재 충족된 상태다.

- `GOV24_SERVICE_FIELD` exact-label term 우선 read-model 경계가 코드와 테스트에 반영됨
- admin diagnostics / admin facet / recommendation response / policy summary/detail/ranking / bookmark / AI prompt가 같은 exact label truth를 읽음
- `CanonicalRecommendationReadModelIntegrationTest` 도 로컬 PostgreSQL/Redis runtime에서 다시 통과함
- `Gov24` taxonomy term 저장도 stable code FK가 아니라 label-first term 경계에 맞게 `code_set_key=null` 로 정리됨

즉 이 문서의 다음 단계는 “이 설계가 맞는지”를 계속 탐색하는 것이 아니라,
현 경계를 closeout truth로 유지하면서 future reopen 범위를 `service_facts/public filter/scoring` 쪽 deferred 판단으로 제한하는 것이다.

## 요약

1. Gov24 다음 active track은 runtime collect가 아니라 **canonical promotion 설계** 다.
2. `서비스분야` 는 exact-label canonical term, `사용자구분/지원유형` 은 allowlist token term 으로 본다.
3. 이번 승격 저장층은 `service_taxonomy_terms` 이고, `service_facts` 는 열지 않는다.
4. raw exact label summary는 계속 유지한다.
5. recommendation scoring은 현재 `RuleScoringService` 의 bounded soft additive bonus까지만 열려 있고, matcher hard condition과 `service_facts` 승격은 계속 deferred 다.
