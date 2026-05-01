# `GOV24_SERVICE_FIELD` / `GOV24_USER_TYPE` / `GOV24_BENEFIT_TYPE` label source plan

이 문서는 `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` import/backfill SQL을 **언제 다시 열 수 있는지**와, 어떤 source를 official truth로 인정할지를 정리하기 위한 메모입니다.

관련 문서:

- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [db-migration.md](../../db-migration.md)
- [phase-plan.md](../../phase-plan.md)
- [troubleshooting-log.md](../../troubleshooting-log.md)

## 현재 상태

현재 확보한 것은 아래 수준이다.

- current 공공데이터포털 `대한민국 공공서비스 정보(보조금24)` dataset page
- current operation set 이 `serviceList`, `serviceDetail`, `supportConditions` 3종이라는 공식 공지
- `supportConditions` 일부 code는 공식 label 근거가 확인돼 대표 subset seed까지 작성 완료

반면 아직 없는 것은 아래다.

- `GOV24_SERVICE_FIELD` 전체 finite code-label inventory
- `GOV24_USER_TYPE` 전체 finite code-label inventory
- `GOV24_BENEFIT_TYPE` 전체 finite code-label inventory

## 확인한 공식 근거

### 1. 2021 개편 공지

공공데이터포털 공지 기준으로, 과거 5개 operation:

- `list`
- `details`
- `category`
- `category-code`
- `org-code`

는 2021-09-15부터 삭제되고, 개편 후 3개 operation:

- `serviceList`
- `serviceDetail`
- `supportConditions`

만 사용하도록 바뀌었다.

즉 과거 `category` / `category-code` endpoint는 **deprecated source** 다.

출처:

- https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000002221

### 2. current dataset page

current public dataset page는 현재 API가 `REST`, `JSON+XML`, `Swagger UI` 기반임을 보여주지만, page text 자체에서 `serviceField`, `userType`, `benefitType` 의 finite code inventory를 직접 제공하지는 않는다.

출처:

- https://www.data.go.kr/data/15113968/openapi.do

## reopen 조건

아래 중 하나가 확보되어야 `GOV24_SERVICE_FIELD` / `USER_TYPE` / `BENEFIT_TYPE` import SQL 초안을 다시 연다.

### 1. current official Swagger/schema export

다음이 직접 보여야 한다.

- code
- official label
- 가능하면 enum domain 전체

즉 current `serviceList` / `serviceDetail` Swagger 또는 schema export 안에서
`serviceField`, `userType`, `benefitType` 의 finite inventory가 직접 확인돼야 한다.

### 2. provider-provided official codebook/export

다음도 허용 가능하다.

- 행정안전부/정부24 운영 측 공식 코드정의서
- 공공데이터포털 제공 부속 문서
- 운영 담당자 export 또는 schema table

핵심은 **current API 의미와 직접 대응되는 code-label inventory** 여야 한다는 점이다.

## reopen 불가 근거

### 1. deprecated `category` / `category-code`

이전 endpoint가 있었다는 사실만으로는 부족하다.

이유:

- 공식 공지에서 이미 deprecated 됐다.
- 공지문 자체가 기존 5종 operation에 현행화되지 않은 정보가 포함된다고 밝힌다.

따라서 old `category` / `category-code` 응답이나 예전 문서 캡처를 current codebook처럼 쓰지 않는다.

### 2. sample payload 관찰만으로의 역추론

- `serviceField` 값 몇 개를 sample에서 본 경우
- `userType` label 몇 개를 representative sample에서 본 경우
- bridge rule상 `주거/일자리/교육` 으로 보인다는 이유

이건 bridge 규칙이나 sample spike에는 쓸 수 있어도, official code import 근거로는 부족하다.

### 3. bridge 결과를 official taxonomy처럼 재사용

`compat_unified_category`, `youth taxonomy bridge` 는 `SYSTEM_DERIVED` 층이다.

이걸 다시 official `GOV24_*` label inventory처럼 넣으면 안 된다.

## 현재 가장 현실적인 다음 액션

2026-05-01 기준으로는 아래 순서가 가장 현실적이다.

1. current Swagger/schema export에서 finite inventory가 직접 보이는지 다시 확보
2. 안 보이면 provider/operator codebook 요청
3. 그 전까지는 metadata set만 유지

즉 지금은 `GOV24_* import/backfill SQL 작성`보다 **source 재확보** 가 먼저다.

## 운영/제공기관 요청 스펙

`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 에 대해 아래 정도면 sufficient source로 본다.

### 최소 필수 컬럼

- `code`
- `official label`

### 있으면 좋은 컬럼

- `sort_order`
- `active/use 여부`
- `설명`
- `적용 필드명` (`serviceField`, `userType`, `benefitType`)

### 허용 형식

- current Swagger export
- JSON schema
- CSV/XLSX
- 공식 문서 PDF

핵심은 형식이 아니라 **current API와 직접 대응되는 finite code-label inventory** 여야 한다는 점이다.

### sufficient 예시

아래처럼 field별 code와 label이 직접 대응되면 충분하다.

| field | code | label | active |
|---|---|---|---|
| `serviceField` | `A01` | `주거` | `Y` |
| `userType` | `U12` | `청년` | `Y` |
| `benefitType` | `B07` | `현금지원` | `Y` |

### 불충분 예시

아래만 있으면 충분하지 않다.

- `serviceField` label 목록만 있는 문서
- Swagger 화면 캡처 몇 장
- representative sample payload 몇 건
- deprecated `category` / `category-code` 응답 예시
- bridge 결과 기준의 내부 매핑표

## 제공기관/운영 담당자에게 보낼 요청 문구 초안

```text
Gov24 public service current API 기준으로
`serviceField`, `userType`, `benefitType` 의 전체 code-label inventory가 필요합니다.

가능하면 아래 컬럼이 포함된 공식 schema/export/codebook 전달 부탁드립니다.
- field name (`serviceField` / `userType` / `benefitType`)
- code
- official label
- active/use 여부 (있으면)
- sort_order 또는 설명 (있으면)

current API 기준의 자료가 필요하며,
deprecated `category` / `category-code` 계열 문서는 제외해도 됩니다.
```

## 실무 의미

현재 단계의 안전한 판단은 이렇다.

1. `supportConditions` 는 일부 official code subset seed가 가능하다
2. `GOV24_SERVICE_FIELD` / `USER_TYPE` / `BENEFIT_TYPE` 는 아직 finite inventory source가 부족하다
3. 따라서 지금은 metadata-only 유지가 맞고, import SQL은 source 확보 후에 다시 연다
