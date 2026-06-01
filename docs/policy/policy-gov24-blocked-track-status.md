# `Gov24` blocked track 현재 상태

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
- [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-normalization-blocked-sql-reopen-priority.md](../history/policy/policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](../history/policy/policy-normalization-gov24-request-package-checklist.md)
- [policy-normalization-gov24-codebook-request-template.md](../history/policy/policy-normalization-gov24-codebook-request-template.md)
- [policy-normalization-gov24-support-condition-request-template.md](../history/policy/policy-normalization-gov24-support-condition-request-template.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)

## 목적

현재 `Gov24` 관련 pending을

- 왜 지금 active 구현 트랙이 아닌지
- 다시 열 조건이 무엇인지
- 자료가 오면 어떤 순서로 reopen 하는지

한 문서에서 바로 보게 정리합니다.

## 현재 결론

현재 `Gov24` 는

- **runtime collect/source 트랙은 active**
- **label-first canonical promotion 트랙도 active**
- **외부 공식 stable-code import 트랙만 blocked 또는 deferred**

상태입니다.

즉 현재 로컬에서 이미 진행 가능한 축은

1. `serviceList -> detail -> supportConditions` runtime collect 확장
2. raw/detail/fact 적재 및 품질 점검
3. collect runtime 안정화

이고, 2026-06-01 작업으로 추가로 진행한 축은

1. `GOV24_SUPPORT_CONDITION` business/industry/startup/no-op full-scope runtime fact 승격
2. `Gov24` taxonomy -> recommendation priority bucket bridge
3. `Gov24 -> YOUTH_MID` bridge
4. `GOV24_SERVICE_FIELD / USER_TYPE_TOKEN / BENEFIT_TYPE_TOKEN` internal stable code seed/backfill

입니다.

여전히 source-of-truth 결정이 필요한 축은

1. 외부 공식 stable codebook 기반 재수입 여부

입니다.

현재 `Gov24` practical next action 은 세 갈래입니다.

- runtime 쪽: backlog closeout 기준을 유지하고 샘플 품질을 점검
- support fact gap 쪽: full-scope runtime fact 승격 완료
- normalization 쪽: 내부 stable code/backfill 상태를 유지하고, 외부 공식 codebook 수신 여부만 별도 판정

## 지금 blocked 인 이유

### 1. runtime collect와 normalization reopen은 다른 문제다

현재 local DB 기준 `Gov24` runtime collect는 이미 active 입니다.

- `serviceList=10945`
- `detail=10945`
- `support raw=10945`
- `support facts=194622`
- `support fact service coverage=10945 / 10945`

따라서 `Gov24` 가 source row 자체가 없는 상태는 아닙니다.

이전 summary slot / import-backfill 진단에서 보이던 `GOV24_* = 0` 상태는
2026-06-01 internal code seed/backfill 이후 더 이상 closeout 기준이 아닙니다.
이제 남은 blocked 의미는 **외부 공식 codebook을 source-of-truth로 다시 받을지 여부**입니다.

### 2. `supportConditions` 는 full-scope runtime fact gap을 해소했다

runtime fact extractor가 읽는 축:

- 성별: `JA0101`, `JA0102`
- 연령: `JA0110`, `JA0111`
- 소득: `JA0201~JA0205`
- 교육: `JA0317~JA0320`
- 고용: `JA0326`, `JA0327`
- 가구: `JA0401~JA0404`, `JA0411~JA0414`
- 특수대상: `JA0328~JA0330`
- 산업 종사자: `JA0313~JA0316`
- 창업/사업 단계: `JA1101~JA1103`
- 업종: `JA1201`, `JA1202`, `JA1299`, `JA2201~JA2203`, `JA2299`
- 사업체 유형: `JA2101~JA2103`

실제 local DB 기준 `service_facts.fact_code_set_key='GOV24_SUPPORT_CONDITION'` 로 적재된 서비스는
`10945 / 10945` 입니다.

`JA0322`, `JA0410` 같은 `해당사항없음` 계열 code는 `NO_OP` fact로 보존합니다.

### 3. label 3종은 더 이상 “공개 codebook 대기” 상태로 보지 않는다

`서비스분야 / 사용자구분 / 지원유형` 3축은 current API 기준

- field name 은 확인됐고
- observed raw inventory 도 local DB 에 존재하며
- 공개 공식 Swagger 기준으로도 이 값들은 enum/codebook 이 아니라 `string` 필드다

즉 현재 active 과제는 “외부 codebook이 오기 전까지 멈춤”이 아니라,
**공식 codebook 부재를 전제로 raw exact label + allowlist token split 규칙을 내부 canonical 층에 어떻게 고정할지 정하는 것**이다.

반대로 계속 blocked 로 남는 것은

- 외부 공식 stable codebook 기반 재수입 여부

같은 더 강한 구조화 단계다.

## reopen 조건

아래 중 하나가 오면 `Gov24` stable-code blocked SQL 을 다시 active 로 올립니다.

- current API 기준 공식 codebook / enum / schema export 확보
- 운영자가 current API 기준 inventory를 전달
- 내부에서 current raw inventory 기준 canonical 매핑 규칙을 승인

그 전까지는 blocked/backlog 유지가 기본입니다.

## 자료가 오면 다시 여는 순서

우선순위는 아래입니다.

1. 외부 공식 stable codebook 기반 재수입 여부

이유:

- label 3종의 internal canonical code/backfill은 이미 닫힘
- `supportConditions` runtime fact gap은 이미 닫혔으므로,
  남은 것은 외부 source-of-truth가 실제로 생기는지 여부임

## 발송/수신 패키지 기준

발송은 one package로 묶고,
판정은 two tracks로 나눕니다.

### track A

- `serviceField`
- `userType`
- `benefitType`

판정 기준:

- field name 있음
- code 있음
- official label 있음
- current API 기준 자료임이 분명함

### track B

- `supportConditions` full-scope

판정 기준:

- code 있음
- official label 있음
- current runtime extractor가 읽는 code와 다른 외부 공식 codebook 변경이 제품적으로 의미가 분명함
- current API 기준 자료임이 분명함

## practical next action

현재 practical next action 은 아래 둘 중 하나입니다.

1. runtime collect + quality audit 기준선을 계속 유지
2. 외부 공식 stable codebook 이 생기면 internal code와 비교해 재수입 여부 판정

즉 label-first canonical 승격, internal stable code/backfill, `YOUTH_MID` bridge는 닫혔고,
외부 source-of-truth 재수입만 별도 blocked/backlog로 남습니다.

단, `Gov24` runtime collect 자체를 검토/구현할 때는
[policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
기준으로 `serviceList -> detail -> supportConditions` 범위를 분리해서 진행합니다.

runtime collect가 이미 붙은 뒤 coverage/shape/null-heavy sample을 다시 볼 때는
[policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)
기준으로 closeout, raw shape, missing fact sample 순서를 고정합니다.

현재 local runtime 기준 practical status:

- `serviceList` 는 `10945` 건까지 적재됨
- `detail` raw/detail row 는 `10945` 건까지 적재됨
- `supportConditions` raw 는 `10945` 건까지 적재됨
- `supportConditions` fact 는 `194622` row, 서비스 기준 coverage는 `10945 / 10945`
- `.env` / `docker-compose` 는 `PUBLIC_DATA_PORTAL_API_KEY` 기준으로 통합됨
- `detail/support` fetch에는 lightweight retry가 적용됨
- `support raw` 는 모두 `{"서비스ID","서비스명","conditions":{...}}` shape로 통일됨
- raw는 있지만 fact가 없는 나머지 `0`건이다.
  - 현재 local audit 기준 `missing_no_support_raw=0`, `missing_all_null_payload=0`
  - `missing_unmapped_only_payload=0`, `missing_mapped_signal_payload=0`
- `JA210*`, `JA220*`, `JA120*`, `JA1299/JA2299`, `JA110*` 중심 이전 unmapped inventory는
  [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
  에 따로 정리했고, 현재 runtime fact scope로 승격 완료했다

즉 current blocked 의미는 한 층으로 줄었습니다.

1. 외부 공식 codebook 기반 hard import/backfill 트랙은 여전히 stable code/schema 기준으로 blocked
2. `GOV24_SUPPORT_CONDITION` full-scope runtime fact gap은 닫혔다

## 요약

1. `Gov24` runtime collect 트랙은 현재 active 다.
2. `GOV24_SUPPORT_CONDITION` 은 business/industry/startup/no-op 축까지 runtime fact로 승격 완료했다.
3. `서비스분야 / 사용자구분 / 지원유형` label-first canonical 승격과 internal stable code backfill은 완료했다.
4. `GOV24_SERVICE_FIELD` summary/legacy code와 `Gov24` 3축 taxonomy term code는 현재 local DB에 backfill 되어 있다.
5. `supportConditions` gap의 중심이던 사업체/업종/창업 상태 code는 현재 제품 경계 기준으로 active 처리했다.
6. 외부 공식 stable codebook 을 다시 열 조건은 current API 기준 codebook/schema 확보, 또는 제품이 더 강한 구조화를 실제로 요구하기 시작하는 것이다.
7. 현재 남은 큰 deferred 범위는 외부 공식 codebook 재수입 여부다.
