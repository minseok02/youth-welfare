# Policy Normalization Current State

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 코드:

- [DeferredNormalizedPolicySidecarWriter.java](../backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java)
- [WelfareServiceRepository.java](../backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java)
- [CanonicalRecommendationReadModelRepository.java](../backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java)
- [RecommendationCandidateProjection.java](../backend/src/main/java/com/example/welfare/recommend/dto/RecommendationCandidateProjection.java)
- [RuleScoringService.java](../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)
- [DefaultPriorityMatcher.java](../backend/src/main/java/com/example/welfare/recommend/service/DefaultPriorityMatcher.java)

관련 문서:

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
- [policy-normalization-compat-storage-policy.md](../history/policy/policy-normalization-compat-storage-policy.md)
- [policy-post-local-closeout-track-split.md](./policy-post-local-closeout-track-split.md)

## 목적

`policy-normalization-*` 문서가 많아진 상태에서,
현재 실제 코드/로컬 검증 기준으로 무엇이 구현됐고 무엇이 아직 blocked 인지
한 문서에서 바로 볼 수 있게 정리합니다.

이 문서는 **현재 normalization source of truth 요약** 입니다.  
세부 drift, request template, 실험 기록, source 조사 메모는 개별 문서를 design history / research 로 읽습니다.

## 현재 구현된 핵심 범위

현재 코드 기준으로 이미 살아 있는 normalization 축은 아래입니다.

1. `YOUTH` canonical sidecar 저장
2. `YOUTH_MID_RAW_ALIAS` raw alias 보존
3. `youth_major` summary 정제
4. recommendation read-model projection hydrate
5. `YOUTH 0/0 income` repository pass-through
6. `교육 -> 교육·직업훈련` narrow scoring experiment

## 1. canonical sidecar 저장

현재 canonical sidecar의 중심 테이블은:

- `service_taxonomies`
- `service_taxonomy_terms`
- `service_facts`

입니다.

현재 로컬 실수집 검증 기준:

- `welfare_services=2363`
- `service_taxonomies=3708`
- `service_taxonomy_terms=7931`
- `service_facts=8257`
- `service_taxonomy_summary_slots=5674`

즉 `YOUTH` snapshot 기준으로 core row와 canonical sidecar 저장은 현재 로컬에서 정상동작 확인 상태입니다.

현재 local snapshot source 분포는:

- `YOUTH=2364`
- `BOKJIRO_CENTRAL=119`
- `BOKJIRO_LOCAL=1225`

즉 runtime collect 기준으로는 아직 `GOV24` source row 자체가 없습니다.
그래서 현재 `GOV24_*` summary slot density가 `0` 인 것은
writer/read-model 누락이라기보다,
local snapshot에 `Gov24` source 적재나 별도 import/backfill 이 아직 없기 때문입니다.

추가로 local replay closeout 기준:

- `slot_services=2331`
- `slot_education_services=110`
- `slot_services_YOUTH_MAJOR=2313`
- `slot_services_YOUTH_MID=2191`
- `slot_services_PROVISION_METHOD=1170`
- `slot_services_GOV24_SERVICE_FIELD=0`
- `slot_services_GOV24_USER_TYPE=0`
- `slot_services_GOV24_BENEFIT_TYPE=0`
- `slot_rows_YOUTH_MAJOR=2313`
- `slot_rows_YOUTH_MID=2191`
- `slot_rows_PROVISION_METHOD=1170`
- `slot_rows_GOV24_SERVICE_FIELD=0`
- `slot_rows_GOV24_USER_TYPE=0`
- `slot_rows_GOV24_BENEFIT_TYPE=0`

즉 summary slot dual-write/backfill 도 현재 로컬 snapshot에서 density 확인까지 끝난 상태입니다.
latest replay artifact(`/tmp/tmp.lP4I9NWUUU`) 기준 `SUMMARY_SLOT_METRIC` 도 이제
`slot_services_*` 뿐 아니라 `slot_rows_*` 를 같이 출력하므로,
apply/replay 양쪽에서 같은 축으로 raw row density를 바로 대조할 수 있습니다.
현재 local snapshot에서 실제로 채워지는 managed slot은 사실상 `YOUTH_MAJOR`, `YOUTH_MID`, `PROVISION_METHOD` 이고,
`GOV24_*` stable code/import-backfill 은 아직 열지 않았습니다.
다만 `Gov24` raw inventory 자체는 이미 확인돼 있고,
`serviceField/userType/benefitType` 는 1차 internal mapping draft가 정리된 상태입니다.
즉 현재 남은 문제는 “값을 아직 모른다”보다
“label-first canonical 구현을 먼저 열지, stable code/import-backfill 단계까지 deferred로 둘지”에 가깝습니다.
추가로 `YOUTH` 의 `PROVISION_METHOD` 는 이제 `applyMethodName` 오용 대신
공식 `plcyPvsnMthdCd` label을 우선 source로 쓰고, payload에 코드가 비는 경우에만 기존 신청방법 라벨을 compatibility fallback으로 유지합니다.
즉 `YOUTH_MID` stable code는 여전히 보류지만, `PROVISION_METHOD` 축은 공식 codebook이 있어 별도로 바로잡을 수 있는 상태입니다.
또 2026-05-18 local 재수집 기준으로 `YOUTH` LIST raw는 `pvsnInstGroupCd`, `plcyPvsnMthdCd`, `plcyAprvSttsCd`, `aplyPrdSeCd`, `bizPrdSeCd`, `mrgSttsCd`, `earnCndSeCd`, `plcyMajorCd`, `jobCd`, `schoolCd`, `sbizCd` 를 거의 전 row에서 관측한다.
다만 `jobCd`, `schoolCd`, `plcyMajorCd`, `sbizCd` 는 단일 코드만 오는 게 아니라 comma-delimited multi-code(`0013001,0013003` 등)도 실제로 섞여 있으므로,
다음 eligibility fact 단계는 scalar 가정이 아니라 split + trim + de-dup 규칙을 전제로 열어야 한다.
추가로 active local DB는 `normalization_code_sets` row는 있지만 `normalization_codes` 는 아직 `0건` 이다.
즉 현재 runtime에서 온통청년 공식 요건코드를 읽을 때는 DB seed lookup이 아니라
`YouthOfficialCodeSupport` 의 in-code official label map을 기준으로 해석하는 쪽이 더 안전하다.
이 단계의 목적은 code import/backfill reopening이 아니라,
future fact scope에서 multi-code를 잘못된 단일값으로 오해하지 않도록 parsing contract를 먼저 고정하는 것이다.
그 위에서 2026-05-18 기준 `earnCndSeCd` 는 첫 official eligibility fact로 좁게 열었다.
현재 local DB 분포는 `LIST 2566 / 2571`, `DETAIL 982 / 2568` 이 `earnCndSeCd` 를 보유하고, multi-code는 `0건` 이다.
분포도 `0043001=무관 2226`, `0043003=기타 313`, `0043002=연소득 27` 로 scalar shape가 유지되므로,
이번 단계에서는 `YOUTH_INCOME_CONDITION_TYPE` 을 별도 official fact로만 저장하고 추천/검색 gate에는 연결하지 않았다.
중요한 경계는 이것이 `earnMinAmt/earnMaxAmt` 를 대체하는 hard income rule이 아니라, 소득조건 **유형 설명 fact** 라는 점이다.
merge key도 code별 fan-out 대신 단일 `YOUTH_INCOME_CONDITION_TYPE` 으로 유지해, 향후 source 값이 바뀌더라도 stale fact가 누적되지 않게 했다.
그 다음 단계로 이 fact는 public 응답이 아니라 internal read-model/projection 경계에만 노출했다.
현재 `RecommendationCandidateProjection` 과 admin `recommendation-diagnostics` 는
`youthIncomeConditionTypeCode`, `youthIncomeConditionTypeLabel` 을 읽을 수 있지만,
recommendation scoring, retrieval filter, public policy/recommendation response는 아직 이 값을 소비하지 않는다.
의도는 “값이 저장되는가” 다음에 바로 “운영자가 읽을 수 있는가”를 닫되,
일반 사용자 UX나 추천 규칙에 의미를 과대투영하지 않는 것이다.
서버에서도 이 경계는 재수집 후 실제로 닫혔다. `87b49c3` 반영 직후엔 stale YOUTH raw 때문에 `service_facts` 와 diagnostics 값이 비어 있었지만,
`POST /api/admin/collect/youth` + `POST /api/admin/collect/youth-details` 재실행 후
`LIST raw earnCndSeCd = 2568 / 2569`, `DETAIL raw earnCndSeCd = 2564 / 2564`, `YOUTH_INCOME_CONDITION_TYPE fact_rows = 2567`
까지 올라왔고, admin diagnostics도 `youthIncomeConditionTypeCode/Label` 을 실제로 반환했다.
그 다음 official fact 후보 `jobCd` 는 같은 방식으로 열지 않았다. local `LIST raw` 기준 `jobCd` 는 `2568건` signal 중
`0013010=제한없음` 이 `1902건`, multi-code가 `111건` 으로 실제 조합값이 섞여 있다.
그래서 이번 단계에서는 `YOUTH_EMPLOYMENT_REQUIREMENT` 를 fan-out fact가 아니라 **단일 aggregate observation fact** 로 저장한다.
shape는 `fact_code='0013003,0013006'`, `text_value='미취업자, (예비)창업자'`, `operator=MEMBER`, `fact_merge_key='YOUTH_EMPLOYMENT_REQUIREMENT'` 이다.
이 값도 마찬가지로 internal read-model/projection 과 admin diagnostics에서만
`youthEmploymentRequirementCodes`, `youthEmploymentRequirementLabels` 리스트로 풀어 주고,
public policy/recommendation response, retrieval filter, scoring에는 아직 연결하지 않는다.
서버에서도 이 경계는 재수집 후 닫혔다. `ba0d3db` 반영 직후 `POST /api/admin/collect/youth`, `POST /api/admin/collect/youth-details` 를 다시 실행하자
`YOUTH LIST raw jobCd = 2568 / 2569`, `YOUTH DETAIL raw jobCd = 2569 / 2569`, `YOUTH_EMPLOYMENT_REQUIREMENT fact_rows = 2557`,
그중 multi-code aggregate fact가 `100건` 까지 생성되었다.
admin diagnostics에서도 `serviceId=672` 가 `youthEmploymentRequirementCodes=['0013010']`, `youthEmploymentRequirementLabels=['제한없음']`
으로 실제 노출되었고, multi-code fact 샘플은 `0013003,0013006,0013009 -> 미취업자, (예비)창업자, 기타` 형태로 DB에 저장되는 것이 확인됐다.
즉 현재 truth는 `jobCd` 도 raw -> aggregate fact -> internal read-model -> admin diagnostics 경계까지는 닫혔고,
남은 것은 이 값을 public UX나 추천 규칙으로 소비할지 여부뿐이다.
그 다음 후보 `sbizCd` 는 `jobCd` 와 같은 fan-out 위험은 있지만 더 좁은 범위에서 열 만하다.
local `LIST raw` 기준 signal은 `325건`, multi-code는 `25건` 이라 `schoolCd(149건)` 보다 구현 위험이 낮고,
의미도 `중소기업`, `여성`, `기초생활수급자`, `한부모가정`, `장애인`, `농업인`, `군인`, `지역인재` 처럼 직접적이다.
그래서 이번 단계에서는 `YOUTH_SPECIAL_REQUIREMENT` 를 역시 fan-out이 아닌 **단일 aggregate observation fact** 로 저장하고,
internal read-model/projection 과 admin diagnostics에만 `youthSpecialRequirementCodes/Labels` 로 노출한다.
의도는 특화요건 신호를 잃지 않고 관찰 가능하게 만드는 것이며, public response, retrieval filter, scoring은 그대로 둔다.
서버 `fb57377` 재수집 검증에서도 이 경계는 그대로 닫혔다.
`POST /api/admin/collect/youth`, `POST /api/admin/collect/youth-details` 뒤 `YOUTH LIST raw sbizCd = 2568 / 2569`,
`YOUTH_SPECIAL_REQUIREMENT fact_rows = 2557`, 그중 multi-code aggregate fact가 `24건` 생성되었고,
sample row는 `0014001,0014009 -> 중소기업, 기타`, `0014003,0014004 -> 기초생활수급자, 한부모가정`,
`0014003,0014004,0014005,0014008 -> 기초생활수급자, 한부모가정, 장애인, 지역인재` 형태였다.
admin diagnostics도 `serviceId=672` 에서 `youthSpecialRequirementCodes=['0014010']`,
`youthSpecialRequirementLabels=['제한없음']` 을 반환했다.
즉 현재 truth는 `sbizCd` 도 raw -> aggregate fact -> internal read-model -> admin diagnostics 경계까지는 닫혔고,
남은 것은 이 값을 public UX나 rule 소비로 넓힐지 여부뿐이다.

## 2. `YOUTH_MID_RAW_ALIAS` 현재 상태

현재 정책은:

- official `YOUTH_MID` token 은 canonical `YOUTH_MID`
- non-official variant 는 `YOUTH_MID_RAW_ALIAS`

로 보존합니다.

중요한 현재 구현 포인트:

- `DeferredNormalizedPolicySidecarWriter` 는 `YOUTH_MID` 와 `YOUTH_MID_RAW_ALIAS` 를 같은 refresh family로 봅니다
- 따라서 raw alias가 official token으로 정규화된 뒤 stale alias row가 남지 않게 처리합니다

즉 raw alias 보존은 유지하되, refresh 후 read-model 기준 stale row는 남기지 않는 상태입니다.

## 3. `youth_major` summary 현재 상태

현재 `youth_major` summary는 raw `category_main` 복사가 아니라:

- canonical major 1개로 collapse 가능한 경우만 채움
- multi-major면 `NULL`
- variant label은 canonical label로 normalize

규칙으로 정리돼 있습니다.

현재 writer / backfill SQL 모두 이 규칙을 따르도록 맞춰진 상태입니다.

## 4. recommendation read-model 현재 상태

현재 recommendation 쪽 canonical projection은:

- `CanonicalRecommendationReadModelRepository`
- `RecommendationCandidateProjection`

경계로 들어와 있습니다.

projection이 현재 담는 대표값:

- `unifiedCategoryCompat`
- `youthMajorLabel`
- `youthMidLabel`
- `provisionMethodLabel`
- `gov24ServiceFieldLabel`
- `gov24UserTypeLabel`
- `gov24BenefitTypeLabel`
- `applyEndDate`
- `interestThemes`
- `targetGroupsRaw`
- `targetGroupBuckets`
- `beneficiaryTerms`
- `factKeys`

현재 `youthMajorLabel` 은 slot-first / legacy fallback 으로 읽고 있고,
`youthMidLabel`, `provisionMethodLabel`, `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel`
도 같은 projection 경계에 먼저 실어 둔 상태입니다.

즉 raw sidecar를 추천 서비스가 직접 읽는 게 아니라,
추천 전용 projection을 통해 hydrate 하는 구조가 이미 코드에 있습니다.

또 policy summary/detail/ranking 응답은 이제 이 projection에서
`youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel`, `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel`
을 additive field로 같이 노출합니다.
관련 WebMvc contract도 목록/상세/랭킹/북마크 응답 기준으로 테스트 고정된 상태입니다.
추천 목록/refresh 응답도 같은 additive field를 projection 기준으로 노출합니다.
또 `RealtimeAiGateway` prompt도 이제 compat 분류 외에
`youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel`, `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel`
을 같이 실어
AI 재평가 입력에서 canonical summary를 직접 소비합니다.

아래 `SUMMARY_REASON_METRIC` / `real-openai artifact` 값은
**canonical summary prompt 영향도를 보던 실험/diagnostic slice** 로 읽습니다.
현재 broad-suite current baseline 자체는
[recommendation-current-state.md](../recommendation/recommendation-current-state.md),
[recommendation-operation-checklist.md](../recommendation/recommendation-operation-checklist.md)
쪽의 `A_top10_target=9->9`, `B_top10_target=1->1`, `A_fp/B_fp=same`, `reason_changed=0`
기준을 우선합니다.

그 위에서, latest local replay(`rule-only-invalid-key`) 기준으로는
`SUMMARY_REASON_METRIC A_reason_changed=8 B_reason_changed=0`
`A_reason_text_changed=0 B_reason_text_changed=0`
`A_reason_membership_changed=8 B_reason_membership_changed=0`
가 나왔습니다.
즉 현재 baseline에서 sample A의 변화는 실제 `ai_reason` 문장 변화가 아니라
top snapshot membership 변화이고, control sample B에서는 reason drift가 없습니다.

반대로 `USE_REAL_OPENAI_FOR_REPLAY=true CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY=true`
기준 latest artifact(`/tmp/tmp.TBDFrxfGqo`) 에서는
`A_reason_text_changed=15`, `B_reason_text_changed=15`,
`A_top10_target=3->5`, `B_top10_target=0->2`, `B_fp=different`
가 나왔습니다.
즉 live AI 경로에서는 canonical summary prompt 영향이 실제 reason text 변화로 이어지지만,
control sample B drift도 커서 아직 diagnostic 용도로만 보는 게 맞습니다.
artifact diff를 보면 sample A는 `text_changed 15 + membership_changed 8`,
sample B는 `text_changed 15 + membership_changed 0` 이고,
문장 패턴도 `직접적 도움`, `특정 분야에 국한`, `주거비 부담 완화` 같은 서술이
off/on 사이에 함께 바뀌었습니다.
즉 현재 단계의 live AI 결과는 “canonical summary prompt가 reason wording에 영향 없음”이 아니라,
“영향은 보이지만 control drift와 분리되지 않음”으로 해석하는 쪽이 맞습니다.
세부 패턴 분류는
[policy-normalization-live-ai-reason-patterns.md](../history/ai/policy-normalization-live-ai-reason-patterns.md)
에 따로 정리해 두었습니다.
그래서 replay 스크립트도 현재는 `real-openai` 모드에서
sample A 미개선을 hard fail로 보지 않고 warning으로만 남깁니다.
다음 replay부터는 summary stdout의 `SUMMARY_REASON_PATTERN` 과
artifact `ai-reason-pattern-summary.tsv` 를 먼저 보면,
`연관성이 낮`, `특정 분야에 국한`, `실질적인 도움이`, `주거비 부담`
같은 phrase drift를 TSV 전체를 다시 읽지 않고도 빠르게 볼 수 있습니다.
latest cache-clear `real-openai` artifact(`/tmp/tmp.6YybgXCbIo`) 기준으로는
`SUMMARY_REASON_PATTERN A_top_patterns=interest_fit:2,direct_help:1,job_opportunity:1`
`B_top_patterns=strong_help:1`
가 나왔습니다.
같은 run의 핵심 값은
`A_reason_text_changed=10`, `B_reason_text_changed=14`,
`A_reason_membership_changed=2`, `B_reason_membership_changed=0`,
`A_fp=different`, `B_fp=same` 이었습니다.

## 5. retrieval / repository 현재 상태

현재 repository 의미에서 이미 구현된 중요한 보정은:

- `YOUTH 0/0 income => pass-through`

입니다.

즉 `min_income=0 && max_income=0` 은 실제 소득 하드 게이트가 아니라
미지정 sentinel 로 보고 candidate pool 에서 막지 않습니다.

이 보정이 들어간 뒤에야
교육 experiment target row가 실제 candidate/result set 에 들어오기 시작했습니다.

## 6. scoring / priority 현재 상태

### RuleScoringService

현재 이미 구현된 것:

- canonical projection 병행 입력
- `BENEFICIARY_SUPPORT` bucket max-one bonus
- `recommend.priority.education-canonical-bonus.enabled` flag
- `compat=기타 + youth_major=교육 + priority=EDUCATION` narrow bonus

즉 education experiment 는 현재 **문서만 있는 상태가 아니라 코드에 들어와 있습니다**.

### DefaultPriorityMatcher

현재 matcher는 canonical full taxonomy 로 넘어가지 않았습니다.

대신 좁은 bridge만 구현돼 있습니다.

- `projection.unifiedCategoryCompat`
- `projection.applyEndDate`

우선 사용

즉 현재 priority contract 는 여전히 compat layer 중심입니다.

## 7. response 계약 현재 상태

현재 recommendation/search/detail/ranking 응답의 `unifiedCategory` 는
canonical taxonomy 대표값이 아니라
계속 **legacy compat category 의미** 로 유지합니다.

즉 canonical taxonomy는:

- inventory
- explanation
- future experiment hint

용 보조 신호이고,
현재 response 대표 category를 대체하지 않습니다.

## 8. local validation 현재 상태

현재 로컬에서 실제로 다시 확인된 것은:

- `POST /api/admin/collect/youth` actual collect success
- canonical sidecar row 저장 success
- `deploy/smoke/run-local-education-priority-replay.sh` success
- replay summary:
  - `A_top10_target=9->9`
  - `B_top10_target=1->1`
  - `A_fp=same`
  - `B_fp=same`
  - `reason_changed=0`

즉 normalization 은 “문서 설계” 수준이 아니라
실제 collect -> sidecar -> recommendation downstream 까지 로컬 검증이 끝난 상태입니다.

## 아직 blocked 인 것

현재 코드/로컬 기준으로 아직 blocked 인 normalization 항목은 아래입니다.

### external blocked

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` full codebook
- `GOV24_SUPPORT_CONDITION` full inventory
- `YOUTH_MID` stable code mapping

공통 이유:

- provider/operator/codebook 응답이 먼저 필요

### future infra/deploy memo

- 실제 서버/DB/secret 경계가 생긴 뒤의 전환 메모
- migration / datasource / deploy smoke 재정의

### storage model follow-up

- `service_taxonomies` 는 아직 legacy summary row 중심
- generic summary slot 저장 구조는 후속 설계 상태
- 기준 문서: [policy-normalization-summary-slot-storage-plan.md](../history/policy/policy-normalization-summary-slot-storage-plan.md)

## design history 로 읽을 문서

아래는 current-state 문서가 아니라 세부 판단/조사/실험 기록입니다.

### source onboarding / schema

- `policy-source-onboarding-playbook.md`
- `policy-normalization-schema-draft.md`
- `policy-normalization-sample-spike.md`
- `policy-listing-source-schema-draft.md`
- `policy-scholarship-reference-matrix-draft.md`

### recommendation bridge / compat / drift

- `policy-normalization-recommendation-read-model.md`
- `policy-normalization-recommendation-migration-order.md`
- `policy-normalization-compat-*`
- `policy-normalization-education-*`

### blocked source acquisition

- `policy-normalization-gov24-*`
- `policy-normalization-youth-mid-*`

## 지금 이 문서를 먼저 보면 좋은 경우

1. `정규화가 지금 코드에 어디까지 들어갔는지` 빠르게 알고 싶을 때
2. `education experiment가 문서만 있는지, 실제 코드에 있는지` 헷갈릴 때
3. `blocked source` 와 `현재 구현` 을 분리해서 보고 싶을 때
4. `README` 에서 normalization 문서가 너무 많아 어디서부터 읽어야 할지 모를 때

## 짧은 결론

1. `YOUTH` canonical sidecar 저장과 recommendation bridge는 현재 코드/로컬 기준으로 이미 살아 있습니다.
2. 현재 recommendation contract 는 여전히 compat layer 중심이고, canonical taxonomy 는 projection/bonus/hint 로 병행 사용됩니다.
3. 남은 큰 normalization 미완은 `GOV24_*` 와 `YOUTH_MID stable code` 처럼 external blocked 인 항목입니다.
