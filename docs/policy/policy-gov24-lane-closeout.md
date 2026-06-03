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
5. broad keyword public search first-page discovery balance
6. recommendation soft additive scoring
7. recommendation priority bucket bridge
8. `Gov24 -> YOUTH_MID` bridge
9. `supportConditions` full-scope business/industry/startup fact 승격
10. `Gov24` 3축 stable internal code seed/backfill
11. `JA0322`, `JA0410` no-op support condition fact 보존
12. `Gov24` list 텍스트 기반 지역 row 추론

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

2026-06-02 기준으로 필터가 없는 넓은 키워드 검색의 첫 페이지 발견성도 bounded 하게 보정했습니다.
조건은 `keyword` 토큰이 있고, 첫 페이지(`offset=0`), `RELEVANCE` 정렬, `pageSize>=10`,
`sourceType` 및 Gov24 3축 필터가 모두 없는 경우로 제한합니다.
이때 repository는 후보 window를 최대 100건까지 넓혀 읽고, 첫 페이지에 `GOV24` 가 하나도 없지만
window 안에는 `GOV24` 후보가 있으면 첫 `GOV24` 후보 1건을 첫 페이지 중간부에 삽입합니다.
명시 Gov24 필터, 출처 필터, 페이지네이션 후속 페이지, 최신순/마감순 정렬에는 적용하지 않습니다.

### 2. region enrichment

`Gov24` 는 원천 응답에 온통청년 같은 명시 지역코드가 없기 때문에,
현재 수집 단계에서 아래 list text를 함께 읽어 `service_regions` 를 추론합니다.

- 소관기관명
- 접수기관
- 부서명
- 서비스명
- 서비스목적요약
- 지원대상
- 선정기준
- 지원내용
- 신청방법

명확한 `시도 + 시군구` 또는 전국 고유 시군구명이 있으면 해당 5자리 행정구역코드를 저장합니다.
시도 단위 기관명이나 본문 신호만 있으면 해당 시도 전체 행정구역코드로 확장합니다.
`전국` 신호가 있으면 지역 row를 만들지 않아 기존 전국 노출 계약을 유지합니다.
추가로 2026-06-02 runtime audit에서 확인된 누락 패턴을 닫기 위해
`세종특별자치시` 는 단일 region `36110/세종시` 로 저장하고,
전국 유일 시군구명에서 `시/군/구` 접미사만 빠진 기관명도 추론합니다
(`재단법인안산인재육성재단` -> `안산시`). 지방 계열 기관유형(`광역시도`, `시군구`,
`교육청`, `지방공기업`, `지방출자_출연기관`)에서는 기관명에 bare 시도 약칭이 있을 때
해당 시도 전체로 확장합니다. 공공기관의 bare 시도명은 false positive 위험 때문에 확장하지 않습니다
(`서울올림픽기념국민체육진흥공단` 은 서울 지역 정책으로 보지 않음).

이 보강은 기존 recommendation retrieval 의 Gov24 제목/설명 text fallback을 대체하지 않고,
수집된 Gov24 row가 다른 source와 같은 `service_regions` 경로도 탈 수 있게 하는 보완입니다.

이미 저장된 Gov24 row는 외부 API 재호출 없이 아래 경로로 LIST raw payload를 재생해
지역 row만 교체할 수 있습니다.

```bash
curl -sS -X POST "http://127.0.0.1:8082/api/admin/collect/gov24-sidecars-backfill?scope=regions&limitPerSource=0"
```

2026-06-03 local runtime 재검증 결과:

- Gov24 LIST 수집 row: `10954`
- region backfill: `scanned=10954`, `upserted=10954`, `missing=0`, `failed=0`
- 2026-06-03 latest local region backfill smoke: endpoint metric `5209ms`,
  `region_rows=36545`
- 최종 region coverage: `9816/10954 = 89.61%`
- 최종 region row: `36545`
- regionless service: `1138`
- regionless non-central agency metric: `448`
- regionless true local-agency metric: `1`
- 남은 미추론 상위 기관은 `대한법률구조공단`, `기술보증기금`, `한국전력공사`,
  `소상공인시장진흥공단`, `한국장학재단` 등 공공기관 중심입니다.
- 추가 복구는 `소관기관코드 -> 로컬정부 기관코드 lookup` 으로 처리했습니다.
  - `재단법인고성교육재단` → `5420000` → `경상남도 고성군(48820)`
  - `(재)한국전통문화전당` → `4641000` → `전주시 하위 구(52111, 52113)`
- 남은 true residual은 `온라인 신청 시연 테스트(운영)` `1`건뿐이고,
  테스트성 행정안전부 row라 자동 추론하지 않습니다.

회귀 확인은 아래 smoke로 봅니다.

```bash
bash deploy/smoke/run-local-gov24-acceptance-suite.sh
bash deploy/smoke/run-local-gov24-region-backfill-smoke.sh
bash deploy/smoke/run-local-gov24-region-coverage-audit.sh
```

주의: collect batch scope의 embedding refresh는 수집 저장 후처리입니다. local fallback 설정에서
strict embedding refresh가 `OpenAI embeddings are unavailable` 로 실패해도 수집 응답은 깨지지 않고,
warning 로그로만 남깁니다. 데이터 무결성을 확인할 때는 LIST/detail/support 저장 count와
embedding rebuild 상태를 분리해서 봅니다.

```bash
bash deploy/smoke/run-local-gov24-collect-embedding-boundary-smoke.sh
```

### 3. recommendation scoring

recommendation 은 이제 `Gov24` taxonomy를 **soft additive bonus** 와
priority matcher bucket bridge로 소비합니다.

- `serviceField`: category-aligned bounded bonus
- `benefitType`: 일부 managed token의 bounded bonus
- `userType`: `개인`, `가구` audience bonus

또한 `주거·자립`, `고용·창업`, `보육·교육`, `생활안정` 같은 `serviceField` 와
`현금`, `현금(융자)`, `서비스(일자리)`, `서비스(돌봄)` 같은 managed `benefitType` 은
projection의 `priorityBuckets` 로 연결됩니다.

2026-06-02 기준 additive bonus cap은 `10` 입니다. 즉 `Gov24` 값은 추천에서 더 강하게 소비되지만,
raw 조합값 전체를 eligibility hard gate로 승격한 것은 아닙니다.
사용자 우선순위 matcher가 이해하는 bucket에는 공식 taxonomy 기반으로 연결됩니다.

2026-06-02 server Docker `run-local-gov24-housing-signal-smoke.sh` 기준 fresh recommendation batch는
`44`건 전부 Gov24였고, top2 source distribution은 `GOV24:2`, top10 Gov24 row는 `10`건입니다.
이어 `run-local-gov24-recommend-surface-audit.sh` 와 `run-local-gov24-recommend-score-audit.sh` 는
latest batch cohort를 `GOV24_EDUCATION_SIGNAL:2,GOV24_HOUSING_SIGNAL:2,GOV24_SURFACE_AUDIT:1`
로 분리해 출력합니다.
현재 top1/top2/top10 Gov24 share는 모두 `100.00%` 이고, top10 source distribution은
5개 Gov24 smoke user 기준 `GOV24:50` 입니다.
같은 서버에서 `run-local-gov24-surface-audit.sh` 도 통과했고, `keyword=청년` 검색,
`sourceType=GOV24` 필터 검색, 기본 목록, Gov24 상세, 추천 top10 모두 Gov24 surface를 정상 반환했습니다.
2026-06-02 server Docker `run-local-gov24-education-signal-smoke.sh` 기준 교육축 fresh batch는 `32`건 전부
Gov24였고, top2는 `청년 학자금대출 장기연체자 학자금상환 지원`, `화성시 학생 장학금 지원` 입니다.
같은 서버의 signal 포함 acceptance suite는 `10` steps 모두 통과했고 `suite_duration_ms=71712` 입니다.

### 4. `supportConditions` fact scope

`GOV24_SUPPORT_CONDITION` runtime fact scope는 개인 eligibility subset에서
사업체/업종/창업 상태까지 확장했습니다.

- 산업 종사자: `JA0313~JA0316`
- 창업/사업 단계: `JA1101~JA1103`
- 업종: `JA1201`, `JA1202`, `JA1299`, `JA2201~JA2203`, `JA2299`
- 사업체 유형: `JA2101~JA2103`

2026-06-02 current server Docker audit 기준 coverage는 `10954 / 10954` 입니다.
동일 audit에서 `support_raw=10954`, `support_fact_rows=194819`, `support_missing_fact_services=0`,
`support_nested_shape=10954`, `support_flat_shape=0` 으로 닫혔습니다.
`JA0322`, `JA0410` 같은 `해당사항없음` 계열 code는 `NO_OP` fact로 보존합니다.

### 4-1. missing list sidecar repair 성능

`gov24-sidecars-backfill?scope=list&missingOnly=true&limitPerSource=0` 은 Gov24 필수 summary slot
(`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE`) 이 비어 있는 LIST row만 빠르게 복구합니다.
2026-06-02 server Docker runtime에서 필수 slot을 의도적으로 비운 뒤 재측정했고,
`before_missing=10954 -> after_missing=0`, service log `elapsedMs=16118`,
acceptance suite step `15377ms` 로 확인했습니다. 이전 generic writer 기반 suite 측정은 `308290ms` 였습니다.

### 5. `Gov24 -> YOUTH_MID` bridge

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

### 6. stable internal code

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
2. 넓은 키워드 검색 첫 페이지에서도 Gov24 후보가 candidate window 안에 있으면 완전히 묻히지 않게 했습니다.
3. recommendation scoring도 bounded soft additive bonus까지는 열렸습니다.
4. Gov24 list text 기반 지역 row 추론도 열어 다른 source와 같은 `service_regions` 경로를 탈 수 있게 했습니다.
5. `supportConditions` full-scope business/industry/startup/no-op fact gap은 해소했습니다.
6. 현재 남은 것은 외부 공식 codebook이 생겼을 때의 재수입 여부입니다.
