# 수집 현재 동작 기준

[수집 목차](collect-docs-index.md)의 현재 코드 계약입니다. 실행·장애 판정은 [수집 운영](collect-ops.md), 호출 순서·예산의 상세 기준은 [detail 실행 계약](collect-detail-execution-contract.md)을 봅니다. 코드 기본값과 실제 서버 env override, 과거 로컬/서버 관측을 구분합니다.

## 현재 미해결 문제

2026-10-21 [로컬 Docker 기록](../phase-plan.md#2026-10-21-로컬-수집-실행-확인)에서는 목록 4개 source 저장은 성공했으나 snapshot 두 테이블 누락으로 list diff가 실패했고 Gov24 support는 48/50 저장·2건 실패였습니다. local fallback 설정으로 strict embedding도 미갱신입니다. HTTP 200·`completedWithFailures=false`·health UP으로 이를 해결 완료 처리하지 않습니다. [DB 진단](../core/db-migration.md#수집-snapshot-테이블-누락)과 별도 기능 작업이 필요합니다.

2026-10-23 코드에서는 항목 부분 실패와 list diff 오류를 아래 결과 계약에 반영하도록 보완했습니다. 이는 실패의 노출·집계 수정이며, 위 로컬 DB의 누락 테이블·지원조건 실패·embedding 미갱신을 실제 복구한 것은 아닙니다.

## 진입점과 저장

- 수동: `POST /api/admin/collect/all`, `/{sourceKey}`, `/{sourceKey}/async`; 상태는 `GET /{sourceKey}/async-status`입니다.
- 상세/보정: `/youth-details`, `/bokjiro-details-gap-fill`, `/inverted-age-backfill`, `/bokjiro-sidecars-backfill`, `/gov24-sidecars-backfill`을 목적별로 분리합니다.
- 정기: `CollectBatchService.collectAll()`, 매일 02:00 Asia/Seoul.
- Dispatch: CollectSource·CollectSourceAdapter·CollectSourceExecutionService → CollectBatchService/CollectAdminService. adapter뿐 아니라 enum·registry·source별 분기도 점검합니다.

저장은 정책 `welfare_services/service_regions/service_tags`, snapshot `raw_api_payloads`, sidecar `service_taxonomies/service_taxonomy_terms/service_taxonomy_summary_slots/service_facts`, 실행 `api_sync_logs`로 나뉩니다. [RawApiPayloadService](../../backend/src/main/java/com/example/welfare/collect/service/RawApiPayloadService.java)는 DTO/객체를 JSON 재직렬화해 sourceType/sourceId/apiCategory로 upsert합니다. 모든 HTTP 원문·이전 버전 보존은 보장하지 않습니다. sidecar 존재·실제 적재와 replay precondition을 따로 확인하며 draft MySQL SQL 자동 적용으로 해결하지 않습니다.

## 전체 수집 결과 계약

`POST /api/admin/collect/all`은 실행 결과를 반환하면 기존 HTTP `200`과 응답 envelope의 `success=true`를 유지합니다. 이것은 모든 수집의 성공을 뜻하지 않습니다. `data.status`와 실행별 결과를 확인합니다.

| `data.status` | 판정 범위 |
| --- | --- |
| `SUCCESS` | 반환된 source 실행과 수행한 list diff에서 실패가 보고되지 않음 |
| `PARTIAL_SUCCESS` | 성공 또는 일부 처리된 실행이 있으나 source의 항목/예외 실패 또는 list diff 오류도 있음 |
| `FAILED` | 성공 또는 일부 처리된 실행 없이 실패만 보고됨 |
| `SKIPPED` | 수행한 source 실행·성공한 비교·실패가 없고, 결과가 비어 있거나 비교가 생략됨 |

- `completedWithFailures`는 source 부분 실패·전건/예외 실패·list diff 오류 중 하나라도 있으면 `true`입니다. `false`가 데이터 의미의 정확성, 전체 detail coverage, embedding·추천 완료까지 보증하지는 않습니다.
- `sourceResults[].success`는 해당 실행이 예외 없이 반환되고 `failedCount=0`일 때만 `true`입니다. `status`는 `SUCCESS`·`PARTIAL_SUCCESS`·`FAILED`로 나뉩니다. `48/50` 저장·2건 실패는 저장 건수를 유지한 `PARTIAL_SUCCESS`이고, 처리/skip/필터 결과 없이 전건 실패하면 `FAILED`입니다. 예외로 건수를 알 수 없을 때는 가짜 항목 실패 건수를 만들지 않습니다.
- `requestedSourceCount = succeededSourceCount + partialSourceCount + failedSourceCount`입니다. 이는 고유 source 종류가 아니라 실행 entry 수이며 forced/rotation의 같은 lane을 합치지 않습니다. 저장·skip·filtered·failed의 기존 항목 건수도 유지합니다. skip·필터 제외를 자동으로 실패에 합산하지 않으며, 제외가 적절했는지는 별도 품질 검증 대상입니다.
- `listDiffResults[]`는 sourceKey·jobName·status·reason·안전한 오류 코드/메시지를 별도로 반환합니다. snapshot 저장과 변경 정책 판정이 끝나면 `SUCCESS`, source 실패/부분 실패 때문에 비교하지 않으면 `SKIPPED`, snapshot 저장 또는 정책 판정에서 예외가 나면 `FAILED`입니다. `failedListDiffCount`는 이 비교 오류만 집계하며 성공한 목록 저장을 source 실패로 바꾸거나 항목 건수를 덮어쓰지 않습니다.
- baseline·임계치 미달·후보 없음·count-drop guard는 정상적으로 끝난 비교의 reason으로 남깁니다. forced detail을 생략했다는 이유만으로 비교 실패를 만들지 않습니다. 오류 원문의 SQL·URL·비밀·PII는 응답에 포함하지 않습니다.

Scheduled 실행도 같은 결과를 계산하고 완료 로그에 전체 status와 성공/부분 실패/실패 실행 수·비교 실패 수를 남깁니다. 이 계약은 `collect/all` 범위이며 개별/async API의 결과 모델이나 DB의 `api_sync_logs` schema를 바꾼 것은 아닙니다.

## Scheduled와 manual의 차이

`collect/all`은 매일 모든 상세를 전량 수집하는 경로가 아닙니다.

1. YOUTH → BOKJIRO_CENTRAL → BOKJIRO_LOCAL → GOV24 list를 처리합니다.
2. `collect_list_snapshots/collect_list_snapshot_items` fingerprint로 new/changed/missing을 계산합니다.
3. 모든 list가 끝난 뒤 임계치를 넘은 sourceId 후보의 forced detail을 먼저 실행합니다.
4. 마지막에 요일별 detail rotation을 실행합니다. forced와 rotation이 같은 lane 결과에 각각 나타날 수 있습니다.

최초 snapshot은 baseline만 만들며, 대량 missing은 upstream 응답 축소일 수 있어 `COUNT_DROP_GUARDED`로 막습니다. saved_count는 upsert 처리량이지 변경 건수가 아닙니다. 기본 force 기준은 신규 30건 또는 2%, 변경 100건 또는 5%; 이전 total 대비 missing 15% 이상이며 신규/변경보다 크면 guard합니다. 후보 cap 기본 50건입니다.

| 요일 | 기본 rotation |
| --- | --- |
| 월 | BOKJIRO_DETAIL, missing detail 보강 100 calls/run |
| 화·수 | GOV24_DETAIL / GOV24_SUPPORT_CONDITIONS, 각각 50 |
| 목·금 | BOKJIRO_DETAIL_REFRESH 50 / YOUTH_DETAILS 50 |
| 토·일 | heavy detail rotation 없음 |

manual Gov24 list는 완료 후 detail/support를 기본 50건씩 follow-up합니다. 운영형 수동 경로는 async trigger 202(QUEUED/active) → status QUEUED/RUNNING/SUCCEEDED/FAILED이며 latest sync log·metadataJson을 같이 확인합니다. List chunk 기본 500, pause 100ms; metadata는 chunkSize/chunkPauseMs/chunkCount/elapsedMs/totalCount/staleDeletedCount입니다. stale cleanup은 chunk 중간이 아닌 full-run 성공 뒤 한 번 수행합니다.

## 호출량과 동시 실행

아래는 [application.yml](../../backend/src/main/resources/application.yml)과 service의 기본값입니다. admin lane inventory `configEntries`가 실제 runtime 값의 확인 경로입니다. `collect-failures`는 scheduled/rotation/manual lane·최근 log·failure/partial/streak/circuit·설정 요약을 제공하며 raw replay 전용 경로는 외부 호출 inventory와 구분합니다.

| 경로 | 기본 예산·pacing·retry |
| --- | --- |
| List 공통 | 300ms, 3 attempts / 1000ms backoff |
| 복지로 list | source별 10,000 items/run, 429 3회 / cooldown 10,000ms; local circuit 1,800,000ms |
| 복지로 detail | central/local 독립 10,000 calls/run. application 기본 300ms·연속 429 2회, retry 3/1500ms |
| Gov24 list | 20,000 items/run, chunk 500 / pause 100ms |
| Gov24 detail/support | 각각 50 calls/run, 300ms, retry 3/1500ms |
| YOUTH detail | missing detail 우선, 50 calls/run, 500ms |
| Gap-fill/refresh 수동 | 호출 상한과 source별 적용은 [수집 운영](collect-ops.md#작업-선택과-상한)을 확인 |

복지로 detail service의 property fallback은 1000ms·429 5회지만 일반 application 설정은 300ms·2회입니다. 둘을 같은 “코드 기본값”으로 섞지 않습니다. runtime override는 둘과 또 다를 수 있습니다.

[CollectExecutionGuard](../../backend/src/main/java/com/example/welfare/collect/service/CollectExecutionGuard.java)는 DB 전역 `collect-global` lock, lease 기본 15분·heartbeat 60초를 사용합니다. 겹친 collect는 `409/COL002`입니다. 현재 lock을 없는 기능으로 보고 Redis 도입을 필수 개선처럼 적지 않습니다. Backfill도 일반 수집과 겹치지 않게 대상 service의 guard를 확인합니다.

## YOUTH 상세 링크와 지역

LIST(pageType=1)의 `aplyUrlAddr/sbizCd`와 DETAIL(pageType=2)의 `refUrlAddr1/2` 범위를 구분합니다. 링크 우선순위는 aplyUrlAddr → refUrlAddr1 → refUrlAddr2지만 참고 URL은 기관 메인·기사·SNS일 수 있어 확정 신청 링크로 표현하지 않습니다. YouthDetailCollectService는 detail row가 없는 정책 우선, API 오류는 건별 skip할 수 있고 raw category는 DETAIL입니다.

지역은 `WelfareServiceMapper.regionsFromYouth()`에서 zipCd의 시도가 15개 이상이면 전국형 입력으로 보고 host_org 추론을 사용합니다. 전국 유일 시군구 → 시도 전체 → 추론 불가 시 빈 region 순서이며, 빈 region은 현 조회에서 전국 노출될 수 있습니다. 중앙기관 주관 지역 한정 정책의 오노출 위험은 미해결 의미 문제이지 완벽한 자격판정이 아닙니다.

2026-06-02 대조에서 강원 51***·전북 52***·대구 군위 27720과 multi-district city의 ward 단위 입력을 확인했습니다. 법정동 전체자료를 query/프로필 UX 검토 없이 코드 생성 원본으로 치환하지 않습니다. 세부 진단은 [YOUTH regionless](youth-regionless-audit-runbook.md), artifact는 `tmp/youth-region-code-validation/latest-youth-region-code-validation.json`입니다.

## 과거 관측

| 시점·환경 | 당시 기록과 범위 |
| --- | --- |
| 2026-05-10 운영 계정 확보 메모 | 복지로 central/local list/detail 4개 각각 일일 100,000 quota. 현재 계정 권한/잔여 quota는 실행 전에 재확인 |
| 2026-05-18 서버 override | 복지로/Gov24 detail pacing 50ms, 복지로 429 abort 2회. 현재 실효값으로 고정하지 않음 |
| 기존 로컬 async 메모(날짜 미기재) | trigger 18ms, full run 약 89,550ms, 22 chunks, requested=saved 10,954 / failed 0 |
| 기존 YOUTH DETAIL 메모(날짜 미기재) | 초기 링크 없음 68%(1,754); detail 후 링크 2,012/2,570 (78.3%), 558건은 응답에도 URL 없음. 현재 전건 상태 아님 |
| 2026-06-04 로컬 regionless | total 2,619 / region services 2,275 / regionless 344. empty zipCd 0, CSV·전국형 344 |
| 같은 bounded override 검증 | `20250110005400210145 → 46800`, `20250618005400211022 → 31140`; regionless 344→342, suspicious 2→0 |

YOUTH/복지로/Gov24 의미·코드 변동·미입력·수동 보정은 [정규화 과제](../policy/policy-normalization-current-state.md#리팩토링-확인-과제), Gov24의 개별 계약·과거 coverage는 [Gov24 데이터 계약](../policy/policy-gov24-lane-closeout.md)에서 확인합니다. 외부 API 지연·rate limit 영향과 내부 DB/transaction 병목은 측정으로 분리하며, 특정 source가 항상 최대 병목이라고 고정하지 않습니다.
