# `GOV24_*` current page visibility check

관련 문서:

- [policy-normalization-gov24-schema-acquisition-path.md](./policy-normalization-gov24-schema-acquisition-path.md)
- [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
- [policy-normalization-gov24-support-condition-source-plan.md](./policy-normalization-gov24-support-condition-source-plan.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` import/backfill SQL을 다시 열기 전에
current `data.go.kr` dataset page와 metadata만으로
finite code-label inventory가 실제로 보이는지 확인합니다.

## 대상

- current dataset page  
  https://www.data.go.kr/data/15113968/openapi.do
- `schema.org` metadata  
  https://www.data.go.kr/catalog/15113968/openapi.json

## 확인 결과

결론은 단순합니다.

1. current dataset page는 **official current entrypoint** 임이 분명하다
2. 하지만 page 본문과 metadata만으로는
   `serviceField`, `userType`, `benefitType` 의 **finite inventory** 를 직접 확인할 수 없다
3. 따라서 이 단계 이후의 다음 액션은
   current page 재탐색이 아니라
   **provider/operator codebook 요청 실행** 이다

## current page에서 실제로 보이는 것

current dataset page에서는 아래 정도만 직접 확인된다.

- dataset title / provider / update cycle
- `메타데이터 다운로드`
  - `schema.org`
  - `DCAT`
- `활용 명세`
  - `Open API 명세 확인 가이드`
  - `가이드 다운로드`
- “웹기반 오픈 API 활용 명세서인 Swagger UI는 ... 확인할 수 있습니다” 안내 문구

즉 page는 current source의 entrypoint와 metadata provenance는 제공하지만,
field-level enum/code inventory를 page text로 직접 내보내지는 않는다.

## current page에서 이번 단계에 확인되지 않은 것

이번 단계에서는 아래가 직접 확인되지 않았다.

- `serviceField` 의 finite allowed values
- `userType` 의 finite allowed values
- `benefitType` 의 finite allowed values
- 각 field의 `code <-> official label` 직접 대응표

또 page source 기준으로도 아래 문자열이 current page text에서 직접 보이지 않았다.

- `serviceField`
- `userType`
- `benefitType`
- `serviceList`
- `serviceDetail`
- `supportConditions`

즉 current dataset page는 “Swagger UI가 있다”는 안내는 주지만,
small-step reopen 판단에 필요한 field inventory를 HTML/page text만으로는 드러내지 않는다.

## `schema.org` metadata에서 확인되는 것과 한계

`https://www.data.go.kr/catalog/15113968/openapi.json` 에서는 아래는 확인된다.

- dataset name
- description
- dataset URL
- provider
- contact point
- date created / modified
- format

하지만 여기서도 아래는 주어지지 않는다.

- field별 finite enum
- code-label inventory
- `serviceField` / `userType` / `benefitType` allowed values

따라서 `schema.org` 는 provenance 보강에는 충분하지만,
SQL reopen의 source-of-truth 로는 부족하다.

## 현재 phase의 실무 결론

이번 visibility check 이후 practical next step은 아래 순서로 더 좁혀진다.

1. current dataset page가 official current entrypoint 임은 인정
2. page text + metadata만으로 finite inventory는 확인 불가로 판정
3. 따라서 `GOV24_*` import/backfill SQL reopen 전에는
   [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
   에 적어 둔 provider/operator 요청 스펙을 실제 요청 단계로 넘긴다

## 일부러 안 여는 것

이번 메모에서는 아래를 다시 열지 않는다.

- deprecated `category` / `category-code` endpoint 재검토
- sample payload 역추론으로 finite inventory 구성
- import/backfill SQL 실제 작성

## 요약

1. current `data.go.kr` page는 official current entrypoint 임이 분명하다.
2. 하지만 page text와 `schema.org` metadata만으로 `serviceField` / `userType` / `benefitType` finite inventory는 보이지 않는다.
3. 그래서 이번 단계의 결론은 “current page 확인 완료, finite inventory 미확인”이다.
4. 다음 액션은 current page 재탐색이 아니라 provider/operator codebook 요청 실행이다.
