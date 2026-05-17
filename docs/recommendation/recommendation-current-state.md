# 추천 현재 동작 기준

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)

## 목적

이 문서는 현재 코드 기준으로 추천이 어떻게 동작하는지,
무엇이 현재 계약이고 무엇이 실험/보조 신호인지 빠르게 확인하기 위한 current-state 문서입니다.

현재 제품 해석은 `청년정책 통합포털 + 개인화 추천` 이며, 추천 재사용 전략도 군집 캐시보다 개인 캐시를 우선 검토하는 쪽으로 정리합니다.
현재 코드 기준 개인 캐시는 추천 payload 전체를 Redis에 저장하는 구조가 아니라, `non-personal refresh` 를 최근에 끝냈는지 나타내는 짧은 TTL 마커만 저장하고 실제 추천 row 는 계속 DB에서 읽는 형태입니다. 이 마커 key 는 `userKey` 뿐 아니라 현재 추천 규칙 버전(예: `educationCanonicalBonusEnabled`)도 함께 포함해, 앱 재기동으로 추천 규칙 플래그가 바뀐 뒤 이전 refresh 결과를 재사용하지 않게 합니다.

## 현재 추천 파이프라인

현재 추천 흐름은 아래 순서입니다.

1. `RetrievalService`
2. `RuleScoringService`
3. `AiScoringService`
4. `ReRankingService`
5. `RecommendationPersistenceService`

조회는 저장된 `user_recommendations` 를 읽는 구조입니다.
또한 `POST /api/recommendations/refresh?personal=false` 는 최근 same-user refresh 마커가 살아 있으면 재계산을 생략하고 최신 저장 row 를 그대로 반환합니다. 반대로 `personal=true` refresh 는 항상 실계산하며, 프로필/우선순위/탈퇴 변경 시 refresh 마커는 즉시 invalidate 됩니다.

즉 현재 개인화의 기본 단위는 군집이 아니라 사용자입니다. 군집은 현재 `youth_all` 단일 경계로만 유지하고, 실제 추천 응답 가속도 먼저 `userKey` 기준 캐시로 해결합니다. 나이대×소득분위 2D 군집은 사용자 규모와 hit-rate가 충분히 커졌을 때만 다시 검토합니다.

## 현재 retrieval 기준

기본 축:

- 상태 `ACTIVE/UPCOMING`
- 나이
- 지역
- 소득
- `regionCode` / `sido` 가 있으면 해당 지역과 직접 매칭되는 `BOKJIRO_LOCAL` 후보를 전국 정책보다 먼저 읽음

주의:

- `min_income=0 && max_income=0` 은 source와 무관하게 미지정 sentinel 로 보고 pass-through
- 복지로 계열은 소득 구조화 값이 약해서 사실상 pass-through가 많음

즉 현재 candidate pool 은 “정확한 hard gate 전부” 가 아니라
`구조화된 것은 필터`, 나머지는 `태그/후속 scoring 보조` 에 가깝습니다.
다만 `2026-05-17` 운영 진단 기준으로는 인천 `REAL_USER` no-priority 세그먼트에서 `2736/3257/3281/3575/3714` 같은 `BOKJIRO_LOCAL / 기타` 후보가 youth/age filter나 scoring에서 떨어진 것이 아니라, 애초에 SQL retrieval 150건 안에 들어오지 못하는 경계가 확인됐습니다. 이를 줄이기 위해 현재 base/latest region query는 지역 매칭 `BOKJIRO_LOCAL` 후보를 전국 정책보다 먼저 정렬합니다. 서버 `fc5523a` 반영 후 재진단에서는 위 후보들이 모두 `inBaseRetrieval=true` 로 바뀌었고, `2736` 은 `PRESENT_IN_SAVED_BATCH(savedRank=8)` 까지 올라왔으며 `3257/3281/3575/3714` 는 실제로는 저소득/특수대상/지역/고위험군 조건 불일치에 가까운 `FILTERED_BY_PRIMARY_AUDIENCE_RELEVANCE` 경계로 읽는 편이 맞습니다. 즉 현재 다음 병목은 더 이상 SQL retrieval 진입 자체가 아니라 **local 후보의 primary audience 적합도와 saved batch 상위권 경쟁력** 쪽입니다.

## 현재 scoring 기준

### rule score

현재 핵심 신호:

- interest match
- keyword match
- target group match
- deadline soon

제거된 것:

- `onlineApply`
- 단순 `sourceType=YOUTH`
- 구조화되지 않은 큰 지원금 액수

### priority weight

현재 우선순위는 `rule_base_score * maxApplicableWeight` 구조입니다.

즉 여러 priority가 동시에 맞아도 최고 배율 하나만 적용합니다.

## 현재 canonical bridge

현재 recommendation 은 canonical full migration 상태가 아닙니다.

대신 아래가 이미 들어와 있습니다.

- `CanonicalRecommendationReadModelRepository`
- `RecommendationCandidateProjection`
- `unifiedCategoryCompat`
- `youthMajorLabel`
- `factKeys`
- target group bucket
- `projection.youthRelevant` 우선 사용, heuristic은 projection 부재 시 fallback
- `projection.audienceRelevanceBonus`, `projection.specialTargetBuckets` 우선 사용, legacy text heuristic은 fallback
- priority 매칭은 `projection.priorityBuckets` 우선 사용, compat 문자열 비교는 fallback
- education narrow experiment도 `projection.educationPriorityBoostEligible` 우선 사용, raw compat+youthMajor 조합은 fallback
- recommendation response의 `unifiedCategory` 는 여전히 compat contract지만, 응답 생성 시 projection compat 값을 우선 사용
- policy/search/detail/ranking/bookmark 응답도 `unifiedCategory` 의미는 compat contract를 유지하되, 값은 projection compat를 우선 사용

즉 canonical sidecar는 현재 recommendation 의 보조 입력입니다.

## 현재 response 계약

`RecommendationResponse.unifiedCategory` 는
아직 canonical taxonomy 대표값이 아니라
계속 legacy compat category 의미로 유지합니다.

즉 current contract는:

- response 대표 category = compat
- canonical taxonomy = hint / projection / experiment signal

입니다.

## 현재 education 실험 상태

이미 코드에 들어간 상태:

- `recommend.priority.education-canonical-bonus.enabled`
- `compat=기타 + youth_major=교육 + priority=EDUCATION`
- `RuleScoringService` narrow bonus

즉 education experiment는 설계 문서만 있는 것이 아니라
현재 코드/로컬 검증 기준으로 살아 있습니다.

## 현재 AI score 해석

현재 `ai_score` 는 deterministic truth가 아닙니다.

현재 제품 해석:

- `rule-only` replay = hard verification baseline
- `real-openai` replay = diagnostic / exploratory
- same prompt / seed / fingerprint 에도 drift 가능

즉 `ai_score exact equality` 는 현재 제품 보장 범위가 아닙니다.

## 현재 로컬 검증 기준

현재 로컬에서 다시 확인된 것은:

- auth/runtime smoke 통과
- recommendation click smoke 통과
- actual collect 이후 downstream replay 통과
- `education replay` rule-only 성공
- broad regression 통과

대표 replay 결과:

- `A_top10_target=9->9`
- `B_top10_target=1->1`
- `A_fp=same`
- `B_fp=same`
- `reason_changed=0`

즉 current local 기준으로는:

- collect
- sidecar
- recommendation downstream
- CTR click instrumentation

이 다시 이어져 있습니다.

## 현재 병목/주의점

### 1. collect 이후 snapshot 품질 의존

recommendation/replay 는 collect와 sidecar snapshot 품질에 직접 의존합니다.

### 2. real OpenAI latency / variability

`rule-only` 와 달리 real OpenAI 모드에서는:

- 응답 시간 증가
- `ai_score` drift

가 남습니다.

### 3. CTR raw count는 늘었지만 tuning signal은 아직 bounded synthetic-heavy

클릭 추적 경계 자체는 현재 정상입니다.

- 추천 응답의 `serviceId + logId` 로 정책 상세 진입 시
- `recommendation_logs.is_clicked=1` 이 실제 DB에 기록됨

즉 현재 병목은 click instrumentation bug가 아니라,
현재 로그가 실제 품질 튜닝에 써도 되는 신호인지의 문제입니다.

`2026-05-17` local audit 기준:

- `USER_COHORT=all`
- total recommendation logs: `4143`
- clicked logs: `31`
- overall CTR: `0.75%`
- clicked users / services: `31 / 2`
- fallback sent/clicked: `1067 / 0`
- AI sent/clicked: `3022 / 28`
- example logs/users: `4119 / 458`
- bounded-local logs/users: `6 / 1`
- real non-example logs/users: `18 / 3`
- weight bucket:
  - `0.40:0.60` -> `3630 sent / 28 clicked / 0.77%`
  - `0.60:0.40` -> `366 sent / 3 clicked / 0.82%`
  - `0.80:0.20` -> `147 sent / 0 clicked / 0.00%`
- all-cohort readiness: `DEFERRED_NO_REAL_USER_TRAFFIC`
- `USER_COHORT=bounded_local`
  - total recommendation logs: `6`
  - clicked logs: `1`
  - clicked users / services: `1 / 1`
  - readiness: `DIAGNOSTIC_BOUNDED_LOCAL_TRAFFIC`
- `USER_COHORT=real_non_example`
  - total recommendation logs: `18`
  - clicked logs: `3`
  - readiness: `DEFERRED_CLICK_SAMPLE_THIN`

즉 raw click 수와 weight bucket 개수만 보면 review 문턱은 넘었지만, current gate는 그걸로 reopen 하지 않습니다.
현재 `REAL_NON_EXAMPLE` 로그도 이번에 연 `run-local-real-non-example-recommendation-seed-smoke.sh` 가 만든 local synthetic account `1명` 뿐이라 current signal은 아직 **실사용 품질 신호**가 아니라 **synthetic-heavy local baseline + cohort drill** 로 읽는 편이 맞습니다.
게다가 클릭도 여전히 `2`개 서비스(`2622`, `3688`)에만 몰려 있고 fallback click은 `0` 이라,
이 상태에서 곧바로 weight를 바꾸면 smoke 계정 편향과 특정 서비스 편향을 함께 품질 신호로 과대해석하게 됩니다.
따라서 recommendation 쪽의 다음 active 작업은 direct weight 변경이 아니라
bounded/local seed를 넘어서는 로그 기준선 확보, concentration audit와 diversity/fallback 경계 재확인입니다.

참고로 로컬 bounded drill 경로는 이제 따로 있습니다.

- `deploy/smoke/run-local-real-user-gate-drill-smoke.sh`
- 최근 `LOCAL_REAL_NON_EXAMPLE_SEED` 사용자 `3명`을 일시적으로 `REAL_USER` 로 승격
- all-cohort CTR readiness: `READY_FOR_WEIGHT_REVIEW`
- all-cohort concentration `real_user_cohort_gate`: `READY_REAL_USER_COHORT`
- admin summary/breakdowns `realUserTrafficGateInWindow`: `READY_REAL_USER_TRAFFIC`
- admin summary/breakdowns `recommendationReviewGate`: `READY_CONCENTRATED_TOP1_REVIEW`
- top1 leader signal summary: `MIXED_REAL_USER_LEADER`

즉 gate 전이 로직 자체는 현재 코드 기준으로 정상입니다. 남은 blocker는 "gate가 안 열린다"가 아니라, 운영에서 읽을 수 있는 진짜 `REAL_USER` 표본이 아직 없다는 점입니다.

### 4. 저장 추천 편중

CTR readiness와 별개로, 최신 `user_recommendations` batch 자체도 현재 꽤 집중되어 있습니다.

`2026-05-17` local concentration audit 기준:

- `USER_COHORT=all`
- latest batch `4119 rows / 462 users / 113 distinct services`
- real-user cohort gate: `DEFERRED_NO_REAL_USER_COHORT`
- latest batch signal quality는 `LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH`
- top1 leader:
  - `2622 청년월세 지원사업`
  - `278 / 462 users`
  - `60.17%`
- latest source distribution:
  - `YOUTH 1466`
  - `GOV24 964`
  - `BOKJIRO_CENTRAL 904`
  - `BOKJIRO_LOCAL 785`
- latest category distribution:
  - `금융·생활지원 1353`
  - `주거 1113`
  - `교육·직업훈련 700`
  - `일자리 666`
- `USER_COHORT=bounded_local`
  - latest batch `6 rows / 1 users`
  - concentration readiness: `CONCENTRATED_TOP1`
  - signal quality: `BOUNDED_LOCAL_ONLY_COHORT`
- `USER_COHORT=local_real_non_example_seed`
  - latest batch `18 rows / 3 users`
  - real-user cohort gate: `DIAGNOSTIC_LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT`
  - signal quality: `LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT`
- `USER_COHORT=real_user`
  - latest batch `0 rows / 0 users`
  - real-user cohort gate: `DEFERRED_EMPTY_REAL_USER_COHORT`
  - signal quality: `EMPTY_REAL_USER_COHORT`

우선순위가 완전히 무시되는 상태는 아닙니다.
다만 현재 latest batch의 `bounded local 1명 + local real-non-example seed 3명` 모두 실사용자가 아니라 로컬 seed 계정이고, `real_user` 는 여전히 `0명` 입니다. 이 수치는 제품 실사용 baseline이라기보다 로컬 smoke/validation baseline으로 해석해야 합니다.
같은 날 새 `8명` no-priority bounded smoke를 다시 만들면 top1은 다시 `2622 8/8 (100%)` 로 잠기고, 스크립트도 `audit_user_cohort=example`, `signal_quality=SYNTHETIC_SIGNUP_SAMPLES` 를 함께 출력합니다.
즉 최근 no-priority 조정 실험은 진단 자료로는 남지만, 지금 시점의 practical reading은 “분산이 확정됐다”가 아니라 **synthetic-heavy baseline 안에서만 반복 측정이 이뤄지고 있다** 쪽입니다.

- `HAS_PRIORITY` 사용자 `38`
- `NO_PRIORITY` 사용자 `416`
- `NO_PRIORITY` top1 대표는 `청년월세 지원사업(2622)` `261명`
- 같은 `NO_PRIORITY` 구간에서 `청년 웰컴페이(이사비) 지원사업(3611)` 가 `39명`, `청년내일저축계좌(2571)` 가 `107명`의 top1까지 올라와 있어, 최근 완화 로직이 신규/최근 refresh 사용자에선 일부 분산을 만들고 있습니다.
- `HAS_PRIORITY` 쪽에서는 `드림나래(3688)` `12명`, `지역인재육성을 위한 장학금 지원(6790)` `11명`, `청년월세 지원사업(YOUTH 1411)` `5명` 등 일부 차이가 보입니다.

다만 no-priority candidate audit 기준 top5 source rank는 아직 `BOKJIRO_CENTRAL -> BOKJIRO_CENTRAL -> BOKJIRO_LOCAL -> GOV24 -> GOV24` 로 고정되고, top1도 여전히 `2571/2622` 둘 사이에서만 갈라집니다. 즉 현재 병목은 `priority 미반영` 보다는 **same rule peer 안에서 AI 차이가 top1을 결정하고, 같은 category bucket 안 local/source 후보가 뒤로 밀리는 구조** 쪽으로 해석하는 편이 맞습니다.
최근 no-priority retrieval pool 재배열과 top-band rotation 이후에도 overall 판정은 아직 `CONCENTRATED_TOP1` 이므로, 효과는 “분산 시작” 수준으로 보고 추가 보정 여부를 계속 판단해야 합니다.
추가로 `2026-05-17` 새 `8명` no-priority bounded smoke에서는 top1이 다시 `2622 8/8` 로 잠겼고, `NO_PRIORITY_TOP_BAND=0.20` 실험도 실제 분산 효과를 만들지 못했습니다. 따라서 다음 액션은 band 상수 추가 조정보다 **주거 후보군 내부 score gap과 no-priority AI trust 해석** 을 먼저 보는 쪽이 맞습니다.

### 5. fresh reset 뒤 collect/replay 전제

current PostgreSQL mainline에서 canonical sidecar 자체는 integrated schema의 일부입니다.

다만 fresh reset 뒤에는 replay가 기대하는 policy snapshot이나 canonical read-model 데이터가 비어 있을 수 있으므로,
local helper/replay smoke는 integrated schema 존재 여부와 collect/replay precondition을 먼저 확인하게 보강돼 있습니다.

## 지금 정상으로 보는 것

아래는 현재 정상 범주입니다.

- `rule-only` replay 기준 target row 개선
- control sample count 유지
- `real-openai` 에서 score drift가 있어도 input trace는 동일

## 지금 장애로 보는 것

- collect/snapshot 없이 replay 자체가 불가능
- canonical sidecar row가 비어 projection이 깨짐
- rule-only baseline 에서도 target improvement가 사라짐
- retrieval/scoring/re-ranking contract가 regression 으로 무너짐

## 요약

1. 현재 recommendation contract는 아직 compat 중심입니다.
2. canonical sidecar는 projection/bonus/hint 로 병행 사용됩니다.
3. education experiment는 이미 코드에 들어가 있고 local replay로 검증됐습니다.
4. `ai_score` exact match는 현재 제품 보장 범위가 아닙니다.
5. notification 후보 선택은 현재 `[A, A, B?]` 슬롯 배치입니다.
6. 운영 지표는 `GET /api/admin/dashboard/summary` 에서 collect/recommendation/notification/search/user_pii_sync 묶음으로 조회합니다.
   recommendation 섹션에는 현재 active weight, 누적 recommendation log 수, latest clicked 시각, 최근 7일 weight bucket 분포, `trafficMixInWindow(example/boundedLocal/localRealNonExampleSeed/realUser logs/users/clicked users)`, `realUserTrafficGateInWindow`, `recommendationReviewGate`, 그리고 `latestBatchConcentration` 이 포함됩니다.
   현재 로컬 summary smoke 값은 `realUserTrafficGateInWindow=DEFERRED_NO_REAL_USER_TRAFFIC`, `recommendationReviewGate=DEFERRED_NO_REAL_USER_TRAFFIC`, `top1LeaderServiceId=2622`, `top1LeaderSharePct=60.17`, `top1LeaderUserMix.exampleUsers=274`, `top1LeaderUserMix.boundedLocalUsers=1`, `top1LeaderUserMix.localRealNonExampleSeedUsers=3`, `top1LeaderUserMix.realUserUsers=0`, `top1LeaderSignalSummary=LOCAL_SEED_WITHOUT_REAL_USER_LEADER`, `concentrationReadiness=CONCENTRATED_TOP1`, `realUserCohortGate=DEFERRED_NO_REAL_USER_COHORT`, `signalQuality=LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH` 입니다.
   `GET /api/admin/dashboard/recommendation-breakdowns` 도 같은 `realUserTrafficGateInWindow`, `recommendationReviewGate` 를 내려주고, 추가로 `latestBatchConcentration` 에 `latestBatchRows/users/distinctServices`, `top1Leader*`, `top1LeaderUserMix`, `top1LeaderSignalSummary`, `concentrationReadiness`, `realUserCohortGate`, `signalQuality` 를 포함합니다.
   또한 `topRepeatedServices`, `top1Services` 도 같이 내려 current latest batch에서 어떤 서비스가 반복 노출되고 top1을 나눠 갖는지 API에서 바로 읽을 수 있고, 각 row의 `userMix(example/boundedLocal/localRealNonExampleSeed/realUser/realNonExample users)` 로 leader 편중이 어느 cohort에서 왔는지도 바로 확인할 수 있습니다.
   현재 로컬 breakdown smoke 값은 `recommendationReviewGate=DEFERRED_NO_REAL_USER_TRAFFIC`, `top1LeaderServiceId=2622`, `top1LeaderSharePct=60.17`, `top1LeaderUserMix.exampleUsers=274`, `top1LeaderUserMix.boundedLocalUsers=1`, `top1LeaderUserMix.localRealNonExampleSeedUsers=3`, `top1LeaderUserMix.realUserUsers=0`, `top1LeaderSignalSummary=LOCAL_SEED_WITHOUT_REAL_USER_LEADER`, `concentrationReadiness=CONCENTRATED_TOP1`, `realUserCohortGate=DEFERRED_NO_REAL_USER_COHORT`, `signalQuality=LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH`, `topRepeatedServices[0].serviceId=2622`, `topRepeatedServices[0].userMix.exampleUsers=453`, `topRepeatedServices[0].userMix.localRealNonExampleSeedUsers=3`, `top1Services[0].serviceId=2622`, `top1Services[0].userMix.exampleUsers=274`, `top1Services[0].userMix.localRealNonExampleSeedUsers=3`, `top1Services[0].userMix.realUserUsers=0` 입니다.
   프론트엔드에는 이제 `ROLE_ADMIN` 전용 `/admin/dashboard` consumer가 추가되어, summary/breakdown gate와 leader cohort mix를 브라우저에서 바로 읽을 수 있습니다. 같은 화면에서 collect/search/user-pii-sync 핵심 지표와 최근 collect failure, zero-result keyword도 함께 읽습니다. 관리자가 아닌 사용자가 직접 접근하면 홈에서 안내 토스트를 보여 줍니다. 현재 local verification은 `frontend npm run lint`, `frontend npm run build` 까지만 닫혀 있고 브라우저 수동 QA는 아직 별도 미실행입니다.
   `recentFallbackSamples`, `recentClickedSamples`, `repeatExposureGroups` 각 row에는 계속 `userCohort(EXAMPLE_SMOKE/BOUNDED_LOCAL/LOCAL_REAL_NON_EXAMPLE_SEED/REAL_USER)` 가 포함되어, 상세 triage 시 bounded local seed, local real-non-example seed, real user를 분리해서 읽을 수 있습니다.
   collect 섹션에는 최근 실패 run 목록, search 섹션에는 최근 7일 0건 검색 수가 포함됩니다.
   `trend` 섹션에는 collect/recommendation/search 의 1일/7일/30일 추세가 포함됩니다.
7. 추천 가중치/프롬프트 재조정은 CTR readiness audit가 `READY_FOR_WEIGHT_REVIEW` 를 줄 때에만 reopen 합니다.

## Gov24 추적 결론

`2026-05-17` 기준 Gov24 추천 추적은 여기서 1차 closeout으로 봅니다.

- Gov24가 추천에서 아예 안 보이는 문제는 아닙니다.
- fresh batch 기준 병목은 source 전체 억압보다 **개별 정책 제약 + 사용자 맥락 차이** 쪽에 더 가깝습니다.

대표 해석:

- `4689` 계열
  - 기본 fresh user에선 `rule` 이 낮아 약할 수 있습니다.
  - 하지만 `주거` 관심 + `HOUSING` priority fresh user bounded smoke에선 Gov24 주거 서비스 `5728` 이 `rank2`, `rule=54`, `ai=70`, `final=0.63261` 까지 올라왔습니다.
  - 즉 Gov24 주거 계열이 구조적으로 rule-side에서 막혀 있는 상태로 일반화하면 안 됩니다.

- `7193` 계열
  - 특정 fresh user에선 `안산 거주 초·중·고·대학생 장학금` 성격 때문에 `ai=0` 으로 약할 수 있습니다.
  - 하지만 `교육·직업훈련` 관심 + `EDUCATION` priority + `경기도/안산시` fresh user bounded smoke에선 교육 Gov24 `6790` 이 `rank1`, `rule=27`, `ai=80`, `final=1.03936` 으로 올라왔습니다.
  - 즉 교육/장학금 Gov24 전체가 AI에서 구조적으로 밀린다고 보기도 어렵습니다.

현재 practical 해석:

1. Gov24 source 자체를 별도 가산해 억지로 올릴 단계는 아닙니다.
2. fresh user 문맥에 따라 어떤 Gov24는 충분히 상위권까지 올라옵니다.
3. 다음 reopen이 필요하면 source 일반론보다 **개별 정책군** 기준으로 봐야 합니다.
   - 주거/월세보증
   - 지역 장학금
   - 창업/소상공인
