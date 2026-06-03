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

2026-06-02 current server Docker runtime Gov24 acceptance 기준:

- closeout coverage: `gov24_total_services=10954`, `gov24_detail_rows=10954`, `gov24_support_raw=10954`
- support fact coverage: `gov24_support_fact_rows=194819`, `gov24_support_fact_services=10954`, `gov24_support_missing_fact_services=0`
- support raw shape: `nested=10954`, `flat=0`
- region backfill `10954`건: latest suite step `7839ms`, endpoint metric `4708ms`
- region coverage: `9814 / 10954 = 89.59%`
- region rows: `33010`
- collect embedding boundary: `requested=1`, `saved=1`, `failed=0`, warning count `1`, error count `0`
- sidecar missing-list refill: `before_missing=10954 -> after_missing=0`, suite step `15377ms`
- filter axis audit: Gov24 3축 filter count를 DB truth와 API `totalElements` 로 비교
- 기본 acceptance suite: `passed`, `8` steps, `suite_duration_ms=53202`
- recommendation surface/score audit: `latest_batch_user_count=5`,
  `latest_batch_user_cohort_distribution=GOV24_EDUCATION_SIGNAL:2,GOV24_HOUSING_SIGNAL:2,GOV24_SURFACE_AUDIT:1`,
  `top1/top2/top10_gov24_share_pct=100.00`, `top10_distinct_sources=GOV24:50`
- signal 포함 acceptance suite: `passed`, `10` steps, `suite_duration_ms=71712`
  - housing signal: `fresh_batch_rows=44`, `gov24_rows=44`, `gov24_top10_rows=10`, `gov24_top2_rows=2`
  - education signal: `fresh_batch_rows=32`, `gov24_rows=32`, `gov24_top10_rows=10`, `gov24_top2_rows=2`
- latest artifact: `tmp/gov24-acceptance-suite/latest`

2026-06-03 regionless follow-up 해석:

- 지방 계열 기관명에서 전국 유일 시군구명/어간을 먼저 추론해 `20`개 서비스를 추가 복구한 뒤,
  `소관기관코드 -> 로컬정부 기관코드 lookup` 을 더해 `2`건을 추가 복구했습니다.
- regionless service는 `1160 -> 1140 -> 1138` 로 줄었습니다.
- 직접 분류한 true local-agency 잔여는 `3 -> 1` 로 줄었습니다.
- 새로 복구된 케이스:
  - `재단법인고성교육재단` → `소관기관코드=5420000` → `경상남도 고성군(48820)`
  - `(재)한국전통문화전당` → `소관기관코드=4641000` → `전주시 하위 구(52111, 52113)`
- 남은 true local-agency 잔여 `1`건은 `온라인 신청 시연 테스트(운영)` 하나이고,
  테스트성 행정안전부 row라 자동 추론 대상에서 계속 제외합니다.

2026-06-03 local runtime 적용 순서:

1. 앱 재기동 또는 재배포로 `gov24-local-agency-region-lookup.tsv` classpath 반영
2. `bash deploy/smoke/run-local-gov24-region-backfill-smoke.sh`
3. `bash deploy/smoke/run-local-gov24-region-coverage-audit.sh`
4. 필요하면 `bash deploy/smoke/run-local-gov24-acceptance-suite.sh` 로 전체 Gov24 guard 재확인

## 2. 한 번에 보는 기본 명령

```bash
bash deploy/smoke/run-local-gov24-acceptance-suite.sh
```

이 wrapper는 현재 서버 Docker runtime에서 accepted 재측정을 반복하는 기본 순서입니다.

기본 포함 단계:

1. `run-local-gov24-region-backfill-smoke.sh`
2. `run-local-gov24-region-coverage-audit.sh`
3. `run-local-gov24-collect-embedding-boundary-smoke.sh`
4. `run-local-gov24-sidecar-backfill-smoke.sh`
5. `run-local-gov24-quality-audit.sh`
6. `run-local-gov24-support-conditions-validation.sh`
7. `run-local-gov24-taxonomy-validation.sh`
8. `run-local-gov24-filter-axis-audit.sh`
9. `run-local-gov24-recommend-surface-audit.sh`
10. `run-local-gov24-recommend-score-audit.sh`

3축 canonical seed/code map drift만 빠르게 확인하려면 아래 wrapper를 따로 돌린다.

```bash
bash deploy/smoke/run-local-gov24-taxonomy-validation.sh
```

이 wrapper는 아래를 순서대로 수행한다.

1. live `serviceList` 기준 `서비스분야 / 사용자구분 / 지원유형` inventory 재수집
2. live inventory 와 `Gov24TaxonomyCodeSupport.java` code map 비교
3. live inventory 와 `V2026_06_01_01__seed_gov24_taxonomy_codes.sql` seed 비교
4. latest summary/json/note artifact publish

artifact:

- `tmp/gov24-taxonomy-validation/latest-gov24-taxonomy-validation-summary.txt`
- `tmp/gov24-taxonomy-validation/latest-gov24-taxonomy-validation-summary.json`
- `tmp/gov24-taxonomy-validation/latest-gov24-taxonomy-validation-note.md`

`supportConditions` 공식 Swagger inventory와 mapper/quality-audit 목록 drift를 빠르게 보려면 아래 wrapper를 쓴다.

```bash
bash deploy/smoke/run-local-gov24-support-conditions-validation.sh
```

이 wrapper는 아래를 수행한다.

1. hidden Swagger `supportConditions_model` 에서 official `JA*` field + description 재조회
2. `WelfareServiceMapper` 의 support condition definition 비교
3. `run-local-gov24-quality-audit.sh` 의 mapped code 목록 비교
4. latest summary/json/note artifact publish

artifact:

- `tmp/gov24-support-conditions-validation/latest-gov24-support-conditions-validation-summary.txt`
- `tmp/gov24-support-conditions-validation/latest-gov24-support-conditions-validation-summary.json`
- `tmp/gov24-support-conditions-validation/latest-gov24-support-conditions-validation-note.md`

교육/주거 signal smoke까지 포함하려면 아래처럼 켭니다.

```bash
RUN_GOV24_SIGNAL_SMOKES=true bash deploy/smoke/run-local-gov24-acceptance-suite.sh
```

wrapper는 `tmp/gov24-acceptance-suite/latest*` 에 summary/json을 남깁니다.

## 2-1. Gov24 runtime collect 기본 운영 경로

`Gov24` list collect는 이제 chunked runtime collect + async trigger/status를 기본 운영 경계로 읽습니다.

기본값:

- `chunkSize=500`
- `chunkPauseMs=100`
- stale cleanup: full run 성공 후 마지막에만 `1회`

운영 기본 명령:

```bash
bash deploy/smoke/run-local-gov24-async-collect-smoke.sh
```

이 smoke는 아래를 한 번에 확인합니다.

1. `POST /api/admin/collect/gov24/async` 가 `202 Accepted` 로 빠르게 반환되는지
2. `GET /api/admin/collect/gov24/async-status` 가 `QUEUED -> RUNNING -> SUCCEEDED` 로 전이하는지
3. latest `api_sync_logs` 의 `metadata_json` 에
   - `chunkSize`
   - `chunkPauseMs`
   - `chunkCount`
   - `elapsedMs`
   - `totalCount`
   - `staleDeletedCount`
   가 실제로 남는지

최근 local 기준:

- trigger wall-clock: `18ms`
- full async run latest DB elapsed: 약 `89.5s`
- `chunkCount=22`
- `requested=saved=10954`, `failed=0`

주의: `run-local-gov24-quality-audit.sh` 는 현재 관측값을 출력하는 audit입니다.
현재 서버 Docker DB가 LIST 중심으로만 적재된 상태면 `support_raw=0` 같은 낮은 값도 출력될 수 있습니다.
detail/support closeout 판정에서는 `gov24_total_services/detail_rows/support_raw` 를 같은 규모로 맞춘 뒤
support fact gap 분해를 읽습니다.

## 3. 품질 audit 단독 명령

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

## 4. 결과 해석 순서

### A. 먼저 backlog closeout 확인

아래가 모두 같으면 runtime collect 자체는 닫힌 상태입니다.

- `gov24_total_services`
- `gov24_detail_rows`
- `gov24_support_raw`

### B. 그 다음 support raw shape 확인

기대값:

- `gov24_support_nested_shape = 10954`
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

## 5. detail/support 재수집 명령

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

## 6. 샘플 감사 기준

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

## 7. 언제 여기서 멈춰야 하나

- `detail/support raw` 가 이미 `10937` 까지 닫혀 있는데도 계속 같은 재수집을 반복하려는 경우
- `support fact coverage` 차이를 전부 코드 버그라고 단정하려는 경우
- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` hard import/backfill을 이 문서 단계에서 같이 열려는 경우

이 문서의 범위는 runtime audit 입니다.
hard taxonomy/import-backfill 은 여전히 blocked track 입니다.

## 8. 요약

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
