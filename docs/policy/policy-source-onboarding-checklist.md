# 신규 정책 소스 온보딩 가이드

새 API의 row 의미와 근거를 확인한 뒤 수집·정규화·추천 연결 범위를 정합니다. 정책형·공고형·참조형을 같은 정책 테이블에 넣지 않으며, 수집 성공과 추천 사용 가능 판정을 분리합니다.

- 현재 계약: [정규화 현재 상태](policy-normalization-current-state.md), [수집 현재 상태](../collect/collect-current-state.md).
- 검토 결과 작성: [온보딩 템플릿](policy-source-onboarding-template.md).
- 명령 실행 전: [환경 안내](../core/environment-guide.md). 수집·backfill·replay는 데이터 변경이나 외부 호출을 수반합니다.

## 1. Row 의미와 저장 대상

기관명이 아니라 한 row가 무엇을 뜻하는지 먼저 분류합니다. 여러 유형이 섞이면 endpoint/row별로 나누고, 구분할 수 없으면 canonical 적재를 보류합니다.

| 유형 | 판정 기준과 예시 | 저장·추천 경계 |
| --- | --- | --- |
| 정책형 | 안정적인 지원제도/프로그램 1건. 온통청년·복지로·Gov24 혜택, 월세지원, 장학금 상품, 일자리 사업 | `welfare_services`와 정책 sidecar 후보. 대상·지원내용·기관·신청기한의 의미를 확인 |
| listing형 | 특정 채용공고·기업·행사·공급 회차/모집건. 마감·급여·공급호수처럼 변동하는 inventory 중심 | 별도 listing domain. 정책 추천·북마크·클릭 지표와 lane 분리 |
| reference형 | 대학/학기/소득구간/금액표, 단지 메타, 대기현황처럼 제도를 보강하는 행 | reference/fact variant로 연결. 대학·학기별 행을 정책 카드로 늘리지 않음 |

공공임대 단지정보는 listing/reference 중 목적에 맞게 분리하고, 예비입주자 대기현황은 reference/status feed로 다룹니다. `주거지원 제도`와 `주택공급 공고`는 같은 grain이 아닙니다. 분리 스키마는 [listing 초안](../history/policy/policy-listing-source-schema-draft.md), [장학금 참조행렬 초안](../history/policy/policy-scholarship-reference-matrix-draft.md)을 참고합니다.

## 2. Endpoint와 payload 보존

먼저 provider/source 이름, list/detail/codebook endpoint, 인증·quota, external ID, 갱신 주기와 조회 시각을 기록합니다. 공식 schema와 대표 응답에서 필드 의미를 확인합니다.

- `sourceType + sourceId + apiCategory`로 list/detail/support를 구분하고 같은 외부 ID로 연결할 수 있어야 합니다.
- 저장 형식이 실제 HTTP 응답 원문인지 DTO 재직렬화인지 구분하고, 누락 필드·변환·덮어쓰기·실패 처리를 기록합니다. 현재 `RawApiPayloadService`는 객체를 JSON으로 직렬화해 upsert하므로 완전한 원문·변경 이력 보존을 보장하지 않습니다.
- payload snapshot과 수집 시각/hash·sync log를 확보해 mapping 실패 뒤 재파싱할 수 있게 합니다. raw 저장 실패를 무시할지 수집 실패로 셀지도 명시합니다.
- live list 수집, detail refresh/gap-fill, 저장 payload 기반 backfill/replay는 별도 실행으로 둡니다. 한 경로의 성공을 다른 경로의 완료로 간주하지 않습니다.

## 3. Canonical 승격과 근거

정책형은 `core/detail/taxonomy/facts`에 대응시킵니다. title·summary·provider·detail URL·deadline·age·income·target group·provision method별로 공식 필드와 fallback을 구분합니다. 분류는 `service_taxonomies`/`service_taxonomy_terms`, 자격·지원조건은 `service_facts`에 담되 원래 의미와 출처를 유지합니다.

1. 검증한 공식 필드·codebook을 우선합니다.
2. 내부 bridge/rule은 공식 정보와 구분하고 입력·규칙 버전을 남깁니다.
3. AI 보완은 원문 근거·불확실성을 가진 검수 후보로 둡니다.

`NormalizedPolicyAggregate.Authority`의 `OFFICIAL`, `SYSTEM_DERIVED`, `RULE_DERIVED`, `AI_ENRICHED`를 구분합니다. 공식 지원조건도 코드·연산자·단위·기준 연도·가구 기준을 확인한 뒤 사용합니다. hard filter는 공식 근거 또는 검토된 결정 규칙에 한정하고, 높은 AI confidence나 title keyword만으로 자격을 확정하지 않습니다. 자유서술의 무주택·미취업·신혼부부 추출과 추천사유 보완은 공식 자격조건을 대체하지 않습니다.

list/detail이 소유한 필드를 구분하고 재수집 시 사라진 조건·누락·삭제·수동 보정의 우선순위를 정합니다. 현재 fact merge의 완전성은 [리팩토링 확인 과제](policy-normalization-current-state.md#리팩토링-확인-과제)이며, 저장됐다는 이유로 의미 검증이 끝난 것은 아닙니다.

## 4. 응답 호환과 추천

현재 `unifiedCategory` 응답은 legacy compat 대표값입니다. canonical taxonomy를 채웠다고 대표 category를 바로 바꾸지 않으며, `compat_unified_category` bridge와 원본 taxonomy/facts를 나란히 보존합니다.

추천 연결 전에는 다음을 확인합니다.

- 정책형 후보인지, 기존 API 필드·read-model·retrieval·scoring·priority matcher를 깨지 않는지.
- canonical projection을 보조 신호로만 쓸지, 검증된 자격조건으로 사용할지.
- listing/reference의 북마크·추천·클릭 의미를 정책 lane에 섞지 않는지.

저장·projection·scoring·사용자 응답의 경계는 [추천 파이프라인](../recommendation/recommendation-pipeline.md)을 따릅니다. 기존 read-model 설계 배경은 [read-model 초안](policy-normalization-recommendation-read-model.md), summary 저장 배경은 [summary slot 설계](../history/policy/policy-normalization-summary-slot-storage-plan.md)에 있습니다.

## 5. taxonomy / codebook 판정

공식 codebook이 없다는 이유만으로 source 전체 ingest를 막지 않습니다. 필드마다 다음 중 하나를 선택합니다.

| 근거 | 허용 범위 | 보류 경계 |
| --- | --- | --- |
| 공식 code + 검증된 codebook/schema | 출처·버전을 남긴 공식 mapping | 미확인 code/단위·의미의 승격 |
| 공식 text label + 관측 inventory + 검토된 내부 규칙 | label-first taxonomy/compat와 내부 stable code | 내부 코드를 공식 코드로 설명하거나 text 분류를 hard eligibility로 승격 |
| 의미·조건 단위·row grain 불명확 | 해당 필드를 metadata-only/manual review/blocked로 분리 | 근거 없는 SQL/backfill·title/AI 추정 기반 자격판정 |

raw label, 관측 inventory의 범위·날짜, 규칙 버전, 검토자·근거를 보존합니다. 관측 목록을 공식 enum 전체로 간주하지 않으며, 새 label은 unknown/review로 격리합니다. 확정된 다른 필드나 안전한 payload 수집까지 일괄 중단할 필요는 없습니다.

Gov24는 runtime collect·label-first·내부 stable-code seed/backfill이 active이며, 외부 공식 stable-code import 보류와 다릅니다. 최신 경계는 [Gov24 상태](policy-gov24-lane-closeout.md#보류와-재검토), 제공된 공식 코드 자산은 [코드북 출처와 변환](policy-user-provided-official-codebooks.md)을 확인합니다.

## 6. 코드 진입점

정책형은 아래 순서로 확인합니다. listing/reference형은 adapter·payload 보존·별도 domain 설계부터 시작하고 `welfare_services` 연결을 기본값으로 두지 않습니다.

| 경계 | 먼저 열 파일 | 확인할 내용 |
| --- | --- | --- |
| 관리자 수집 | [CollectAdminController](../../backend/src/main/java/com/example/welfare/collect/controller/CollectAdminController.java), [CollectAdminService](../../backend/src/main/java/com/example/welfare/collect/service/CollectAdminService.java) | `POST /api/admin/collect/{sourceKey}`, `/all`, sidecar backfill·detail gap-fill 경로와 권한 |
| 등록·dispatch | [CollectSource](../../backend/src/main/java/com/example/welfare/collect/service/CollectSource.java), [CollectSourceExecutionService](../../backend/src/main/java/com/example/welfare/collect/service/CollectSourceExecutionService.java) | enum/path key·adapter registry·batch 대상·detail 경로. adapter 추가만으로 모든 분기가 자동 연결되지는 않음 |
| Adapter 예시 | [CollectSourceAdapter](../../backend/src/main/java/com/example/welfare/collect/service/CollectSourceAdapter.java), [AbstractListCollectSourceAdapter](../../backend/src/main/java/com/example/welfare/collect/service/AbstractListCollectSourceAdapter.java), [YouthCollectSourceAdapter](../../backend/src/main/java/com/example/welfare/collect/service/YouthCollectSourceAdapter.java) | client/gateway·DTO·list binding. 복지로 central/local/detail/refresh adapter와 Gov24 경로를 유형에 맞게 비교 |
| 배치·후속 상세 | [CollectBatchService](../../backend/src/main/java/com/example/welfare/collect/service/CollectBatchService.java) | 매일 02:00 Asia/Seoul list, list diff·강제 상세·요일별 rotation. 신규 enum의 switch·소스별 실패 처리도 확인 |
| Payload | [RawApiPayloadService](../../backend/src/main/java/com/example/welfare/collect/service/RawApiPayloadService.java) | `RawApiPayload`, read/command repository, category·외부 ID·재직렬화·저장 실패·replay |
| 정책 저장 | [WelfareServiceMapper](../../backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java), [CollectItemSaver](../../backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java), [CollectPolicyAggregateApplyService](../../backend/src/main/java/com/example/welfare/collect/service/CollectPolicyAggregateApplyService.java) | DTO/aggregate → 정책 upsert·지역/tag·청년 관련성·sidecar apply |
| Sidecar | [NormalizedPolicyAggregate](../../backend/src/main/java/com/example/welfare/collect/normalization/NormalizedPolicyAggregate.java), [DeferredNormalizedPolicySidecarWriter](../../backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java), [NormalizedFactMergeSupport](../../backend/src/main/java/com/example/welfare/collect/normalization/NormalizedFactMergeSupport.java) | summary/term/fact merge·출처. sidecar table이 없으면 skip할 수 있으므로 실제 적재를 별도 확인 |
| 상세·재처리 | [BokjiroDetailCollectService](../../backend/src/main/java/com/example/welfare/collect/service/BokjiroDetailCollectService.java), [NormalizedPolicySidecarBackfillService](../../backend/src/main/java/com/example/welfare/collect/normalization/NormalizedPolicySidecarBackfillService.java) | live detail·refresh/gap-fill·raw backfill을 분리 |
| 실행 이력 | [ApiSyncLogService](../../backend/src/main/java/com/example/welfare/collect/service/ApiSyncLogService.java), [ApiSyncLog](../../backend/src/main/java/com/example/welfare/collect/entity/ApiSyncLog.java) | status converter/repository와 requested/saved/skipped/filtered/failed 집계·부분 성공 |
| 추천 projection | [CanonicalRecommendationReadModelRepository](../../backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java), [RecommendationCandidateProjection](../../backend/src/main/java/com/example/welfare/recommend/dto/RecommendationCandidateProjection.java) | sidecar 조회·hydrate와 원본 term/버킷의 구분 |
| 후보·점수 | [RetrievalService](../../backend/src/main/java/com/example/welfare/recommend/service/RetrievalService.java), [RuleScoringService](../../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java), [DefaultPriorityMatcher](../../backend/src/main/java/com/example/welfare/recommend/service/DefaultPriorityMatcher.java) | 후보·자격조건·가점·compat/category의 실제 사용 범위 |

신규 source는 수집 enum뿐 아니라 정책 entity/aggregate의 SourceType, raw 저장·DB 제약·source별 분기·테스트를 함께 점검합니다. 기존 raw 테이블을 listing/reference에 재사용할 수 있는지도 이 검토 없이 확정하지 않습니다.

## 7. 검증과 완료 판정

대표 sample 20~50건부터 시작하되 누락·미지정·알 수 없는 label·변경/삭제·다른 row grain·부분 실패 사례를 포함합니다. 표본 검사만으로 전체 inventory 완료를 선언하지 않습니다.

- **수집:** client 호출 → payload 저장 → mapper → saver/sidecar의 실제 저장을 대조합니다. `api_sync_logs`의 요청·저장·실패와 후속 detail/support 결과를 따로 확인합니다.
- **재처리:** external ID 중복·upsert, list/detail 소유권, 사라진 fact, 수동 보정 유지, raw 기반 backfill을 검증합니다.
- **호환:** API 대표 category·표시 필드·필터·추천 projection의 회귀를 확인하고 hard fact와 soft signal을 분리합니다.
- **운영:** 실행 환경·앱/DB·권한·호출량·비용·개인정보 영향을 먼저 정합니다. 임의 운영 SQL/seed나 실제 발송을 기본 검수에 넣지 않습니다.

최종 판정은 `진행 / 일부 필드 보류 / metadata-only / blocked` 중 하나로 남깁니다. [템플릿](policy-source-onboarding-template.md)에 허용/보류 필드, 근거, 필요한 provider 응답, 다음 작업과 제외 범위를 기록합니다. row 의미가 불명확하거나 listing을 정책처럼 넣으려는 경우에는 canonical 구현을 멈춥니다.

## 8. 소스별 검토 예시와 출처

아래는 기존 확장 조사에서 정리한 분류 후보입니다. 새 도입 전 공식 schema·권한·대표 payload로 다시 판정하며, 제안된 domain 이름을 구현 완료로 읽지 않습니다.

| Source/row | 분류·매핑 검토 | 기존 조사 출처 |
| --- | --- | --- |
| 고용24/워크넷 채용정보·채용행사·공채속보·기업정보 | listing. employer/position/location/salary/closing date 중심의 `job_listings`/`job_opportunities` 후보, 정책 lane과 분리 | [Work24 Open API](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiIntro.do?bbsClCd=OosccI71O3P2dBxVz5A40Q%3D%3D) |
| 정부지원일자리정보 | 사업/프로그램이면 정책형. 기관정보는 reference로 분리. `일자리` compat, 취업대상·연령·소득·참여조건 검토 | 위 Work24 출처 |
| 구직자취업역량 강화프로그램 | 제도형과 일정/회차를 분리. 목적·지원내용·훈련방식으로 `일자리`/`교육·직업훈련` bridge 판단 | 위 Work24 출처 |
| 마이홈 공공주택 모집공고 | listing. `housing_recruitments`/`housing_listings`, 정책과 연결만 허용 | [공급 공고 API](https://www.data.go.kr/data/15108420/openapi.do) |
| 마이홈 공공임대주택 단지정보 | listing/reference. 위치·단지·공급유형·세대수 등의 `housing_complexes` 후보 | [단지정보 API](https://www.data.go.kr/data/15110581/openapi.do) |
| 마이홈 예비입주자 대기현황 | reference/status feed. `housing_waitlist_stats` 후보, 직접 정책 카드화하지 않음 | [대기현황 API](https://www.data.go.kr/data/15108378/openapi.do) |
| 월세지원·보증료·전세대출 이자지원 제도 | 정책형. `주거` compat, 소득·무주택·청년/신혼·지역·임차 조건 확인 | 해당 제도의 공식 안내·조건 |
| 국가장학금 I유형·다자녀·국가근로·학자금대출 이자지원 | 제도/상품은 정책형. `교육·직업훈련`/`금융·생활지원` bridge, 교육·소득구간·학적·학점·가구 조건 확인 | [학자금지원정보](https://www.data.go.kr/data/15028252/fileData.do), [국가장학금 안내](https://www.kosaf.go.kr/ko/scholar.do?pg=scholarship05_12_01_01) |
| 지원가능대학·학기·구간 경곗값·지원금액표 | reference. `scholarship_reference`/`scholarship_eligibility_matrix` 후보. 제도에 연결하고 파생 정책 row로 flatten하지 않음 | 위 학자금 출처, [참조행렬 초안](../history/policy/policy-scholarship-reference-matrix-draft.md) |
| Gov24/보조금24 혜택 | 정책형. `serviceList → serviceDetail → supportConditions`로 core/detail/facts 검증. 분야·사용자·지원유형은 원본 label과 내부 코드를 구분하고 청년/compat는 파생 bridge로 유지 | [공공서비스 정보 API](https://www.data.go.kr/data/15113968/openapi.do), [현재 Gov24 상태](policy-gov24-lane-closeout.md#보류와-재검토) |

## 9. 초기 조사와 우선순위

### 2026년 4월 29일 로컬 관측 메모

당시 Docker `youth_welfare` 기록이며 현재 DB 기준선이 아닙니다. 원메모의 `welfare_services=3,376`과 아래 source 합계 `3,583`은 일치하지 않습니다. 집계 조건을 재구성하기 전 전체 건수·비율의 근거로 사용하지 않습니다.

| Source | 정책 | raw snapshot | age rows | income rows | deadline rows | detail URL rows |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| YOUTH | 2,299 | 2,299 | 1,695 | 24 | 1,140 | 723 |
| BOKJIRO_LOCAL | 1,169 | 4,352 | 24 | 0 | 0 | 1,169 |
| BOKJIRO_CENTRAL | 115 | 392 | 2 | 0 | 0 | 115 |

- 당시 `welfare_service_details=0`; tag는 KEYWORD 3,701 / LIFE_STAGE 3,257 / INTEREST_THEME 1,271 / TARGET_GROUP 616이었습니다.
- 당시 category는 기타 1,375 / 일자리 941 / 금융·생활지원 404 / 주거 246 / 참여·기회 190 / 교육·직업훈련 177 / 가족·돌봄 82 / 건강·의료 41 / 안전·위기 18 / 문화·여가 2로 적혀 있습니다. 분포 역시 위 총계와 합계가 맞지 않으므로 원메모의 불일치로 남깁니다.
- 당시 BOKJIRO_LOCAL의 `기타` 사례는 `대전 청년 월세지원`, `청년·신혼부부 전세보증금 반환보증 보증료 지원사업`, `대학생 학자금 대출이자 지원(경기도)`, `학자금 대출이자 지원사업`, `취업청년정착수당`, `청년 취업사진 촬영비 지원`입니다. taxonomy/compat/facts 분리의 조사 예시이며 title만으로 공식 분류를 확정하는 규칙은 아닙니다.

상세 설계 근거는 [정규화 조사](../history/policy/policy-normalization-research.md), [sample spike](../history/policy/policy-normalization-sample-spike.md), [schema 초안](../history/policy/policy-normalization-schema-draft.md), [bridge 규칙](../history/policy/policy-normalization-bridge-rules.md)을 참고합니다.

### 초기 확장 검토 순서

초기 제안은 `Gov24 → 정부지원일자리정보 → 구직자취업역량 강화프로그램` 순서였습니다. 비교 축은 canonical 적합도·공식 fact 강도·compat bridge 필요도·live 검증 비용입니다.

- Gov24는 list/detail/support 분리와 구조화 조건을 검증하는 기준 사례였습니다. 현재는 수집·label-first가 active이므로 신규 미구현 후보로 다시 취급하지 않습니다.
- 일자리정보는 사업과 기관/reference 분리 및 `일자리` compat 검증이 핵심입니다.
- 역량강화 프로그램은 제도/회차 분리와 `일자리 vs 교육·직업훈련`의 해석 비용을 먼저 확인합니다.

이 순서는 현재 전체 개발 우선순위나 실행 승인이 아닙니다. source 검토만으로 listing schema 도입, 외부 Gov24 공식 codebook import, YOUTH_MID stable-code SQL/backfill, 추천 lane 직접 연결까지 함께 승인하지 않습니다.
