# 수집 실행 체크리스트

관련 문서:

- [collect-current-state.md](./collect-current-state.md)
- [collect-ops.md](./collect-ops.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)

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

### B. 전체 collect

- `POST /api/admin/collect/all`

사용:

- 배치와 같은 순서로 전체 수집 확인

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

### 필요 시 추가 확인

- `welfare_services`
- `raw_api_payloads`
- `service_taxonomies`
- `service_facts`

## 4. 결과 해석

### 정상 범주

- `SUCCESS`
- `PARTIAL_SUCCESS`
- 일부 `429`
- 일부 저장 실패

### 바로 원인 확인이 필요한 것

- `FAILED`
- `requested=0` 이 반복
- `saved=0` 이 반복
- `api_sync_logs` row 없음

## 5. collect 후 downstream 확인 여부

아래 중 하나면 downstream 확인까지 같이 봅니다.

- recommendation 영향 있는 정책형 source collect
- canonical sidecar backfill 이후
- local replay/closeout 검증 목적

확인 예:

- replay smoke
- read-model count
- `service_taxonomies` / `service_facts` density

## 6. 여기서 멈춰야 하는 경우

- 다른 collect 작업이 이미 실행 중
- 외부 API가 반복적으로 `403/429`
- 지금 목적이 collect가 아니라 source 구조 조사인데 실수로 운영성 호출을 하려는 경우

## 7. 실행 후 남길 최소 기록

- 언제 실행했는지
- 어떤 source/endpoint 였는지
- latest `api_sync_logs` 요약
- row count 변화
- 장애/경고 포인트
- 다음 액션

## 요약

1. 먼저 어떤 collect 종류인지 정합니다.
2. 응답보다 `api_sync_logs` 를 먼저 봅니다.
3. 필요하면 DB row count와 sidecar density를 같이 봅니다.
4. collect 성공과 downstream 성공은 구분해서 확인합니다.
