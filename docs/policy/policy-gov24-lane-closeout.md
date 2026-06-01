# `Gov24` lane closeout

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-reopen-checklist.md](./policy-gov24-reopen-checklist.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)

## 목적

이 문서는 `Gov24` lane에서

- 이미 운영 반영까지 닫힌 범위
- 계속 deferred 로 남기는 범위
- 다시 열 때의 최소 조건

만 빠르게 읽기 위한 closeout 요약입니다.

## 현재 결론

현재 `Gov24` lane은 **bounded 제품 확장, runtime fact gap 해소, `YOUTH_MID` bridge, stable internal code seed/backfill까지 닫힌 상태** 입니다.

이미 active/closed 인 범위:

1. `serviceField` public filter
2. `userType` public filter
3. `benefitType` public filter
4. detail page `Gov24` tag -> filtered discovery bridge
5. recommendation soft additive scoring
6. recommendation priority bucket bridge
7. `Gov24 -> YOUTH_MID` bridge
8. `supportConditions` full-scope business/industry/startup fact 승격
9. `Gov24` 3축 stable internal code seed/backfill
10. `JA0322`, `JA0410` no-op support condition fact 보존

계속 deferred 인 범위:

1. 외부 공식 stable codebook 기반 import/backfill
2. `Gov24` raw 조합값 전체를 hard eligibility fact로 승격

## 지금 운영 기준으로 열린 것

### 1. public discovery filter

현재 `/api/policies`, `/api/policies/search`, `/policies` 에서 실제로 열린 축은 아래 셋입니다.

1. `gov24ServiceField`
2. `gov24UserType`
3. `gov24BenefitType`

이 셋은 모두 canonical term 우선, legacy raw summary fallback 계약을 유지합니다.

추가로 `PolicyDetailPage` 도 `Gov24` 태그를 read-only badge로만 두지 않고,
같은 `Gov24` filter 결과로 바로 돌아가는 discovery bridge까지 연 상태입니다.

- `분야 {gov24ServiceFieldLabel}` -> `/policies?sourceType=GOV24&gov24ServiceField=...`
- `대상 {gov24UserTypeLabel}` -> `/policies?sourceType=GOV24&gov24UserType=...`
- `유형 {gov24BenefitTypeLabel}` -> `/policies?sourceType=GOV24&gov24BenefitType=...`

운영 데이터처럼 detail API의 `gov24UserTypeLabel`, `gov24BenefitTypeLabel` 이 비어 있는 경우에도,
현재 프런트는 managed token 범위 안에서만 `tags` / `provisionType` fallback 을 써서 같은 discovery chip 을 유지합니다.

### 2. recommendation scoring

recommendation 은 이제 `Gov24` taxonomy를 **soft additive bonus** 와
priority matcher bucket bridge로 소비합니다.

- `serviceField`: small category-aligned bonus
- `benefitType`: 일부 managed token만 small bonus
- `userType`: `개인`, `가구` 정도의 tiny audience bonus

또한 `주거·자립`, `고용·창업`, `보육·교육`, `생활안정` 같은 `serviceField` 와
`현금`, `현금(융자)`, `서비스(일자리)`, `서비스(돌봄)` 같은 managed `benefitType` 은
projection의 `priorityBuckets` 로 연결됩니다.

즉 `Gov24` 값이 추천 hard gate로 승격된 것은 아니지만,
사용자 우선순위 matcher가 이해하는 bucket에는 공식 taxonomy 기반으로 연결됩니다.

### 3. `supportConditions` fact scope

`GOV24_SUPPORT_CONDITION` runtime fact scope는 개인 eligibility subset에서
사업체/업종/창업 상태까지 확장했습니다.

- 산업 종사자: `JA0313~JA0316`
- 창업/사업 단계: `JA1101~JA1103`
- 업종: `JA1201`, `JA1202`, `JA1299`, `JA2201~JA2203`, `JA2299`
- 사업체 유형: `JA2101~JA2103`

2026-06-01 local audit 기준 coverage는 `10945 / 10945` 입니다.
`JA0322`, `JA0410` 같은 `해당사항없음` 계열 code는 `NO_OP` fact로 보존합니다.

### 4. `Gov24 -> YOUTH_MID` bridge

`Gov24` `serviceField` 를 온통청년 대/중분류 label로 연결합니다.

- `주거·자립` -> `주거 / 주택 및 거주지`
- `고용·창업` -> `일자리 / 취업|재직자|창업`
- `보육·교육` -> `교육 / 미래역량강화|교육비지원|온라인교육`
- `생활안정` -> `복지문화 / 취약계층 및 금융지원`
- `문화·환경` -> `복지문화 / 문화활동`
- `보건·의료`, `임신·출산` -> `복지문화 / 건강`
- `보호·돌봄` -> `복지문화 / 권익보호`
- `행정·안전` -> `참여권리 / 정책인프라구축`
- `농림축산어업` -> `일자리 / 재직자`

### 5. stable internal code

`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE_TOKEN`, `GOV24_BENEFIT_TYPE_TOKEN` 은
현재 observed inventory 기준 internal code를 seed하고,
기존 `service_taxonomy_terms` 와 `service_taxonomy_summary_slots` 에 backfill 했습니다.

## 지금 일부러 안 연 것

아래는 현재 명시적으로 안 연 상태입니다.

### 1. 외부 공식 stable codebook import

현재 code는 current observed inventory 기준의 internal stable code입니다.
Gov24가 별도 공식 enum/codebook을 제공하면 그 값을 source-of-truth로 다시 import할 수 있습니다.

### 2. raw 조합값 hard fact 승격

`개인||가구`, `현금||서비스(의료)` 같은 raw 조합값 전체를
hard eligibility fact로 올리지는 않습니다.

## 왜 여기서 멈췄는가

이 lane의 목적은 `Gov24` 값을 제품 surface에 실제로 연결하되,
외부 공식 codebook 없이 raw 조합값 전체를 hard eligibility로 오해하지 않게 경계를 두는 것입니다.

여기서 더 가면 다음부터는 observed inventory 기반 internal code가 아니라,
외부 공식 codebook이나 더 강한 제품/데이터 모델 결정을 여는 작업이 됩니다.

즉 지금 남은 것은 “구현 누락”보다 **명시 승인 전제의 deferred scope** 입니다.

## 다시 열 조건

아래 중 하나가 명시적으로 승인될 때만 reopen 하는 편이 맞습니다.

1. 외부 공식 stable codebook import
2. raw 조합값 전체를 hard eligibility fact로 승격할 제품 요구

그 전까지는 현재 label-first canonical + public filter + soft scoring 경계를 유지합니다.

## 지금 먼저 볼 문서

1. 현재 구현 계약: [policy-normalization-current-state.md](./policy-normalization-current-state.md)
2. blocked/deferred 경계: [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
3. reopen 판단 절차: [policy-gov24-reopen-checklist.md](./policy-gov24-reopen-checklist.md)

## 요약

1. `Gov24` public filter 3축은 이미 운영 반영까지 닫혔습니다.
2. recommendation scoring도 bounded soft additive bonus까지는 열렸습니다.
3. `supportConditions` full-scope business/industry/startup/no-op fact gap은 해소했습니다.
4. 현재 남은 것은 외부 공식 codebook이 생겼을 때의 재수입 여부입니다.
