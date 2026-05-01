# `GOV24_SUPPORT_CONDITION` full inventory 요청 템플릿

관련 문서:

- [policy-normalization-gov24-support-condition-source-plan.md](./policy-normalization-gov24-support-condition-source-plan.md)
- [policy-normalization-gov24-codebook-request-template.md](./policy-normalization-gov24-codebook-request-template.md)
- [policy-normalization-gov24-schema-acquisition-path.md](./policy-normalization-gov24-schema-acquisition-path.md)
- [phase-plan.md](../../phase-plan.md)

## 목적

`GOV24_SUPPORT_CONDITION` 을 current representative subset seed에서
full inventory/backfill 단계로 넓히기 위해,
provider/operator 에 실제로 전달할 수 있는 요청 템플릿을 고정합니다.

핵심은 label 3종(`serviceField`, `userType`, `benefitType`)과 달리,
`supportConditions` 는 이미 representative subset seed가 있고
full inventory의 범위도 더 넓다는 점입니다.
그래서 요청도 공통 `GOV24_*` codebook과 분리합니다.

## 왜 별도 템플릿으로 분리하는가

`GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` 요청과
`GOV24_SUPPORT_CONDITION` 요청은 성격이 다릅니다.

### label 3종 요청의 성격

- field별 finite enum/code-label inventory 확인
- `field name + code + official label` 이 핵심

### `supportConditions` 요청의 성격

- 전체 code domain 확인
- code별 official label
- 가능하면 fact group / source section / active 여부까지 확인
- representative subset이 이미 있으므로
  “현재 subset 유지”와 “full inventory reopen”을 분리해서 봐야 함

즉 `supportConditions` 는 범위도 넓고
reopen 조건도 더 강하므로 별도 템플릿이 맞습니다.

## 요청 대상

우선순위는 아래 순서입니다.

1. 공공데이터포털 dataset 문의/제공기관 연결 경로
2. 행정안전부/정부24 운영 담당자
3. 내부 운영자가 가진 current export/schema table

## 요청 제목 템플릿

```text
[Gov24 supportConditions full inventory 요청] current API 지원조건 코드 전체표 확인 요청
```

## 요청 본문 템플릿

```text
안녕하세요.

Gov24 대한민국 공공서비스(혜택) current API 기준으로
`supportConditions` 전체 code inventory가 필요합니다.

현재는 대표 subset code만 활용 중이며,
full inventory/backfill 단계로 넓히기 위해
current API 기준의 공식 schema/export/codebook이 필요합니다.

필요한 최소 정보는 아래와 같습니다.
- code
- official label

있으면 같이 부탁드리는 정보:
- fact group 또는 분류
- source section
- active/use 여부
- 설명

허용 가능한 자료 형식:
- current Swagger/schema export
- CSV/XLSX
- 공식 문서 PDF

중요한 점은 representative sample이나 일부 code 목록이 아니라,
current API와 직접 대응되는 전체 finite code-label inventory여야 한다는 점입니다.

감사합니다.
```

## 더 짧은 운영자용 템플릿

```text
Gov24 current API `supportConditions` 전체 code-label inventory가 필요합니다.

최소:
- code
- official label

있으면 좋음:
- fact group/분류
- source section
- active 여부

sample payload나 대표 code 일부가 아니라
current API 기준 full inventory 자료가 필요합니다.
```

## sufficient example

아래처럼 current API 기준 code와 label이 직접 대응되고,
가능하면 분류까지 보이면 충분합니다.

| code | official_label | fact_group | active |
|---|---|---|---|
| `JA0203` | `중위소득 76~100%` | `INCOME` | `Y` |
| `JA0327` | `구직자/실업자` | `EMPLOYMENT` | `Y` |
| `JA0412` | `다자녀가구` | `HOUSEHOLD` | `Y` |

또는 아래처럼 schema export도 충분합니다.

```json
{
  "operation": "supportConditions",
  "enum": [
    {"code": "JA0203", "label": "중위소득 76~100%"},
    {"code": "JA0327", "label": "구직자/실업자"}
  ]
}
```

## insufficient example

아래는 응답으로 와도 full inventory reopen 근거로는 부족합니다.

- 현재 seed된 대표 code 몇 개만 보이는 표
- sample payload 누적 관찰 목록
- fact group 추론 메모
- label만 있고 code가 없는 문서
- code만 있고 official label이 없는 문서
- deprecated source 자료

## 요청 후 판정 기준

### reopen 가능

아래가 충족되면 `GOV24_SUPPORT_CONDITION` full inventory/backfill 초안을 다시 연다.

- `code + official label` 직접 대응 확인
- current API 기준 자료임이 분명함
- representative subset이 아니라 full inventory로 취급 가능함

### reopen 보류

아래 중 하나면 full inventory/backfill은 계속 보류한다.

- representative subset만 옴
- sample payload 관찰 목록만 옴
- label-only 또는 code-only 자료만 옴
- current API 기준인지 불분명함

## 실무 결론

현재 practical next action은 아래처럼 정리됩니다.

1. label 3종은 [policy-normalization-gov24-codebook-request-template.md](./policy-normalization-gov24-codebook-request-template.md) 로 요청
2. `supportConditions` full inventory는 이 문서의 별도 템플릿으로 요청
3. 둘을 하나의 “Gov24 current codebook package” 로 묶어 보내더라도,
   sufficient/insufficient 판정은 문서 단위로 따로 한다

## 요약

1. `GOV24_SUPPORT_CONDITION` 은 label 3종과 성격이 달라 별도 요청 템플릿이 맞다.
2. representative subset 근거가 있다는 사실은 full inventory reopen 근거가 아니다.
3. full inventory reopen의 최소 단위는 `code + official label` 이다.
4. practical next step은 공통 요청 묶음이 아니라, 별도 판정 가능한 요청 템플릿 실행이다.
