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

1. 현재 남은 `978`건 gap이 어떤 code 군집 때문에 생기는지 고정
2. 나중에 `supportConditions fact scope` 를 넓힐지 판단할 때 출발점 제공

이 문서는 `hard taxonomy/import-backfill` 문서가 아닙니다.
현재 runtime extractor 기준의 `unmapped official code inventory` 메모입니다.

## 현재 기준선

현재 local audit baseline:

- `Gov24 total services = 10942`
- `support fact services = 9963`
- `support missing fact services = 979`
- `missing_no_support_raw = 0`
- `missing_all_null_payload = 0`
- `missing_unmapped_only_payload = 979`
- `missing_mapped_signal_payload = 0`

즉 남은 gap은 수집 실패가 아니라,
현재 extractor가 아직 승격하지 않는 official support code로 설명됩니다.

## 현재 extractor가 읽는 support code

현재 runtime fact extractor는 아래 축을 읽습니다.

- 성별: `JA0101`, `JA0102`
- 연령: `JA0110`, `JA0111`
- 소득: `JA0201~JA0205`
- 교육: `JA0317~JA0320`
- 고용: `JA0326`, `JA0327`
- 특수대상: `JA0328~JA0330`
- 가구: `JA0401~JA0404`, `JA0411~JA0414`

## 현재 missing fact 서비스에서 많이 보이는 unmapped code

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

## 해석

지금 gap의 중심은 거의 명확합니다.

1. **사업체 유형**
   - `JA2101`, `JA2102`, `JA2103`
2. **업종**
   - `JA2201`, `JA2202`, `JA2203`, `JA2299`
   - `JA1201`, `JA1202`, `JA1299`
3. **창업/사업 단계**
   - `JA1101`, `JA1102`, `JA1103`

즉 남은 `979`건은
현재 추천/정책 facts가 주로 보는 `개인 eligibility` 축보다,
`사업자/업종/기관` 축이 중심인 payload가 대부분입니다.

## 지금 당장 의미하는 것

- retry/backfill을 더 해도 이 gap은 줄지 않습니다.
- 현재 runtime collect는 이미 닫혔습니다.
- 다음 질문은 “수집이 실패했나?”가 아니라
  “사업체/업종/창업 상태 code를 현재 fact scope에 넣을 것인가?” 입니다.

## 현재 판단

현재 단계에서는 **바로 fact scope에 넣지 않고 deferred** 가 맞습니다.

이유:

1. 현재 사용자 프로필과 추천 경계는 `연령`, `소득`, `가구`, `고용`, `교육`, `특수대상` 같은
   **개인 eligibility 축** 중심입니다.
2. 현재 unmapped gap의 중심은
   - 사업체 유형
   - 업종
   - 창업/사업 단계
   처럼 **사업자/기관 축** 입니다.
3. 이 축은 지금 제품의 사용자 입력, 우선순위 matcher, 추천 score에서
   직접 소비하는 경로가 거의 없습니다.

즉 지금 당장 `JA2101`, `JA2201`, `JA1102` 를 fact로 승격해도
현재 추천/정책 UX 개선으로 바로 이어질 가능성은 낮고,
오히려 fact group만 늘어나 제품 의미가 불분명해질 수 있습니다.

## deferred 결론

현재 runtime collect closeout 기준으로는 아래 판단을 유지합니다.

- `JA210*` 사업체 유형: deferred
- `JA220*`, `JA120*`, `JA1299`, `JA2299` 업종: deferred
- `JA110*` 창업/사업 단계: deferred

다만 아래 중 하나가 생기면 다시 active 검토합니다.

1. 사용자 프로필에 사업자/창업자/기관 속성이 실제로 들어감
2. 추천 matcher가 사업체/업종 조건을 실제로 사용하게 됨
3. `창업` lane을 별도 fact group으로 다룰 제품 요구가 생김

그 전까지는 이 inventory를 **gap 설명용 기준선**으로만 유지하는 편이 맞습니다.

## 지금 단계에서 아직 하지 않는 것

이 문서를 만들었다고 해서 아래를 바로 여는 것은 아닙니다.

- `GOV24_SUPPORT_CONDITION` full inventory hard import/backfill
- `JA2101/JA2202` 류를 즉시 recommendation fact로 사용
- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` blocked SQL 재개

이 inventory는 우선 **현재 gap의 성격을 정확히 말하기 위한 메모**입니다.

## 다음 액션 후보

다음 턴에서 선택 가능한 practical action:

1. `JA2101/JA2102/JA2103` 같은 사업체 유형 code를 fact scope에 넣을지 검토
2. `JA2201/JA2202/JA2203/JA2299` 를 업종 fact로 승격할지 검토
3. `JA1101/JA1102/JA1103` 를 창업/사업 단계 fact로 승격할지 검토
4. 그대로 두고 runtime collect closeout 상태만 유지

현재 단계의 판단은 위 세 축 모두 **deferred 유지** 입니다.
즉 다음 practical action은 구현보다 이 결론을 기준선으로 고정하고,
정말 제품 요구가 생겼을 때만 다시 여는 쪽이 맞습니다.
