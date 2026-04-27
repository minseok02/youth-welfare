# 수집 운영 기준

## 목적

공공 API 수집은 외부 서비스 상태와 호출 제한에 영향을 받는다.  
이 문서는 청년복지 플랫폼의 수집 배치를 운영할 때 따르는 기준을 정리한다.

---

## 현재 운영 원칙

### 1. 수집은 한 번에 하나만 실행

- `collect/all`, `collect/youth`, `collect/bokjiro-central`, `collect/bokjiro-local`, `collect/bokjiro-details`는 동시 실행하지 않는다.
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

---

## 장애 판단 기준

### 경고로 처리

- 복지로 목록 수집 결과가 0건
- 상세 수집 중 일부 429 발생
- 일부 정책 저장 실패
- 일부 상세 저장 실패

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
- `InterruptedRun` 복구 로그

## DB에 저장되는 수집 실행 로그

수집 source별 실행 결과는 `api_sync_logs` 테이블에 저장한다.

주요 상태:

- `RUNNING`: 수집 시작 직후
- `SUCCESS`: source 수집이 실패 건수 없이 완료
- `PARTIAL_SUCCESS`: 일부 아이템 저장 실패가 있었지만 source 수집은 완료
- `FAILED`: 외부 API 예외 등으로 source 수집이 중단

앱이 재시작되거나 비정상 종료되면 이전 실행에서 남은 `RUNNING` 로그는 다음 앱 시작 시 `FAILED`로 자동 전환한다.
`error_code=InterruptedRun` 으로 표시되며, 이는 수집 로직 예외가 아니라 중단 복구 기록으로 해석한다.

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
