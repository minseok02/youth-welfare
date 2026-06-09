# 수집 실행 기준

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

공공 API 수집은 외부 서비스 상태와 호출 제한에 영향을 받는다.  
이 문서는 현재 로컬 기준으로 수집을 실행하고 해석할 때 따르는 기준을 정리한다.

compact daily operator 확인은 [collect-governance-observation-runbook.md](./collect-governance-observation-runbook.md) 와 `bash deploy/smoke/run-local-collect-governance-observation-suite.sh` 를 먼저 쓴다. 이 문서는 compact summary 뒤에 lane별 운영 해석과 bounded repair 판단이 더 필요할 때 여는 상세 runbook이다.

---

## 현재 운영 원칙

### 1. 수집은 한 번에 하나만 실행

- `collect/all` 과 `collect/{sourceKey}` 수동 경로는 동시 실행하지 않는다.
- 현재 `sourceKey` 는 `youth`, `bokjiro-central`, `bokjiro-local`, `gov24`, `gov24-details`, `gov24-support-conditions`, `bokjiro-details`, `bokjiro-details-refresh` 를 지원한다.
- canonical sidecar replay 전용 수동 경로
  - `POST /api/admin/collect/bokjiro-sidecars-backfill?scope=all|list|detail&limitPerSource=0`
  - `POST /api/admin/collect/gov24-sidecars-backfill?limitPerSource=0`
  도 같은 시간대에 일반 collect 수동 실행과 겹치지 않게 사용한다.
- 주의:
  - 현재 controller 기준 `limitPerSource=0` 은 무제한이 아니라 capped default `1000` 으로 정규화된다.
  - admin 수동 파라미터 상한은 `maxCallsPerRun <= 5000`, `limitPerSource <= 1000`, `rounds <= 10`, `maxCallsPerRound <= 1000` 이다.
- 이미 다른 수집 작업이 실행 중이면 새 요청은 `409 Conflict (COL002)`로 거절한다.
- 이유: 중복 실행 시 `service_tags` 저장 경합과 deadlock 위험이 커진다.

### 2. 외부 API가 429를 반환해도 서버는 실패로 종료하지 않음

- 복지로 목록 수집은 `429 Too Many Requests`가 발생하면 짧은 backoff로 최대 3회 재시도한다.
- 재시도 후에도 계속 429이면 해당 실행은 현재까지 확보한 결과까지만 반영하고 종료한다.
- 상세 수집은 기본 `300ms` pacing 으로 호출하고, 연속 `429` `2회`를 넘기면 해당 source를 중단 조건으로 처리한다.

### 3. 목록 source에서 수집 결과가 0건이면 기존 적재 데이터 유지

- `youth`, `bokjiro-central`, `bokjiro-local`, `gov24` 같은 목록(list) source에서 0건이 반환되더라도 기존 DB 데이터를 삭제하거나 비우지 않는다.
- 현재 구조는 수집된 아이템만 upsert하는 방식이므로, 0건이면 실제 저장 루프가 돌지 않아 기존 데이터가 그대로 남는다.
- 운영 해석:
  - 목록 source의 `0건`은 "정상적으로 데이터가 하나도 없다"보다
  - "외부 API 제한, 일시 장애, 응답 품질 저하"로 보는 것이 안전하다.
  - 반대로 backlog가 이미 닫힌 `gov24-details`, `gov24-support-conditions`, `bokjiro-details-gap-fill` 재실행에서
    - `requested=0`
    - `saved=0`
    - `skipped_count` 증가
    가 함께 보이면 이상 징후가 아니라 closeout rerun일 수 있다.

### 3-1. nightly는 list diff를 먼저 보고 detail은 후보/요일별 예산으로 처리

- `POST /api/admin/collect/all` 과 scheduled `CollectBatchService.collectAll()` 은 이제 아래 순서로 읽는다.
  1. `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`, `GOV24` list snapshot 수집
  2. `collect_list_snapshots`, `collect_list_snapshot_items` 에 list fingerprint 저장
  3. 이전 snapshot 대비 `new / changed / missing` diff 계산
  4. 신규/변경 임계치 초과 시 해당 sourceId 후보만 forced detail 우선 처리
  5. 남은 기본 흐름으로 요일별 detail rotation 처리
- 초기 snapshot은 baseline만 만들고 forced detail을 실행하지 않는다.
- `missing` 대량 발생은 upstream 장애/응답 축소 가능성이 있으므로 detail 강제가 아니라 count-drop guard로 막는다.
- detail rotation 기본값:
  - 월: `BOKJIRO_DETAIL` missing detail 보강
  - 화: `GOV24_DETAIL`
  - 수: `GOV24_SUPPORT_CONDITIONS`
  - 목: `BOKJIRO_DETAIL_REFRESH`
  - 금: `YOUTH_DETAILS`
  - 토/일: heavy detail rotation 없음
- 기본 임계치:
  - 신규 `30건 이상` 또는 현재 total의 `2% 이상`
  - fingerprint 변경 `100건 이상` 또는 현재 total의 `5% 이상`
  - missing 급감 guard: 이전 total 대비 `15% 이상` missing이고 missing이 신규/변경보다 크면 forced detail 금지
- forced detail은 full refresh가 아니라 sourceId 후보 목록만 처리한다.

### 4. 부분 성공을 허용

- 수집 중 일부 아이템 저장 실패가 있어도 전체 배치를 롤백하지 않는다.
- 아이템 단위 저장은 별도 트랜잭션으로 처리한다.
- deadlock/lock timeout/낙관적 락 충돌은 최대 3회 재시도 후 최종 실패로 기록한다.

### 5. 복지로 상세는 기본 수집과 refresh 수동 경로를 구분

- `/api/admin/collect/bokjiro-details` 는 detail row가 없는 정책 위주로 채우는 기본 경로다.
- `/api/admin/collect/bokjiro-details-refresh` 는 기존 detail row가 있어도 다시 fetch/merge 하는 refresh 전용 수동 경로다.
- `bokjiro-details-refresh` 는 `?sourceId=<복지로 서비스ID>` 단건 refresh도 지원한다.
- `/api/admin/collect/bokjiro-details-gap-fill` 는 기본 detail 경로를 여러 라운드로 반복 호출해, 현재 controller 상한 기준 `round당 source별 최대 1,000 calls`, `최대 10 rounds` 안에서 missing detail backlog 를 더 채우는 coverage 확장 전용 수동 경로다.
- `/api/admin/collect/bokjiro-sidecars-backfill` 는 외부 API를 다시 호출하지 않고, 이미 저장된 `raw_api_payloads` 를 canonical sidecar(`service_taxonomies`, `service_taxonomy_terms`, `service_facts`) 로 재적재하는 replay 전용 경로다.
- `/api/admin/collect/gov24-sidecars-backfill` 는 Gov24 `LIST raw` 를 다시 읽어 `service_taxonomy_summary_slots` 같은 canonical sidecar summary label을 재적재하는 replay 전용 경로다.
- 운영 해석:
  - 일반 배치는 기본 경로를 유지해 호출량을 억제한다.
  - 상세 본문 포맷이 바뀌었거나 기존 적재값을 다시 동기화해야 할 때만 refresh 경로를 쓴다.
  - 특정 stale row만 다시 맞출 때는 `sourceId` 단건 refresh를 먼저 쓴다. 현재 local self-heal 기준 예시는 `POST /api/admin/collect/bokjiro-details-refresh?sourceId=WLF00004717` 이고, 이 경로로 `3700(인천형 청년월세 지원사업)` 의 뒤집힌 `min_age/max_age=35/34` row를 `35/39` 로 복구했다.
  - 기존 legacy row 분포를 먼저 보고 싶으면 아래 sweep SQL을 쓴다.

```sql
SELECT id, source_type, source_id, title, min_age, max_age
FROM welfare_services
WHERE min_age IS NOT NULL
  AND max_age IS NOT NULL
  AND min_age > max_age
ORDER BY id;
```

  - `2026-05-29` local sweep 기준 이 inverted row는 `3700` 한 건이 아니라 `YOUTH`, `BOKJIRO_LOCAL(2726, 3197)`, `GOV24` 전반에 더 남아 있었다. 즉 sourceId self-heal은 특정 row 복구용이고, 전체 backfill은 별도 작업으로 읽는 편이 맞다.
  - 현재 whole-batch repair lane은 `POST /api/admin/collect/inverted-age-backfill?sourceType=<선택>&limitPerSource=0` 이다. 이 경로는 외부 API를 다시 치지 않고 저장돼 있던 `DETAIL raw_api_payloads` 를 replay해 `welfare_services` 와 `service_facts` 의 age range를 다시 맞춘다.
  - source별 backfill, 남은 `GOV24/BOKJIRO_*` row의 `sourceId` 단건 self-heal, 복지로 detail coverage audit를 한 번에 보고 싶으면 `bash deploy/smoke/run-local-collect-legacy-repair-suite.sh` 를 쓴다. 기본값은 `source별 backfill -> 남은 row targeted repair -> 복지로 detail coverage summary` 까지이고, `RUN_GAP_FILL=true` 를 줄 때만 `2 rounds x 20 calls` 기본 gap-fill을 추가로 태운다.
  - `2026-05-29` local rerun에서는 이 wrapper가 `GOV24 before=117, repaired=117, after=0` 으로 끝났고 `remaining_inverted_rows=(none)` 을 반환했다. 즉 current local truth 기준 legacy inverted-age backlog는 whole-suite 한 번으로 closeout 상태다.
  - 응답 해석:
    - `repairedCount`: replay 뒤 정상 range로 복구된 row 수
    - `missingRawPayloadCount`: detail raw가 없어 replay 자체를 못 한 row 수
    - `unrepairedCount`: replay는 했지만 row가 여전히 `min_age > max_age` 인 row 수
    - `failedCount`: raw parse 또는 apply 중 예외가 난 row 수
  - current source별 bounded repair 전략:
    - `BOKJIRO_LOCAL/BOKJIRO_CENTRAL`: `POST /api/admin/collect/bokjiro-details-refresh?sourceId=<복지로 서비스ID>`
    - `GOV24`: `POST /api/admin/collect/gov24-details?sourceId=<Gov24 서비스ID>`
    - `YOUTH`: current code 기준 `sourceId` 단건 detail repair lane은 여전히 없지만, broad raw replay backfill(`inverted-age-backfill`) 은 now supported 한다. 외부 API를 다시 부를 필요가 없으면 이 경로를 먼저 쓴다.
  - stored detail payload coverage 가 낮아 sidecar density가 detail raw 개수에 묶여 있을 때만 gap fill 경로를 써서 여러 라운드 backlog 를 메운다.
  - 기존 raw payload 로 sidecar를 다시 채우거나 density를 재측정할 때만 backfill 경로를 쓴다.
  - 특히 Gov24 상세에서 `gov24ServiceFieldLabel/userType/benefitType` 같은 summary label이 비는 소수 row drift는 `gov24-sidecars-backfill` 로 먼저 메운다.

### 5-1. Gov24는 list/detail/support 수동 경로를 분리해서 보되, manual list는 새 backlog follow-up을 같이 태운다

- `/api/admin/collect/gov24` 는 Gov24 목록(list) 수집 경로다.
- 현재 manual `gov24` 경로는 목록 저장이 끝나면 같은 요청 안에서 `gov24-details`, `gov24-support-conditions` 를 기본 chunk 설정(`50`)으로 한 번씩 더 태워, 새로 들어온 Gov24 row의 detail/support backlog를 자동으로 좁힌다.
- `/api/admin/collect/gov24-details` 는 Gov24 상세(detail) 수집 경로다.
- `/api/admin/collect/gov24-support-conditions` 는 Gov24 지원조건(supportConditions) 수집 경로다.
- `gov24-details`, `gov24-support-conditions` 는 둘 다
  - `?maxCallsPerRun=<N>` 으로 chunk 크기를 조절할 수 있고
  - `?sourceId=<서비스ID>` 로 단건 재시도를 할 수 있다.
- 운영 해석:
  - 목록 closeout은 `gov24`
  - 상세 coverage 확장은 `gov24-details`
  - 지원조건/fact coverage 확장은 `gov24-support-conditions`
  로 분리해서 본다.
  - 다만 manual `gov24` 는 새 list 유입 뒤 `detail/support` count가 바로 벌어지는 것을 줄이기 위해 follow-up 1라운드를 같이 돈다.
  - 상세나 지원조건의 transient upstream 실패를 재확인할 때만 `sourceId` 단건 경로를 쓴다.
  - `gov24-details?sourceId=` 는 current local 기준 age self-heal lane으로도 동작한다. `supportTarget/selectionCriteria` 에서 `만 39세 이하 청년` 같은 max-only youth 문구가 오면 `18~39` range fact를 다시 만들고, 뒤집힌 `min_age/max_age` row를 복구한다.

### 5-2. 현재 복지로는 4개 독립 quota 기준으로 coverage 확장을 다시 기본 작업으로 본다

- 2026-05-10 기준 복지로 운영 계정을 확보했고, `중앙 list`, `중앙 detail`, `지자체 list`, `지자체 detail` 이 각각 일일 `100,000` quota를 사용한다.
- 기본 수집 안전 상한은 현재 코드 기본값 기준:
  - 중앙 list `1회 10,000 items`
  - 지자체 list `1회 10,000 items`
  - 중앙 detail `1회 10,000 calls`
  - 지자체 detail `1회 10,000 calls`
- 따라서 detail coverage 부족분은 더 이상 개발 계정 quota만으로 설명하지 않고, backlog drain 속도와 저장 품질을 함께 본다.
- 운영 해석:
  - 기본 수집은 missing detail backlog 를 빠르게 줄이는 경로다.
  - refresh 는 기존 row 재동기화가 필요할 때 다시 연다.
  - gap fill 은 남은 backlog 를 라운드 단위로 밀어내는 coverage 확장 경로다.

### 6. 복지로 상세 호출 budget 은 source별로 독립 cap 을 가진다

- `collectBokjiroDetailsResult()` 는 중앙/지자체 상세를 shared pool로 나누지 않고, source별 독립 상한으로 돈다.
- 현재 코드 기본값은 `중앙 detail 10,000 calls`, `지자체 detail 10,000 calls` 이다.
- `collectBokjiroDetailsResult(maxCalls)` / `collectBokjiroDetailGapFillResult(rounds, maxCallsPerRound)` 의 `maxCalls*` 값도 total budget이 아니라 source별 override 로 해석한다.
- 다만 현재 admin 수동 진입점은 별도 운영 상한을 둔다.
  - `collect/{sourceKey}?maxCallsPerRun=` 는 `5000` 초과 불가
  - `bokjiro-details-gap-fill?maxCallsPerRound=` 는 `1000` 초과 불가
  - `rounds` 는 `10` 초과 불가
- 이유:
  - 공공데이터포털 운영 계정 quota가 `중앙 detail` 과 `지자체 detail` 에서 서로 독립이기 때문이다.
  - one source backlog가 커도 다른 source quota를 같이 깎아 먹지 않게 해야 한다.

---

## 장애 판단 기준

### 경고로 처리

- 목록 source(`youth`, `bokjiro-central`, `bokjiro-local`, `gov24`) 수집 결과가 0건
- 상세 수집 중 일부 429 발생
- 일부 정책 저장 실패
- 일부 상세 저장 실패
- 운영 계정 quota 상향 뒤에도 복지로 detail backlog 가 비정상적으로 줄지 않음

### 실제 장애로 판단

- 앱이 수집 요청 자체를 처리하지 못함
- 목록 source(`youth`, `bokjiro-central`, `bokjiro-local`, `gov24`) 수집이 연속해서 여러 배치 동안 0건
- 수집 API 응답은 200인데 DB 반영이 전혀 일어나지 않음
- 전체 수집이 반복적으로 중간 종료되며 로그가 남지 않음
- `gov24-details`, `gov24-support-conditions`, `bokjiro-details-gap-fill` 같은 closeout lane이 backlog 미종료 상태인데도 반복적으로
  - `requested=0`
  - `saved=0`
  - `skipped_count` 증가 없음
  으로 멈춤

---

## 운영 로그에서 봐야 할 항목

- `수집 실행 시작/종료`
- `source별 수집 완료 건수`
- `FieldQuality`
- `saved / skip / filtered`
- `requested / failed / skipped_count`
- `429 재시도 로그`
- `429로 수집 중단 로그`
- `목록 source 수집 결과 0건, 기존 데이터 유지` 경고

## DB에 저장되는 수집 실행 로그

수집 source별 실행 결과는 `api_sync_logs` 테이블에 저장한다.

주요 상태:

- `RUNNING`: 수집 시작 직후
- `SUCCESS`: source 수집이 실패 건수 없이 완료
- `PARTIAL_SUCCESS`: 일부 아이템 저장 실패가 있었지만 source 수집은 완료
- `FAILED`: 외부 API 예외 등으로 source 수집이 중단

현재 PostgreSQL mainline에서도 `api_sync_logs.status` 저장값은 소문자(`running`, `success`, `partial_success`, `failed`, `skipped`)로 보일 수 있다.

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
- 목록 수집: 매일 `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`, `GOV24`
- 상세 수집: list diff forced 후보 우선 + 요일별 호출 상한 적용
- 무중단 배포는 현재 우선순위 아님
- 운영상 변경은 사용자 적은 시간대에 공지 후 반영

---

## 향후 개선 후보

- Redis 기반 분산 락으로 멀티 인스턴스 대응
- 429 발생 시 다음 실행 시점까지 source 단위 쿨다운
- 소스별 마지막 성공 시각/마지막 성공 건수 대시보드화
- 운영 계정 quota(`중앙/지자체 각각 100,000`) 기준 복지로 detail gap fill / refresh 실표본 재검증
- list diff snapshot을 admin collect lane inventory에 직접 노출

## 운영 baseline wrapper

수집 lane 운영 surface를 서버에서 다시 볼 때는 아래 wrapper를 같이 씁니다.

- [ops-baseline-runbook.md](../core/ops-baseline-runbook.md)
- `bash deploy/smoke/run-local-ops-baseline-suite.sh`

이 wrapper는

- health
- admin dashboard summary
- admin `collect-failures`
- admin recommendation breakdowns

를 한 번에 다시 확인합니다.
