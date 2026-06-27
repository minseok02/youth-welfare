# 수집 실행 체크리스트

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

관련 문서:

- [collect-current-state.md](./collect-current-state.md)
- [collect-ops.md](./collect-ops.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

## 목적

이 문서는 수집을 실제로 실행하거나,
실행 후 상태를 확인할 때 따라가는 짧은 runbook 입니다.

## 1. 실행 전 확인

- 지금 다른 collect가 돌고 있지 않은가
- 이번 실행이 `all` 인가 `single source` 인가
- `detail refresh`, `gap-fill`, `sidecar backfill` 중 무엇인가
- 현재 목적이 snapshot 확보인가, detail coverage 확장인가, sidecar replay 인가

## 2. 실행 유형 선택

### A. 일반 source collect

- `POST /api/admin/collect/{sourceKey}`

사용:

- source 최신 snapshot 갱신
- 현재 `sourceKey` 예:
  - `youth`
  - `bokjiro-central`
  - `bokjiro-local`
  - `gov24`
  - `gov24-details`
  - `gov24-support-conditions`

### B. 전체 collect

- `POST /api/admin/collect/all`

사용:

- 배치와 같은 순서로 daily list snapshot, list diff, forced detail 후보, 요일별 detail rotation 확인

해석:

- `collect/all` 은 더 이상 "모든 detail lane 전량 실행" 이 아니다.
- list source는 매일 `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`, `GOV24` 순서로 실행된다.
- 첫 list snapshot은 baseline만 만들고 forced detail을 실행하지 않는다.
- 신규/변경 diff가 임계치를 넘으면 sourceId 후보만 forced detail로 먼저 처리한다.
- 대량 missing/count drop은 forced detail이 아니라 guard/warning으로 본다.
- 요일별 detail rotation은 남은 예산 경로로 읽는다.

### C. detail gap-fill

- `POST /api/admin/collect/bokjiro-details-gap-fill`

사용:

- stored detail coverage 확장

### D. sidecar backfill

- `POST /api/admin/collect/bokjiro-sidecars-backfill`

사용:

- raw payload 기준 canonical sidecar 재적재

## 3. 실행 직후 확인

### 최소 확인

- 응답 status
- `api_sync_logs` latest row
- requested / saved / failed count
- list run이면 `collect_list_snapshots` latest row의 `new_count / changed_count / missing_count`
- 외부 API 재호출 없이 DB 이력만 먼저 볼 때는 `bash deploy/smoke/run-local-collect-external-api-smoke.sh`
- 앱이 RDS-backed prod profile이면 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres` 로 앱 DB와 smoke DB를 맞춘다.

### 필요 시 추가 확인

- `welfare_services`
- `raw_api_payloads`
- `welfare_service_details`
- `collect_list_snapshot_items`
- `service_taxonomies`
- `service_taxonomy_summary_slots`
- `service_facts`

## 4. 결과 해석

### 정상 범주

- `SUCCESS`
- `PARTIAL_SUCCESS` 이더라도
  - `saved_count > 0`
  - 실패 원인이 `429`/일부 upstream transient/부분 저장 실패로 설명 가능
  - latest `api_sync_logs` 와 row count가 같은 방향으로 움직이는 경우
- 일부 `429`
- 일부 저장 실패
- backlog가 이미 닫힌 `detail`/`support`/`gap-fill` 재실행에서
  - `requested=0`
  - `saved=0`
  - `skipped_count` 증가
  가 함께 보이는 경우

### 바로 원인 확인이 필요한 것

- `FAILED`
- `collect/all`, 일반 목록 collect, closeout 전 1차 실행인데 `requested=0` 이 반복
- `saved=0` 이 반복되는데 `skipped_count` 증가나 backlog closeout 맥락으로 설명되지 않는 경우
- `api_sync_logs` row 없음
- list snapshot의 `missing_count` 가 대량으로 증가했는데 upstream 장애/필터 변경 설명이 없는 경우
- forced detail 후보가 반복적으로 실패해 같은 sourceId가 계속 남는 경우
- `DETAIL_SUPPORT_COVERAGE_REVIEW`: 최근 detail/support 수집 성공이 있는데 `DETAIL` raw, detail row, `SUPPORT` raw, support fact coverage가 비어 있는 경우
- `STORAGE_PARITY_REVIEW` 또는 `SIDECAR_PARITY_REVIEW`: 수집 성공 로그와 raw/welfare/sidecar 저장 결과가 서로 맞지 않는 경우

`api_sync_logs`와 `raw_api_payloads`가 모두 비어 있으면 `NO_COLLECT_HISTORY`로 분리합니다. 이는 실패가 아니라 source 품질을 결론 낼 수 없는 로컬 DB 상태입니다.
단, 앱 화면/로그에는 정책 데이터가 많은데 smoke가 `NO_COLLECT_HISTORY`를 내면 먼저 smoke가 로컬 Docker DB를 보고 있는지 확인합니다.

## 5. collect 후 downstream 확인 여부

아래 중 하나면 downstream 확인까지 같이 봅니다.

- recommendation 영향 있는 정책형 source collect
- canonical sidecar backfill 이후
- local replay/closeout 검증 목적

확인 예:

- replay smoke
- read-model count
- `service_taxonomies` / `service_facts` density
- `collect-detail-support-coverage.tsv` 의 source별 missing detail/support count

## 6. 여기서 멈춰야 하는 경우

- 다른 collect 작업이 이미 실행 중
- 외부 API가 반복적으로 `403/429`
- 지금 목적이 collect가 아니라 source 구조 조사인데 실수로 운영성 호출을 하려는 경우

## 7. 실행 후 남길 최소 기록

- 언제 실행했는지
- 어떤 source/endpoint 였는지
- latest `api_sync_logs` 요약
- list diff 요약(`new / changed / missing`, guard 여부)
- row count 변화
- 장애/경고 포인트
- 다음 액션

## 요약

1. 먼저 어떤 collect 종류인지 정합니다.
2. 응답보다 `api_sync_logs` 를 먼저 봅니다.
3. 필요하면 DB row count와 sidecar density를 같이 봅니다.
4. collect 성공과 downstream 성공은 구분해서 확인합니다.
5. `requested=0` 은 항상 장애가 아니라, 현재 lane이 backlog closeout 재실행인지부터 먼저 구분합니다.
