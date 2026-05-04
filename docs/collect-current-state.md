# 수집 현재 동작 기준

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

관련 문서:

- [collect-ops.md](./collect-ops.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [phase-plan.md](./phase-plan.md)

## 목적

이 문서는 현재 코드 기준으로 collect가 어떻게 동작하는지,
무엇이 정상이고 무엇이 장애인지 빠르게 확인하는 current-state 문서입니다.

실행 체크리스트나 장애 기록 양식은 별도 문서를 봅니다.

## 현재 collect entry

수동 진입:

- `POST /api/admin/collect/all`
- `POST /api/admin/collect/{sourceKey}`
- `POST /api/admin/collect/bokjiro-details-gap-fill`
- `POST /api/admin/collect/bokjiro-sidecars-backfill`

정기 진입:

- `CollectBatchService.collectAll()`
- 매일 새벽 2시 실행

## 현재 source dispatch 구조

현재 collect는 source별 service 메서드를 늘리는 구조가 아니라:

1. `CollectSource`
2. `CollectSourceAdapter`
3. `CollectSourceExecutionService`
4. `CollectBatchService` / `CollectAdminService`

registry dispatch 구조로 동작합니다.

즉 새 source는 보통 adapter 추가로 연결합니다.

## 현재 저장 레이어

collect 이후 저장되는 축은 아래입니다.

### 1. 정책 기본 row

- `welfare_services`
- `service_regions`
- `service_tags`

### 2. raw payload

- `raw_api_payloads`

용도:

- list/detail/category 원문 보존
- 이후 sidecar backfill/replay

### 3. canonical sidecar

- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

주의:

- 이 sidecar는 current runtime 기준으로도 실제 저장되지만
- fresh reset 뒤 draft schema/bootstrap 공백은 local helper로 보완하는 경계가 남아 있습니다

### 4. sync log

- `api_sync_logs`

용도:

- source별 실행 시작/성공/부분성공/실패 기록
- requested/saved/skipped/filtered/failed 집계

## 현재 운영 원칙

### 1. 동시 실행 금지

- collect는 한 번에 하나만 실행
- 다른 collect 작업이 실행 중이면 `409 / COL002`

### 2. 부분 성공 허용

- 일부 item 저장 실패가 있어도 전체 롤백하지 않음
- item 단위 트랜잭션과 retry 사용

### 3. 외부 API 0건/429는 바로 데이터 삭제로 해석하지 않음

- `0건` 은 외부 응답 품질 문제일 가능성을 먼저 봄
- `429` 는 retry 후 현재까지 확보한 결과만 반영 가능

### 4. collect 성공과 downstream 재현은 분리해서 본다

- collect success
- sidecar 저장
- replay/recommendation downstream

은 같은 경계가 아닙니다.

특히 local reset 직후에는 collect 성공만으로 replay가 바로 재현되지 않을 수 있습니다.

## 현재 정상으로 보는 것

아래는 현재 정상 범주입니다.

- 일부 item 저장 실패가 있지만 source collect 전체는 완료
- 복지로 detail 일부 skip
- `429` 발생 후 retry 또는 조기 종료
- `0건` 반환이 한 번 발생했지만 기존 snapshot 유지

## 현재 장애로 보는 것

아래는 실제 장애 쪽입니다.

- collect 요청 자체가 처리되지 않음
- 여러 배치 연속 0건
- `api_sync_logs` 가 남지 않음
- 외부 응답은 왔는데 DB 반영이 전혀 없음
- 같은 source가 반복적으로 시작 직후 실패

## 현재 collect 병목 해석

현재 로컬 기준 가장 큰 병목은 `POST /api/admin/collect/youth` 입니다.

해석:

- 내부 코드만의 문제로 보지 않음
- external API latency / rate limit / upstream instability 영향이 큼

즉 운영 기준 해석은:

- collect는 배치/스냅샷 확보 경로
- serving은 내부 DB snapshot 사용

입니다.

## 현재 확인할 기본 지표

1. `api_sync_logs`
2. `welfare_services` row 수
3. `raw_api_payloads` row 수
4. `service_taxonomies` / `service_facts` row 수
5. 필요 시 replay/downstream smoke

## 요약

1. collect는 adapter registry 기반으로 돈다.
2. 저장 레이어는 `welfare_services`, `raw_api_payloads`, `sidecars`, `api_sync_logs` 네 축이다.
3. 부분 성공과 외부 변동성은 정상 범주로 본다.
4. 진짜 장애는 “로그 없음”, “반영 없음”, “연속 실패” 쪽이다.
5. collect 성공과 downstream 재현은 분리해서 봐야 한다.
