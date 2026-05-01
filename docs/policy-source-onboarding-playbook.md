# 정책 source 온보딩 플레이북

이 문서는 실제 DB 적재 스냅샷과 공식 source 메타데이터를 기준으로, 신규 정책/일자리/주거/장학 source를 어떤 방식으로 붙일지 정리한 실무용 메모입니다.

관련 문서:

- [policy-normalization-research.md](./history/policy/policy-normalization-research.md)
- [policy-normalization-sample-spike.md](./history/policy/policy-normalization-sample-spike.md)
- [policy-normalization-schema-draft.md](./history/policy/policy-normalization-schema-draft.md)
- [policy-normalization-bridge-rules.md](./history/policy/policy-normalization-bridge-rules.md)
- [collect-ops.md](./collect-ops.md)
- [phase-plan.md](./phase-plan.md)

## 목적

이번 정리는 두 가지를 확인하기 위한 것입니다.

1. 실제 DB에 적재된 정책 row를 기준으로 현재 모델이 어디까지는 잘 담고, 어디부터는 한계가 있는지 확인
2. 새 source를 붙일 때 `무조건 welfare_services` 로 넣지 않고, source의 row grain에 따라 `정책형 / listing형 / reference형` 으로 분리하는 기준을 고정

## 2026-04-29 실제 DB 스냅샷

검증 시점 기준 local Docker MySQL `youth_welfare` 스냅샷은 아래와 같습니다.

### 정책 row 분포

- `welfare_services`: `3376`
- `source_type=YOUTH`: `2299`
- `source_type=BOKJIRO_LOCAL`: `1169`
- `source_type=BOKJIRO_CENTRAL`: `115`

### raw payload 분포

- `raw_api_payloads.source_type=BOKJIRO_LOCAL`: `4352`
- `raw_api_payloads.source_type=YOUTH`: `2299`
- `raw_api_payloads.source_type=BOKJIRO_CENTRAL`: `392`

### 상세/구조화 필드 분포

| source | total | age rows | income rows | deadline rows | detail_url rows |
|---|---:|---:|---:|---:|---:|
| `YOUTH` | 2299 | 1695 | 24 | 1140 | 723 |
| `BOKJIRO_LOCAL` | 1169 | 24 | 0 | 0 | 1169 |
| `BOKJIRO_CENTRAL` | 115 | 2 | 0 | 0 | 115 |

- `welfare_service_details`: `0`
- `service_tags.KEYWORD`: `3701`
- `service_tags.LIFE_STAGE`: `3257`
- `service_tags.INTEREST_THEME`: `1271`
- `service_tags.TARGET_GROUP`: `616`

### 현재 `unified_category` 분포

- `기타`: `1375`
- `일자리`: `941`
- `금융·생활지원`: `404`
- `주거`: `246`
- `참여·기회`: `190`
- `교육·직업훈련`: `177`
- `가족·돌봄`: `82`
- `건강·의료`: `41`
- `안전·위기`: `18`
- `문화·여가`: `2`

## 실제 DB에서 확인한 핵심 판단

### 1. 현재 구조는 `YOUTH` 같은 정책형 source에는 비교적 잘 맞는다

`YOUTH` 는 `age`, `income`, `deadline`, `description/support_content` 가 어느 정도 채워집니다.
즉, 지금 `WelfareService + ServiceTag` 구조는 “청년정책 프로그램 row” 에 대해서는 당장 동작합니다.

### 2. 복지로 계열은 list 적재는 되지만 detail/facts 레이어 검증은 아직 부족하다

`BOKJIRO_LOCAL`, `BOKJIRO_CENTRAL` 는 row는 많이 들어와 있지만:

- `welfare_service_details=0`
- 구조화 `income_rows=0`
- 구조화 `age_rows` 도 매우 적음

즉 현재 live DB는 `복지로 목록 + tag/display 필드` 수준은 검증됐지만, 새 canonical의 `detail / facts` 레이어까지 검증된 상태는 아닙니다.

### 3. title 기준으로는 분명한 주거/장학/일자리 정책이 현재 `기타` 로 많이 남는다

실제 DB example:

- `대전 청년 월세지원`
- `청년·신혼부부 전세보증금 반환보증 보증료 지원사업`
- `대학생 학자금 대출이자 지원(경기도)`
- `학자금 대출이자 지원사업`
- `취업청년정착수당`
- `청년 취업사진 촬영비 지원`

이 중 다수가 `BOKJIRO_LOCAL + unified_category=기타` 로 남아 있었습니다.

이건 다음 의미를 가집니다.

- 현재 `unified_category` 는 source 확장이 진행될수록 더 많은 예외 title keyword 보정을 요구한다
- 따라서 신규 source 대응은 “keyword rule 더 추가”가 아니라 `official taxonomy + compat bridge + facts` 구조로 가야 한다

## source shape 분류 기준

신규 source는 기관명이 아니라 `row grain` 으로 먼저 분류합니다.

### 1. 정책형 source

정의:

- 한 row가 비교적 안정적인 “지원 제도/정책/프로그램” 을 의미
- 제목, 요약, 대상, 지원내용, 신청기한, 기관, 자격조건이 있음
- 추천에서 “정책 카드 1개” 로 보여도 무리가 없음

저장 방식:

- `welfare_services`
- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`
- `raw_api_payloads`

예:

- 온통청년 정책
- 복지로 정책성 row
- Gov24/보조금24 공공서비스 혜택 row
- 정부지원일자리정보의 사업/프로그램성 row
- 국가장학금, 청년월세지원, 전세보증금 보증료 지원 같은 제도 row

### 2. listing형 source

정의:

- 한 row가 “공고/채용건/주택공급건/행사건” 같이 빠르게 변하는 inventory 를 의미
- 모집마감, 공급호수, 기업/단지/회차 같은 listing 속성이 강함
- 정책 카드 1개와 동일 의미로 섞으면 추천 의미가 흐려짐

저장 방식:

- `welfare_services` 로 바로 넣지 않음
- 별도 domain table 또는 read model로 분리

예:

- 고용24/워크넷 `채용정보`, `채용행사`, `공채속보`
- 마이홈포털 `공공주택 모집공고`
- 마이홈포털 `공공임대주택 단지정보`
- 마이홈포털 `예비입주자 대기현황`

### 3. reference형 source

정의:

- 한 row가 “제도 안내를 위한 조건표/지원가능대학/구간표/코드표” 성격
- 직접 추천 카드로 보여주기보다 정책 facts를 보강하는 참조 데이터에 가까움

저장 방식:

- reference table 또는 fact variant table
- canonical 정책 row를 보강하는 쪽으로 사용

예:

- 한국장학재단 `학자금지원정보(대학생)` 의 대학/학기/지원구간/지원가능대학 매트릭스
- 국가장학금 지원구간 경곗값
- 장학금 상품별 가능대학 기본정보

## source별 대응안

## 1. 고용24 / 워크넷

공식 source 기준:

- Work24 Open-API 소개 페이지는 `채용정보`, `정부지원일자리정보`, `구직자취업역량 강화프로그램` 을 별도 군으로 공개합니다.
- 출처: https://www.work24.go.kr/cm/e/a/0110/selectOpenApiIntro.do?bbsClCd=OosccI71O3P2dBxVz5A40Q%3D%3D

### A. `채용정보`, `채용행사`, `공채속보`, `공채기업정보`

판단:

- 정책형이 아니라 listing형입니다.
- 한 row는 “지원 제도” 가 아니라 “특정 기업/공고/채용건” 에 가깝습니다.

대응:

- `welfare_services` 로 넣지 않음
- 별도 `job_opportunities` 또는 `job_listings` 도메인으로 분리
- 이후 추천도 `정책 추천` 과 `채용 listing 추천` 을 별도 lane으로 다룸

이유:

- 채용공고는 churn 이 빠르고, employer/position/location/salary/closing date 중심입니다.
- 정책 추천 row와 같은 테이블에 넣으면 row grain이 깨지고 CTR/북마크 의미도 섞입니다.

### B. `정부지원일자리정보`

판단:

- 이름 그대로 “일자리 사업/프로그램” 성격이면 정책형 source 후보입니다.
- `일자리사업정보`, `일자리사업상세정보`, `기관기본정보` 는 canonical 적합도가 높습니다.

대응:

- `core/detail/taxonomy/facts` canonical에 직접 매핑
- `compat_unified_category` 는 우선 `일자리`
- 취업대상, 연령, 소득, 훈련/참여형 여부는 `service_facts` 우선

### C. `구직자취업역량 강화프로그램`

판단:

- 프로그램형이라 정책형 source에 가깝습니다.
- `일자리` 또는 `교육·직업훈련` 으로 내려갈 가능성이 높습니다.

대응:

- canonical 적재
- `provision_method`, `training/program`, `eligibility facts`, `target group` 매핑 강화
- 제목만 보고 `일자리` 로 고정하지 말고 프로그램 목적/지원내용을 같이 봅니다

## 2. 주거: 마이홈포털 공공주택 계열

공식 source 기준:

- `국토교통부_마이홈포털 공공주택 모집공고 조회 서비스`
- `국토교통부_마이홈포털 공공임대주택 단지정보 조회 서비스`
- `국토교통부_마이홈포털 예비입주자 대기현황 조회서비스`
- 출처:
  - https://www.data.go.kr/data/15108420/openapi.do
  - https://www.data.go.kr/data/15110581/openapi.do
  - https://www.data.go.kr/data/15108378/openapi.do

### A. 공공주택 모집공고

판단:

- 정책형보다는 listing형입니다.
- 한 row는 공급 공고/회차/단지/주택형/모집기간 의미가 강합니다.

대응:

- 별도 `housing_recruitments` 또는 `housing_listings`
- 정책 canonical과는 링크만 가능하게 분리

### B. 공공임대주택 단지정보

판단:

- reference/listing 중간 성격입니다.
- 지원제도 자체보다는 단지 inventory/meta에 가깝습니다.

대응:

- `housing_complexes` 또는 reference domain
- 위치/단지명/공급유형/세대수/입지정보 같은 listing/reference 속성 중심

### C. 예비입주자 대기현황

판단:

- 명백한 reference형 또는 status feed 입니다.
- 추천 카드 row가 아니라 대기현황 정보입니다.

대응:

- `housing_waitlist_stats` 류의 reference table
- canonical 정책 카드로 직접 노출하지 않음

### D. 주거지원 제도 자체

예:

- 청년 월세 지원
- 전세보증금 반환보증 보증료 지원
- 전세자금대출 이자 지원

판단:

- 이건 정책형입니다.

대응:

- canonical `welfare_services`
- `compat_unified_category=주거`
- `service_facts` 에 소득, 무주택, 청년/신혼부부, 지역, 임차/전세/월세 요건 저장

즉 `주거지원 제도` 와 `주택공급/단지/공고 listing` 은 분리해야 합니다.

## 3. 장학금 / 국가장학금 / 한국장학재단

공식 source 기준:

- `한국장학재단_학자금지원정보(대학생)` 파일데이터
- 한국장학재단 국가장학금 안내 페이지
- 출처:
  - https://www.data.go.kr/data/15028252/fileData.do
  - https://www.kosaf.go.kr/ko/scholar.do?pg=scholarship05_12_01_01

### A. 제도/상품 단위 장학금

예:

- 국가장학금 I유형
- 다자녀 국가장학금
- 국가근로장학금
- 학자금대출 이자지원

판단:

- 정책형입니다.

대응:

- canonical 적재
- `compat_unified_category=교육·직업훈련` 또는 `금융·생활지원` bridge
- `service_facts` 에 교육, 소득구간, 재학생/복학생/학점요건, 다자녀 여부 등 저장

### B. 지원가능대학/학기/세부 구간표/금액표

예:

- 가능 대학 목록
- 학기별 지원금액 표
- 지원구간 경곗값
- 상품별 가능대학 기본정보

판단:

- reference형입니다.

대응:

- `scholarship_reference` / `scholarship_eligibility_matrix` 류로 분리
- canonical row 1건에 연결되는 fact variant 또는 reference payload로 보관

이유:

- 이 데이터를 `welfare_services` row로 직접 flatten 하면 대학/학기별 파생 row가 과도하게 늘고, 정책 1건의 의미가 깨집니다.

## 4. 대한민국 공공서비스 정보 API (Gov24 / 보조금24)

공식 source 기준:

- `행정안전부_대한민국 공공서비스(혜택) 정보`
- `serviceList`, `serviceDetail`, `supportConditions`
- 출처: https://www.data.go.kr/data/15113968/openapi.do

판단:

- 가장 canonical 친화적인 정책형 source입니다.
- `core/detail/facts` 레이어의 기준 source로 쓰기 좋습니다.

대응:

- `welfare_services` + sidecar 저장
- `supportConditions` 는 `service_facts` 우선
- `서비스분야/사용자구분/지원유형` 은 official taxonomy 저장
- 다만 `청년 taxonomy` 는 source가 직접 주지 않으므로 `compat_unified_category` / `SYSTEM_DERIVED youth taxonomy bridge` 가 필요합니다

## 추천/AI 대응 원칙

### 1. official field가 있으면 AI보다 official을 우선

- Gov24 `supportConditions`
- 온통청년 운영 코드북
- 장학금 제도 공식 eligibility

이 값은 `service_facts(authority=OFFICIAL)` 로 저장합니다.

### 2. AI는 official 축 대체가 아니라 보강에만 쓴다

예:

- 복지로 자유서술에서 `청년 신혼부부`, `무주택`, `미취업`, `전세보증금` 신호 추출
- 장학제도 설명문에서 지원방식/서류부담도/추천사유 보강

즉 AI는:

- `service_facts(authority=AI_ENRICHED)`
- 또는 별도 enrichment summary

로만 사용하고, `hard filter` 는 official/rule-derived high confidence 값만 씁니다.

### 3. listing형 source는 추천 lane도 분리한다

- `job_listings`
- `housing_recruitments`

는 나중에 추천을 붙이더라도 정책 추천과 섞지 않고 별도 feed/ranking 으로 갑니다.

## 온보딩 체크리스트

새 source를 붙일 때는 아래 순서로 판단합니다.

1. 이 source의 row grain이 `정책형 / listing형 / reference형` 중 무엇인지 먼저 결정
2. 정책형이면 `core/detail/taxonomy/facts/raw` 에 매핑
3. listing형이면 `welfare_services` 에 넣지 말고 별도 domain 초안을 먼저 작성
4. reference형이면 canonical 정책 row를 보강하는 reference/fact variant 구조를 먼저 설계
5. `compat_unified_category` 가 필요한지, 필요하면 derived bridge 규칙을 따로 둠
6. live sample 20~50건으로 DB snapshot 검증
7. 추천 hard filter에 쓸 fact와 soft signal로만 쓸 fact를 분리

## 이번 정리의 결론

1. 실제 DB 기준으로도 현재 구조는 `YOUTH` 같은 정책형 source에는 유효하지만, 복지로/주거/장학/일자리 확장에는 `기타` 누수와 detail/facts 공백이 분명합니다.
2. 앞으로 새 source를 많이 붙일 계획이라면, 기관명보다 `source shape` 기준으로 먼저 분기해야 합니다.
3. `고용24 채용정보`, `마이홈 공공주택 모집공고/단지/대기현황` 은 canonical 정책 row에 바로 넣지 않는 것이 맞습니다.
4. `Gov24`, `정부지원일자리정보`, `장학금 제도 row` 같은 정책형 source는 새 canonical의 주요 입력이 될 수 있습니다.
5. AI는 official 필드가 없는 곳을 보강하는 용도로만 쓰고, 공식 facts/taxonomy를 대체하지 않습니다.
