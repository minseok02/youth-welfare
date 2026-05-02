# 신규 Policy Source 온보딩 공통 구조

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-source-canonical-onboarding-priority.md](./policy-source-canonical-onboarding-priority.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [history/policy/policy-normalization-summary-slot-storage-plan.md](./history/policy/policy-normalization-summary-slot-storage-plan.md)
- [history/policy/policy-listing-source-schema-draft.md](./history/policy/policy-listing-source-schema-draft.md)
- [history/policy/policy-scholarship-reference-matrix-draft.md](./history/policy/policy-scholarship-reference-matrix-draft.md)

## 목적

이 문서는 `Gov24` 같은 특정 API를 붙이기 위한 문서가 아닙니다.

목표는 하나입니다.

- **새로운 정책/일자리/주거/장학 API가 들어와도 같은 절차로 분류하고 저장할 수 있는 공통 구조를 고정**

즉, `source 하나 추가` 보다
`source를 계속 추가해도 구조가 안 무너지게 만드는 기준` 을 정리합니다.

## 한 줄 요약

신규 source는 먼저 `기관명` 이 아니라 `row grain` 으로 분류합니다.

1. 정책형 source
2. listing형 source
3. reference matrix형 source

그리고 공통 파이프라인은 아래 순서를 따릅니다.

1. raw payload 저장
2. row grain 분류
3. canonical 승격 가능한 공식 필드 추출
4. 부족한 부분만 rule-derived bridge 적용
5. codebook/공식 inventory가 없으면 metadata-only 또는 blocked 상태로 멈춤

## source type 분류

### 1. 정책형 source

정의:

- 한 row가 비교적 안정적인 “정책/지원제도/프로그램” 을 의미
- 추천 카드 1개로 보여도 의미가 무너지지 않음
- title, summary, 대상, 지원내용, 신청기한, 기관, 자격조건이 비교적 안정적으로 존재

저장:

- `welfare_services`
- `raw_api_payloads`
- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

예:

- 온통청년 정책
- 복지로 정책 row
- Gov24/보조금24 혜택형 서비스 row
- 정부지원일자리정보의 사업/프로그램 row
- 청년월세지원, 국가장학금 같은 제도 row

### 2. listing형 source

정의:

- 한 row가 “공고/채용건/주택모집건/회차” 같이 빠르게 변하는 inventory
- 제도 그 자체보다 모집/공급/공고가 중심
- 정책 카드와 같은 lane에 섞으면 추천 의미가 흐려짐

저장:

- `welfare_services` 로 바로 넣지 않음
- source별 별도 listing domain으로 분리

예:

- 고용24/워크넷 채용정보
- 공채속보
- 마이홈포털 공공주택 모집공고
- 공공임대 단지정보

### 3. reference matrix형 source

정의:

- 한 row가 직접 추천 대상이 아니라 정책 해석을 돕는 표/매트릭스/참조행
- 대학별 가능 여부, 구간표, 코드표, 상태표처럼 제도 설명을 보강

저장:

- 별도 reference domain 또는 fact variant table
- canonical 정책 row를 보강하는 방향으로만 사용

예:

- 국가장학금 지원가능대학/학기/지원구간
- 장학금 금액표
- 공공주택 대기현황

## 공통 저장 원칙

### 1. raw는 항상 먼저 보존

신규 source는 먼저 raw payload를 보존합니다.

이유:

- mapping 실패 시 재파싱 가능
- source field inventory 추적 가능
- codebook/official schema 응답이 나중에 와도 backfill 가능

### 2. official field가 있으면 먼저 canonical로 승격

우선순위:

1. source official field
2. rule-derived bridge
3. AI enriched signal

즉 title keyword만으로 바로 canonical을 강하게 확정하지 않습니다.

### 3. compat는 bridge layer로만 유지

현재 response/recommendation 계약은 아직 `legacy compat unifiedCategory` 중심입니다.

그래서 신규 source도:

- canonical taxonomy/facts는 sidecar에 저장
- `compat_unified_category` 는 bridge layer로만 유지

합니다.

canonical이 있다고 바로 response 대표 category를 바꾸지 않습니다.

### 4. codebook이 없으면 무리해서 hard mapping 하지 않음

다음은 reopen 조건이 충족되기 전까지 blocked 또는 metadata-only로 둡니다.

- finite code inventory가 안 보이는 label field
- official code/label 대응이 없는 taxonomy
- source가 주지 않는 classification을 title keyword만으로 강제 확정하는 경우

## 공통 파이프라인

### 단계 1. source inventory

먼저 확인:

- list endpoint
- detail endpoint
- codebook/schema endpoint
- row grain
- official identifier
- update cadence

이 단계에서 이미 `정책형 / listing형 / reference형` 을 1차 판정합니다.

### 단계 2. raw ingest

최소 저장:

- source type
- external id
- category/list/detail 구분
- raw payload json
- fetched at / sync log

이 단계는 canonical 승격과 분리합니다.

### 단계 3. canonical 적합도 판정

정책형이면:

- `welfare_services` 후보
- `service_taxonomies`
- `service_facts`

listing형이면:

- 별도 listing table
- 필요 시 정책형 row와 연결만 허용

reference형이면:

- reference set / reference row
- 정책 row 보강용만 허용

### 단계 4. bridge / compat 적용

정책형 source만 compat bridge를 봅니다.

기준:

- 현재 사용자 응답 계약을 깨지 않음
- `unifiedCategory` 는 legacy compat 의미 유지
- canonical taxonomy는 secondary hint 또는 future migration 자산으로 유지

### 단계 5. blocked 판정

아래면 진행을 멈춥니다.

- codebook 필요
- official finite inventory 부재
- row grain 불명확
- 정책형인지 listing형인지 섞여 있음

멈추는 것이 실패가 아니라 정상 동작입니다.

## source 추가 체크리스트

신규 source를 붙일 때는 아래 순서로 체크합니다.

### A. 분류 체크

- 이 row는 정책형인가
- listing형인가
- reference matrix형인가

### B. 저장 체크

- `welfare_services` 로 들어가도 row 의미가 유지되는가
- 아니면 별도 listing/reference schema가 필요한가
- raw payload는 category/list/detail 단위로 재수집 가능한가

### C. canonical 체크

- official title/summary/기관/링크는 안정적인가
- age/income/deadline/provision method 같은 hard fact 후보가 실제 field로 존재하는가
- taxonomy는 official code/label이 있는가, 아니면 derived bridge만 가능한가

### D. blocked 체크

- codebook 없이는 SQL/backfill을 열면 안 되는가
- operator/provider 응답이 와야만 진행되는가
- 지금은 metadata-only로 멈추는 것이 맞는가

### E. recommendation 체크

- 이 source가 current recommendation lane에 직접 들어가는가
- compat bridge 없이는 `unifiedCategory` 가 흔들리는가
- recommendation/read-model에 넣더라도 response contract를 깨지 않는가

## 현재 코드 기준 진입점

신규 정책형 source가 현재 구조에 들어올 때 최소 경계는 이렇습니다.

### 수집

- source collector / admin collect entry
- `raw_api_payloads`
- `api_sync_logs`
- 필요 시 `welfare_services`

### canonical sidecar

- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

### 추천

- `CanonicalRecommendationReadModelRepository`
- `RecommendationCandidateProjection`
- `RetrievalService`
- `RuleScoringService`

즉 신규 source onboarding은 collect에서 끝나는 작업이 아니라,
`raw -> canonical sidecar -> recommendation read model 호환` 까지 봐야 닫힙니다.

## 지금 당장 안 하는 것

이 문서는 아래를 당장 구현하자는 문서가 아닙니다.

- `Gov24` 실수집 구현
- `YOUTH_MID` stable code mapping SQL reopen
- external blocked source 강제 매핑
- listing형 source를 억지로 `welfare_services` 에 넣는 것

이 문서는 오히려 **무엇을 지금 안 해야 하는지** 도 정합니다.

## 현재 기준 practical next action

현재 단계에서 다음 액션은 `Gov24 API key 확보` 가 아닙니다.

우선순위는 아래입니다.

1. 신규 source를 붙일 때 이 문서 기준으로 먼저 `정책형 / listing형 / reference형` 판정
2. raw 저장과 canonical 승격 경계를 분리
3. codebook/공식 inventory가 없으면 blocked 또는 metadata-only로 멈춤
4. 외부 응답이 필요한 source는 요청 템플릿/blocked 트랙으로 넘김

## 요약

1. `Gov24` 는 목표가 아니라 예시 source였다.
2. 실제 목표는 “다른 API도 쉽게 넣을 수 있는 구조” 를 고정하는 것이다.
3. 신규 source는 `정책형 / listing형 / reference형` 으로 먼저 나눈다.
4. raw 저장, canonical 승격, compat bridge, blocked 판정을 분리한다.
5. codebook 없이는 hard mapping을 열지 않는다.
