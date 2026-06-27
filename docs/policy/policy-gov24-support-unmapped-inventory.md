# `Gov24` support unmapped inventory

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)

## 목적

이 문서는 `Gov24 supportConditions` raw는 있지만
현재 `GOV24_SUPPORT_CONDITION` fact로는 승격하지 않는 code 집합을
runtime audit 기준으로 정리합니다.

이 문서의 목적은 두 가지입니다.

1. 과거 남았던 `979`건 gap이 어떤 code 군집 때문에 생겼는지 고정
2. 나중에 `supportConditions fact scope` 를 넓힐지 판단할 때 출발점 제공

이 문서는 `hard taxonomy/import-backfill` 문서가 아닙니다.
현재 runtime extractor 기준의 `unmapped official code inventory` 메모입니다.

## 현재 기준선

2026-06-01 local audit baseline:

- `Gov24 total services = 10945`
- `support fact services = 10945`
- `support missing fact services = 0`
- `missing_no_support_raw = 0`
- `missing_all_null_payload = 0`
- `missing_unmapped_only_payload = 0`
- `missing_mapped_signal_payload = 0`

즉 현재 남은 support fact gap은 없습니다.

직전 baseline에서는 `support fact services = 9967`, `support missing fact services = 978` 이었고,
그중 `977`건은 사업체/업종/창업 상태 code를 fact scope에 추가하면서 해소했습니다.

## 현재 extractor가 읽는 support code

현재 runtime fact extractor는 아래 축을 읽습니다.

- 성별: `JA0101`, `JA0102`
- 연령: `JA0110`, `JA0111`
- 소득: `JA0201~JA0205`
- 교육: `JA0317~JA0320`
- 고용: `JA0326`, `JA0327`
- 산업 종사자: `JA0313~JA0316`
- 특수대상: `JA0328~JA0330`
- 가구: `JA0401~JA0404`, `JA0411~JA0414`
- 창업/사업 단계: `JA1101~JA1103`
- 업종: `JA1201`, `JA1202`, `JA1299`, `JA2201~JA2203`, `JA2299`
- 사업체 유형: `JA2101~JA2103`

## 이전 missing fact 서비스에서 많이 보였던 unmapped code

`deploy/smoke/run-local-gov24-quality-audit.sh` 기준 top code:

| code | 서비스 수 | 해석 |
|---|---:|---|
| `JA2101` | 465 | 중소기업 |
| `JA2201` | 382 | 제조업 |
| `JA2299` | 381 | 기타업종 |
| `JA2103` | 287 | 기관/단체 |
| `JA2202` | 280 | 농업, 임업 및 어업 |
| `JA1102` | 269 | 영업중 |
| `JA1299` | 244 | 기타업종 |
| `JA1201` | 239 | 음식업 |
| `JA1202` | 233 | 제조업 |
| `JA2203` | 224 | 정보통신업 |
| `JA2102` | 52 | 사회복지시설 |
| `JA1101` | 46 | 예비창업자 |
| `JA1103` | 18 | 생계곤란/폐업예정자 |
| `JA0410` | 3 | 해당사항없음 |
| `JA0313` | 2 | 농업인 |

반대로 이전 missing fact 집합에는 아래 개인 eligibility 축이 사실상 남지 않았습니다.

- `JA0301` 예비부모/난임: `0`
- `JA0302` 임산부: `0`
- `JA0303` 출산/입양: `0`

즉 이 축들은 raw에는 존재하지만, 실제 missing 서비스는 다른 이미-매핑된 조건 fact도 같이 갖고 있어
현재 gap의 원인으로 남아 있지 않습니다.

또한 개인 eligibility 쪽 잔여는 매우 작습니다.

- `JA0313` 농업인: `2`
- `JA0314` 어업인: `1`
- `JA0315` 축산업인: `1`
- `JA0316` 임업인: `1`
- `JA0322` 해당사항없음: `2`
- `JA0410` 해당사항없음: `3`

## 해석

지금 gap의 중심은 거의 명확합니다.

1. **사업체 유형**
   - `JA2101`, `JA2102`, `JA2103`
2. **업종**
   - `JA2201`, `JA2202`, `JA2203`, `JA2299`
   - `JA1201`, `JA1202`, `JA1299`
3. **창업/사업 단계**
   - `JA1101`, `JA1102`, `JA1103`

즉 이전에 남았던 `979`건은
현재 추천/정책 facts가 주로 보는 `개인 eligibility` 축보다,
`사업자/업종/기관` 축이 중심인 payload가 대부분입니다.

개인 eligibility 미매핑 축 자체가 없는 것은 아닙니다.
다만 현재 missing fact 집합에서 의미 있게 남아 있는 값은 거의 없고,
실질적인 gap 설명력은 사업체/업종/창업 상태 쪽이 압도적입니다.

## 현재 판단

2026-06-01 작업에서 아래 축은 `GOV24_SUPPORT_CONDITION` runtime fact scope로 승격했습니다.

- `JA210*` 사업체 유형
- `JA220*`, `JA120*`, `JA1299`, `JA2299` 업종
- `JA110*` 창업/사업 단계
- `JA0313~JA0316` 농/어/축/임업인

`JA0322`, `JA0410` 은 `해당사항없음` 이라 `NO_OP` fact로만 보존합니다.

현재 이 문서의 역할은 deferred 판단 기록이 아니라,
과거 gap의 원인과 현재 제외 대상의 근거를 보존하는 것입니다.

추가로 `2026-06-02` 기준 official Swagger inventory drift guard도 붙였다.

- [run-local-gov24-support-conditions-validation.sh](../../deploy/smoke/run-local-gov24-support-conditions-validation.sh)
- artifact: `tmp/gov24-support-conditions-validation/latest-gov24-support-conditions-validation-summary.json`

이 guard의 current 기준은 아래다.

1. official `JA*` code `48개`
2. 현재 handled code는 `45개`
3. intentional deferred official code는 `JA0301`, `JA0302`, `JA0303`
4. `JA1201` 공식 label `음식적업` 은 내부에서 `음식업` 으로 정정
5. `JA2202` 공식 label `농업,임업 및 어업` 은 내부에서 공백만 정규화

## 지금 단계에서 아직 하지 않는 것

이 문서를 만들었다고 해서 아래를 바로 여는 것은 아닙니다.

- `JA0322`, `JA0410` 같은 no-op code를 positive eligibility로 해석하기
- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` blocked SQL 재개

이 inventory는 **과거 gap의 성격과 현재 gap 해소 근거를 설명하기 위한 메모**입니다.
