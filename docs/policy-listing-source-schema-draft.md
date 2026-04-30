# Listing형 Source 분리 스키마 초안

관련 문서:

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [phase-plan.md](./phase-plan.md)

## 목적

`고용24/워크넷 채용정보`, `마이홈포털 공공주택 모집공고/단지/예비입주자 대기현황` 같은 listing형 source를
`welfare_services` 에 억지로 넣지 않고 받을 최소 스키마 방향을 고정합니다.

이번 초안의 목표는:

1. 정책형 canonical row와 listing inventory row를 분리
2. 공통 listing 헤더와 source-specific detail 경계를 분리
3. 추천/북마크/CTR 의미가 정책형과 섞이지 않게 lane을 분리

## 결론

listing형 source는 1차에서 아래 구조로 받습니다.

- 공통 헤더: `listing_items`
- source-specific detail:
  - `job_listings`
  - `housing_recruitments`
  - `housing_complexes`
  - `housing_waitlist_stats`
- raw 보존: 기존 `raw_api_payloads` 재사용 가능

즉 “모든 listing을 한 상세 테이블에 flatten” 하지 않고,
공통 inventory header + source별 detail table 조합으로 갑니다.

## 왜 `welfare_services` 로 받지 않는가

listing형 row는 아래 속성이 정책형 row와 다릅니다.

- churn 이 빠름
- `공고/단지/회차/채용건` 같은 inventory grain 이 강함
- 북마크/추천/클릭 의미가 “정책 1건” 과 다름
- `기업/단지/공급회차/세대수/채용마감` 같은 source-specific field 비중이 큼

따라서 이 row를 `welfare_services + unifiedCategory + service_facts` 로 바로 밀어 넣으면:

- row grain 이 깨지고
- recommendation lane 이 섞이며
- `service_facts` 가 listing inventory 속성으로 오염됩니다.

## 스키마 원칙

### 1. 공통 헤더는 inventory 추적용으로만 둔다

`listing_items` 는 아래 질문만 공통으로 답하면 됩니다.

- 이 row가 어느 source/provider에서 왔는가
- 외부 ID가 무엇인가
- 제목/기관/지역/상태/링크/수집시각이 무엇인가
- 마지막으로 언제 봤는가

즉 정책 canonical의 `core/detail/taxonomy/facts` 를 재현하지 않습니다.

### 2. source-specific field는 별도 detail table로 뺀다

예:

- 채용공고의 `직무`, `고용형태`, `급여`, `근무지`
- 주택공고의 `공급유형`, `접수기간`, `세대수`, `주택형`
- 단지정보의 `단지명`, `주소`, `준공`, `총세대수`
- 대기현황의 `지역`, `단지`, `대기자수`, `갱신기준일`

이런 값은 공통 테이블에 nullable column으로 계속 늘리지 않습니다.

### 3. recommendation lane은 정책형과 분리한다

현재 1차에서는 listing형 source를:

- `RetrievalService.findCandidates*`
- `RuleScoringService`
- `user_recommendations`

에 직접 연결하지 않습니다.

필요하면 future에 별도 lane을 둡니다.

## 테이블 초안

## 1. `listing_items`

공통 inventory header입니다.

예상 컬럼:

- `id`
- `source_type`
- `listing_kind`
  - `JOB_POSTING`
  - `JOB_EVENT`
  - `HOUSING_RECRUITMENT`
  - `HOUSING_COMPLEX`
  - `HOUSING_WAITLIST_STATUS`
- `provider_name`
- `external_id`
- `title`
- `organization_name`
- `region_code`
- `region_label`
- `status`
  - `OPEN`
  - `CLOSED`
  - `UPCOMING`
  - `UNKNOWN`
- `open_at`
- `close_at`
- `detail_url`
- `raw_payload_id`
- `collected_at`
- `last_seen_at`
- `content_hash`

제약:

- unique: `(source_type, external_id)`
- index: `(listing_kind, status, close_at)`
- index: `(region_code, listing_kind, status)`

## 2. `job_listings`

`listing_items.id` 를 FK로 받는 job detail table입니다.

예상 컬럼:

- `listing_item_id`
- `employer_name`
- `job_title`
- `job_category`
- `employment_type`
- `salary_text`
- `salary_min`
- `salary_max`
- `work_region`
- `experience_requirement`
- `education_requirement`
- `recruit_count`
- `apply_method`

메모:

- `salary_*` 는 future filter 후보일 수 있지만, 지금은 policy fact로 승격하지 않습니다.
- `employment_type`, `job_category` 는 source-specific label로만 보존합니다.

## 3. `housing_recruitments`

공공주택 모집공고 detail table입니다.

예상 컬럼:

- `listing_item_id`
- `housing_type`
- `recruitment_round`
- `application_start_at`
- `application_end_at`
- `supply_household_count`
- `residence_area_text`
- `target_description`
- `announcement_number`

메모:

- 모집공고 row는 정책 제도 row가 아니라 공급 inventory row로 봅니다.
- `application_*` 은 listing open/close 의미이지 canonical `apply_end_date` 와 동일 취급하지 않습니다.

## 4. `housing_complexes`

공공임대주택 단지정보 detail table입니다.

예상 컬럼:

- `listing_item_id`
- `complex_name`
- `address_text`
- `housing_provider`
- `supply_type`
- `total_household_count`
- `move_in_period_text`
- `contact_text`

메모:

- 추천 카드보다는 검색/참조 inventory 성격이 강합니다.

## 5. `housing_waitlist_stats`

예비입주자 대기현황 detail table입니다.

예상 컬럼:

- `listing_item_id`
- `complex_name`
- `region_label`
- `waiting_household_count`
- `updated_date`
- `status_note`

메모:

- status feed 성격이므로 정책 추천 lane으로 올리지 않습니다.

## raw payload / collect 연계

listing형도 raw 보존은 계속 필요합니다.

기준:

- `raw_api_payloads` 는 계속 재사용 가능
- `listing_items.raw_payload_id` 로 최신 raw snapshot 을 가리킴
- source-specific detail table은 normalized projection 역할만 담당

즉 1차에서는:

- raw truth = `raw_api_payloads`
- listing inventory truth = `listing_items + detail table`
- 정책 canonical truth = `welfare_services + sidecars`

로 3층을 분리합니다.

## 정책형과의 연결을 지금 하지 않는 것

이번 초안에서는 아래를 일부러 열지 않습니다.

- `listing_items -> welfare_services` 직접 FK
- listing row의 `service_facts` 저장
- listing row의 `unified_category` 저장
- listing row의 `user_recommendations` 저장

이 연결은 future `listing recommendation lane` 또는 `policy + listing mixed experience` 가 필요할 때 다시 엽니다.

## future reopen 후보

나중에 다시 열 수 있는 항목:

- `listing_bookmarks`
- `listing_views`
- `job_listings` 전용 retrieval/ranking
- `housing_recruitments` 전용 alert/subscription
- 정책 row와 listing row 사이의 weak link
  - 예: `청년 월세지원 제도` ↔ `공공주택 모집공고 feed`

## 이번 초안의 요약

1. listing형 source는 `welfare_services` 로 바로 넣지 않는다.
2. 공통 header는 `listing_items` 하나로 받는다.
3. 세부 컬럼은 `job_listings`, `housing_recruitments`, `housing_complexes`, `housing_waitlist_stats` 로 분리한다.
4. raw truth, listing inventory truth, 정책 canonical truth를 섞지 않는다.
5. 추천 lane 연결은 별도 reopen 항목으로 남긴다.
