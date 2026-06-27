# `Gov24` canonical mapping 1차 초안

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-benefit-type-grouping-draft.md](./policy-gov24-benefit-type-grouping-draft.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [api-mapping.md](../core/api-mapping.md)

## 목적

이 문서는 `Gov24 serviceList` 의

- `서비스분야`
- `사용자구분`
- `지원유형`

을 현재 로컬 DB에 적재된 raw inventory 기준으로 **어떻게 내부 규칙에 적용할지**를 고정하기 위한 1차 초안입니다.

이 문서는

- import/backfill SQL 구현
- stable code 발급
- `YOUTH_MID` 연결

문서가 아닙니다.

이번 초안의 목적은 **“지금 바로 확정 가능한 label-first 규칙”** 을 먼저 분리하는 것입니다.

## 2026-05-18 구현 메모

이번 초안은 문서로만 남기지 않고, 현재 코드에도 아래 범위까지만 반영했습니다.

1. `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel` raw exact label은 그대로 유지
2. `서비스분야` exact label term 1개, `사용자구분/지원유형` allowlist token term을 `service_taxonomy_terms` 에 저장
3. recommendation read-model 은 `GOV24_USER_TYPE_TOKEN`, `GOV24_BENEFIT_TYPE_TOKEN` term을 우선 읽고 raw split은 fallback 으로만 사용
4. token은 `admin recommendation diagnostics` 와 `admin recommendation facet` 에서 실제 노출

이번 단계에서 일부러 하지 않은 것:

1. public API (`/api/recommendations`, `/api/policies`) 응답 필드 확대
2. `service_facts` 에 `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` 를 새 fact 축으로 승격
3. 추천 matcher / scoring / AI prompt 입력 변경

이렇게 자른 이유:

- 현재 runtime에는 raw exact label summary가 이미 붙어 있고, 먼저 부족한 것은 “표시”가 아니라 “내부 해석을 관찰 가능하게 만드는 것”이다.
- `개인||가구`, `현금||서비스(의료)` 같은 복합값을 바로 적격성 fact처럼 올리면, 추천 규칙이나 검색 facet에 의도치 않은 영향을 줄 수 있다.
- 따라서 이번 단계는 **label 유지 + taxonomy term 승격 + read-only 관찰 가능화**까지만 열고, scoring/filter 소비는 별도 active 목표로 분리한다.

### 서버 검증 메모

운영 서버 `cde9cec` 기준으로 admin diagnostics 재검증 시 아래와 같이 raw label 유지 + token 분해가 확인됐다.

- raw
  - `소상공인||법인/시설/단체`
  - `현금(융자)`
- diagnostics
  - `gov24UserTypeTokens=['소상공인', '법인/시설/단체']`
  - `gov24BenefitTypeTokens=['현금(융자)']`

즉 parser 자체는 서버에서도 기대대로 동작했고, 다음 단계의 남은 질문은 parser 버그가 아니라 **이 token을 실제 어디서 소비할 것인가** 이다.

## 이번 초안의 범위

이번 초안에서 하는 것:

1. 현재 DB에 실제로 들어온 raw inventory를 기준으로 `서비스분야 / 사용자구분 / 지원유형` 을 정리
2. raw label을 그대로 쓸 수 있는 층과, token 분해가 필요한 층을 분리
3. 현재 코드(`CollectCategorySupport.mapGov24CompatCategory`)와 충돌하지 않는 internal rule 초안 작성

이번 초안에서 하지 않는 것:

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` stable code SQL 생성
2. `Gov24` 값을 `YOUTH_MID` 로 연결
3. `지원유형` 172 raw 문자열을 하나의 hard closed set 으로 강제 collapse
4. recommendation / matcher / read-model 점수 규칙 변경

## 현재 DB 기준 raw inventory

기준:

- `raw_api_payloads.source_type='GOV24'`
- `api_category='LIST'`
- local snapshot `10942` services

### 1. `서비스분야`

현재 exact label inventory는 `10개` 입니다.

| raw label | 서비스 수 |
|---|---:|
| `생활안정` | 2274 |
| `농림축산어업` | 1674 |
| `보육·교육` | 1506 |
| `보건·의료` | 1216 |
| `임신·출산` | 915 |
| `고용·창업` | 845 |
| `문화·환경` | 668 |
| `보호·돌봄` | 638 |
| `행정·안전` | 637 |
| `주거·자립` | 569 |

### 2. `사용자구분`

raw 조합은 `14개` 이지만, `||` split 기준 base token은 `4개` 입니다.

base token:

| token | 서비스 수 |
|---|---:|
| `개인` | 9489 |
| `법인/시설/단체` | 1041 |
| `가구` | 698 |
| `소상공인` | 366 |

대표 raw 조합:

| raw label | 서비스 수 |
|---|---:|
| `개인` | 8978 |
| `법인/시설/단체` | 723 |
| `가구` | 425 |
| `소상공인` | 236 |
| `개인||가구` | 230 |
| `개인||법인/시설/단체` | 206 |
| `소상공인||법인/시설/단체` | 63 |
| `개인||소상공인` | 27 |

### 3. `지원유형`

raw 문자열은 `172개` 이지만, `||` split 기준 base token은 `20개` 입니다.

base token:

| token | 서비스 수 |
|---|---:|
| `현금` | 4493 |
| `현물` | 1328 |
| `기타` | 807 |
| `현금(감면)` | 648 |
| `이용권` | 628 |
| `서비스(의료)` | 605 |
| `시설이용` | 504 |
| `기타(교육)` | 483 |
| `현금(보험)` | 411 |
| `현금(장학금)` | 371 |
| `현금(융자)` | 306 |
| `기타(상담)` | 301 |
| `서비스(돌봄)` | 269 |
| `서비스(일자리)` | 155 |
| `의료지원` | 83 |
| `상담/법률지원` | 80 |
| `기술지원` | 68 |
| `문화/여가지원` | 25 |
| `민원` | 8 |
| `봉사/기부` | 4 |

즉 `지원유형` 은 raw 조합은 많지만, 실제 token inventory는 그보다 훨씬 좁습니다.

## 1차 internal rule

## 1. `서비스분야`

### 규칙 A. summary label은 exact raw label을 그대로 쓴다

- `gov24ServiceFieldLabel` 은 exact raw label을 그대로 사용
- 현재 managed inventory는 위 `10개` label
- 이번 단계에서는 code를 새로 만들지 않고 label-first 로 유지

즉:

- `생활안정` 은 `생활안정`
- `농림축산어업` 은 `농림축산어업`
- `보육·교육` 은 `보육·교육`

처럼 **raw exact label을 보존**합니다.

### 규칙 B. compat unified category는 현재 코드 규칙을 유지한다

현재 [CollectCategorySupport.java](../../backend/src/main/java/com/example/welfare/collect/support/CollectCategorySupport.java) 기준 compat mapping은 아래처럼 읽는 것이 맞습니다.

| `서비스분야` raw | current compat unified category |
|---|---|
| `생활안정` | `금융·생활지원` |
| `농림축산어업` | `기타` |
| `보육·교육` | `교육·직업훈련` |
| `보건·의료` | `건강·의료` |
| `임신·출산` | `가족·돌봄` |
| `고용·창업` | `일자리` |
| `문화·환경` | `문화·여가` |
| `보호·돌봄` | `가족·돌봄` |
| `행정·안전` | `안전·위기` |
| `주거·자립` | `주거` |

중요:

- 이 표는 `Gov24 official category` 를 다시 정의하는 표가 아닙니다.
- 현재 우리 시스템의 `compat_unified_category` 로 어떻게 bridge 하는지에 대한 규칙입니다.

특히 `농림축산어업 -> 기타` 는 **현재 코드 규칙 그대로**이며,
별도 농업/산업 lane을 지금 새로 열지는 않습니다.

## 2. `사용자구분`

### 규칙 A. summary label은 exact raw label을 그대로 쓴다

- `gov24UserTypeLabel` 은 exact raw label을 그대로 사용
- 예:
  - `개인`
  - `개인||가구`
  - `개인||법인/시설/단체`

를 그대로 보존

즉 이 단계에서는 `개인||가구` 를 억지로 하나의 단일 label로 접지 않습니다.

### 규칙 B. internal 해석은 `||` token split 기준으로 본다

base token inventory는 현재 `4개` 로 고정 가능합니다.

| token | 내부 해석 |
|---|---|
| `개인` | 개인 대상 |
| `가구` | 가구 대상 |
| `법인/시설/단체` | 기관/시설/단체 대상 |
| `소상공인` | 소상공인 대상 |

복합값 해석 규칙:

1. `||` 로 split
2. trim
3. 순서 보존
4. 중복 제거
5. token별 의미는 위 `4개` inventory로만 해석

예:

- `개인||가구` -> `개인`, `가구`
- `개인||소상공인||법인/시설/단체` -> `개인`, `소상공인`, `법인/시설/단체`

### 규칙 C. 이번 단계에서는 `YOUTH_MID` 와 연결하지 않는다

`사용자구분` 은 `Gov24` 축입니다.

- `YOUTH_MID` 는 온통청년(`YOUTH`) canonical 축
- 따라서 `개인`, `가구`, `소상공인` 같은 값을 `YOUTH_MID` 로 연결하지 않습니다

즉 현재 단계에서 `Gov24 userType` 은

- summary label
- multi-target token inventory

까지만 관리합니다.

## 3. `지원유형`

### 규칙 A. summary label은 exact raw label을 그대로 쓴다

- `gov24BenefitTypeLabel` 은 exact raw label을 그대로 사용
- 예:
  - `현금`
  - `현금(감면)`
  - `서비스(의료)||의료지원`
  - `기타(교육)||기타(상담)`

를 그대로 보존

즉 `172개` raw 표현을 이번 단계에서 단일 closed label set으로 강제 collapse 하지 않습니다.

### 규칙 B. internal 해석은 `||` token split 기준으로 본다

base token inventory는 현재 `20개` 로 고정 가능합니다.

상위 UX grouping이나 사용자 제공 대표 샘플 기준의 QA 참고표는
[policy-gov24-benefit-type-grouping-draft.md](./policy-gov24-benefit-type-grouping-draft.md) 를 따릅니다.
다만 이 문서는 seed source가 아니며, 아래 `20`개 token inventory를 대체하지 않습니다.

1차 token inventory:

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

복합값 해석 규칙:

1. `||` 로 split
2. trim
3. 순서 보존
4. 중복 제거
5. token inventory 밖의 새 표현이 나오면 별도 backlog로 분리

### 규칙 C. 이번 단계에서는 “token-first, raw-preserving” 으로만 간다

즉 지금은

- raw exact label은 그대로 보존하고
- 내부 해석은 token 단위로 본다

까지만 합니다.

아래는 **지금 하지 않습니다**.

- `현금(감면)` 과 `현금` 을 완전히 같은 code로 강제 병합
- `기타(교육)` 을 `교육` canonical 축으로 승격
- `서비스(의료)` 와 `의료지원` 을 하나의 hard label로 통합

이유:

- raw 표현을 조기에 과도하게 접으면 의미 손실이 생깁니다.
- 현재 단계는 `Gov24` 의 raw inventory를 안전하게 canonical 경계 안으로 들이는 것이 우선입니다.

## 지금 바로 구현 가능한 층

이번 초안 기준으로 지금 바로 구현 가능한 건 아래 두 층입니다.

1. summary label layer
   - `gov24ServiceFieldLabel`
   - `gov24UserTypeLabel`
   - `gov24BenefitTypeLabel`
   exact raw label 유지

2. token inventory layer
   - `사용자구분`: 4 token
   - `지원유형`: 20 token
   `||` split + trim + de-dup

반대로 지금 바로 열지 않는 층은 아래입니다.

- stable code import/backfill SQL
- `지원유형` full hard taxonomy
- `사용자구분` 을 recommendation matcher fact로 직접 사용
- `YOUTH_MID` 와의 bridge

## 현재 deferred

이번 초안 이후에도 아래는 deferred 로 둡니다.

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` stable code SQL
2. `지원유형` 172 raw 표현의 full collapse
3. `Gov24` 값을 `YOUTH_MID` 로 연결하는 작업
4. `supportConditions` 사업체/업종/창업 상태(`JA210*`, `JA220*`, `JA120*`, `JA110*`) full-scope 승격

## 요약

1. `서비스분야` 는 exact label `10개` 가 현재 managed inventory 로 충분하다.
2. `사용자구분` 은 raw 조합 `14개` 이지만, 실제 base token은 `개인 / 가구 / 법인/시설/단체 / 소상공인` `4개`다.
3. `지원유형` 은 raw 문자열 `172개` 이지만, token 기준으로는 `20개` 가 core inventory 다.
4. 이번 단계에서는 raw exact label 보존 + `||` token split 규칙까지만 고정한다.
5. 즉 남은 문제는 “값이 뭔지 모르겠다”가 아니라 “어디까지를 hard canonical/code SQL로 올릴지”다.
