# Collect Detail Execution Contract

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

nightly `collect/all` 에서 list source, list diff forced detail, 요일별 detail rotation이 어떤 순서와 호출량 경계로 실행되는지 고정합니다.

이 문서의 계약은 아래 검증으로 같이 고정합니다.

- `bash deploy/smoke/verify-collect-detail-execution-contract.sh`
- `CollectDetailExecutionContractTest`
- `CollectBatchServiceTest.collectAllRunsForcedDetailsAfterAllListSourcesAndBeforeRotation`

## COLLECT_DETAIL_PHASE_LIST_SNAPSHOT

`CollectBatchService.collectAllNow()` 의 첫 phase는 list snapshot입니다.

고정 순서:

1. `YOUTH`
2. `BOKJIRO_CENTRAL`
3. `BOKJIRO_LOCAL`
4. `GOV24`

이 순서는 `CollectSource.executionOrder()` 가 반환하는 scheduled source 순서입니다.
`GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS`, `BOKJIRO_DETAIL`, `BOKJIRO_DETAIL_REFRESH` 는 detail/maintenance lane이므로 이 첫 phase에 직접 들어가지 않습니다.

list source가 실패해도 다음 source는 계속 실행합니다.
list source가 partial success, 즉 `failedCount > 0` 이면 그 source의 list diff snapshot은 저장하지 않습니다.

## COLLECT_DETAIL_PHASE_FORCED_DETAIL

모든 list source 실행이 끝난 뒤에만 forced detail phase를 실행합니다.

forced detail은 list 수집 직후 바로 끼어들지 않고, list source 전체가 끝난 뒤 `forcedDetailPlans` 를 순서대로 처리합니다.
이 구조는 한 source의 list diff가 다른 source의 list 기준선 생성을 막지 않게 하려는 것입니다.

forced detail mapping:

| list source | forced detail lane | 호출 단위 |
| --- | --- | --- |
| `YOUTH` | `YOUTH_DETAILS` result는 `YOUTH` 로 집계 | `collectYouthDetailsForSourceId(sourceId)` |
| `BOKJIRO_CENTRAL` | `BOKJIRO_DETAIL_REFRESH` | `collectBokjiroDetailsRefreshForSourceId(sourceId)` |
| `BOKJIRO_LOCAL` | `BOKJIRO_DETAIL_REFRESH` | `collectBokjiroDetailsRefreshForSourceId(sourceId)` |
| `GOV24` | `GOV24_DETAIL` then `GOV24_SUPPORT_CONDITIONS` | `collectGov24DetailsForSourceId(sourceId)`, `collectGov24SupportConditionsForSourceId(sourceId)` |

forced detail 후보는 `CollectListChangePolicy` 가 만든 `detailCandidateSourceIds` 만 사용합니다.
기본 후보 상한은 `collect.list.diff.force-detail.max-candidates-per-run=50` 입니다.

forced detail이 실행되는 조건:

- 첫 snapshot이 아님
- list source가 success
- `failedCount = 0`
- count drop guard가 아님
- 신규/변경 임계치를 넘음

기본 임계치:

- 신규 `30건` 이상 또는 현재 total의 `2%` 이상
- fingerprint 변경 `100건` 이상 또는 현재 total의 `5%` 이상
- missing 급감 guard: 이전 total 대비 `15%` 이상 missing이고 missing이 신규/변경보다 크면 forced detail 금지

## COLLECT_DETAIL_PHASE_ROTATION_DETAIL

forced detail phase가 모두 끝난 뒤 마지막에 요일별 rotation detail을 실행합니다.

rotation은 `collect.list.rotation.enabled=true` 일 때만 실행합니다.

기본 rotation schedule:

| day in `Asia/Seoul` | rotation lane | 기본 호출량 |
| --- | --- | --- |
| Monday | `BOKJIRO_DETAIL` | `collect.list.rotation.bokjiro-detail-max-calls-per-run=100` |
| Tuesday | `GOV24_DETAIL` | `collect.list.rotation.gov24-detail-max-calls-per-run=50` |
| Wednesday | `GOV24_SUPPORT_CONDITIONS` | `collect.list.rotation.gov24-support-conditions-max-calls-per-run=50` |
| Thursday | `BOKJIRO_DETAIL_REFRESH` | `collect.list.rotation.bokjiro-refresh-max-calls-per-run=50` |
| Friday | `YOUTH_DETAILS` result는 `YOUTH` 로 집계 | `collect.youth.detail.max-calls-per-run=50` |
| Saturday/Sunday | none | `0` |

rotation detail은 backlog/refresh 보강용입니다.
forced detail은 list diff 변화량 대응용입니다.
따라서 두 phase는 목적이 다르고, 같은 run에서 같은 detail lane이 두 번 결과에 나타날 수 있습니다.

## COLLECT_DETAIL_DUPLICATE_LANE_ALLOWED

같은 detail lane이 한 run 결과에 두 번 보이는 것은 허용된 상태입니다.

예:

- Tuesday에 `GOV24` list diff가 forced detail 조건을 넘음
- forced phase에서 `GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS` 후보 sourceId를 먼저 처리
- rotation phase에서 Tuesday 기본 rotation인 `GOV24_DETAIL` 을 `50` calls budget으로 한 번 더 처리

이 경우 결과 source list에는 `GOV24_DETAIL` 이 두 번 나타날 수 있습니다.
첫 번째는 forced candidate 처리 결과이고, 두 번째는 rotation backlog 처리 결과입니다.

운영자는 이것을 중복 버그로 보지 않고 아래 값을 같이 봅니다.

- `api_sync_logs.requested_count`
- `api_sync_logs.saved_count`
- `api_sync_logs.skipped_count`
- `api_sync_logs.failed_count`
- lane별 `metadata_json`
- admin collect lane inventory의 effective config entries

## COLLECT_DETAIL_CALL_BUDGETS

scheduled `collect/all` 의 외부 detail call 상한은 forced candidate cap과 rotation budget을 합쳐서 읽습니다.

기본값 기준 최대치:

| source family | forced detail 기본 상한 | rotation 기본 상한 |
| --- | --- | --- |
| `YOUTH` | 최대 `50` sourceId | Friday `50` calls |
| `BOKJIRO_CENTRAL` | 최대 `50` sourceId refresh | Monday detail `100` calls 또는 Thursday refresh `50` calls |
| `BOKJIRO_LOCAL` | 최대 `50` sourceId refresh | Monday detail `100` calls 또는 Thursday refresh `50` calls |
| `GOV24` | 최대 `50` sourceId detail + 최대 `50` sourceId support conditions | Tuesday detail `50` calls 또는 Wednesday support conditions `50` calls |

주의:

- forced detail cap은 list source별 후보 cap입니다.
- rotation budget은 요일별로 하나만 실행됩니다.
- manual `POST /api/admin/collect/{sourceKey}` 는 이 scheduled call budget과 별도입니다.
- detail service 내부의 기존 raw/detail 존재 확인, skip, maxCallsPerRun, retry/rate-limit guard가 최종 외부 호출량을 다시 제한합니다.

## 운영 확인 순서

1. `bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
2. `tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt`
3. admin collect lane inventory의 scheduled/rotation/manual lane count
4. `api_sync_logs` 에서 같은 run 주변의 `requested/saved/skipped/failed` 비율
5. 같은 lane이 두 번 보이면 forced candidate와 rotation budget을 구분
