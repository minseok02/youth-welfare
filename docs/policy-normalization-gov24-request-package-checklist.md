# `Gov24` current codebook request package 체크리스트

관련 문서:

- [policy-normalization-gov24-codebook-request-template.md](./policy-normalization-gov24-codebook-request-template.md)
- [policy-normalization-gov24-support-condition-request-template.md](./policy-normalization-gov24-support-condition-request-template.md)
- [policy-normalization-gov24-schema-acquisition-path.md](./policy-normalization-gov24-schema-acquisition-path.md)
- [policy-normalization-gov24-swagger-visibility-check.md](./policy-normalization-gov24-swagger-visibility-check.md)
- [phase-plan.md](./phase-plan.md)

## 목적

`GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE`,
`GOV24_SUPPORT_CONDITION` 요청을
실제로 한 번에 보낼 때의 발송 단위와 수신 후 판정 체크리스트를 고정합니다.

핵심은 “한 패키지로 보내되, 판정은 두 축으로 나눈다” 입니다.

## 패키지 구성 원칙

발송은 한 번에 묶을 수 있습니다.

### 묶는 이유

- 수신자 입장에서는 same dataset/current API 기준 요청이라는 공통점이 있음
- `data.go.kr` current dataset page와 current API 기준이라는 전제가 동일함
- provider/operator 에 여러 메일로 쪼개 보내는 것보다 한 번에 정리하는 편이 운영상 단순함

### 그래도 판정은 분리하는 이유

아래 둘은 reopen 기준이 다릅니다.

1. label 3종
   - `serviceField`
   - `userType`
   - `benefitType`
2. `supportConditions`

label 3종은 `field name + code + official label` 이 핵심이고,
`supportConditions` 는 representative subset 과 full inventory reopen을 분리해 봐야 합니다.

따라서 **발송은 one package, 판정은 two tracks** 로 둡니다.

## 발송 패키지 순서

### 1. 패키지 cover message

한 문단으로 아래를 먼저 적습니다.

- current dataset page는 이미 확인함
- page/metadata만으로 finite inventory는 안 보였음
- 그래서 current API 기준 공식 codebook/schema export가 필요함

### 2. label 3종 요청 블록

다음 문서의 long template를 붙입니다.

- [policy-normalization-gov24-codebook-request-template.md](./policy-normalization-gov24-codebook-request-template.md)

대상:

- `serviceField`
- `userType`
- `benefitType`

### 3. `supportConditions` 요청 블록

다음 문서의 long template를 붙입니다.

- [policy-normalization-gov24-support-condition-request-template.md](./policy-normalization-gov24-support-condition-request-template.md)

대상:

- `supportConditions`

### 4. sufficient/insufficient 판단 기준 명시

마지막에 짧게 아래를 적습니다.

- screenshot/sample payload만으로는 reopen 근거가 되지 않음
- current API 기준 `code + official label` 직접 대응이 필요함

## 발송 전 체크리스트

보내기 전에 아래만 확인합니다.

- current dataset page URL를 명시했는가
- deprecated `category` / `category-code` 자료는 제외한다고 썼는가
- label 3종과 `supportConditions` 를 같은 의미로 섞지 않았는가
- “sample payload는 insufficient” 를 분명히 적었는가
- 필요한 최소 컬럼을 적었는가

## 수신 후 1차 판정 체크리스트

### A. label 3종 트랙

아래를 확인합니다.

- `field name` 이 있는가
- `code` 가 있는가
- `official label` 이 있는가
- current API 기준 자료임이 분명한가

충족 시:

- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`

SQL reopen 가능

### B. `supportConditions` 트랙

아래를 확인합니다.

- `code` 가 있는가
- `official label` 이 있는가
- representative subset이 아니라 full inventory로 볼 수 있는가
- current API 기준 자료임이 분명한가

충족 시:

- `GOV24_SUPPORT_CONDITION` full inventory/backfill reopen 가능

## 수신 후 2차 판정 예시

### case 1. label 3종만 충분

- `serviceField/userType/benefitType` codebook은 충분
- `supportConditions` 는 representative subset 수준만 옴

이 경우:

- label 3종 SQL만 reopen
- `supportConditions` full inventory는 계속 보류

### case 2. `supportConditions` 만 충분

- 드물지만 가능
- label 3종은 screenshot/sample만 옴
- `supportConditions` 전체 codebook은 충분

이 경우:

- `supportConditions` full inventory만 reopen
- label 3종은 계속 보류

### case 3. 둘 다 충분

- 두 트랙 모두 current API 기준 codebook 확보

이 경우:

- blocked SQL reopen 우선순위 문서 순서대로
  label 3종 -> `supportConditions` full inventory 순으로 다시 연다

## 패키지에 일부러 안 넣는 것

이번 패키지에는 아래를 포함하지 않습니다.

- `YOUTH_MID` codebook 요청
- deprecated endpoint 문의
- 내부 bridge/compat 매핑표
- SQL 초안 자체

## 실무 결론

이 문서 기준 practical next step은 아래처럼 정리됩니다.

1. 발송은 one package로 묶는다
2. 판정은 label 3종 / `supportConditions` 두 트랙으로 나눈다
3. 자료가 오면 트랙별 sufficient/insufficient 부터 판정한다
4. sufficient 한 축만 먼저 reopen해도 된다

## 요약

1. `Gov24` current codebook 요청은 한 패키지로 보낼 수 있다.
2. 하지만 reopen 판정은 label 3종과 `supportConditions` 를 따로 내려야 한다.
3. sample payload나 screenshot은 어느 트랙에서도 충분조건이 아니다.
4. practical next action은 이제 실제 발송 여부 결정이다.
