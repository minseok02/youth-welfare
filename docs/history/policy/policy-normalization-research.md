# 정책 정규화 구조 조사 메모

이 문서는 신규 데이터 API를 유연하게 붙일 수 있도록, 현재 `온통청년` 중심 정규화 구조를 어떤 공식 축으로 재편하는 게 맞는지 조사한 결과를 정리한 메모입니다.

관련 내부 기준:

- [api-mapping.md](../../core/api-mapping.md)
- [collect-ops.md](../../collect/collect-ops.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [recommendation-pipeline.md](../../recommendation/recommendation-pipeline.md)
- [phase-plan.md](../../phase-plan.md)

## 현재 구조 요약

현재 백엔드의 정규화 중심 엔티티는 [WelfareService.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/policy/entity/WelfareService.java) 입니다.
실제 매핑은 [WelfareServiceMapper.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java) 에서 처리하고 있고, 문서 기준으로도 [api-mapping.md](../../core/api-mapping.md) 의 `DB 컬럼 ← API 필드 매핑표`가 사실상 기준입니다.

이 구조의 특징은 다음과 같습니다.

- 카테고리 축은 `온통청년 대분류/중분류`를 기준으로 `unified_category`를 만든 흔적이 강함
- 소득 컬럼 `min_income`, `max_income` 은 사실상 `온통청년` 구조화 값 전용
- 복지로 계열은 다수 조건을 `service_tags` 와 본문 텍스트 추출에 의존
- 추천은 정규화 필드와 tag를 주로 사용하고, source 고유 필드는 거의 잃어버림

즉, 지금 구조는 `온통청년이 가장 구조화가 잘 된 source` 라는 전제에서는 실용적이지만, 범정부 서비스나 신규 API 확장 기준으로는 공식 분류축이 약합니다.

## 조사한 공식 기준

### 1. 온통청년 공개 분류 축

온통청년 `청년정책 개요`는 상위 정책 축을 아래 6개로 공개합니다.

- 일자리
- 교육
- 주거
- 금융
- 생활복지문화
- 참여

출처:

- https://www.youthcenter.go.kr/youthPolicy/ythPlcyInfoMain

이 축은 `청년정책` 도메인 탐색 UX에는 좋지만, 복지/보조금/지자체 일반 서비스 전체를 담는 범용 canonical schema로 보기에는 범위가 좁습니다.

### 2. 온통청년 운영 입력 모델

온통청년 `청년정책관리` 화면은 공개 API보다 더 상위의 입력 모델을 보여줍니다.
운영자는 정책 등록 시 아래 구조를 입력합니다.

- `정책상위과제`
  - 기본계획 차수
  - 정책분야
  - 중점과제
  - 세부과제
- `분류`
- `분야상세태그`
- `정책제공수단`
- `정책명`
- `정책요약설명`
- `정책지원내용`
- `주관부처/기관`
- `운영기관`
- `신청기간`
- `사업기간`
- `신청절차`
- `제출서류`

출처:

- https://www.youthcenter.go.kr/manageYthPlcy/ythPlcyManageMain/ythPlcyManageRegistry/ypmRegistryStep01

이 화면 기준으로 보면, 현재 우리 스키마에는 아래 공식 축이 충분히 살아 있지 않습니다.

- 정책 상위과제 체계
- 정책 제공수단
- 제출서류 / 신청절차
- 운영기관 / 담당 정보
- 청년정책 내부 상세 태그 체계

### 3. 온통청년 코드정의서

온통청년 공개 코드정의서 `API코드정보.xlsx` 에는 실제 코드북이 들어 있습니다.
조사 중 확인된 축은 아래와 같습니다.

- `정책대분류`
  - 일자리 / 주거 / 교육 / 복지문화 / 참여권리
- `정책중분류`
  - 취업 / 재직자 / 창업 / 주택 및 거주지 / 기숙사 / 전월세 및 주거급여 지원 / 미래역량강화 / 교육비지원 / 온라인교육 / 취약계층 및 금융지원 / 건강 / 예술인지원 / 문화활동 / 청년참여 / 정책인프라구축 / 청년국제교류 / 권익보호
- `정책키워드`
  - 대출 / 보조금 / 바우처 / 금리혜택 / 교육지원 / 맞춤형상담서비스 / 인턴 / 벤처 / 중소기업 / 청년가장 / 장기미취업청년 / 공공임대주택 / 신용회복 / 육아 / 출산 / 해외진출 / 주거지원
- `정책제공방법코드`
  - 인프라 구축 / 프로그램 / 직접대출 / 공공기관 / 계약(위탁운영) / 보조금 / 대출보증 / 공적보험 / 조세지출 / 바우처 / 정보제공 / 경제적 규제 / 기타
- `정책취업요건코드`
  - 재직자 / 자영업자 / 미취업자 / 프리랜서 / 일용근로자 / `(예비)창업자` / 단기근로자 / 영농종사자 / 기타 / 제한없음
- `정책학력요건코드`
  - 고졸 미만 / 고교 재학 / 고졸 예정 / 고교 졸업 / 대학 재학 / 대졸 예정 / 대학 졸업 / 석·박사 / 기타 / 제한없음
- `정책특화요건코드`
  - 중소기업 / 여성 / 기초생활수급자 / 한부모가정 / 장애인 / 농업인 / 군인 / 지역인재 / 기타 / 제한없음
- `결혼상태코드`
  - 기혼 / 미혼 / 제한없음
- `소득조건구분코드`
  - 무관 / 연소득 / 기타

출처:

- https://www.youthcenter.go.kr/downloadform/API%EC%BD%94%EB%93%9C%EC%A0%95%EB%B3%B4.xlsx

이 코드는 단순 display tag가 아니라, 청년정책 운영 시스템이 실제로 쓰는 공식 도메인 코드북에 가깝습니다.

### 4. 청년기본법 기준 정책 축

청년기본법은 청년정책의 기본 축을 아래처럼 설명합니다.

- 정책결정과정 참여 확대
- 고용 촉진
- 능력 개발
- 복지 향상
- 정치·경제·사회·문화 전 영역 삶의 질 향상
- 교육, 고용, 직업훈련의 평등한 기회

출처:

- https://www.youthcenter.go.kr/youthPolicy/ythPlcyRaw/ythPlcyRawMain

이 법은 필드 스키마 자체를 주진 않지만, `청년정책 taxonomy`를 설계할 때 상위 의미 축의 정당성을 줍니다.

### 5. 정부24/보조금24 공공서비스 API

행정안전부 `대한민국 공공서비스(혜택) 정보`는 정부 부처, 지자체, 공공기관, 교육청 서비스 전체를 다루는 범정부 API입니다.
공공데이터포털 공지와 Swagger 기준으로 API는 크게 3개로 분리됩니다.

- `serviceList`
- `serviceDetail`
- `supportConditions`

출처:

- https://www.data.go.kr/data/15113968/openapi.do
- https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000002221
- https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000004156

공식 Swagger 스펙에서 확인한 핵심 구조는 아래와 같습니다.

#### `serviceList` 핵심 필드

- `서비스ID`
- `지원유형`
- `서비스명`
- `서비스목적요약`
- `지원대상`
- `선정기준`
- `지원내용`
- `신청방법`
- `신청기한`
- `상세조회URL`
- `소관기관코드`
- `소관기관명`
- `부서명`
- `조회수`
- `소관기관유형`
- `사용자구분`
- `서비스분야`
- `접수기관`
- `전화문의`
- `등록일시`
- `수정일시`

#### `serviceDetail` 핵심 필드

- `서비스ID`
- `지원유형`
- `서비스명`
- `서비스목적`
- `신청기한`
- `지원대상`
- `선정기준`
- `지원내용`
- `신청방법`
- `구비서류`
- `접수기관명`
- `문의처`
- `온라인신청사이트URL`
- `수정일시`
- `소관기관명`
- `행정규칙`
- `자치법규`
- `법령`
- `공무원확인구비서류`
- `본인확인필요구비서류`

#### `supportConditions` 핵심 필드

정부24는 지원조건을 텍스트가 아니라 코드 집합으로도 분리합니다.
조사 중 확인된 공식 조건 코드는 아래와 같습니다.

- 성별
  - `JA0101` 남성
  - `JA0102` 여성
- 연령
  - `JA0110` 대상연령(시작)
  - `JA0111` 대상연령(종료)
- 소득
  - `JA0201` 중위소득 0~50%
  - `JA0202` 중위소득 51~75%
  - `JA0203` 중위소득 76~100%
  - `JA0204` 중위소득 101~200%
  - `JA0205` 중위소득 200% 초과
- 개인 상태
  - 예비부모/난임, 임산부, 출산/입양
  - 농업인, 어업인, 축산업인, 임업인
  - 초등학생, 중학생, 고등학생, 대학생/대학원생
  - 근로자/직장인, 구직자/실업자
  - 장애인, 국가보훈대상자, 질병/질환자
- 가구 특성
  - 다문화가족, 북한이탈주민, 한부모/조손가정, 1인가구
  - 다자녀가구, 무주택세대, 신규전입, 확대가족
- 창업/사업 상태
  - 예비창업자, 영업중, 생계곤란/폐업예정자
- 업종 / 기관 유형 일부
  - 제조업, 음식업, 기타업종
  - 중소기업, 사회복지시설, 기관/단체

이 구조는 `정책 일반 메타데이터`와 `지원조건`을 분리한, 범정부 확장에 훨씬 강한 정규화 축입니다.

## 조사 결론

단일 소스 기준으로 canonical schema를 잡는다면, 현재처럼 `온통청년` 중심으로 계속 가는 것보다 아래 이중 축이 더 타당합니다.

### 권장 구조

1. `범정부 공공서비스 core`
2. `청년정책 taxonomy`
3. `구조화 eligibility facts`
4. `source-specific/AI enrichment`

### 1. 범정부 공공서비스 core

`Gov24 serviceList + serviceDetail`를 기준으로 서비스 공통 메타데이터를 잡습니다.

예시:

- `service_id`
- `source_type`
- `title`
- `purpose_summary`
- `purpose_detail`
- `support_content`
- `target_text`
- `selection_criteria_text`
- `apply_method_text`
- `apply_deadline_text`
- `detail_url`
- `online_apply_url`
- `agency_code`
- `agency_name`
- `agency_type`
- `department_name`
- `receiving_org_name`
- `contact`
- `legal_basis_law`
- `legal_basis_rule`
- `required_documents`
- `verified_documents`
- `identity_documents`
- `registered_at`
- `last_modified_at`

이 레이어는 청년정책이 아니어도 유지될 수 있어야 합니다.

### 2. 청년정책 taxonomy

청년정책 분류는 `온통청년 운영 코드북`을 기준으로 별도 레이어로 분리하는 편이 맞습니다.

예시:

- `youth_policy_major_category`
  - 일자리 / 주거 / 교육 / 복지문화 / 참여권리
- `youth_policy_mid_category`
  - 취업 / 창업 / 미래역량강화 / 주거지원 / 건강 / 청년참여 등
- `youth_policy_keywords`
  - 대출 / 보조금 / 바우처 / 인턴 / 공공임대주택 / 해외진출 등
- `policy_provision_method`
  - 보조금 / 바우처 / 직접대출 / 조세지출 / 정보제공 등
- `provider_group`
  - 중앙부처 / 지자체

이 축은 `WelfareService.unifiedCategory` 하나로 눌러 담지 말고, 별도 taxonomy 구조로 유지하는 게 낫습니다.

### 3. 구조화 eligibility facts

지원조건은 텍스트 컬럼으로만 저장하지 말고, 별도 fact 구조로 분리해야 합니다.
핵심 기준은 `Gov24 supportConditions`이고, 청년 특화 필드는 `온통청년 코드북`으로 보완합니다.

예시 스키마:

- `service_id`
- `authority`
  - `GOV24_SUPPORT_CONDITION`
  - `YOUTHCENTER_CODEBOOK`
  - `SOURCE_EXTRACTED`
  - `AI_ENRICHED`
- `fact_group`
  - `age`
  - `income`
  - `employment`
  - `education`
  - `family`
  - `housing`
  - `special_group`
  - `industry`
  - `entrepreneurship`
- `fact_code`
  - `JA0110`, `JA0203`, `jobCd:0013003`, `schoolCd:0049005` 같은 식별값
- `fact_label`
- `bool_value`
- `min_value`
- `max_value`
- `text_value`
- `confidence`

이 구조가 있으면 추천 hard filter와 soft score를 분리하기 쉬워집니다.

### 4. source-specific / AI enrichment

모든 신규 API 필드를 즉시 core schema에 넣는 건 과합니다.
공식 축에 안 맞는 필드는 raw 저장 후 enrichment 레이어로 보내는 게 맞습니다.

예시:

- `selection_method`
- `competition_type`
- `required_certificate`
- `major_preference`
- `residency_duration`
- `credit_recovery`
- `military_service`
- `document_burden_level`
- `benefit_amount_range`

이 레이어는 `AI batch enrichment`가 가장 잘 맞습니다.

## AI는 어디에 쓰는 게 맞는가

AI를 추천 최종판정에 바로 넣기보다, `공식 정규화 축으로 안 들어오는 필드`를 구조화하는 데 쓰는 편이 낫습니다.

### AI에 맡기기 좋은 것

- source별 자유서술에서 eligibility fact 후보 추출
- 정책 키워드 / 세부 주제 / 지원유형 분류
- 신청 난이도 / 서류 부담도 / 혜택 유형 같은 soft signal 추정
- 추천 설명문 생성

### AI에 바로 맡기면 위험한 것

- 신청 가능/불가능의 최종 hard exclusion
- 법적 자격조건 확정
- 연령/소득/가구 조건의 authoritative 값 대체

### 권장 사용 방식

1. raw payload 저장
2. 공식 구조로 1차 deterministic 매핑
3. 남는 필드는 AI batch enrichment로 fact/tag 후보 생성
4. 추천은
   - hard filter: deterministic field만 사용
   - rule score: deterministic + 고신뢰 fact 사용
   - AI rerank: top N 보정에만 사용

## 추천 구조에 대한 영향

이 구조로 바꾸면 추천 쪽은 아래처럼 단순해집니다.

### hard filter

- `age_min/max`
- `income_band`
- `employment_status`
- `education_level`
- `housing_status`
- `special_group`

### rule scoring

- `youth_policy_major_category`
- `youth_policy_mid_category`
- `policy_provision_method`
- `special_group`
- `benefit_type`
- `deadline`

### AI rerank

- core summary
- detail text
- AI-enriched facts
- user profile snapshot

즉, 추천이 더 많은 source field를 사용할 수 있으면서도, `공식 구조`와 `AI 추정`을 섞어 쓰되 혼동하지 않게 됩니다.

## 권장 다음 작업

1. `정규화 4계층 구조`를 내부 canonical decision으로 확정
2. `WelfareService` 단일 엔티티에 몰린 축을 `core / taxonomy / fact`로 분리하는 스키마 초안 작성
3. `Gov24 supportConditions` 와 `온통청년 코드북`을 각각 enum/code table로 보관할지 결정
4. 신규 source는 우선 `core + taxonomy + raw` 까지만 붙이고, source 특이 필드는 AI batch enrichment로 보내는 경로 설계
5. 추천 hard filter/rule scoring이 어떤 fact group까지 직접 읽을지 먼저 고정

## 가능 여부와 타당성 검토

### 결론

제안한 `범정부 core + 청년 taxonomy + eligibility facts + AI enrichment` 구조로 바꾸는 것은 **가능합니다**.
다만 방식은 `기존 엔티티를 바로 대체하는 빅뱅 교체`가 아니라, **기존 `WelfareService` 옆에 sidecar 구조를 추가하고 추천/조회가 점진적으로 읽는 구조로 전환하는 방식만 타당**합니다.

즉 아래 둘을 구분해야 합니다.

- 가능한가: 가능
- 지금 당장 한 번에 갈아엎는 게 타당한가: 아니오

### 왜 가능한가

현재 수집 파이프라인은 이미 source fan-out을 일부 줄였기 때문에, 저장 직전 aggregate를 확장할 자리가 있습니다.

현재 코드 기준으로 보면:

- [CollectService.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectService.java)
  - source dispatch가 adapter 기반으로 정리되어 있어 신규 source 또는 신규 정규화 산출물을 연결하기 쉬움
- [CollectItemSaver.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java)
  - 지금은 `WelfareService + ServiceRegion + ServiceTag`만 저장하지만, item 저장 경계가 한 군데라 `taxonomy/facts` 저장을 여기에 붙이기 좋음
- [WelfareServiceMapper.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java)
  - source별 mapping이 한 클래스에 몰려 있어 문제이기도 하지만, 반대로 “어디서 공식 core/taxonomy/facts를 뽑아야 하는지”가 명확함
- `raw_api_payloads`
  - 이미 존재하므로 source-specific 필드를 AI batch enrichment로 다시 해석할 재료가 있음

즉 수집 진입과 raw 보관은 이미 준비돼 있고, 부족한 것은 저장 모델뿐입니다.

### 왜 빅뱅 교체는 타당하지 않은가

현재 추천/검색/응답은 기존 `WelfareService` 컬럼과 `ServiceTag` 4종에 강하게 묶여 있습니다.

#### 1. 추천 후보 SQL이 기존 필드에 직접 묶여 있음

[WelfareServiceRepository.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java) 의 후보 조회는 아래 컬럼을 직접 사용합니다.

- `minAge`
- `maxAge`
- `minIncome`
- `maxIncome`
- `status`
- `sourceType`
- `unifiedCategory`

특히 `findCandidates*` 쿼리는 `YOUTH` 소스일 때만 소득 필터를 다르게 걸고 있어, `facts` 구조가 생긴다고 해서 바로 바꿀 수 있는 상태가 아닙니다.

#### 2. 추천 후처리가 `ServiceTag` 4종 enum에 묶여 있음

[ServiceTag.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/policy/entity/ServiceTag.java) 의 현재 타입은 아래 4개뿐입니다.

- `INTEREST_THEME`
- `TARGET_GROUP`
- `LIFE_STAGE`
- `KEYWORD`

[RetrievalService.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RetrievalService.java) 와 [RuleScoringService.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java) 는 이 4종을 전제로 age 보조필터, 관심사 일치, 대상군 일치, 특수대상 추론을 하고 있습니다.

즉 지금 구조는 `facts`가 아니라 `tag + free text` 기반입니다.

#### 3. 우선순위/응답 DTO가 `unifiedCategory` 하나에 묶여 있음

- [DefaultPriorityMatcher.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/DefaultPriorityMatcher.java)
- [PolicyDetailResponse.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/policy/dto/PolicyDetailResponse.java)
- [RecommendationResponse.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/recommend/dto/RecommendationResponse.java)

이 경로들은 `주거`, `일자리`, `교육·직업훈련`, `금융·생활지원` 같은 현재 `unifiedCategory` 문자열에 바로 의존합니다.
따라서 taxonomy가 생겨도 기존 응답 계약은 한동안 `unifiedCategory`를 계속 제공해야 합니다.

#### 4. AI도 아직 새 구조를 쓰지 않음

[RealtimeAiGateway.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/recommend/gateway/RealtimeAiGateway.java) 는 현재 AI에 아래만 보냅니다.

- 제목
- `unifiedCategory`
- 설명 요약

즉 `facts`나 `detail`을 반영하는 구조로 아직 설계돼 있지 않습니다.
AI enrichment를 도입하더라도 이 경로는 별도 확장이 필요합니다.

### 타당한 이행 방식

현재 코드에서는 아래 순서만 타당합니다.

#### 1단계: sidecar 테이블 추가

기존 테이블은 유지하고 아래를 새로 붙입니다.

- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

이 단계에서는 기존 API/추천 쿼리는 건드리지 않습니다.

#### 2단계: 저장 경계 확장

`WelfareServiceMapper` 가 source DTO를 아래 내부 aggregate로 만들게 바꿉니다.

- `core`
- `detail`
- `taxonomy`
- `facts`
- `raw`

그리고 [CollectItemSaver.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java) 가 기존 저장과 함께 sidecar 저장도 수행하게 만듭니다.

핵심은 이 단계에서도 기존 `WelfareService`, `ServiceTag`, `ServiceRegion` 저장을 없애지 않는 것입니다.

#### 3단계: 조회 read-model 추가

기존 응답 DTO를 바로 바꾸지 말고, 새로운 read-model service를 먼저 만듭니다.

예:

- `PolicyClassificationReadService`
- `PolicyEligibilityReadService`

이 서비스가 `service_taxonomies`, `service_facts`를 읽어 조합하게 합니다.

#### 4단계: 추천 hard filter를 facts로 일부 이관

처음부터 전체 추천을 옮기면 위험합니다.
우선 deterministic한 것부터 옮깁니다.

- 연령
- 소득 구간
- 취업 상태
- 학력
- 특화 대상
- 가구 특성

이 단계에서만 `WelfareServiceRepository.findCandidates*` 쿼리를 서서히 바꾸는 것이 맞습니다.

#### 5단계: `ServiceTag`의 책임 축소

그 다음에야 현재 `KEYWORD`, `TARGET_GROUP`에 과도하게 실린 의미를 줄일 수 있습니다.

미래 상태:

- `ServiceTag`
  - 검색/표시용 lightweight tag
- `service_facts`
  - hard filter / structured signal
- `service_taxonomies`
  - 정책 분류 / 제공수단 / 서비스 분야

#### 6단계: AI enrichment 추가

마지막에 raw payload와 detail을 읽어 공식 구조에 안 들어오는 값을 `service_facts(authority=AI_ENRICHED)`로 넣습니다.

이건 추천 core migration보다 뒤에 두는 게 맞습니다.
AI를 먼저 넣으면 구조화 기준이 흐려집니다.

### 비용 평가

#### 낮은 비용

- 조사 결과를 canonical decision으로 문서화
- `service_taxonomies`, `service_facts` 스키마 추가
- collect 저장 aggregate 추가
- raw 기반 AI batch 설계

#### 중간 비용

- `CollectItemSaver` 저장 경계 확장
- `WelfareServiceMapper` 를 `core/taxonomy/facts` 산출형으로 분리
- policy detail/list read-model 확장

#### 높은 비용

- `WelfareServiceRepository.findCandidates*` 전면 개편
- `RuleScoringService`, `RetrievalService`, `YouthPolicyFilter` 의 태그/텍스트 의존 축소
- `DefaultPriorityMatcher` 의 `unifiedCategory` 직접 비교 제거
- 응답 DTO 및 프론트 계약 점진 전환

### 최종 판단

현재 코드 기준으로 가장 현실적인 판단은 아래입니다.

- 구조 변경은 기술적으로 가능하다.
- 공식 근거도 충분하다.
- 신규 데이터 API 확장성 측면에서도 타당하다.
- 하지만 구현 방식은 반드시 `sidecar + 점진 이행` 이어야 한다.
- 특히 추천 SQL과 `ServiceTag` 의존부를 건너뛰고 바로 최종 구조로 가는 건 타당하지 않다.

즉 이 구조는 **도입 자체는 타당**, **빅뱅 전환은 부적절**, **2~4단계로 쪼개서 가는 것이 유일하게 실무적인 방법**입니다.

## 판단

권장 canonical 기준은 아래입니다.

- `서비스 공통 메타데이터`: 정부24/보조금24
- `청년정책 분류`: 온통청년 운영 코드북
- `청년정책 상위 의미`: 청년기본법 및 온통청년 정책 개요
- `나머지 source-specific 필드`: raw + AI enrichment

즉, 다음 리팩터링 목표는 `온통청년 중심 단일 정규화`에서 `범정부 core + 청년 taxonomy + eligibility facts + AI enrichment` 구조로 바꾸는 것입니다.
