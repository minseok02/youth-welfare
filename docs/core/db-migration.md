# DB Migration Guide

전체 cross-cutting schema / migration 문서 진입점은 [system-docs-index.md](./system-docs-index.md)를 먼저 봅니다.
현재 프로젝트는 신규 DB 초기화는 `backend/src/main/resources/db/schema.sql`로 처리하고, 기존 DB 갱신은 수동 마이그레이션 SQL로 처리한다.
현재는 운영 서버가 없으므로, 이 문서는 로컬/테스트 DB 기준 migration 메모로만 본다.

## 대상

- 이미 생성되어 데이터가 있는 MySQL
- `schema.sql`이 컨테이너 최초 기동 시점에만 적용된 환경

## 최신 마이그레이션

- draft 파일: [`backend/src/main/resources/db/migration-draft/V2026_04_30_01__create_policy_sidecars.sql`](../backend/src/main/resources/db/migration-draft/V2026_04_30_01__create_policy_sidecars.sql)
- 포함 내용:
  - `normalization_code_sets`, `normalization_codes` 생성 초안
  - `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 생성 초안
  - `service_facts.fact_merge_key` 유니크 키와 sidecar FK/인덱스 초안
  - collect writer 는 이제 이 draft 스키마가 존재하면 실제 `service_taxonomies / service_taxonomy_terms / service_facts` upsert 를 수행하고, 테이블이 없으면 안전하게 skip 한다
  - 아직 `schema.sql` / 정식 `db/migration/` / read-model 전환이 끝나지 않아 `db/migration/` 이 아닌 `db/migration-draft/` 에만 두고 실제 적용 대상에서는 제외
  - `service_taxonomy_terms.term_code` 는 MySQL nullable unique semantics를 피하려고 코드가 없을 때 `''` 로 normalize 하는 안을 포함
  - `service_taxonomies` 가 여전히 legacy summary row 중심이므로, generic summary slot 병행 저장 구조는 [policy-normalization-summary-slot-storage-plan.md](../history/policy/policy-normalization-summary-slot-storage-plan.md) 기준으로 후속 draft migration으로 분리한다

- draft 파일: [`backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql`](../backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql)
- 포함 내용:
  - `SYSTEM_COMPAT_UNIFIED_CATEGORY`, `YOUTH_MAJOR` 중심의 최소 대표 `normalization_codes` seed 초안
  - `YOUTH_MID`, `GOV24_*` 는 공식 코드 import 전 단계라 metadata set만 먼저 생성
  - 현재 `welfare_services` 기반 `service_taxonomies` summary backfill 초안
  - `YOUTH category_main` 은 raw 문자열을 그대로 복사하지 않고 `JSON_TABLE` split + punctuation normalize 후 single canonical major로 collapse 가능한 경우에만 `youth_major_*` summary 를 채움
  - `YOUTH_MID` / `GOV24` 공식 코드 전체 import 와 `service_taxonomy_terms` / `service_facts` backfill 은 후속 task로 분리

- draft 파일: [`backend/src/main/resources/db/migration-draft/V2026_04_30_03__seed_policy_official_code_subsets.sql`](../backend/src/main/resources/db/migration-draft/V2026_04_30_03__seed_policy_official_code_subsets.sql)
- 포함 내용:
  - 온통청년 공개 코드정의서에서 stable code 값이 실제로 보이는 집합(`YOUTH_PROVIDER_GROUP`, `YOUTH_PROVISION_METHOD`, `YOUTH_*_REQUIREMENT`, `YOUTH_MARITAL_STATUS`, `YOUTH_INCOME_CONDITION_TYPE`)의 `normalization_codes` seed 초안
  - Gov24 `supportConditions` 에서 현재 조사로 근거가 확인된 대표 공식 코드(`JA0101`, `JA0102`, `JA0110`, `JA0111`, `JA0201~JA0205`, `JA0320`, `JA0327`, `JA0412`) seed 초안
  - `YOUTH_MID` 는 stable code 없이 official label/정렬만 공개된 상태라 `normalization_codes` 는 비우고, 기존 `welfare_services.category_sub` 를 쉼표 기준 split/trim 한 뒤 official label과 exact match 하는 term만 `service_taxonomy_terms(term_code='')` 로 backfill 하는 초안
  - 로컬 DB 검증 기준 `YOUTH.category_sub` split token은 official 17개 라벨에 수렴하지만 `온·오프라인교육`, `문화활동 및 생활지원` 두 variant가 추가로 보여, 이 값들은 canonical `YOUTH_MID` 에 넣지 않고 future sidecar 에 `YOUTH_MID_RAW_ALIAS` term group으로 별도 보존하는 정책을 사용
  - `service_taxonomies.youth_mid_label` summary backfill 은 raw `category_sub` 전체를 넣지 않고, exact official 단일 label일 때만 채우며 comma 조합/variant 는 `NULL` 로 둔다
  - 공개 API HTML에서는 `srchPolyBizSecd=003002001,003002002` 예시만 보이고, `/sur/link/openApiIntro/46`, `/sur/link/openInfoChcApi` 는 비로그인 상태에서 `Unauthorized` 를 반환하므로 `YOUTH_MID` stable code import 는 authenticated testbed/live inventory 확보 전까지 보류
  - 2026-04-30 authenticated live 목록 응답을 실제로 다시 끝까지 스캔한 결과도 `srchPolyBizSecd` 필드는 payload에 직접 보이지 않았고, 대신 보인 `plcyMajorCd/jobCd/schoolCd/sbizCd` 는 broad/default-like 값과 multi-code가 섞여 있어 stable `YOUTH_MID` code 대체 축으로 쓰기 어려웠다. 세부 inventory는 [policy-normalization-youth-mid-live-inventory.md](../history/policy/policy-normalization-youth-mid-live-inventory.md)에 정리했다.
  - `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 는 field 자체는 확인됐지만 finite code inventory 를 확보하지 못해 이번 단계에서는 metadata/placeholder만 유지
  - 2026-05-01 기준 `GOV24` current public dataset/공지 재확인 결과, old `category` / `category-code` operation은 2021 개편 때 deprecated 되었고 current source-of-truth는 `serviceList` / `serviceDetail` / `supportConditions` 3종이다. 하지만 current public page text만으로는 `serviceField` / `userType` / `benefitType` finite inventory 가 드러나지 않아 import SQL은 계속 보류한다. 자세한 기준은 [policy-normalization-gov24-label-source-plan.md](../history/policy/policy-normalization-gov24-label-source-plan.md)에 정리했다.
  - `GOV24_SUPPORT_CONDITION` 도 현재는 대표 subset code만 공식 근거가 확인된 상태이고, full inventory/backfill은 current Swagger/schema export 또는 provider codebook 확보 전까지 보류한다. 자세한 기준은 [policy-normalization-gov24-support-condition-source-plan.md](../history/policy/policy-normalization-gov24-support-condition-source-plan.md)에 정리했다.

- draft 파일: [`backend/src/main/resources/db/migration-draft/V2026_05_02_01__add_service_taxonomy_summary_slots.sql`](../backend/src/main/resources/db/migration-draft/V2026_05_02_01__add_service_taxonomy_summary_slots.sql)
- 포함 내용:
  - `service_taxonomy_summary_slots` 생성 초안
  - canonical summary slot을 `slot_key / slot_code / slot_label` row로 병행 저장하기 위한 반복 테이블
  - `service_taxonomies` 는 당분간 legacy projection row로 유지하고, 이 테이블은 장기 canonical summary truth 후보로 분리
  - 최신 writer는 이 테이블이 존재하면 optional dual-write 를 수행하고, 없으면 기존과 같이 조용히 skip 한다
  - backfill / read-model 전환은 아직 열지 않았고, 현재 단계는 collect writer dual-write까지만 검증 범위다
  - 배경 설계는 [policy-normalization-summary-slot-storage-plan.md](../history/policy/policy-normalization-summary-slot-storage-plan.md)를 따른다

- draft 파일: [`backend/src/main/resources/db/migration-draft/V2026_05_02_02__backfill_service_taxonomy_summary_slots.sql`](../backend/src/main/resources/db/migration-draft/V2026_05_02_02__backfill_service_taxonomy_summary_slots.sql)
- 포함 내용:
  - 기존 `service_taxonomies` summary row를 `service_taxonomy_summary_slots` 로 재적재하는 local backfill 초안
  - `YOUTH_MAJOR`, `YOUTH_MID`, `GOV24_*`, `PROVISION_METHOD` managed slot만 재생성
  - 현재 local replay closeout에서는 dual-write 이전 snapshot을 이 SQL로 먼저 메운 뒤 density를 확인한다

- draft 파일: [`backend/src/main/resources/db/migration-draft/V2026_05_02_03__widen_service_taxonomy_summary_slot_label.sql`](../backend/src/main/resources/db/migration-draft/V2026_05_02_03__widen_service_taxonomy_summary_slot_label.sql)
- 포함 내용:
  - `service_taxonomy_summary_slots.slot_label` 을 `TEXT` 로 보정
  - 유니크 키에서 `slot_label` 을 제외하고 `(service_id, slot_key, slot_code, authority)` 로 재정의
  - 이유: `provision_method_label` 계열 장문 summary가 `slot_label` 길이 제한과 MySQL unique index 제약에 걸렸기 때문

## 로컬 draft sidecar smoke

로컬 Docker MySQL에서 draft sidecar 스키마와 실제 writer 정합성을 확인할 때는 아래 순서로 검증한다.

```bash
docker exec -e MYSQL_PWD="$DB_PASSWORD" -i youth-welfare-db mysql -uroot youth_welfare < backend/src/main/resources/db/migration-draft/V2026_04_30_01__create_policy_sidecars.sql
docker exec -e MYSQL_PWD="$DB_PASSWORD" -i youth-welfare-db mysql -uroot youth_welfare < backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql
docker exec -e MYSQL_PWD="$DB_PASSWORD" -i youth-welfare-db mysql -uroot youth_welfare < backend/src/main/resources/db/migration-draft/V2026_04_30_03__seed_policy_official_code_subsets.sql
docker exec -e MYSQL_PWD="$DB_PASSWORD" -i youth-welfare-db mysql -uroot youth_welfare < backend/src/main/resources/db/migration-draft/V2026_05_02_01__add_service_taxonomy_summary_slots.sql
docker exec -e MYSQL_PWD="$DB_PASSWORD" -i youth-welfare-db mysql -uroot youth_welfare < backend/src/main/resources/db/migration-draft/V2026_05_02_03__widen_service_taxonomy_summary_slot_label.sql
docker exec -e MYSQL_PWD="$DB_PASSWORD" -i youth-welfare-db mysql -uroot youth_welfare < backend/src/main/resources/db/migration-draft/V2026_05_02_02__backfill_service_taxonomy_summary_slots.sql

cd backend
./gradlew test --no-daemon --tests com.example.welfare.collect.normalization.NormalizedFactMergeSupportTest --tests com.example.welfare.collect.normalization.DeferredNormalizedPolicySidecarWriterTest
./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.NormalizedPolicySidecarPersistenceIntegrationTest
```

`NormalizedPolicySidecarPersistenceIntegrationTest` 는 local MySQL에서 실제 `CollectItemSaver.saveYouth(...)` refresh 경로를 두 번 태워 다음을 확인한다.

- `service_taxonomies` summary 가 최신 aggregate 기준으로 upsert 되는지
- `service_taxonomy_terms` 의 `YOUTH_MID` / `YOUTH_MID_RAW_ALIAS` / keyword refresh 가 실제 DB에서 교체되는지
- `service_facts` 가 같은 `fact_merge_key` 에 대해 stale 값을 남기지 않고 최신 값으로 갱신되는지

복지로 기존 raw payload를 다시 써서 sidecar를 채울 때는 `NormalizedPolicySidecarBackfillService` 초안을 사용한다. 이 서비스는 `raw_api_payloads` 의 `LIST` / `DETAIL` JSON 을 다시 읽어 canonical aggregate를 만들고 `NormalizedPolicySidecarWriter` 로 넘긴다. `BokjiroDetailClient.DetailPayload` 는 과거 JSON에 `empty=false` 가 남아 있어도 역직렬화되도록 `ignoreUnknown` 계약으로 맞췄다.

수동 실행이 필요하면 로컬/개발 환경에서 아래 admin endpoint를 사용한다.

```bash
curl -X POST "http://127.0.0.1:8082/api/admin/collect/bokjiro-sidecars-backfill?scope=all&limitPerSource=0" \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

2026-04-30 로컬 smoke 기준 draft 적용 직후 count:

```sql
SELECT COUNT(*) FROM normalization_code_sets;   -- 15
SELECT COUNT(*) FROM normalization_codes;       -- 78
SELECT COUNT(*) FROM service_taxonomies;             -- 3634
SELECT COUNT(*) FROM service_taxonomy_terms;         -- 2395
SELECT COUNT(*) FROM service_taxonomy_summary_slots; -- 5619
SELECT COUNT(*) FROM service_facts;                  -- 0
```

2026-04-30 `V2026_04_30_02__seed_policy_normalization_codes.sql` youth major summary collapse 검증:

```sql
-- local YOUTH snapshot 기준
SELECT 2299 AS youth_total, 2248 AS summary_filled, 0 AS summary_with_comma, 0 AS raw_variant_labels;
```

즉 draft SQL 의 `YOUTH_MAJOR` summary backfill 은 `금융･복지･문화`, `참여･기반` 같은 raw variant를 canonical label(`복지문화`, `참여권리`)로 normalize 하고, `일자리,일자리` 같은 duplicate multi-value 는 collapse 하며, `일자리,교육` 같은 multi-major case 는 summary 를 `NULL` 로 두는 방향으로 고정됐다.

2026-04-30 local stored 복지로 raw payload replay 후 count:

```sql
SELECT COUNT(*) FROM welfare_services WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL'); -- 1335
SELECT COUNT(*) FROM raw_api_payloads WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL') AND api_category = 'DETAIL'; -- 199
SELECT COUNT(*) FROM service_facts sf JOIN welfare_services ws ON ws.id = sf.service_id
WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL'); -- 103
SELECT COUNT(DISTINCT sf.service_id) FROM service_facts sf JOIN welfare_services ws ON ws.id = sf.service_id
WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL'); -- 103
```

현재 local snapshot에서는 `BK_AGE_ELIGIBILITY` 만 `104`건(`BOKJIRO_CENTRAL 52`, `BOKJIRO_LOCAL 52`) 적재됐고, `BK_APPLY_END_DATE` 는 `0`건이다. `TextConstraintExtractor` 보강 뒤 `bokjiro-details-gap-fill` 을 `2 rounds x 20 calls` 로 실제 실행하자 detail raw payload 는 `190 -> 199`, missing detail service 는 `1145 -> 1136`, `service_facts` 는 `99 -> 103` 으로 올라갔다. 이어 residual `만 15세~만 40세` 패턴 대응을 반영하고 stored raw payload replay 를 다시 태우자 `service_facts` 는 `103 -> 104` 로 한 건 더 늘었고, residual sample 이던 `청년내일저축계좌`(`service_id=2300`) 에도 `BK_AGE_ELIGIBILITY 15~40` 가 실제로 적재됐다. 다만 새 raw payload `9`건 중 age fact 증가는 `4`건, residual replay 추가 증가는 `1`건뿐이라 남은 갭은 extractor 하나보다 payload 자체 signal 부재와 sample 분포 영향이 더 크다.

추가 샘플링 결과 `detail raw payload 는 있지만 service_facts 는 없는 복지로 서비스` 는 `130`건(`BOKJIRO_CENTRAL 48`, `BOKJIRO_LOCAL 82`)이었다. 이 집합에서 `income-like` text signal 은 `40`, `date-like` 는 `2`, strict `age-like` residual candidate 는 `1`건(`청년내일저축계좌` 의 `만 15세~만 40세`) 수준이었다. 따라서 다음 보강 우선순위는 광범위한 age regex 확대보다 `second-bound 만` 패턴 처리와 `income-like` soft fact 전략 검토에 가깝다.

`second-bound 만` residual을 처리한 최신 snapshot 기준으로는 `detail raw payload 는 있지만 service_facts 는 없는 복지로 서비스` 가 `129`건(`BOKJIRO_CENTRAL 47`, `BOKJIRO_LOCAL 82`)이며, 이 중 income/beneficiary soft candidate pool 은 `49`건이다. 세부 구성은 `beneficiary_only 28`, `threshold_like 13`, `low_income_label 7`, `won_threshold 1`, `other 2` 였다. 특히 `threshold_like 13` 건에는 `신혼/2자녀/출산/맞벌이/우대형/일반형/개별심사` 같은 분기 케이스 `4`건, 다중 `% 이하` threshold `3`건, 다중 `만원 이하` threshold `1`건이 섞여 있어, 현재 단계에서 이를 canonical `INCOME_PCT` / `INCOME_WON` hard fact 로 직접 승격하면 의미 손실이 크다. 따라서 복지로 income-like signal 은 즉시 hard fact 로 올리지 않고, 후속 작업을 `beneficiary-like` soft taxonomy 와 `threshold-like` optional soft signal 설계로 분리한다.

`beneficiary_only` 집합은 별도 결정이 가능했다. local snapshot 재분류 기준 `기초생활수급자` 계열 `24`, `차상위계층` 계열 `5` 는 의미가 비교적 안정적이라 future canonical `TARGET_GROUP` soft taxonomy 후보로 본다. 반면 `취업취약계층`, `정보 소외계층`, `저소득 한부모가족` 같은 나머지 `6`건은 범위가 넓거나 다른 taxonomy 축과 겹쳐서 이번 단계의 whitelist 에 넣지 않는다. 이 선행조건이던 writer refresh contract 도 정리했고, 이제 sidecar writer는 `term_group` 전체가 아니라 incoming term의 `(term_group, source_field)` 조합만 refresh 하므로 복지로 list `TARGET_GROUP(source_field=trgterIndvdlNmArray)` 와 detail derived `TARGET_GROUP(source_field=targetDetail/selectionCriteria)` 를 같이 보존할 수 있다. 현재 `WelfareServiceMapper.toNormalizedBokjiroDetail()` 는 `targetDetail/selectionCriteria` 본문에서 `국민기초생활보장수급자`, `기초생활수급자`, `생계/의료/주거/교육급여 수급자`, `수급권자` 를 `기초생활수급자` 로, `차상위*` 표현을 `차상위계층` 으로 정규화해 detail aggregate `TARGET_GROUP` soft taxonomy term 으로 실제 적재한다.

반면 `threshold_like` (`13`건) 은 별도 정책으로 둔다. 이 집합은 `% 이하`, `만원 이하`, `신혼/맞벌이/우대형/일반형/개별심사` 같은 branch/context 가 섞여 있어 현재 단계에서 canonical `INCOME_PCT` / `INCOME_WON` hard fact 로 flatten 하지 않는다. 후속 저장이 필요하면 hard fact 가 아니라 raw/context 를 같이 보존하는 optional soft signal 계층으로만 검토한다. 세부 기준은 [policy-normalization-income-threshold-soft-signal-policy.md](../history/policy/policy-normalization-income-threshold-soft-signal-policy.md)를 따른다.

local draft migration 상태에서 stored 복지로 detail raw payload replay를 다시 실행해 보니 beneficiary-like candidate payload 는 `42`건이었고, replay 후 `service_taxonomy_terms(term_group='TARGET_GROUP', source_field='targetDetail/selectionCriteria')` 의 whitelist term 은 총 `59 rows / 42 services` 로 적재됐다. 라벨별로는 `기초생활수급자 38 rows / 38 services`, `차상위계층 21 rows / 21 services` 였다. 한 서비스가 두 beneficiary label을 동시에 가질 수 있으므로, beneficiary density는 앞으로도 `term row count` 와 `distinct service count` 를 같이 기록한다.

overlap service 는 `17`건이었고, 샘플은 `여성청소년 생리용품 지원`, `통합문화이용권`, `자활근로(기초, 차상위)`, `재난적의료비 지원 사업` 처럼 source 자체가 두 집단을 함께 명시한 경우가 대부분이었다. 따라서 현재 canonical 정책은 `기초생활수급자` 와 `차상위계층` 을 상하위 collapse 하지 않고 multi-term 으로 그대로 유지한다. 이 bucket은 hard fact 가 아니라 soft taxonomy 이므로, 추천/read-model 단계에서 필요하면 중복 가중치만 제어하고 원본 term 정보는 보존하는 쪽이 맞다.

추천/read-model 단계의 초기 dedupe 전략도 같이 고정했다. persistence 에는 raw `TARGET_GROUP` term 둘 다 남기고, canonical read-model 이 `BENEFICIARY_SUPPORT` bucket 을 별도로 만든 뒤 scoring 은 이 bucket 기준으로 서비스당 최대 1회만 bonus 를 주는 방식이다. 현재 [RuleScoringService](../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java) 도 boolean 매칭 기반이라 동일 축 중복 가산은 하지 않으므로, future sidecar read-path 도 같은 “max-one bonus” 규칙을 유지한다.

이 dedupe는 repository SQL에서 직접 collapse 하지 않고, `RecommendationCandidateProjection` 류의 canonical recommendation read-model projection 에서 raw `targetGroupsRaw` / `beneficiaryTerms` / `targetGroupBuckets` 를 함께 만드는 방식으로 분리한다. 즉 persistence 는 raw truth, projection 은 scoring-friendly view, response/UI 는 raw explanation 을 각각 따로 가진다.

추가 확인 결과 `targetDetail/supportDetail/selectionCriteria` 의 date-like token 수는 `4 / 1 / 1` 이었지만, 샘플은 출생연도 범위나 혜택 적용기간처럼 신청마감이 아닌 날짜가 대부분이었다. 따라서 현재 canonical collect path 에서는 `BK_APPLY_END_DATE` 를 optional fact 로 유지하고, `targetDetail/selectionCriteria` 까지 deadline fallback 을 넓히지 않는다.

2026-04-30 live/detail 재확인에서도 이 판단을 유지했다. local DB에 저장된 복지로 `DETAIL` raw payload key는 `targetDetail`, `supportDetail`, `applyMethodDetail`, `selectionCriteria`, `contactList`, `supportCycle`, `provisionType` 뿐이었고, `applyEndDate`, `aplyEndDt`, `deadline`, `rcptEndDt` 같은 explicit deadline key는 `0`건이었다. 같은 날 `BOKJIRO_API_KEY` 로 중앙/지자체 live detail endpoint를 직접 다시 호출하려 했지만 두 endpoint 모두 `HTTP 429` 로 막혀 신규 raw field inventory는 확보하지 못했다. public data.go.kr 설명도 detail API를 `eligibility / selection criteria / application procedures` 수준으로만 설명하고 있어, 현재 단계에서는 `BK_APPLY_END_DATE` 를 계속 optional fact로 유지하는 쪽이 맞다.

- 파일: [`backend/src/main/resources/db/migration/V2026_04_28_02__add_user_pii_sync_queue.sql`](../backend/src/main/resources/db/migration/V2026_04_28_02__add_user_pii_sync_queue.sql)
- 포함 내용:
  - `user_pii_sync_queue` 생성
  - request-path `user_pii` sync payload 와 상태(`PENDING/SYNCED/FAILED`) 저장
  - 향후 retry/admin replay 용 `attempt_count`, `last_error`, `last_*_at` 컬럼 추가

- 파일: [`backend/src/main/resources/db/migration/V2026_04_28_01__drop_runtime_legacy_user_id.sql`](../backend/src/main/resources/db/migration/V2026_04_28_01__drop_runtime_legacy_user_id.sql)
- 포함 내용:
  - `user_recommendations`, `recommendation_logs`, `notifications`, `chat_sessions`, `service_view_logs` 의 legacy `user_id` 컬럼 제거
  - `user_recommendations` 유니크 키를 `(user_key, service_id, recommended_at)` 기준으로 재구성
  - `recommendation_logs`, `notifications`, `chat_sessions` 의 `user_key NOT NULL` 제약 확정
  - runtime 테이블의 `user_id` 기반 인덱스/FK 제거 후 `user_key` 기반 인덱스만 유지

- 파일: [`backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql`](../backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql)
- 포함 내용:
  - `auth_users`, `user_profiles` 생성
  - `youth_welfare_pii.user_pii` 생성
  - 기존 `users` 기준 1회 backfill
  - `auth_users.email_lookup_hash`, `user_profiles.age/age_band/has_*` 파생값 채움
  - `user_pii` 는 migration 시점에는 `phone_enc` 만 backfill하고, 이후 최신 백엔드의 관리자 백필 API로 `email_enc/name_enc/birth_date_enc` 를 채우는 구조

- 파일: [`backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql`](../backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql)
- 포함 내용:
  - `users.user_key CHAR(32)` 추가 및 기존 사용자 deterministic hash backfill
  - `user_attributes`, `user_priorities`, `user_recommendations`, `recommendation_logs`, `notifications`, `chat_sessions`, `service_view_logs` 에 `user_key` nullable 컬럼 추가
  - 기존 `user_id -> users.user_key` 기준 backfill
  - PII 분리 1단계용 공용 사용자 식별자 호환 경로 준비

- 파일: [`backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql`](../backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql)
- 포함 내용:
  - `service_regions(service_id, sido_name, sgg_name)` 복합 인덱스 추가
  - `service_regions(service_id, region_code)` 복합 인덱스 추가
  - 지역 검색 `EXISTS` 서브쿼리와 추천 지역 후보 판정 비용 완화

- 파일: [`backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql`](../backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql)
- 포함 내용:
  - `chat_sessions` 생성
  - `chat_messages` 생성
  - 챗 세션/메시지 기본 인덱스 추가
  - 로그아웃/회원탈퇴 시 `users -> chat_sessions -> chat_messages` cascade delete 준비

- 파일: [`backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql`](../backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql)
- 포함 내용:
  - `welfare_services.search_youth_relevant` 컬럼 추가
  - `idx_ws_search_youth` 인덱스 추가
  - 검색용 청년 관련성 플래그 기반 SQL 필터 준비

- 파일: [`backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql`](../backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql)
- 포함 내용:
  - `api_sync_logs` 생성
  - source별 수집 실행 상태와 저장/스킵/필터/실패 건수 기록

- 파일: [`backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql`](../backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql)
- 포함 내용:
  - `service_view_logs` 생성
  - `notifications`, `notification_services` 생성
  - `notifications.retry_count`, `notifications.next_retry_at` 추가
  - `idx_noti_retry` 인덱스 추가

- 파일: [`backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql`](../backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql)
- 포함 내용:
  - `raw_api_payloads` 생성
  - 공공 API 목록/상세 원문 payload 보관

## 적용 전 체크리스트

- 현재 저장소는 Flyway 자동 적용이 없으므로, 기존 Docker DB/운영 DB/통합 테스트용 고정 DB에는 새 migration SQL을 수동 적용해야 한다.
- 최신 백엔드가 `user_recommendations`, `recommendation_logs`, `notifications`, `chat_sessions`, `service_view_logs` 를 모두 `user_key` 기준으로 읽고 쓰는지 확인
- `user_attributes`, `user_priorities` 의 저장/삭제 경로가 더 이상 `userId` 에 의존하지 않는지 확인
- 최신 백엔드가 request-path `user_pii` sync 를 `user_pii_sync_queue -> app_pii_rw` 경로로 수행하는지 확인
- `schema.sql` 과 엔티티 `@Table(indexes=...)` 정의를 drop 후 구조와 같이 수정
- 운영 DB에서 `user_key IS NULL` row 가 없는지 확인
- 운영 DB에 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정과 권한이 준비됐는지 확인

## 적용 후 검증 쿼리

```sql
SHOW TABLES LIKE 'user_pii_sync_queue';
DESCRIBE user_pii_sync_queue;
SELECT status, COUNT(*) AS cnt
FROM user_pii_sync_queue
GROUP BY status;
```

```sql
SHOW COLUMNS FROM user_recommendations LIKE 'user_id';
SHOW COLUMNS FROM recommendation_logs LIKE 'user_id';
SHOW COLUMNS FROM notifications LIKE 'user_id';
SHOW COLUMNS FROM chat_sessions LIKE 'user_id';
SHOW COLUMNS FROM service_view_logs LIKE 'user_id';
SHOW INDEX FROM user_recommendations WHERE Key_name IN ('uq_ur_user_key_service_time', 'idx_ur_user_key_score', 'idx_ur_user_key_bookmark');
SHOW INDEX FROM recommendation_logs WHERE Key_name = 'idx_rl_user_key_sent';
SHOW INDEX FROM notifications WHERE Key_name = 'idx_noti_user_key_created';
SHOW INDEX FROM chat_sessions WHERE Key_name IN ('idx_cs_user_key_last_message', 'idx_cs_user_key_created');
SHOW INDEX FROM service_view_logs WHERE Key_name = 'idx_svl_user_key_service_viewed';
```

## 적용 방법

최신 로컬 수동 리허설 기준으로, `V2026_04_28_01__drop_runtime_legacy_user_id.sql` 과 `V2026_04_28_02__add_user_pii_sync_queue.sql` 는 pre-28 schema 상태에서 `V2026_04_28_01 -> V2026_04_28_02` 순서로 적용/검증했다.

```bash
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_28_01__drop_runtime_legacy_user_id.sql
mysql -h 127.0.0.1 -P 3307 -u "$DB_MIGRATION_USERNAME" -p"$DB_MIGRATION_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_28_02__add_user_pii_sync_queue.sql
```

도커 컨테이너를 쓰는 경우:

```bash
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_28_01__drop_runtime_legacy_user_id.sql
docker exec -e MYSQL_PWD="$DB_MIGRATION_PASSWORD" -i youth-welfare-db mysql -u"$DB_MIGRATION_USERNAME" youth_welfare < backend/src/main/resources/db/migration/V2026_04_28_02__add_user_pii_sync_queue.sql
```

인덱스를 추가한 뒤에는 통계를 한 번 갱신한다.

```sql
ANALYZE TABLE service_regions;
```

마이그레이션 후 검색용 청년 플래그를 실제 규칙으로 다시 계산한다.

```bash
curl -X POST http://127.0.0.1:8082/api/admin/policies/search-youth-relevance/rebuild
```

주의:

- 컬럼 기본값은 `1`이라 마이그레이션 직후 기존 데이터는 모두 검색 후보로 남아 있다.
- 백엔드 최신 코드 배포 후 위 백필 호출까지 끝나야 실제 청년 필터 기준 검색 성능과 결과가 맞는다.
- 응답 본문에는 `processedCount`, `updatedCount`, `relevantCount`, `excludedCount`가 포함된다.

## 앱 레벨 PII backfill 실행

최신 백엔드 배포 후 `user_pii.email_enc/name_enc/birth_date_enc` 는 관리자 API로 채운다.

```bash
curl -X POST http://127.0.0.1:8082/api/admin/users/pii-backfill \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

응답 본문:

- `processedCount`: 누락 암호문이 있어 검사한 `user_pii` row 수
- `updatedUserCount`: 실제로 하나 이상 암호문을 채운 사용자 수
- `emailBackfilledCount`, `nameBackfilledCount`, `birthDateBackfilledCount`: 필드별 채운 건수
- `skippedCount`: 원본 `users.email/name/birth_date` 가 비어 있어 채우지 못한 row 수

## 앱 레벨 metadata `user_key` backfill 실행

최신 백엔드 배포 후 `user_attributes`, `user_priorities` 의 `user_key` 누락 row는 관리자 API로 채운다.

```bash
curl -X POST http://127.0.0.1:8082/api/admin/users/metadata-user-key-backfill \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

응답 본문:

- `processedCount`: `user_key` 누락 상태로 검사한 `user_attributes + user_priorities` row 수
- `updatedRowCount`: 실제로 `user_key` 를 채운 전체 row 수
- `attributeUpdatedCount`: `user_attributes.user_key` 채운 row 수
- `priorityUpdatedCount`: `user_priorities.user_key` 채운 row 수

## `user_pii_sync_queue` admin replay 실행

최신 백엔드 배포 후 `user_pii_sync_queue` 의 실패 row 또는 특정 사용자 row는 관리자 API로 다시 반영할 수 있다.

특정 사용자 재처리:

```bash
curl -X POST "http://127.0.0.1:8082/api/admin/users/pii-sync-replay?userKey=<USER_KEY>" \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

실패 row + 대기 row batch 재처리:

```bash
curl -X POST "http://127.0.0.1:8082/api/admin/users/pii-sync-replay?limit=100" \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

응답 본문:

- `attemptedCount`: 이번 replay에서 실제로 processor를 태운 queue row 수
- `syncedCount`: replay 후 `SYNCED` 로 끝난 row 수
- `failedCount`: replay 후에도 `FAILED` 로 남은 row 수
- `missingCount`: replay 요청 시점 대비 queue row가 없어 처리하지 못한 수

## `user_pii_sync_queue` 상태 조회 / 운영 기준

최신 백엔드 배포 후 운영자는 queue 적체와 실패 상태를 아래 API로 확인할 수 있다.

```bash
curl "http://127.0.0.1:8082/api/admin/users/pii-sync-status?failedSampleLimit=5" \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

응답 본문:

- `pendingCount`: 아직 `app_pii_rw` 반영이 끝나지 않은 queue row 수
- `failedCount`: 자동 retry 후에도 `FAILED` 로 남은 row 수
- `syncedCount`: 최근까지 `SYNCED` 상태인 row 수
- `oldestPendingUserKey`, `oldestPendingEnqueuedAt`: 가장 오래된 대기 row
- `oldestFailedUserKey`, `oldestFailedAttemptAt`: 가장 오래된 실패 row
- `latestSyncedAt`: 가장 최근 성공 반영 시각
- `failedSamples`: `attemptCount` 높은 순 실패 sample 목록. `failedSampleLimit` 는 1~20으로 제한

권장 운영 기준:

- `failedCount > 0` 이면 `failedSamples[*].lastError` 를 먼저 확인하고 수동 replay 또는 DB 연결 점검
- `oldestPendingEnqueuedAt` 가 5분 이상 오래됐으면 after-commit listener 또는 `app_pii_rw` 연결 이상 여부 확인
- `oldestFailedAttemptAt` 가 10분 이상 오래됐으면 자동 retry만으로 해소되지 않는 장애로 보고 운영 개입
- `failedSamples[*].attemptCount >= 5` row 는 반복 실패 payload로 보고 우선 조사

회원가입 -> 로그인 -> 프로필 수정 -> queue `SYNCED` 까지 한 번에 확인하려면 아래 smoke 스크립트를 사용한다.

```bash
ENV_FILE=.env APP_BASE_URL=http://127.0.0.1:8082 \
  deploy/smoke/user-pii-sync-cutover-smoke.sh
```

- `.env` 의 JDBC URL query string에 `&` 가 포함되므로 shell `source .env` 대신 `ENV_FILE=.env ...` 형태를 기준으로 사용한다.
- local fresh init + 앱 기동 + smoke를 같이 태우려면 `SMOKE_RESET_DB=true deploy/smoke/run-local-pii-sync-cutover-smoke.sh` 를 사용한다.
- `APPLY_PII_SYNC_QUEUE_MIGRATION=true` 를 주면 `V2026_04_28_02__add_user_pii_sync_queue.sql` 적용까지 같이 수행한다.
- query 계정은 `DB_QUERY_USERNAME` / `DB_QUERY_PASSWORD` 로 override 할 수 있고, 미지정 시 `DB_MIGRATION_*`, 다시 미지정이면 `DB_USERNAME` / `DB_PASSWORD` 로 fallback 한다. `ENV_FILE=.env` 와 같이 써도 explicit override가 우선한다.
- `app_core_rw` 의 `user_pii` 권한을 회수한 뒤에는 cross-schema 확인 쿼리에 `migration_admin` 또는 `DB_QUERY_*` 로 넘긴 별도 점검 계정을 쓰는 것이 기준이다.
- 앱이 `AES_SECRET_KEY` 없이 떠 있으면 회원가입/프로필 수정 단계에서 `C002` 500으로 멈추므로, 운영 smoke 전 secret 주입 상태를 먼저 확인한다.
- `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 은 `youth_welfare_pii` schema를 가리켜야 한다. 같은 호스트를 쓰더라도 DB 이름까지 `DB_URL` 과 동일하게 두면 최소권한 계정에서 연결이 거부된다.

## 확인 쿼리

```sql
SHOW COLUMNS FROM notifications;
SHOW INDEX FROM notifications;
SHOW TABLES LIKE 'service_view_logs';
SHOW TABLES LIKE 'notification_services';
SHOW TABLES LIKE 'raw_api_payloads';
SHOW TABLES LIKE 'api_sync_logs';
SHOW TABLES LIKE 'chat_sessions';
SHOW TABLES LIKE 'chat_messages';
SHOW CREATE TABLE chat_sessions;
SHOW CREATE TABLE chat_messages;
SHOW TABLES LIKE 'auth_users';
SHOW TABLES LIKE 'user_profiles';
SHOW TABLES FROM youth_welfare_pii LIKE 'user_pii';
SHOW COLUMNS FROM auth_users;
SHOW COLUMNS FROM user_profiles;
SHOW COLUMNS FROM youth_welfare_pii.user_pii;
SELECT COUNT(*) AS auth_user_count FROM auth_users;
SELECT COUNT(*) AS user_profile_count FROM user_profiles;
SELECT COUNT(*) AS user_pii_count FROM youth_welfare_pii.user_pii;
SELECT COUNT(*) AS user_pii_missing_enc
FROM youth_welfare_pii.user_pii
WHERE email_enc IS NULL OR email_enc = ''
   OR name_enc IS NULL OR name_enc = ''
   OR birth_date_enc IS NULL OR birth_date_enc = '';
SELECT COUNT(*) AS user_pii_missing_enc_but_source_null
FROM users u
JOIN youth_welfare_pii.user_pii up ON up.user_key = u.user_key
WHERE (up.email_enc IS NULL OR up.email_enc = ''
    OR up.name_enc IS NULL OR up.name_enc = ''
    OR up.birth_date_enc IS NULL OR up.birth_date_enc = '')
  AND (u.name IS NULL OR u.birth_date IS NULL);
SELECT COUNT(*) AS auth_users_without_hash FROM auth_users WHERE email_lookup_hash IS NULL OR email_lookup_hash = '';
SELECT COUNT(*) AS user_profiles_without_user_key FROM user_profiles WHERE user_key IS NULL;
SELECT COUNT(*) AS user_pii_without_user_key FROM youth_welfare_pii.user_pii WHERE user_key IS NULL;
SHOW COLUMNS FROM users LIKE 'user_key';
SHOW INDEX FROM users WHERE Key_name = 'uq_users_user_key';
SHOW COLUMNS FROM user_attributes LIKE 'user_key';
SHOW COLUMNS FROM user_priorities LIKE 'user_key';
SHOW COLUMNS FROM user_recommendations LIKE 'user_key';
SHOW COLUMNS FROM recommendation_logs LIKE 'user_key';
SHOW COLUMNS FROM notifications LIKE 'user_key';
SHOW COLUMNS FROM chat_sessions LIKE 'user_key';
SHOW COLUMNS FROM service_view_logs LIKE 'user_key';
SELECT COUNT(*) AS users_without_user_key FROM users WHERE user_key IS NULL;
SELECT COUNT(*) AS attrs_without_user_key FROM user_attributes WHERE user_key IS NULL;
SELECT COUNT(*) AS priorities_without_user_key FROM user_priorities WHERE user_key IS NULL;
SELECT COUNT(*) AS recommendations_without_user_key FROM user_recommendations WHERE user_key IS NULL;
SELECT COUNT(*) AS logs_without_user_key FROM recommendation_logs WHERE user_key IS NULL;
SELECT COUNT(*) AS notifications_without_user_key FROM notifications WHERE user_key IS NULL;
SELECT COUNT(*) AS chat_sessions_without_user_key FROM chat_sessions WHERE user_key IS NULL;
SELECT COUNT(*) AS identified_view_logs_without_user_key
FROM service_view_logs
WHERE user_id IS NOT NULL
  AND user_key IS NULL;
SHOW INDEX FROM service_regions WHERE Key_name IN ('idx_sr_service_sido_sgg', 'idx_sr_service_region_code');
SHOW COLUMNS FROM welfare_services LIKE 'search_youth_relevant';
SHOW INDEX FROM welfare_services WHERE Key_name = 'idx_ws_search_youth';
SHOW INDEX FROM chat_sessions WHERE Key_name = 'idx_cs_user_last_message';
SHOW INDEX FROM chat_messages WHERE Key_name = 'idx_cm_session_created';
SELECT search_youth_relevant, COUNT(*) FROM welfare_services GROUP BY search_youth_relevant;
```

## 주의

- `schema.sql`은 신규 DB 초기화용이다. 기존 DB 갱신에는 자동 적용되지 않는다.
- 배포 전에 마이그레이션 SQL을 먼저 적용하고, 그 다음 백엔드를 올리는 순서로 진행한다.
- `users.user_key`는 migration 직후부터 새 가입에도 자동 채워지도록 `DEFAULT (REPLACE(UUID(), '-', ''))`를 사용한다.
- 기존 사용자 backfill은 `UUID()` 대량 UPDATE 대신 `SHA2(CONCAT('user:', id), 256)` 앞 32자 사용으로 고정했다. 단일 인스턴스뿐 아니라 binlog safety 경고가 있는 환경에서도 적용 가능하게 하기 위해서다.
- `V2026_04_27_02__add_user_key_columns.sql`은 세션 시작 시 `SET SESSION sql_log_bin = 0`을 실행한다. 현재 운영 절차처럼 root 또는 migration 전용 계정으로 수동 적용하는 것을 전제로 한다.
- `V2026_04_27_03__add_user_core_split_tables.sql`도 세션 시작 시 `SET SESSION sql_log_bin = 0`을 실행한다.
- 로컬 `docker compose` 신규 볼륨에서는 `deploy/mysql/init/z90-create-runtime-db-users.sh`가 위 계정을 자동 생성한다. 기존 운영 DB나 기존 Docker 볼륨은 이 스크립트가 재실행되지 않으므로 동일한 권한을 수동으로 맞춰야 한다.
- `user_pii` 의 `email_enc/name_enc/birth_date_enc` 는 migration SQL로 직접 채우지 않는다. 최신 백엔드의 `/api/admin/users/pii-backfill` 가 `AesEncryptUtil` 과 같은 경로로 채우는 것이 기준이다.
- `user_attributes`, `user_priorities` 의 `user_key` 도 migration backfill만으로 끝내지 않는다. migration 이후 JPA 저장 경로가 `user_key` 를 같이 쓰도록 최신 백엔드를 먼저 배포하고, 기존 누락 row는 `/api/admin/users/metadata-user-key-backfill` 로 마무리하는 것이 기준이다.
- `users.name` 또는 `users.birth_date` 가 이미 비어 있는 row는 앱 레벨 backfill 이후에도 남을 수 있다. 이 경우는 source 원문이 없는 상태라 `skippedCount` 로 기록하고 억지로 placeholder 값을 넣지 않는다.
- 현재 runtime 테이블(`user_recommendations`, `recommendation_logs`, `notifications`, `chat_sessions`, `service_view_logs`)은 `user_id` 호환 컬럼을 제거했다.
- `user_attributes`, `user_priorities` 는 아직 `user_id` 컬럼/FK를 유지한다. 이 둘은 `users` soft-delete/관리용 내부 연결을 남겨두되, read/write 경로는 `user_key` 기준으로 정리된 상태다.
