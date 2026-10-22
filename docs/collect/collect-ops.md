# 수집 실행과 장애 판정

수집의 현재 코드·설정은 [현재 동작](collect-current-state.md), 상세 호출 순서는 [detail 실행 계약](collect-detail-execution-contract.md)에 있습니다. 이 절차는 조회와 실제 collect/repair를 구분합니다. 수집은 외부 예산·앱/DB 상태에 영향을 주며 운영 데이터나 schema를 자동 복구하지 않습니다.

## 실행 전

1. [환경 안내](../core/environment-guide.md)에서 앱·DB·Redis·권한·source·전체/단건·호출량을 정합니다. `local/prod/127.0.0.1` 이름으로 판단하지 않습니다.
2. 다른 collect·backfill·repair와 겹치지 않는지, lock/circuit과 현재 오류를 확인합니다.
3. snapshot 두 테이블·sidecar·권한과 payload/replay 전제가 있는지 확인합니다. 누락 schema는 [DB 진단](../core/db-migration.md#수집-snapshot-테이블-누락)으로 분리하고 collect와 patch를 묶지 않습니다.
4. 기존 이력이면 [외부 API 저장 감사](collect-external-api-smoke-runbook.md), daily lane 요약이면 [governance 관측](collect-governance-observation-runbook.md), retry/rate-limit/guard면 [resilience audit](collect-source-resilience-audit-runbook.md)를 먼저 선택합니다.

RDS-backed 앱의 DB 이력을 볼 때는 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres`처럼 승인된 점검 설정과 직접 접속 모드를 명시합니다. 앱 runtime env와 migration/query 계정은 역할이 다릅니다. DB auto mode가 로컬 컨테이너만 읽으면 운영 앱과 다른 결과가 나올 수 있습니다.

## 작업 선택과 상한

아래 endpoint는 `/api/admin/collect` 아래입니다. **POST 수집/보정은 쓰기 작업**이며 일부는 실제 외부 API를 호출합니다. 승인한 앱·admin 인증·origin과 호출 예산이 없으면 실행하지 않습니다.

| 작업 | 경로·의미 |
| --- | --- |
| 전체 | `POST /all`: daily list → diff → forced 후보 → 요일 rotation, 모든 상세 전량 실행 아님 |
| 목록/상세 | `POST /{sourceKey}`: youth, bokjiro-central/local, gov24, gov24-details/support-conditions, bokjiro-details/refresh |
| 비동기 | `POST /gov24/async`, `GET /gov24/async-status`: 목록 chunk와 후속 detail/support 결과까지 별도 대조 |
| YOUTH 상세 | `POST /youth-details`: missing detail 우선. 공개 sourceId 단건 수동 lane을 가정하지 않음 |
| 복지로 기본/refresh | `POST /bokjiro-details`는 missing 우선, `/bokjiro-details-refresh`는 기존 row 재동기화. sourceId 단건 지원 |
| Gap-fill | `POST /bokjiro-details-gap-fill`: source별 상세 예산으로 여러 round. 총 shared pool로 오해하지 않음 |
| Raw sidecar replay | `POST /bokjiro-sidecars-backfill?scope=all|list|detail`, `/gov24-sidecars-backfill`: 외부 API 재호출 없이 DB sidecar를 재적재 |
| Gov24 결측/지역 replay | `/gov24-sidecars-backfill?scope=list&missingOnly=true` 또는 `scope=regions` |
| 뒤집힌 연령 | `POST /inverted-age-backfill?sourceType=...`: 저장 DETAIL snapshot replay. 실제 limit 정규화를 확인 |

상한은 [CollectAdminController](../../backend/src/main/java/com/example/welfare/collect/controller/CollectAdminController.java)를 기준으로 합니다.

- 일반 `maxCallsPerRun=1..5000`; source별 override 지원 범위는 service/controller에서 확인합니다.
- 양수 `limitPerSource=1..1000`. **sidecar backfill의 0 이하 해석은 repository/service에서 unlimited**지만, controller는 음수를 거부합니다.
- inverted-age의 `limitPerSource=0`은 capped default 1000입니다. reference-urls rebuild의 0도 1000이며, sidecar의 unlimited 계약을 일반화하지 않습니다.
- Gap-fill `rounds=1..10`, `maxCallsPerRound=1..1000`는 central/local 각각 적용됩니다. refresh no-arg와 rotation·수동 cap은 다른 값입니다.
- Gov24 detail/support와 복지로 detail/refresh는 지원된 sourceKey에서 sourceId 단건 재시도를 먼저 검토합니다. TITLE을 identity로 쓰지 않습니다.

`run-local-collect-legacy-repair-suite.sh`는 조회가 아니라 source별 연령 replay → 남은 sourceId self-heal → coverage audit입니다. `RUN_GAP_FILL=true`일 때 추가 외부 호출(기본 2 rounds × 20 calls)이 생깁니다. 반복 403/429·기존 실행·목적 불일치가 있으면 중단하고 원인을 확인합니다.

## 실행 직후와 결과 판정

HTTP 응답보다 `api_sync_logs`의 실행별 status·count·시각·오류 코드를 대조합니다. `collect/all`은 후속 detail/support도 별도 실행이며 `completedWithFailures=false`만으로 전체 성공을 확정하지 않습니다. list diff의 new/changed/missing·guard·후보와 실제 row 변화도 기록합니다.

```sql
SELECT job_name, status, started_at, finished_at,
       requested_count, saved_count, skipped_count, filtered_count, failed_count,
       error_code
FROM api_sync_logs
ORDER BY started_at DESC
LIMIT 20;
```

status는 RUNNING/SUCCESS/PARTIAL_SUCCESS/FAILED/SKIPPED이며 PostgreSQL 저장값은 소문자일 수 있습니다. requested는 list 응답 item 또는 실제 detail 호출, saved는 upsert 처리량, skipped는 필수값/기존 detail 등 제외, filtered는 청년 필터 제외, failed는 호출/저장 실패입니다.

| 관측 | 판정·다음 확인 |
| --- | --- |
| Partial·일부 429/skip/저장 실패 | 결과를 유지할 수 있으나 완전 성공 아님. 실패 항목·재시도 범위·실효 pacing/circuit을 기록 |
| List 0건 | upstream 장애·quota·필터·응답 축소 확인. 즉시 전체 삭제로 해석하지 않음 |
| 완료된 backlog의 requested/saved 0 + skip 증가 | 정상 재실행일 수 있음. 첫 수집·미완료 backlog의 반복 0과 구분 |
| NO_COLLECT_HISTORY | 로그와 snapshot 없는 DB. source 품질 결론보다 대상 DB 일치부터 확인 |
| STORAGE_PARITY_REVIEW / SIDECAR_PARITY_REVIEW | 성공 로그와 raw/정책/sidecar 실제 저장 대조 |
| DETAIL_SUPPORT_COVERAGE_REVIEW | DETAIL raw·detail row·SUPPORT raw·fact coverage의 결측/shape 확인 |
| 연속 list 0·반영 없음·로그 없음·즉시 반복 실패·snapshot 누락 | 실제 장애/구조 오류로 분리. health UP으로 정상 처리하지 않음 |

item 단위 transaction·lock/deadlock/낙관적 충돌 재시도는 최대 3회이며 전체 rollback이 안 될 수 있습니다. 외부 0/429가 기존 데이터 삭제 근거는 아니지만, full 성공 뒤 stale cleanup이 있는 source까지 “항상 upsert만 한다”고 일반화하지 않습니다.

## 저장과 downstream 확인

정책·raw·detail·sidecar·snapshot item과 `collect_runtime_statuses/collect_execution_locks`를 대조합니다. source별 coverage는 `collect-detail-support-coverage.tsv`도 확인합니다. 수집 저장, canonical 적재, list diff, embedding, recommendation/replay는 서로 다른 성공 경계입니다.

정책 추천에 영향을 주는 source/sidecar를 바꿨다면 read-model·표시·후보·북마크/로그 보존 회귀를 확인합니다. fresh reset 뒤 integrated schema와 정책 snapshot 없이 replay가 재현된다고 가정하지 않습니다. [sidecar preflight helper](../../deploy/mysql/apply-local-policy-sidecar-draft.sh)는 현재 PostgreSQL 존재 여부 검사이며 과거 MySQL draft SQL auto-apply가 아닙니다.

## 기록과 과거 복구 사례

실행 시각·커밋·환경/대상·source/endpoint·예산·log ID·요청/저장/실패/skip·new/changed/missing/guard·row 변화·후처리·장애·다음 액션을 남깁니다. 로그/응답에 비밀·PII 원문을 복사하지 않습니다.

2026-05-29 로컬 복구 메모에는 `WLF00004717`(정책 3700)의 연령 35/34→35/39, BOKJIRO_LOCAL 2726/3197 등 추가 inverted 사례, Gov24 117→repaired 117→0과 다른 source 0, remaining_inverted_rows=(none)이 있습니다. 이는 현재 DB의 backlog가 없다는 증거가 아닙니다. 응답의 repairedCount·missingRawPayloadCount·unrepairedCount·failedCount를 분리합니다. 원래 전체 이력은 [작업 기록](../history/core/phase-plan-before-doc-cleanup.md)을 확인합니다.

공통 health/dashboard/collect/recommendation surface 관측은 [운영 baseline](../core/ops-baseline-runbook.md), 최신 로컬 부분 실패 사례는 [2026-10-21 기록](../phase-plan.md#2026-10-21-로컬-수집-실행-확인)을 봅니다. 과거 “무중단 배포/새 기능은 지금 하지 않음” 방침은 현재 승인된 개선의 일괄 금지 조건이 아닙니다.
