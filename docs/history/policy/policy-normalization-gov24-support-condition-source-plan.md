# `GOV24_SUPPORT_CONDITION` full inventory source plan

이 문서는 `GOV24_SUPPORT_CONDITION` 을 현재의 representative subset seed에서 **전체 code inventory/backfill** 단계로 넓힐 수 있는지와, 어떤 source가 있어야 그 작업을 다시 열 수 있는지를 정리하기 위한 메모입니다.

관련 문서:

- [policy-normalization-research.md](./policy-normalization-research.md)
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
- [db-migration.md](../../db-migration.md)
- [phase-plan.md](../../phase-plan.md)
- [troubleshooting-log.md](../../troubleshooting-log.md)

## 현재 상태

현재는 아래 code만 representative subset으로 seed 되어 있다.

- `JA0101`, `JA0102`
- `JA0110`, `JA0111`
- `JA0201` ~ `JA0205`
- `JA0320`
- `JA0327`
- `JA0412`

이 집합은 canonical fact group 기준으로 즉시 활용 가치가 높은 대표 code만 먼저 넣은 것이다.

반면 아직 없는 것은 아래다.

- `supportConditions` 전체 finite code inventory
- 각 code의 full official label table
- code별 active/use 여부나 deprecated 여부

## 현재까지 확인한 공식 근거

### 1. current API operation

공공데이터포털 2021 개편 공지 기준으로 current source-of-truth operation은:

- `serviceList`
- `serviceDetail`
- `supportConditions`

뿐이다.

출처:

- https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000002221

### 2. current public dataset page

current public dataset page는 current dataset과 Swagger UI 존재를 보여주지만, page text 자체에서 `supportConditions` 전체 code table을 직접 제공하지는 않는다.

출처:

- https://www.data.go.kr/data/15113968/openapi.do

### 3. 현재 대표 subset 근거

현재 seed에 들어간 대표 code는 공식 조사 문서와 sample spike에서 직접 확인된 코드들이다.

예:

- 성별: `JA0101`, `JA0102`
- 연령: `JA0110`, `JA0111`
- 소득: `JA0201` ~ `JA0205`
- 교육: `JA0320`
- 취업: `JA0327`
- 가구: `JA0412`

즉 representative subset 수준의 official 근거는 있지만, **full inventory source** 는 아직 아니다.

## reopen 조건

아래 중 하나가 확보되어야 `GOV24_SUPPORT_CONDITION` 전체 code inventory 확장/backfill 초안을 다시 연다.

### 1. current Swagger/schema의 full enum inventory

다음이 직접 보여야 한다.

- code
- official label
- 가능하면 fact group 또는 source section
- 가능하면 active/use 여부

즉 current `supportConditions` schema export 안에서 finite code domain 전체가 직접 확인돼야 한다.

### 2. provider-provided official codebook/export

다음도 허용 가능하다.

- 행정안전부/정부24 운영 측 공식 지원조건 코드표
- 공공데이터포털 부속 문서
- 운영 담당자 export/schema table

핵심은 **current API와 직접 대응되는 전체 code-label inventory** 여야 한다는 점이다.

## reopen 불가 근거

### 1. representative subset만 있는 문서

대표 code 몇 개만 보이는 문서는 full inventory 근거가 아니다.

### 2. sample payload 누적 관찰

sample service를 많이 모아도, 관찰되지 않은 code가 남아 있을 수 있다.
이건 coverage를 넓히는 보조 자료일 뿐, official full inventory를 대체하지 못한다.

### 3. fact group 추론

`JA03xx 는 교육/취업일 것` 같은 패턴 추론만으로 code table을 채우면 안 된다.

## 현재 가장 안전한 판단

2026-05-01 기준으로는:

1. representative subset seed는 유지
2. full inventory/backfill 은 보류
3. current Swagger/schema export 또는 provider codebook 확보가 다음 액션

즉 지금은 `subset 확대 SQL 작성`보다 **full inventory source 확보** 가 먼저다.

## 제공기관/운영 담당자 요청 스펙

`GOV24_SUPPORT_CONDITION` full inventory에 대해 아래 정도면 sufficient source로 본다.

### 최소 필수 컬럼

- `code`
- `official label`

### 있으면 좋은 컬럼

- `fact group` 또는 분류
- `active/use 여부`
- `설명`
- `source section`

### 허용 형식

- current Swagger/schema export
- CSV/XLSX
- 공식 PDF/codebook

### sufficient 예시

| code | label | fact_group | active |
|---|---|---|---|
| `JA0203` | `중위소득 76~100%` | `INCOME` | `Y` |
| `JA0327` | `구직자/실업자` | `EMPLOYMENT` | `Y` |

### 불충분 예시

- 대표 code 몇 개만 보이는 표
- sample payload에서 관찰한 code 목록
- 내부 fact group 추론 메모

## 실무 의미

현재 단계의 안전한 결론은 이렇다.

1. `GOV24_SUPPORT_CONDITION` 은 representative subset까지는 공식 근거가 있다
2. full inventory/backfill 은 아직 current source가 부족하다
3. 따라서 지금은 subset을 유지하고, full import는 source 확보 후 다시 연다
