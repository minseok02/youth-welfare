# `Gov24` runtime audit runbook

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-implementation-checklist.md](./policy-gov24-implementation-checklist.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [collect-operation-checklist.md](../collect/collect-operation-checklist.md)

## 목적

이 문서는 `Gov24` runtime collect가 이미 붙은 뒤,

- closeout 상태를 다시 확인할 때
- `detail/support` coverage를 재검증할 때
- `support raw는 있는데 fact가 없는` 케이스를 다시 볼 때

따라가는 짧은 runbook 입니다.

긴 설계 배경은 `blocked-track-status`, 구현 범위 통제는 `implementation-checklist` 를 보고,
실제 운영 점검은 이 문서와 스크립트만 따라가면 됩니다.

## 1. 지금 기준선

2026-06-01 local baseline:

- `serviceList=10945`
- `detail row=10945`
- `support raw=10945`
- `support fact rows=194622`
- `support fact service coverage=10945 / 10945`
- `missing_no_support_raw=0`
- `missing_all_null_payload=0`
- `missing_unmapped_only_payload=0`
- `missing_mapped_signal_payload=0`

해석:

- `list/detail/support raw` 가 모두 `10945` 이면 runtime collect backlog는 닫힌 상태입니다.
- `support fact service coverage` 가 `10945` 과 다르더라도, 남은 차이가 곧바로 버그는 아닙니다.
- 남은 gap이 다시 생기면 한 덩어리로 보지 않고 아래 둘로 나눠 해석합니다.
  - `conditions` 값이 사실상 비어 있는 payload
  - official `supportConditions` 값은 있지만 현재 fact extractor가 아직 읽지 않는 code-only payload
- 현재 local audit 기준으로는 남은 gap이 없습니다.

## 2. 한 번에 보는 기본 명령

```bash
bash deploy/smoke/run-local-gov24-quality-audit.sh
```

스크립트가 출력하는 핵심:

- app health 확인
- `Gov24 total/detail/support/fact` coverage
- `support raw` nested/flat shape
- missing fact gap 분해(`no raw / all-null / unmapped-only / mapped-signal`)
- detail fill rate
- `support raw는 있지만 fact가 없는` 샘플 5건과 `effective_signal_count / mapped_signal_count`

기록할 때는 raw 숫자만 적지 말고 최소 아래를 같이 남깁니다.

- wrapper 실행 시각
- `gov24_total_services / gov24_detail_rows / gov24_support_raw`
- `gov24_support_fact_services / gov24_support_missing_fact_services`
- `gov24_missing_no_support_raw / all_null / unmapped_only / mapped_signal`
- nested/flat shape

## 3. 결과 해석 순서

### A. 먼저 backlog closeout 확인

아래가 모두 같으면 runtime collect 자체는 닫힌 상태입니다.

- `gov24_total_services`
- `gov24_detail_rows`
- `gov24_support_raw`

### B. 그 다음 support raw shape 확인

기대값:

- `gov24_support_nested_shape = 10945`
- `gov24_support_flat_shape = 0`

`flat_shape > 0` 이면 raw shape drift가 다시 생긴 것입니다.

### C. 마지막으로 support fact coverage와 gap 분해를 본다

아래를 같이 봅니다.

- `gov24_support_fact_services`
- `gov24_support_missing_fact_services`
- `gov24_missing_no_support_raw`
- `gov24_missing_all_null_payload`
- `gov24_missing_unmapped_only_payload`
- `gov24_missing_mapped_signal_payload`
- missing sample의 `effective_signal_count / mapped_signal_count`

해석:

- `effective_signal_count = 0` 이면 대체로 all-null payload입니다.
- `effective_signal_count > 0` 이고 `mapped_signal_count = 0` 이면, 현재 extractor가 아직 fact로 승격하지 않는 official code-only payload일 가능성이 큽니다.
- `mapped_signal_count > 0` 인데 fact가 없으면 extractor/fact write anomaly로 봅니다.

## 4. detail/support 재수집 명령

### chunk 재수집

```bash
curl -sS -X POST "http://127.0.0.1:8082/api/admin/collect/gov24-details?maxCallsPerRun=1000"
curl -sS -X POST "http://127.0.0.1:8082/api/admin/collect/gov24-support-conditions?maxCallsPerRun=1000"
```

### 단건 재시도

```bash
curl -sS -X POST "http://127.0.0.1:8082/api/admin/collect/gov24-details?sourceId=<서비스ID>"
curl -sS -X POST "http://127.0.0.1:8082/api/admin/collect/gov24-support-conditions?sourceId=<서비스ID>"
```

사용:

- transient upstream 실패 재현
- missing sample 단건 점검

기록:

- 어떤 lane(`detail` / `support`)을 돌렸는지
- `maxCallsPerRun` 또는 `sourceId`
- 최신 `api_sync_logs` 의 `requested/saved/skipped/failed`

## 5. 샘플 감사 기준

대표 샘플을 볼 때는 아래 순서로 봅니다.

1. `welfare_services` row 존재
2. `welfare_service_details` row 존재
3. `raw_api_payloads SUPPORT` 존재
4. `service_facts fact_code_set_key='GOV24_SUPPORT_CONDITION'` 존재 여부
5. 없으면 `payload_json.conditions` 에 실제 signal 값이 있는지 확인

원칙:

- `support raw` 가 있고 `effective_signal_count = 0` 이면 missing fact는 허용 범주입니다.
- `effective_signal_count > 0` 이고 `mapped_signal_count = 0` 이면 지금 단계에서는 `unmapped official condition payload` 로 분리합니다.
- `mapped_signal_count > 0` 인데 fact가 없으면 anomaly로 봅니다.

## 6. 언제 여기서 멈춰야 하나

- `detail/support raw` 가 이미 `10937` 까지 닫혀 있는데도 계속 같은 재수집을 반복하려는 경우
- `support fact coverage` 차이를 전부 코드 버그라고 단정하려는 경우
- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` hard import/backfill을 이 문서 단계에서 같이 열려는 경우

이 문서의 범위는 runtime audit 입니다.
hard taxonomy/import-backfill 은 여전히 blocked track 입니다.

## 7. 요약

1. 먼저 `list/detail/support raw` closeout부터 확인합니다.
2. 그 다음 `support raw shape` 가 nested only인지 봅니다.
3. 마지막으로 `missing fact` 를 `all-null / unmapped-only / anomaly` 로 나눠 봅니다.
4. all-null payload와 unmapped official code payload는 수집 실패와 분리해서 해석합니다.
5. hard taxonomy/import-backfill 은 이 runbook 범위가 아닙니다.

## 실행 후 남길 최소 기록

- wrapper 또는 collect command
- closeout coverage 3종(`list/detail/support raw`)
- fact coverage / missing service count
- nested/flat shape
- missing fact 분해 4종
- 샘플 sourceId 1~5건과 `effective_signal_count / mapped_signal_count`
- follow-up이 blocked 인지 deferred 인지
