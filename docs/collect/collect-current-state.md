# 수집 현재 동작 기준

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

관련 문서:

- [collect-ops.md](./collect-ops.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [phase-plan.md](../phase-plan.md)

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

### 4. 복지로 coverage 는 운영 계정 4개 quota 기준으로 다시 해석

- 2026-05-10 기준 복지로 운영 계정을 확보했고, `중앙 list`, `중앙 detail`, `지자체 list`, `지자체 detail` 은 각각 일일 `100,000` quota를 사용한다.
- 현재 코드 기본값은 복지로 list source별 `1회 10,000 items`, detail source별 `1회 10,000 calls` 안전 상한이다.
- detail 호출 pacing 기본값은 `300ms`, 연속 `429` 임계치는 `2회`다.
- 따라서 detail backlog 는 더 이상 개발 계정 quota 때문에 남겨둔 의도된 상태로만 보지 않는다.
- coverage 완전성 평가는 이제 운영 계정 기준 gap-fill / refresh 결과까지 포함해 본다.

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
- 복지로 detail coverage 가 운영 계정 quota 상향 뒤에도 비정상적으로 backlog 를 남긴 채 유지

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

## 온통청년 DETAIL 수집 분리 운영

### 배경

온통청년 LIST API(`pageType=1`)는 `refUrlAddr1`, `refUrlAddr2` 필드를 응답에 포함하지 않는다.
해당 필드는 DETAIL API(`pageType=2`)에서만 반환된다.

LIST API 응답 필드: `aplyUrlAddr` 포함, `refUrlAddr1`/`refUrlAddr2`/`sbizCd` **없음**
DETAIL API 응답 필드: `aplyUrlAddr` + `refUrlAddr1` + `refUrlAddr2` + `sbizCd` **있음**

초기 수집(LIST만)에서 링크 없는 정책이 68%(1,754건)였던 원인이 여기에 있다.

### refUrlAddr1/refUrlAddr2 주의사항

이 필드는 API 명세상 "참고 URL"로 신청 URL이 아닐 수 있다.
실제 데이터 기준으로 아래가 혼재한다:

- 실제 신청/프로그램 상세 페이지 (유용)
- 기관 메인 홈페이지
- 뉴스 기사, SNS(인스타그램, 블로그) 등

따라서 `detail_url`로 저장은 하되, 프론트에서 버튼 문구를 "신청하기"가 아닌 **"관련 사이트 보기"** 로 표기한다.
URL 우선순위: `aplyUrlAddr` → `refUrlAddr1` → `refUrlAddr2`

### 수집 구조

- `YouthDetailCollectService.collectYouthDetails()`
- `POST /api/admin/collect/youth-details` (수동 트리거)
- `welfare_service_details` row가 없는 YOUTH 서비스만 대상으로 처리
- raw payload는 `raw_api_payloads` (`api_category = DETAIL`)에 보존
- 수집 간격: 500ms/건 (온통청년 API rate limit 고려)

### 수집 결과 기준

- DETAIL 수집 후 링크 있음: 2,012건 (78.3%, 2,570건 기준)
- 링크 없는 558건은 DETAIL API에도 URL 필드가 없는 경우로 해결 불가

### 한계

- 403 등 API 오류 발생 시 해당 건만 skip하고 수집 계속 진행
- 재수집 시 이미 `welfare_service_details`가 있는 건은 skip (중복 방지)
- 558건은 온통청년 API 자체에 어떤 URL도 없어 현재로서는 불가

---

## 온통청년 지역 코드 처리 방식 (2026-05-03 수정)

### 배경

온통청년 API는 “전국 노출” 설정 정책에 255개 시군구 코드를 모두 `zipCd` 필드에 부여한다.
이 데이터를 그대로 저장하면 서산시 정책이 서울 지역 필터에도 노출되는 문제가 발생했다.

### 수정 내용

**`WelfareServiceMapper.regionsFromYouth()`**

`zipCd`에 포함된 시도 수가 15개 이상이면 전국 마커로 판단하고,
`zipCd` 코드 대신 `host_org`(주관기관명)으로 실제 운영 지역을 추정한다.

**`RegionCodeUtil.inferFromHostOrg()`**

host_org에서 지역 코드를 추정하는 순서:

1. 전국 고유 시군구명 포함 → 해당 5자리 코드 반환 (예: “서산시청” → 44210)
2. 시도명 포함 → 해당 시도 전체 코드 반환 (예: “충청남도청” → 충남 전체)
3. 매핑 불가 → 빈 리스트 → `service_regions` 행 없음 → NOT EXISTS로 전국 노출

### 한계

- **중앙부처 주관 + 지역 한정 정책**: host_org가 “고용노동부” 등 중앙부처면 추정 불가 → 전국 노출로 처리됨
- 온통청년 API 자체에 명확한 지역 구분 필드가 없어 현재로서는 이 방식이 최선

### 적용 방법

코드 수정 후 `POST /api/admin/collect/youth`로 재수집하면 반영된다.
기존 DB 데이터(이전 수집분)는 재수집 시 `source_id` 기준 upsert로 자동 교정된다.

---

## 요약

1. collect는 adapter registry 기반으로 돈다.
2. 저장 레이어는 `welfare_services`, `raw_api_payloads`, `sidecars`, `api_sync_logs` 네 축이다.
3. 부분 성공과 외부 변동성은 정상 범주로 본다.
4. 진짜 장애는 “로그 없음”, “반영 없음”, “연속 실패” 쪽이다.
5. collect 성공과 downstream 재현은 분리해서 봐야 한다.
6. 현재 복지로 detail coverage 부족분은 운영 계정 quota 상향 뒤에도 남는 backlog 인지, 저장/파싱/재시도 경계 문제인지 같이 구분해서 본다.
