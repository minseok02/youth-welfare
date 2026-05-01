# `GOV24_*` Swagger/Schema 확보 경로

관련 문서:

- [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
- [policy-normalization-gov24-support-condition-source-plan.md](./policy-normalization-gov24-support-condition-source-plan.md)
- [policy-normalization-blocked-sql-reopen-priority.md](./policy-normalization-blocked-sql-reopen-priority.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` import/backfill SQL을 다시 열기 전에
어디서 current Swagger/schema export를 확보할지 구체화합니다.

## 결론

현재 가장 현실적인 확보 경로는 아래 순서입니다.

1. `data.go.kr` current dataset page의 **Swagger UI**
2. 같은 페이지의 `schema.org` / `DCAT` 메타데이터
3. 그래도 finite code-label inventory가 안 보이면 provider/operator codebook 요청

즉 다음 practical action은 “deprecated category endpoint 재활용”이 아니라
**current dataset page에서 Swagger/schema export 증적을 먼저 확보하는 것**입니다.

## 현재 확인된 공식 경로

## 1. current dataset page

대상:

- `행정안전부_대한민국 공공서비스(혜택) 정보`
- https://www.data.go.kr/data/15113968/openapi.do

현재 page에서 확인되는 것:

- current API dataset page 자체
- `메타데이터 다운로드`
  - `schema.org`
  - `DCAT`
- `활용 명세`
  - `Open API 명세 확인 가이드`
  - `가이드 다운로드`
- “웹기반 오픈 API 활용 명세서인 Swagger UI는 ... 확인할 수 있습니다” 문구

즉 official current entrypoint 자체는 이미 명확합니다.

## 2. 개편 공지

보조 근거:

- https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000002221

이 공지로 확인되는 것:

- current operation set 은
  - `serviceList`
  - `serviceDetail`
  - `supportConditions`
- old `category` / `category-code` 계열은 deprecated

따라서 schema 확보도 current 3개 operation 기준으로만 봅니다.

## 확보 경로별 해석

## 1순위. Swagger UI

가장 먼저 확인할 것:

- `serviceList`
- `serviceDetail`
- `supportConditions`

안에서

- `serviceField`
- `userType`
- `benefitType`

의 finite enum/code-label inventory가 직접 드러나는지입니다.

충분 조건:

- field별 가능한 값이 Swagger/schema 안에서 직접 보임
- code와 label이 current API 의미와 직접 대응됨

부족 조건:

- field 존재만 보이고 allowed values는 안 보임
- sample response만 있고 finite inventory는 안 보임

## 2순위. `schema.org` / `DCAT`

이 경로는 metadata provenance 확인에는 쓸 수 있습니다.

쓸모:

- dataset identifier
- 제공기관/관리부서
- current dataset metadata 증적

한계:

- field-level finite code-label inventory를 직접 주지 않을 가능성이 큼

따라서 `schema.org` / `DCAT` 는
SQL reopen의 충분조건이 아니라
“current source를 어디서 확인했는지”의 provenance 보강용입니다.

## 3순위. provider/operator codebook 요청

Swagger/schema에서도 finite inventory가 직접 보이지 않으면
그 다음은 provider/operator 쪽 요청입니다.

이때 요청할 최소 스펙:

- field name (`serviceField`, `userType`, `benefitType`)
- code
- official label
- active/use 여부 (있으면)

즉 current API 의미와 직접 대응되는 codebook이 필요합니다.

## 현재 phase의 실무 순서

1. `data.go.kr` current dataset page 열기
2. Swagger UI에서 field-level enum/code inventory 직접 노출 여부 확인
3. `schema.org` / `DCAT` 메타데이터는 provenance 증적으로만 확보
4. finite inventory가 안 보이면 provider/operator codebook 요청

## 이번 단계에서 일부러 안 여는 것

이번 메모에서는 아래를 같이 열지 않습니다.

- import/backfill SQL 실제 작성
- old `category` / `category-code` endpoint 재검토
- representative sample payload로 code inventory 역추론

이 단계는 확보 경로만 고정합니다.

## 요약

1. `GOV24_*` SQL reopen의 practical next step은 `data.go.kr` current dataset page의 Swagger/schema 확인입니다.
2. `schema.org` / `DCAT` 는 provenance 보강용이지 finite inventory의 충분조건은 아닙니다.
3. Swagger에서도 allowed values가 안 보이면 provider/operator codebook 요청으로 넘어갑니다.
4. old `category` / `category-code` 재활용은 다시 열지 않습니다.
