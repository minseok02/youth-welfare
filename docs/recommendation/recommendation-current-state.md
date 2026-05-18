# 추천 현재 동작 기준

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
- [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
- [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md)
- [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md)

## 목적

이 문서는 현재 코드 기준으로 추천이 어떻게 동작하는지,
무엇이 현재 계약이고 무엇이 실험/보조 신호인지 빠르게 확인하기 위한 current-state 문서입니다.

현재 제품 해석은 `청년정책 통합포털 + 개인화 추천` 이며, 추천 재사용 전략도 군집 캐시보다 개인 캐시를 우선 검토하는 쪽으로 정리합니다.
현재 코드 기준 개인 캐시는 추천 payload 전체를 Redis에 저장하는 구조가 아니라, `non-personal refresh` 를 최근에 끝냈는지 나타내는 짧은 TTL 마커만 저장하고 실제 추천 row 는 계속 DB에서 읽는 형태입니다. 이 마커 key 는 `userKey` 뿐 아니라 현재 추천 규칙 버전(예: `educationCanonicalBonusEnabled`)도 함께 포함해, 앱 재기동으로 추천 규칙 플래그가 바뀐 뒤 이전 refresh 결과를 재사용하지 않게 합니다.

## 현재 판단

`2026-05-18` 기준 recommendation 트랙의 현재 상태는 **bug closeout + 제품 판단 deferred** 입니다.

즉 지금까지 닫힌 것은 아래입니다.

- `REAL_USER` gate, concentration gate, dashboard review gate가 운영 서버에서 실제로 열리는지 여부
- `3686` 편중이 전역 retrieval/rerank bug인지, 아니면 동질 no-priority 인천 세그먼트 영향인지 여부
- 인천 `BOKJIRO_LOCAL` 후보가 retrieval SQL에 못 들어오는 문제
- `FILTERED_BY_YOUTH_OR_AGE` 가 실제로는 primary audience mismatch인지 여부
- `GET/POST /api/recommendations` 응답 순서와 persisted rank 불일치 여부
- diagnostics `rerankCurrent*` 와 saved batch `latestSaved*` 차이가 버그인지, pre-AI trace와 persisted AI 결과 차이인지 여부
- `2736` 이 왜 낮은지에 대한 원인 분석

반대로 아직 열지 않은 것은 아래입니다.

- `2736` 같은 local 청년 정책을 더 올릴지 여부
- local 정책에 `interest/theme`, 지역 적합성, direct benefit signal을 더 강하게 구조화할지 여부
- source/category balancing 또는 AI prompt/input 강화를 제품적으로 할지 여부

즉 현재 남은 것은 구현 결함보다 **local 청년 정책을 더 적극적으로 밀고 싶은지에 대한 제품/모델링 선택** 입니다.

이 선택을 실제로 다시 열 때는 [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md) 기준으로
`local 신호 구조화 -> diversity/balancing -> direct tuning` 순서를 먼저 고릅니다.
현재 quick 결론은 [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md) 에 따로 고정합니다.

## 다시 열 조건

다음 중 하나가 생길 때만 recommendation 트랙을 다시 여는 편이 맞습니다.

1. 운영 `REAL_USER` 기준으로 새로운 rank mismatch, click mismatch, cache mismatch, diagnostics mismatch 같은 **재현 가능한 버그** 가 다시 보일 때
2. 운영 지표상 local 청년 정책 노출이 제품 기대보다 약하다는 **명시적 제품 목표** 가 생길 때
3. `2736` 류 local 정책의 `interest/theme` 또는 지역 적합성 신호를 더 구조화하자는 **구체적 모델링 과제** 가 승인될 때

그 전까지는 이 트랙을 direct weight tuning이나 ad-hoc score patch로 reopen 하지 않습니다.

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
다만 `2026-05-17` 운영 진단 기준으로는 인천 `REAL_USER` no-priority 세그먼트에서 `2736/3257/3281/3575/3714` 같은 `BOKJIRO_LOCAL / 기타` 후보가 youth/age filter나 scoring에서 떨어진 것이 아니라, 애초에 SQL retrieval 150건 안에 들어오지 못하는 경계가 확인됐습니다. 이를 줄이기 위해 현재 base/latest region query는 지역 매칭 `BOKJIRO_LOCAL` 후보를 전국 정책보다 먼저 정렬합니다. 서버 `fc5523a` 반영 후 재진단에서는 위 후보들이 모두 `inBaseRetrieval=true` 로 바뀌었고, `2736` 은 `PRESENT_IN_SAVED_BATCH(savedRank=8)` 까지 올라왔으며 `3257/3281/3575/3714` 는 실제로는 저소득/특수대상/지역/고위험군 조건 불일치에 가까운 `FILTERED_BY_PRIMARY_AUDIENCE_RELEVANCE` 경계로 읽는 편이 맞습니다. 이후 `4c93550` diagnostics로 `2736 latestSavedAiScore=40`, `3686/3282/3252 latestSavedAiScore=80/95/90` 이 확인됐고, `2736` 의 입력 신호도 `인천 동구`, `LIFE_STAGE=청년` 외 direct interest/theme가 약해 현재 `인천 중구 / 미취업 / 25세` 사용자에게 경쟁 후보보다 덜 직접적으로 읽힌다는 점이 설명됐습니다. 즉 현재 다음 병목은 버그라기보다 **local 후보 신호를 더 강하게 넣고 싶은지에 대한 제품/모델링 판단** 쪽입니다.

그 뒤 `2026-05-18` bounded signal gap audit를 같은 family(`2736,3257,3281,3575,3714`)에 다시 태운 결과, 특정 latest batch 사용자 기준으로는 이 다섯 건이 다시 전부 `NOT_IN_SQL_RETRIEVAL` 로 관측됐습니다. competitor `672/625/650` 는 `YOUTH` `주거/건강·의료` 축에서 direct `benefit/program` 과 `interest/theme` 신호가 구조화돼 있었지만, target family는 support summary 안의 `월세보증금`, `융자`, `생활안정자금`, `바우처`, `돌봄서비스` 같은 값이 mostly unstructured 상태였습니다. 그래서 현재 lane 1 첫 구현은 `BOKJIRO_LOCAL` summary/provision text에서 `INTEREST_THEME` 와 program `KEYWORD` 를 derived signal로 올리고, exact-region local retrieval 안에서는 `searchYouthRelevant=true`, `unifiedCategory!=기타` 를 pure recency보다 먼저 보게 좁게 보정하는 쪽으로 잡습니다. 이 단계도 여전히 global weight patch가 아니라 **local signal structuring + bounded retrieval ordering** 범위입니다.

서버에서 이 bounded structuring을 재검증하면 `3257` 은 `주거`, `주거/생활지원`, `융자/금융지원/주거지원/월세보증금/주거급여지원` 으로, `3281` 은 `금융·생활지원`, `생활지원`, `융자/금융지원/생활안정자금` 으로 실제 구조화됐습니다. 다만 같은 latest batch 사용자 기준으로는 target family 전부가 여전히 `NOT_IN_SQL_RETRIEVAL` 이고, 남은 공통점은 `3257/3281/3575/3714` 의 `searchYouthRelevant=false` 였습니다. 그래서 lane 1 의 다음 bounded step은 broad mixed life stage 전부를 youth 정책으로 올리는 것이 아니라, **`BOKJIRO_LOCAL + 청년 포함 life stage + structured local support signal` 조합에만 `searchYouthRelevant` bridge를 여는 것**으로 좁힙니다. 즉 이번 단계도 여전히 retrieval 경계 안으로 local 청년 후보를 들이기 위한 bounded fix 이고, global score/weight patch는 아닙니다.

서버에서 이 bridge까지 다시 태운 뒤에는 `3257/3281/3575/3714` 의 `searchYouthRelevant=true` 가 실제 DB에 반영됐습니다. 그럼에도 같은 `target_user_key=05c03e8cfda140cb8c410ac9dbc098fc` bounded audit에서는 target family 전부가 계속 `NOT_IN_SQL_RETRIEVAL` 이었고, saved batch나 diagnostics retrieval 안으로 새로 들어온 target도 없었습니다. 즉 `searchYouthRelevant` 병목 하나는 닫혔지만 retrieval movement는 아직 없고, 현재 남은 다음 evidence 수집 축은 **region projection / exact-region ordering 내부 우선순위 / 150-row candidate window 안에서 이 family가 왜 계속 밀리는지** 쪽입니다.

이 다음 evidence lane은 [recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md) 와 `run-local-recommendation-region-window-audit.sh` 로 actual retrieval branch(`REGION_CODE`/`SIDO`) 안에서 target family의 실제 rank/in-window 여부를 읽는 경로로 고정합니다.

그 다음 bounded fix는 `REGION_CODE` branch 안에서 `BOKJIRO_LOCAL + same-sido + searchYouthRelevant=true + unifiedCategory!=기타` 후보를 exact-region local 뒤의 fallback tier로 편입하는 것입니다. 이건 broad same-sido 전체를 푸는 것이 아니라, `3257/3281` 같은 structured local youth-support 후보가 아예 branch 바깥으로 떨어지는 문제만 겨냥합니다.

서버에서 이 same-sido fallback까지 다시 태운 결과, `2736/3257/3281/3575` 는 `REGION_CODE:28110` branch 안으로 실제로 들어왔습니다. base retrieval 기준으로는 `2736=2위`, `3257=8위`, `3281=18위`, `3575=29위` 였고, `3257/3281/3575` 모두 `in_window=true` 였습니다. latest retrieval 기준으로는 `3575=7위` 만 `20-window` 안에 들어왔고, `3257=28위`, `3281=24위`, `2736=33위` 로 아직 latest window 밖입니다. 즉 현재 lane 1 의 다음 병목은 더 이상 branch inclusion이 아니라 **latest 20 window 안에서 exact-sido local 후보가 더 최신 local row에 밀리는 정렬/창 크기 문제** 로 좁혀졌습니다.

이 다음 evidence lane은 [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md) 와 `run-local-recommendation-latest-window-audit.sh` 로 고정합니다. 이 wrapper는 latest query 상위 `20` 건과 blocker `21~40위` 를 같이 보여 주고, target family와 blocker row의 `region/youth/category` tier를 그대로 드러냅니다. 즉 현재 질문을 “왜 아직 24~33위인가”로 고정하고, direct scoring이 아니라 **latest ordering / latest fetch size** 쪽으로 더 좁히기 위한 단계입니다.

그 다음 확인은 [recommendation-pipeline-lane-audit-runbook.md](./recommendation-pipeline-lane-audit-runbook.md) 와 `run-local-recommendation-pipeline-lane-audit.sh` 로 이어집니다. 이 wrapper는 같은 target family를 actual `recommendation-diagnostics` trace에 넣어 `base/latest/filter/merged/post-scoring/saved` 경계를 한 줄로 보여 줍니다. 즉 `latest 20` 바깥이라는 사실이 실제 merged/saved 병목과 같은 층인지, 아니면 base lane 덕분에 이미 pipeline 안으로 들어오는지 구분하게 합니다.

`2026-05-18` 서버 재진단에서 `3257/3281/3575` 는 `base=true`, `primary=true`, `age=true`, `youthRelevant=true` 인데도 기존 diagnostics 상 `pass_base=false`, `dropStage=FILTERED_BY_YOUTH_OR_AGE` 로 남았습니다. 이건 실제 youth/age mismatch가 아니라 `filteredBase.limit(50)` / `filteredLatest.limit(5)` 이후 window 밖으로 밀린 케이스까지 같은 라벨로 덮어쓴 것이었습니다. 그래서 현재 diagnostics 계약은 다음처럼 읽는 것이 맞습니다.

- `passedBaseFilters` / `passedLatestFilters`: actual youth+age predicate pass
- `retainedBaseWindow` / `retainedLatestWindow`: post-filter top window 안에 남았는지
- `dropStage=TRIMMED_BY_BASE_OR_LATEST_LIMIT`: predicate는 통과했지만 base/latest window에서 밀린 상태

즉 현재 남은 next step은 더 이상 `FILTERED_BY_YOUTH_OR_AGE` 묶음 해석이 아니라, `3257/3281` 이 `TRIMMED_BY_BASE_OR_LATEST_LIMIT` 으로 실제로 재분류되는지 확인한 뒤 `base/latest window size` 나 ordering을 어디까지 bounded 하게 조정할지 결정하는 것입니다.

이 다음 evidence lane은 [recommendation-rebalance-audit-runbook.md](./recommendation-rebalance-audit-runbook.md) 와 `run-local-recommendation-rebalance-audit.sh` 로 고정합니다. 이 wrapper는 raw base 순위, source 내부 순위, source round-robin 후 재배치 순위를 같이 보여 줍니다. 즉 다음 질문을 “window size를 늘릴까”가 아니라 **`3257/3281` 이 실제로 source rebalance 때문에 `base 50` 밖으로 밀리는가** 로 먼저 좁히는 단계입니다.

그 뒤 `2026-05-18` server 재검증에서 `3257/3281` 은 `retain_sim=true` 인데도 runtime `actualBaseRank=110/120`, `retain_base=false` 로 남았습니다. 즉 no-priority source rebalance simulation이 아니라 **actual app runtime ordering** 이 더 큰 병목이라는 뜻입니다. runtime neighbor를 다시 보면 `3257/3281` 앞은 같은 `BOKJIRO_LOCAL EXACT_SIDO` 묶음이고, 더 위 상위 20은 전부 `YOUTH EXACT_REGION` 이었습니다.

마지막으로 `query tier` 를 wrapper에 그대로 드러내서 다시 보면, 상위 `YOUTH EXACT_REGION` 후보는 `tiers=1/3/3`, `3257/3281` 은 `BOKJIRO_LOCAL EXACT_SIDO tiers=2/2/2` 였습니다. 즉 현재 밀리는 이유는 priority-profile 자체가 아니라, **`REGION_CODE` query ordering에서 generic `EXACT_REGION` 후보가 bounded `same-sido BOKJIRO_LOCAL` fallback보다 앞서는 구조** 로 보는 편이 맞습니다. 다음 bounded fix는 score/weight가 아니라, runtime query를 `exact-region local -> same-sido local bounded fallback -> generic exact-region` 순으로 맞추는 것입니다.

이 bounded fix를 서버에 다시 반영한 뒤에는 `3257/3281` 이 실제 runtime에서 `actualBaseRank=8/18`, `retain_base=true`, `actualRetainBaseRank=8/18` 로 올라왔습니다. 즉 retrieval/retain 병목은 닫혔고, 현재 남은 경계는 `dropStage=SCORED_BUT_NOT_IN_SAVED_BATCH` 입니다. 다음 bounded evidence lane은 [recommendation-saved-batch-gap-audit-runbook.md](./recommendation-saved-batch-gap-audit-runbook.md) 와 `run-local-recommendation-saved-batch-gap-audit.sh` 로 고정합니다.

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
