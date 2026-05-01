# `GOV24_*` codebook 요청 템플릿

관련 문서:

- [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
- [policy-normalization-gov24-schema-acquisition-path.md](./policy-normalization-gov24-schema-acquisition-path.md)
- [policy-normalization-gov24-swagger-visibility-check.md](./policy-normalization-gov24-swagger-visibility-check.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` import/backfill SQL을 다시 열기 위해
provider/operator 에 실제로 전달할 수 있는 요청 템플릿을 고정합니다.

핵심은 “무언가 자료를 받기”가 아니라
**current API 의미와 직접 대응되는 finite code-label inventory** 를 받는 것입니다.

## 이 템플릿을 쓰는 조건

아래 두 조건이 모두 참일 때 이 요청 템플릿으로 넘어갑니다.

1. current `data.go.kr` dataset page는 official current entrypoint 로 확인됨
2. page text / `schema.org` metadata만으로는
   `serviceField`, `userType`, `benefitType` finite inventory가 직접 보이지 않음

즉 이 템플릿은
“current page 확인은 끝났고,
finite inventory가 안 보여서 operator/provider source가 필요한 상태”를 전제로 합니다.

## 요청 대상

우선순위는 아래 순서입니다.

1. 공공데이터포털 dataset 문의/제공기관 연결 경로
2. 행정안전부/정부24 운영 담당자
3. 내부 운영자가 이미 가진 current export/schema table

## 요청 제목 템플릿

```text
[Gov24 current API codebook 요청] serviceField / userType / benefitType code-label inventory 확인 요청
```

## 요청 본문 템플릿

```text
안녕하세요.

Gov24 대한민국 공공서비스(혜택) 정보 current API 기준으로
아래 3개 필드의 current code-label inventory가 필요합니다.

- serviceField
- userType
- benefitType

현재 public dataset page와 metadata는 확인했으나,
field-level finite inventory는 직접 확인되지 않아
current API 기준의 공식 schema/export/codebook이 필요합니다.

필요한 최소 정보는 아래와 같습니다.
- field name (`serviceField` / `userType` / `benefitType`)
- code
- official label

있으면 같이 부탁드리는 정보:
- active/use 여부
- sort_order
- 설명

허용 가능한 자료 형식:
- current Swagger export
- JSON schema
- CSV/XLSX
- 공식 문서 PDF

중요한 점은 자료 형식보다
current API 의미와 직접 대응되는 finite code-label inventory여야 한다는 점입니다.

참고로 deprecated `category` / `category-code` 계열 문서는 이번 요청 대상이 아닙니다.

감사합니다.
```

## 더 짧은 운영자용 템플릿

```text
Gov24 current API 기준 `serviceField`, `userType`, `benefitType`
전체 code-label inventory가 필요합니다.

최소:
- field name
- code
- official label

가능 형식:
- Swagger export
- JSON schema
- CSV/XLSX
- 공식 문서 PDF

deprecated `category` / `category-code` 자료는 제외해도 됩니다.
```

## sufficient example

아래처럼 field별 code와 label이 current API 기준으로 직접 대응되면 충분합니다.

| field | code | official_label | active |
|---|---|---|---|
| `serviceField` | `A01` | `주거` | `Y` |
| `serviceField` | `A02` | `교육` | `Y` |
| `userType` | `U12` | `청년` | `Y` |
| `benefitType` | `B07` | `현금지원` | `Y` |

또는 아래처럼 field별 enum schema도 충분합니다.

```json
{
  "field": "userType",
  "enum": [
    {"code": "U12", "label": "청년"},
    {"code": "U13", "label": "노인"}
  ]
}
```

## insufficient example

아래는 요청 응답으로 와도 reopen 근거로는 부족합니다.

- dataset page 캡처 이미지
- Swagger 화면 스크린샷만 있는 문서
- sample payload 1~2건
- label 목록만 있고 code가 없는 문서
- code만 있고 official label이 없는 문서
- deprecated `category` / `category-code` 자료
- 내부 bridge/compat 매핑표

## 요청 후 판정 기준

### reopen 가능

아래가 충족되면 `GOV24_SERVICE_FIELD` / `USER_TYPE` / `BENEFIT_TYPE` import/backfill SQL 초안을 다시 연다.

- `field name + code + official label` 직접 대응 확인
- current API 기준 자료임이 분명함
- field domain이 finite inventory로 취급 가능함

### reopen 보류

아래 중 하나면 SQL reopen은 계속 보류한다.

- screenshot만 옴
- sample payload만 옴
- label-only 자료만 옴
- old/deprecated endpoint 자료만 옴
- current API 기준인지 불분명함

## 이번 단계의 실무 의미

이번 문서로 practical next action은 더 단순해진다.

1. current page 재탐색은 여기서 끝
2. provider/operator 에는 이 템플릿으로 요청
3. 자료가 오면 sufficient / insufficient 판정
4. sufficient면 그때 SQL reopen

## 요약

1. `GOV24_*` SQL reopen의 다음 단계는 더 이상 page 탐색이 아니다.
2. 실제 다음 액션은 provider/operator 에 current codebook 요청을 보내는 것이다.
3. sufficient source의 최소 단위는 `field name + code + official label` 이다.
4. screenshot, sample payload, deprecated 자료는 reopen 근거가 되지 않는다.
