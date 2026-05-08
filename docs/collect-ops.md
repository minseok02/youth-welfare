# 수집 실행 기준

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

공공 API 수집은 외부 서비스 상태와 호출 제한에 영향을 받는다.  
이 문서는 현재 로컬 기준으로 수집을 실행하고 해석할 때 따르는 기준을 정리한다.

---

## 현재 운영 원칙

### 1. 수집은 한 번에 하나만 실행

- `collect/all` 과 `collect/{sourceKey}` 수동 경로는 동시 실행하지 않는다.
- 현재 `sourceKey` 는 `youth`, `bokjiro-central`, `bokjiro-local`, `bokjiro-details`, `bokjiro-details-refresh` 를 지원한다.
- canonical sidecar replay 전용 수동 경로 `POST /api/admin/collect/bokjiro-sidecars-backfill?scope=all|list|detail&limitPerSource=0` 도 같은 시간대에 일반 collect 수동 실행과 겹치지 않게 사용한다.
- 이미 다른 수집 작업이 실행 중이면 새 요청은 `409 Conflict (COL002)`로 거절한다.
- 이유: 중복 실행 시 `service_tags` 저장 경합과 deadlock 위험이 커진다.

### 2. 외부 API가 429를 반환해도 서버는 실패로 종료하지 않음

- 복지로 목록 수집은 `429 Too Many Requests`가 발생하면 짧은 backoff로 최대 3회 재시도한다.
- 재시도 후에도 계속 429이면 해당 실행은 현재까지 확보한 결과까지만 반영하고 종료한다.
- 상세 수집도 429를 감지하면 즉시 폭주하지 않고 중단 조건에 따라 종료한다.

### 3. 수집 결과가 0건이면 기존 적재 데이터 유지

- 복지로 목록 수집에서 0건이 반환되더라도 기존 DB 데이터를 삭제하거나 비우지 않는다.
- 현재 구조는 수집된 아이템만 upsert하는 방식이므로, 0건이면 실제 저장 루프가 돌지 않아 기존 데이터가 그대로 남는다.
- 운영 해석:
  - `0건`은 "정상적으로 데이터가 하나도 없다"보다
  - "외부 API 제한, 일시 장애, 응답 품질 저하"로 보는 것이 안전하다.

### 4. 부분 성공을 허용

- 수집 중 일부 아이템 저장 실패가 있어도 전체 배치를 롤백하지 않는다.
- 아이템 단위 저장은 별도 트랜잭션으로 처리한다.
- deadlock/lock timeout/낙관적 락 충돌은 최대 3회 재시도 후 최종 실패로 기록한다.

### 5. 복지로 상세는 기본 수집과 refresh 수동 경로를 구분

- `/api/admin/collect/bokjiro-details` 는 detail row가 없는 정책 위주로 채우는 기본 경로다.
- `/api/admin/collect/bokjiro-details-refresh` 는 기존 detail row가 있어도 다시 fetch/merge 하는 refresh 전용 수동 경로다.
- `/api/admin/collect/bokjiro-details-gap-fill` 는 기본 detail 경로를 여러 라운드로 반복 호출해, `95/API` cap은 유지하면서 missing detail backlog 를 점진적으로 더 채우는 coverage 확장 전용 수동 경로다.
- `/api/admin/collect/bokjiro-sidecars-backfill` 는 외부 API를 다시 호출하지 않고, 이미 저장된 `raw_api_payloads` 를 canonical sidecar(`service_taxonomies`, `service_taxonomy_terms`, `service_facts`) 로 재적재하는 replay 전용 경로다.
- 운영 해석:
  - 일반 배치는 기본 경로를 유지해 호출량을 억제한다.
  - 상세 본문 포맷이 바뀌었거나 기존 적재값을 다시 동기화해야 할 때만 refresh 경로를 쓴다.
  - stored detail payload coverage 가 낮아 sidecar density가 detail raw 개수에 묶여 있을 때만 gap fill 경로를 써서 여러 라운드 backlog 를 메운다.
  - 기존 raw payload 로 sidecar를 다시 채우거나 density를 재측정할 때만 backfill 경로를 쓴다.

### 5-1. 현재 복지로 detail 은 quota 제한 하의 점진 채움을 기본으로 본다

- 현재 개발 계정 기준으로 복지로 detail 트래픽 한도는 `100` 수준이라, 하루 안에 전체 backlog 를 다 메우는 운영 기준을 세우지 않는다.
- 현재 단계의 목표는:
  - list snapshot 확보
  - detail raw 일부 선적재
  - 서버 오픈 전 최소 동작 검증
- 따라서 detail coverage 부족분 자체만으로 장애 판정을 내리지 않는다.
- 운영 계정/증설 quota 확보 뒤에야 full gap fill / refresh 를 다시 운영 작업으로 올린다.

### 6. 복지로 상세 호출 budget 은 source backlog 비율을 먼저 본다

- `collectBokjiroDetailsResult(maxCalls, ...)` 는 중앙/지자체 상세 대상을 먼저 집계한 뒤, `maxCallsPerApiPerRun` cap 안에서 backlog 비율대로 budget을 나눈다.
- 한쪽 source에 target이 없으면 남은 source가 전체 budget을 가져간다.
- low `maxCalls` 에서도 중앙 source를 무조건 먼저 소진하지 않는다.
- 이유:
  - canonical sidecar merge/backfill 검증은 local source만 따로 태우는 경우가 많다.
  - 중앙에 target이 있거나 개수가 적어도, local backlog가 더 크면 local path가 0 budget으로 굳지 않도록 해야 한다.

---

## 장애 판단 기준

### 경고로 처리

- 복지로 목록 수집 결과가 0건
- 상세 수집 중 일부 429 발생
- 일부 정책 저장 실패
- 일부 상세 저장 실패
- 개발 계정 quota 때문에 복지로 detail backlog 가 남아 있음

### 실제 장애로 판단

- 앱이 수집 요청 자체를 처리하지 못함
- 온통청년/복지로 수집이 연속해서 여러 배치 동안 0건
- 수집 API 응답은 200인데 DB 반영이 전혀 일어나지 않음
- 전체 수집이 반복적으로 중간 종료되며 로그가 남지 않음

---

## 운영 로그에서 봐야 할 항목

- `수집 실행 시작/종료`
- `source별 수집 완료 건수`
- `FieldQuality`
- `saved / skip / filtered`
- `429 재시도 로그`
- `429로 수집 중단 로그`
- `수집 결과 0건, 기존 데이터 유지` 경고

## DB에 저장되는 수집 실행 로그

수집 source별 실행 결과는 `api_sync_logs` 테이블에 저장한다.

주요 상태:

- `RUNNING`: 수집 시작 직후
- `SUCCESS`: source 수집이 실패 건수 없이 완료
- `PARTIAL_SUCCESS`: 일부 아이템 저장 실패가 있었지만 source 수집은 완료
- `FAILED`: 외부 API 예외 등으로 source 수집이 중단

MySQL `api_sync_logs.status` 컬럼은 enum 저장값이 소문자(`running`, `success`, `partial_success`, `failed`, `skipped`)로 보일 수 있다.

주요 집계:

- `requested_count`: 목록 수집은 응답 아이템 수, 상세 수집은 실제 상세 API 호출 수
- `saved_count`: DB에 저장한 정책/상세 건수
- `skipped_count`: 필수 필드 부족 또는 이미 상세가 있어 건너뛴 건수
- `filtered_count`: 청년 대상 필터에서 제외된 건수
- `failed_count`: 저장 실패 또는 상세 호출 실패 건수

장애 확인용 예시:

```sql
SELECT job_name, status, started_at, finished_at,
       requested_count, saved_count, skipped_count, filtered_count, failed_count,
       error_code, error_message
FROM api_sync_logs
ORDER BY started_at DESC
LIMIT 20;
```

---

## 현재 배치 정책

- 전체 수집 스케줄: 매일 새벽 2시
- 상세 수집: 일일 호출 상한 적용
- 무중단 배포는 현재 우선순위 아님
- 운영상 변경은 사용자 적은 시간대에 공지 후 반영

---

## 향후 개선 후보

- Redis 기반 분산 락으로 멀티 인스턴스 대응
- 429 발생 시 다음 실행 시점까지 source 단위 쿨다운
- 소스별 마지막 성공 시각/마지막 성공 건수 대시보드화
- 운영 계정 확보 뒤 복지로 detail quota 상향 및 gap fill / refresh 재검증
