# 추천 현재 동작 기준

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
- [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md)
- [recommendation-review-gate-promotion-approval-record-smoke-runbook.md](./recommendation-review-gate-promotion-approval-record-smoke-runbook.md)
- [recommendation-bounded-promotion-review-runbook.md](./recommendation-bounded-promotion-review-runbook.md)
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
현재 closeout / draft / merge 뒤 follow-up / `REAL_USER` reopen 순서를 같이 읽을 때는
`review brief -> draft exit -> post-merge follow-up -> real-user recheck` 흐름을 따르고,
그 entrypoint는 [recommendation-docs-index.md](./recommendation-docs-index.md) 의 `PR lifecycle order` 섹션에 고정돼 있습니다.
또한 `2026-05-20` 기준 explicit promotion approval record write path도 실제로 연결됐습니다. `run-local-admin-recommendation-review-gate-promotion-approval-record-smoke.sh` 는 baseline pending tuple -> approved tuple -> clear 뒤 baseline 복귀를 실제로 검증하고, existing PostgreSQL volume에서 relation missing 또는 permission denied가 보이면 `bash deploy/postgres/apply-local-runtime-schema-patch.sh` 로 runtime patch/grant를 먼저 적용하는 경계까지 같이 확인합니다.
그리고 여기서 더 이상 status ladder를 늘리지 않고, 실제 bounded promotion review go/no-go를 한 번에 보는 wrapper로 [recommendation-bounded-promotion-review-runbook.md](./recommendation-bounded-promotion-review-runbook.md) 를 추가했습니다. 이 wrapper는 approval record preflight, recent-window clear, historical staleness만 묶어 `bounded_promotion_review_result_status` 로 압축합니다.
현재 explicit policy review 결론도 여기까지입니다. `PASS_RECENT_WINDOW_POLICY_CANDIDATE` 는 **bounded promotion review를 승인할 근거**로 읽되, **primary full latest batch baseline을 recent-window로 즉시 승격하는 결정은 아직 하지 않는 것**이 현재 기준입니다.
사람 말로 더 짧게 풀면, **recent-window는 시험해 볼 만하지만 운영 기본 규칙을 갈아엎을 단계는 아직 아니다** 입니다.

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

`2026-05-18` server saved-gap 재검증에서는 `3257` 이 `rerank_rank=5`, `currentFinal=0.5629` 인데도 `saved_rank=None`, `savedAi=None`, `dropStage=SCORED_BUT_NOT_IN_SAVED_BATCH` 로 남았습니다. 반면 `3281` 은 `rerank_rank=28`, `currentFinal<0` 로 current rerank 자체도 낮았습니다. 즉 `3257` 은 retrieval/retain 문제가 아니라 latest saved batch와 current rerank 사이의 차이를 더 좁혀야 하는 케이스입니다. 다음 bounded step은 [recommendation-fresh-saved-gap-audit-runbook.md](./recommendation-fresh-saved-gap-audit-runbook.md) 와 `run-local-recommendation-fresh-saved-gap-audit.sh` 로 `personal=true` fresh persisted batch를 강제로 다시 만들고, stale latest batch와 fresh persisted gap을 분리하는 것입니다.

`2026-05-18` server fresh-saved-gap 재검증으로 이 경계도 더 좁혀졌습니다. `3257` 은 `personal=true` fresh persisted batch 기준 `inFreshTop=true`, `savedRank=8`, `savedFinal=0.34948`, `savedAi=0`, `savedAiStatus=SCORED` 로 실제 batch 안에 들어왔습니다. 즉 `3257` 의 “saved batch에 안 보임”은 stale latest batch 문제였고, current rerank(`rank=5`, `currentFinal=0.5629`) 와 fresh persisted batch 사이 차이는 persisted AI score가 `0` 으로 들어가 최종점수가 낮아진 데 있습니다. 반대로 `3281` 은 fresh persisted batch에서도 `savedRank=37`, `savedAiStatus=NOT_REQUESTED`, `rerankRank=28` 이라 top window 경쟁력 자체가 낮습니다. 따라서 현재 다음 bounded step은 retrieval이나 stale batch가 아니라, **`3257` 의 persisted AI=0 원인과 `3281/2736/3575` 의 AI 미요청 경계** 를 읽는 것입니다.

이 다음 evidence lane은 [recommendation-ai-stage-gap-audit-runbook.md](./recommendation-ai-stage-gap-audit-runbook.md) 와 `run-local-recommendation-ai-stage-gap-audit.sh` 로 고정합니다. 이 wrapper는 fresh persisted top batch와 target family를 기준으로 `savedAi`, `savedAiStatus`, `currentFinal -> savedFinal delta` 를 같이 출력해, 현재 남은 병목이 AI 0점인지 AI 미요청인지 바로 가르게 합니다.

그리고 `2026-05-18` server `ai zero contrast audit` 결과로 `3257` 류 `savedAi=0` 은 source-wide나 category-wide 현상이 아니라 **선택적 패턴** 임이 더 좁혀졌습니다. fresh top 안에서 `ai_zero_count=3`, `ai_positive_count=11` 이고, `BOKJIRO_LOCAL 주거` 안에서도 `3257/3209` 는 `savedAi=0` 인 반면 `3609` 는 `savedAi=60` 이었습니다. `교육·직업훈련` 도 `3287=0`, `3108=20` 으로 갈렸습니다. 즉 다음 immediate bounded step은 category bonus나 global prompt 변경이 아니라, **zero row와 same source/category positive peer의 입력 신호 차이를 나란히 보는 `AI input contrast audit`** 입니다.

그리고 `2026-05-18` server `ai input contrast / prompt line contrast / ai reason contrast` 까지 다시 태운 결과, `3257/3209` 는 structured signal이 빈약해서 0점인 쪽이 아니라 **AI가 primary audience mismatch를 직접 0점 이유로 적는 케이스** 로 좁혀졌습니다. `3257` 은 `savedAi=0`, reason=`저소득층을 위한 지원으로 소득 5분위인 사용자에게는 적합하지 않음`, `3209` 는 `신혼부부 대상의 지원으로 미혼인 사용자에게는 해당되지 않음` 으로 저장됐습니다. 같은 `BOKJIRO_LOCAL 주거` 인 `3609` 는 `savedAi=60` 이고 reason도 “미취업 상태에서는 직접적인 혜택이 적을 수 있음” 수준이라, 이번 prompt line 보강은 `3288` 처럼 일부 row를 0점 밖으로 끌어내리는 효과는 있었지만 `3257/3209` 핵심 케이스를 뒤집지는 못했습니다. 즉 현재 남은 문제는 prompt에 청년 신호가 안 보이는 것이 아니라, **AI가 수급자/신혼부부/학생 같은 대상군 불일치를 강한 exclusion으로 해석하는 것**입니다.

`2026-05-19` rebuilt local app 기준으로 다시 잡히는 fresh runtime family도 같은 성격입니다. 현재 로컬에서 바로 재현되는 계정은 `<example local recommendation user>` 이고, `run-local-recommendation-ai-stage-gap-audit.sh` / `run-local-recommendation-ai-reason-contrast-audit.sh` 를 fresh top 기준으로 다시 태우면 zero-AI family는 `3289`, `3611`, `3290`, `5837` 으로 잡힙니다. 이 중 `3289` 는 `기초생활수급자 대상이라 소득 5분위 사용자와 불일치`, `3290` 은 `대학생 학자금 대출 이자 지원이라 미취업 청년에게 직접적 도움 부족`, `5837` 은 `대학생 대상 장학금이라 미취업 청년에게 해당되지 않음` 으로 0점 이유가 명시됐고, comparator `3284/12595/3110` 은 같은 category 안에서도 `취업역량 제고`, `청년 창업 지원`, `현장체험학습비는 청년 직접성 낮음` 같이 훨씬 선명한 이유 문장을 가졌습니다. 즉 현재 local HEAD 기준으로도 병목은 reason blank가 아니라 **AI가 대상군/직접성 불일치를 강한 exclusion으로 읽는 것** 입니다.

같은 계정으로 `run-local-recommendation-ai-zero-reason-bucket-audit.sh` 를 fresh top `20` 기준으로 다시 태워 보면, 현재 zero-AI bucket은 `INCOME_MISMATCH:1`, `STUDENT_AUDIENCE_MISMATCH:2`, `OTHER:1` 로 잡힙니다. 실제 row는 `3289(BOKJIRO_LOCAL/교육·직업훈련)`, `3290(BOKJIRO_LOCAL/금융·생활지원)`, `3611(BOKJIRO_LOCAL/금융·생활지원)`, `5837(GOV24/일자리)` 네 건이고, `3289` 는 소득 불일치, `3290/5837` 은 대학생 대상 불일치, `3611` 은 “미취업 상태와 직접 관련 없음” 계열 reason으로 읽혔습니다. 즉 최신 fresh runtime 기준 zero-AI 핵심은 여전히 **명시적 audience exclusion** 이지만, 지금 local top window에는 `학생/대학생 대상 불일치` 외에 `직접성 낮음` 성격 `OTHER` bucket도 한 건 섞여 있습니다.

현재 문서 기준 product 기본값은 이 두 bucket을 완화하지 않고 유지하는 쪽입니다. 세부 판단은 [recommendation-primary-audience-exclusion-decision-memo.md](./recommendation-primary-audience-exclusion-decision-memo.md) 에 따로 고정합니다.

현재 local exclusion baseline을 한 번에 다시 확인할 때는 [recommendation-ai-exclusion-suite-runbook.md](./recommendation-ai-exclusion-suite-runbook.md) 와 `run-local-recommendation-ai-exclusion-suite.sh` 를 entrypoint로 봅니다. 같은 baseline을 날짜별 summary artifact로 남길 때는 [recommendation-ai-exclusion-snapshot-runbook.md](./recommendation-ai-exclusion-snapshot-runbook.md) 와 `run-local-recommendation-ai-exclusion-snapshot.sh` 를 쓰고, 직전 snapshot과 drift만 빠르게 볼 때는 [recommendation-ai-exclusion-snapshot-compare-runbook.md](./recommendation-ai-exclusion-snapshot-compare-runbook.md) 와 `run-local-recommendation-ai-exclusion-snapshot-compare.sh` 를 씁니다. 새 snapshot 생성과 직전 baseline compare를 한 번에 끝낼 때는 [recommendation-ai-exclusion-drift-check-runbook.md](./recommendation-ai-exclusion-drift-check-runbook.md) 와 `run-local-recommendation-ai-exclusion-drift-check.sh` 를 씁니다. fresh target window 자체가 얼마나 흔들리는지 보려면 [recommendation-ai-exclusion-volatility-audit-runbook.md](./recommendation-ai-exclusion-volatility-audit-runbook.md) 와 `run-local-recommendation-ai-exclusion-volatility-audit.sh` 를 쓰고, 어떤 key를 stable baseline으로 볼지 분류할 때는 [recommendation-ai-exclusion-stability-report-runbook.md](./recommendation-ai-exclusion-stability-report-runbook.md) 와 `run-local-recommendation-ai-exclusion-stability-report.sh` 를 씁니다. 개별 compare가 stable baseline drift인지 volatile-only drift인지 바로 판정할 때는 [recommendation-ai-exclusion-drift-classify-runbook.md](./recommendation-ai-exclusion-drift-classify-runbook.md) 와 `run-local-recommendation-ai-exclusion-drift-classify.sh` 를 쓰고, 이 둘을 사람 읽기용 한 장으로 볼 때는 [recommendation-ai-exclusion-baseline-report-runbook.md](./recommendation-ai-exclusion-baseline-report-runbook.md) 와 `run-local-recommendation-ai-exclusion-baseline-report.sh` 를 씁니다. volatility audit와 baseline report를 한 번에 다시 태울 때는 [recommendation-ai-exclusion-baseline-refresh-runbook.md](./recommendation-ai-exclusion-baseline-refresh-runbook.md) 와 `run-local-recommendation-ai-exclusion-baseline-refresh.sh` 를 씁니다.

`2026-05-19` snapshot wrapper 재실행 기준 summary는 `fresh_top_ai_zero_count=2`, `ai_zero_count=4`, `ai_zero_reason_buckets=INCOME_MISMATCH:1,OTHER:1,STUDENT_AUDIENCE_MISMATCH:2`, `baseline_zero_ai_reason_buckets=AUDIENCE_MISMATCH:4,STUDENT_AUDIENCE_MISMATCH:4`, `real_user_distribution_executed=false`, `dashboard_real_user_gate=DEFERRED_NO_REAL_USER_TRAFFIC`, `breakdown_real_user_cohort_gate=DEFERRED_NO_REAL_USER_COHORT` 입니다. 이 값은 **live readiness가 열리기 전 historical pre-live baseline snapshot** 으로 읽는 편이 맞습니다. 즉 target family 핵심은 여전히 `3289/5837` 이지만, fresh top 전체로 보면 `3290/3611` 까지 포함한 4건 zero-AI window로 읽는 편이 더 정확합니다.

같은 baseline을 고정하고 `RUN_COUNT=2` 로 `run-local-recommendation-ai-exclusion-volatility-audit.sh` 를 다시 태워 보면, current drift는 실제로 fresh window 변동성으로 보입니다. 이번 local 반복 결과는 `drift_detected_runs=2`, `changed_keys_frequency=fresh_top_ai_zero_count:2,ai_zero_count:1,ai_zero_reason_buckets:1`, `fresh_top_ai_zero_count_frequency=2:1,4:1` 이었고, cohort baseline과 `REAL_USER` gate는 그대로였습니다. 즉 현재 local truth는 **`non_example` baseline과 `REAL_USER` gate는 안정적이지만, fresh target family zero-AI window는 2~4건 범위에서 흔들릴 수 있다** 는 쪽입니다.

같은 artifact에 `run-local-recommendation-ai-exclusion-stability-report.sh` 를 적용하면 stable/volatile 분리도 더 명확합니다. 현재 stable key는 `target_service_ids_csv`, `baseline_scope_users`, `baseline_zero_ai_reason_buckets`, `target_scope_users`, `target_zero_ai_reason_buckets`, `real_user_distribution_executed`, `dashboard_real_user_gate`, `breakdown_real_user_cohort_gate` 이고, volatile key는 `fresh_top_ai_zero_count`, `ai_zero_count`, `ai_zero_reason_buckets` 입니다. 즉 지금 문서에서 **고정 baseline처럼 써도 되는 건 cohort/gate 계열** 이고, **fresh target family zero-AI 개수/버킷은 관찰값으로만 읽는 편** 이 맞습니다.

실제로 baseline snapshot `20260519T135555Z` 와 volatility run `run-02` snapshot을 `run-local-recommendation-ai-exclusion-drift-classify.sh` 로 분류하면 `drift_class=VOLATILE_ONLY_DRIFT`, `volatile_changed_keys=fresh_top_ai_zero_count`, `stable_changed_keys=` 로 나옵니다. 즉 지금 local compare에서 보이는 drift는 stable baseline 변화가 아니라 **fresh target window count 흔들림** 으로 읽는 것이 맞습니다.

이 기준선을 one-shot으로 다시 태우는 entrypoint는 이제 `run-local-recommendation-ai-exclusion-baseline-refresh.sh` 입니다. latest pointer 기준으로는 `run-local-recommendation-ai-exclusion-latest-status.sh` 가 현재 truth를 가장 빠르게 보여 줍니다. 다만 여기서 말하는 stable baseline의 `dashboard_real_user_gate=DEFERRED_NO_REAL_USER_TRAFFIC`, `breakdown_real_user_cohort_gate=DEFERRED_NO_REAL_USER_COHORT` 는 **historical pre-live baseline artifact 값** 입니다. live current truth는 별도로 `effective_operator_next_step`, readiness live gate, review-gate audit을 같이 봐야 합니다. 최종 판정은 `drift_class=VOLATILE_ONLY_DRIFT`, `recommended_reading=READ_LATEST_AS_VOLATILE_OBSERVATION` 이며, stable baseline은 `baseline_zero_ai_reason_buckets=AUDIENCE_MISMATCH:4,STUDENT_AUDIENCE_MISMATCH:4`, `dashboard_real_user_gate=DEFERRED_NO_REAL_USER_TRAFFIC`, `breakdown_real_user_cohort_gate=DEFERRED_NO_REAL_USER_COHORT` 입니다. 반면 latest volatile observation 은 `fresh_top_ai_zero_count=2`, `ai_zero_count=2`, `ai_zero_reason_buckets=INCOME_MISMATCH:1,STUDENT_AUDIENCE_MISMATCH:1` 으로 읽는 편이 맞습니다.

이제 refresh 결과는 `baseline-refresh-summary.txt` 로도 따로 남기므로, 직전 두 refresh를 high-signal key만으로 바로 비교할 때는 `run-local-recommendation-ai-exclusion-baseline-refresh-compare.sh` 를 쓰는 편이 맞습니다. 여기서 `stable_baseline_changed=false`, `latest_observation_changed=true` 면 stable baseline은 그대로고 fresh window 관찰값만 흔들린 것으로 읽습니다.

새 refresh를 실제로 다시 태운 뒤 직전 refresh summary와 one-shot으로 비교할 때는 `run-local-recommendation-ai-exclusion-baseline-refresh-drift-check.sh` 를 씁니다. 이 wrapper는 `baseline refresh -> compact summary compare` 를 한 번에 끝내므로, latest refresh가 stable baseline drift인지 fresh observation drift인지 바로 읽을 수 있습니다.

이제 drift-check 결과도 `baseline-refresh-drift-summary.txt` 로 따로 남기므로, 가장 최근 compare를 high-signal key만으로 바로 읽을 때는 `latest-baseline-refresh-drift-summary.txt` 를 먼저 보는 편이 맞습니다.

재실행 없이 지금 latest baseline/drift 상태만 한 화면에서 바로 볼 때는 `run-local-recommendation-ai-exclusion-latest-status.sh` 를 쓰면 됩니다. 이 wrapper는 latest refresh summary와 latest drift summary를 같이 읽어 stable baseline, latest volatile observation, latest drift 판정을 한 번에 요약합니다.

같은 latest 상태를 운영 메모/핸드오프용 Markdown note로 바로 뽑을 때는 `run-local-recommendation-ai-exclusion-latest-status-export.sh` 를 쓰면 됩니다.

이 export는 이제 `latest-status.json` 도 같이 남기므로, 후속 자동화나 요약 스크립트에서는 `latest_json_link` 를 바로 읽는 편이 맞습니다. note/json 모두 `generated_at_utc`, `generated_at_kst` 를 같이 남기므로, UTC artifact 경로와 로컬 KST 실행 시각을 함께 확인할 수 있습니다.

같은 latest JSON을 자동 판정용으로 읽을 때는 `run-local-recommendation-ai-exclusion-latest-gate.sh` 를 쓰면 됩니다. 현재 기본 gate는 latest observation 변화만으로는 fail 하지 않고, `interpretation_changed` 나 `stable_baseline_changed` 가 있을 때만 fail 합니다.

`latest-status` 와 `latest-gate` 도 이제 export JSON이 있으면 `generated_at_utc`, `generated_at_kst` 를 같이 보여 주므로, 현재 읽고 있는 latest artifact가 UTC로 언제 생성됐고 KST로는 언제 실행된 것인지 CLI 출력만으로도 바로 확인할 수 있습니다.

같은 latest export JSON은 이제 `operator_next_step`, `effective_operator_next_step`, `gate_action_class`, `gate_policy_status`, `gate_policy_reason`, `review_gate_policy_candidate_status`, `review_gate_policy_candidate_reason`, `review_gate_policy_promotion_status`, `review_gate_policy_promotion_reason`, `review_gate_policy_promotion_action_status`, `review_gate_policy_promotion_action_reason`, `review_gate_policy_promotion_readiness_status`, `review_gate_policy_promotion_readiness_reason`, `review_gate_policy_promotion_execution_status`, `review_gate_policy_promotion_execution_reason`, `review_gate_policy_promotion_approval_criteria_status`, `review_gate_policy_promotion_approval_criteria_reason`, `review_gate_policy_promotion_approval_status`, `review_gate_policy_promotion_approval_reason`, `review_gate_policy_promotion_approval_decision_status`, `review_gate_policy_promotion_approval_decision_reason`, `review_gate_policy_promotion_approval_record_status`, `review_gate_policy_promotion_approval_record_reason`, `review_gate_policy_promotion_review_run_status`, `review_gate_policy_promotion_review_run_reason`, `review_gate_policy_promotion_review_run_criteria_status`, `review_gate_policy_promotion_review_run_criteria_reason`, `review_gate_policy_promotion_review_run_decision_status`, `review_gate_policy_promotion_review_run_decision_reason`, `review_gate_policy_promotion_review_run_approval_criteria_status`, `review_gate_policy_promotion_review_run_approval_criteria_reason`, `review_gate_policy_promotion_review_run_approval_decision_status`, `review_gate_policy_promotion_review_run_approval_decision_reason`, `review_gate_policy_promotion_review_run_approval_status`, `review_gate_policy_promotion_review_run_approval_reason`, `review_gate_policy_promotion_review_run_approval_record_status`, `review_gate_policy_promotion_review_run_approval_record_reason`, `review_gate_policy_promotion_review_run_approval_record_transition_status`, `review_gate_policy_promotion_review_run_approval_record_transition_reason`, `review_gate_policy_promotion_review_run_approval_record_write_status`, `review_gate_policy_promotion_review_run_approval_record_write_reason` 을 같이 남깁니다. `operator_next_step` 은 older baseline artifact를 그대로 요약한 historical pointer이고, `effective_operator_next_step` 은 current local review-gate context를 반영한 실제 해석입니다. 지금 local truth에서는 `effective_operator_next_step=USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`, `gate_action_class=READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES`, `gate_policy_status=PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`, `gate_policy_reason=HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`, `review_gate_policy_candidate_status=RECENT_WINDOW_POLICY_CANDIDATE`, `review_gate_policy_promotion_status=REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`, `review_gate_policy_promotion_action_status=KEEP_PRIMARY_BASELINE`, `review_gate_policy_promotion_readiness_status=READY_FOR_BOUNDED_PROMOTION_REVIEW`, `review_gate_policy_promotion_execution_status=AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`, `review_gate_policy_promotion_approval_criteria_status=READY_FOR_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_status=PENDING_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_decision_status=AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`, `review_gate_policy_promotion_approval_record_status=PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`, `review_gate_policy_promotion_review_run_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN`, `review_gate_policy_promotion_review_run_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN`, `review_gate_policy_promotion_review_run_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`, `review_gate_policy_promotion_review_run_approval_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`, `review_gate_policy_promotion_review_run_approval_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_record_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`, `review_gate_policy_promotion_review_run_approval_record_transition_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE`, `review_gate_policy_promotion_review_run_approval_record_write_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE` 를 먼저 읽고, 그 위에 live readiness와 review gate audit까지 같이 얹어 **full latest batch historical baseline + recent-window current signal** 을 함께 해석하는 편이 맞습니다.

이제 마지막 ladder에는 `review_gate_policy_promotion_review_run_approval_record_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD`, `review_gate_policy_promotion_review_run_approval_record_criteria_reason=REVIEW_RUN_APPROVAL_RECORD_PREREQUISITES_MET_BUT_RECORD_PENDING` 도 같이 붙습니다. 즉 final approval record 자체는 아직 pending이지만, 그 record를 남길 prerequisite은 이미 충족된 상태로 읽는 편이 맞습니다.

다만 `2026-05-20` 에 local generic-domain signup 기반 `REAL_USER` 표본을 distributed baseline `30명` + targeted cohort library `50명` 으로 총 `80명`까지 늘린 뒤에도, live readiness gate는 열렸지만 review gate는 그대로 남아 있습니다. `run-local-real-user-exclusion-readiness-check.sh` 결과는 `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC`, `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT`, `audit_scope_users=80`, `ctr_clicked_users=80`, `real_user_distribution_executed=true` 였고, real-user-only concentration은 `top1_leader=드림나래(인천청년 면접복장 지원)`, `top1_share_pct=7.50`, `NO_PRIORITY_DOMINANT` 로 더 분산됐습니다. real-user latest top-N zero-AI 분포는 계속 `zero_ai_rows=53`, `zero_ai_users=17`, `zero_ai_reason_buckets=AUDIENCE_MISMATCH:8,INCOME_MISMATCH:11,OTHER:13,REGION_MISMATCH:1,STUDENT_AUDIENCE_MISMATCH:20` 이었습니다. 대신 admin recommendation 쪽 review gate는 아직 `recommendation_review_gate=DEFERRED_NON_REAL_LEADER_SIGNAL` 로 남아 있고, mixed latest batch 기준 `recommendation_top1_leader_real_user_users=0`, `recommendation_top1_leader_signal_summary=LOCAL_SEED_WITHOUT_REAL_USER_LEADER` 가 같이 확인됐습니다.

이 해석은 이제 wrapper/runbook 메모에만 남아 있지 않습니다. `GET /api/admin/dashboard/summary`, `GET /api/admin/dashboard/recommendation-breakdowns` 도 full latest batch gate(`recommendationReviewGate`) 외에 `recentWindowLatestBatch`, `reviewGateStaleness`, `recentWindowRecommendationReviewReading`, `historicalExampleDominanceDetected`, `reviewGatePolicyCandidateStatus`, `reviewGatePolicyCandidateReason`, `reviewGatePolicyPromotionStatus`, `reviewGatePolicyPromotionReason`, `reviewGatePolicyPromotionActionStatus`, `reviewGatePolicyPromotionActionReason`, `reviewGatePolicyPromotionReadinessStatus`, `reviewGatePolicyPromotionReadinessReason`, `reviewGatePolicyPromotionExecutionStatus`, `reviewGatePolicyPromotionExecutionReason`, `reviewGatePolicyPromotionApprovalCriteriaStatus`, `reviewGatePolicyPromotionApprovalCriteriaReason`, `reviewGatePolicyPromotionApprovalStatus`, `reviewGatePolicyPromotionApprovalReason`, `reviewGatePolicyPromotionApprovalDecisionStatus`, `reviewGatePolicyPromotionApprovalDecisionReason`, `reviewGatePolicyPromotionApprovalRecordStatus`, `reviewGatePolicyPromotionApprovalRecordReason`, `reviewGatePolicyPromotionReviewRunStatus`, `reviewGatePolicyPromotionReviewRunReason`, `reviewGatePolicyPromotionReviewRunCriteriaStatus`, `reviewGatePolicyPromotionReviewRunCriteriaReason`, `reviewGatePolicyPromotionReviewRunDecisionStatus`, `reviewGatePolicyPromotionReviewRunDecisionReason`, `reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus`, `reviewGatePolicyPromotionReviewRunApprovalCriteriaReason`, `reviewGatePolicyPromotionReviewRunApprovalDecisionStatus`, `reviewGatePolicyPromotionReviewRunApprovalDecisionReason`, `reviewGatePolicyPromotionReviewRunApprovalStatus`, `reviewGatePolicyPromotionReviewRunApprovalReason` 를 함께 내려, historical example inertia와 recent current-live signal, 그리고 “후보이지만 아직 explicit policy change review가 필요하고 지금 action은 keep primary baseline, bounded promotion review를 열 readiness는 이미 충족됐고 execution은 explicit policy review decision 대기, approval criteria는 이미 충족됐지만 approval/approval decision은 아직 미기록이며 approval record도 아직 pending, 그래서 bounded promotion review run도 아직 pending이지만 run criteria는 이미 ready이고 run decision도 아직 pending이며 run approval criteria도 이미 ready이고 run approval decision도 아직 pending이며 run approval도 아직 pending”이라는 상태를 같은 admin surface에서 같이 읽게 했습니다. 최신 local smoke 기준으로는 `recommendation_review_gate=DEFERRED_NON_REAL_LEADER_SIGNAL`, `review_gate_primary_reference_mode=ALL_TIME_LATEST_PER_USER`, `review_gate_example_target_top1_users=272`, `review_gate_example_target_top1_last_24h=0`, `review_gate_real_user_latest_users=80`, `review_gate_real_user_target_top1_users=0`, `recent_window_recommendation_review_reading=RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`, `historical_example_dominance_detected=true`, `review_gate_policy_candidate_status=RECENT_WINDOW_POLICY_CANDIDATE`, `review_gate_policy_candidate_reason=PRIMARY_GATE_BLOCKED_BY_STALE_ALL_TIME_EXAMPLE_REFERENCE_BUT_RECENT_WINDOW_CLEAR`, `review_gate_policy_promotion_status=REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW`, `review_gate_policy_promotion_action_status=KEEP_PRIMARY_BASELINE`, `review_gate_policy_promotion_action_reason=PROMOTION_STILL_REQUIRES_EXPLICIT_POLICY_REVIEW`, `review_gate_policy_promotion_readiness_status=READY_FOR_BOUNDED_PROMOTION_REVIEW`, `review_gate_policy_promotion_readiness_reason=EXPLICIT_POLICY_REVIEW_PENDING_WITH_BOUNDED_REVIEW_PREREQUISITES_MET`, `review_gate_policy_promotion_execution_status=AWAIT_EXPLICIT_POLICY_REVIEW_DECISION`, `review_gate_policy_promotion_execution_reason=READINESS_MET_BUT_EXPLICIT_POLICY_REVIEW_DECISION_IS_STILL_PENDING`, `review_gate_policy_promotion_approval_criteria_status=READY_FOR_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_criteria_reason=PRIMARY_STALENESS_AND_RECENT_WINDOW_SIGNAL_CONFIRMED`, `review_gate_policy_promotion_approval_status=PENDING_EXPLICIT_PROMOTION_APPROVAL`, `review_gate_policy_promotion_approval_reason=EXECUTION_READY_BUT_EXPLICIT_PROMOTION_APPROVAL_NOT_RECORDED`, `review_gate_policy_promotion_approval_decision_status=AWAIT_EXPLICIT_PROMOTION_APPROVAL_DECISION`, `review_gate_policy_promotion_approval_decision_reason=APPROVAL_CRITERIA_MET_BUT_EXPLICIT_APPROVAL_NOT_RECORDED`, `review_gate_policy_promotion_approval_record_status=PENDING_EXPLICIT_PROMOTION_APPROVAL_RECORD`, `review_gate_policy_promotion_approval_record_reason=APPROVAL_DECISION_PENDING_AND_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN`, `review_gate_policy_promotion_review_run_reason=EXPLICIT_APPROVAL_RECORD_NOT_WRITTEN_FOR_BOUNDED_REVIEW_RUN`, `review_gate_policy_promotion_review_run_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_DECISION`, `review_gate_policy_promotion_review_run_decision_reason=REVIEW_RUN_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_approval_criteria_status=READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_criteria_reason=REVIEW_RUN_APPROVAL_PREREQUISITES_MET_BUT_APPROVAL_RECORD_PENDING`, `review_gate_policy_promotion_review_run_approval_decision_status=AWAIT_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION`, `review_gate_policy_promotion_review_run_approval_decision_reason=REVIEW_RUN_APPROVAL_CRITERIA_MET_BUT_APPROVAL_RECORD_NOT_WRITTEN`, `review_gate_policy_promotion_review_run_approval_status=PENDING_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL`, `review_gate_policy_promotion_review_run_approval_reason=REVIEW_RUN_APPROVAL_DECISION_PENDING_BECAUSE_APPROVAL_RECORD_NOT_WRITTEN` 입니다. 다음 정책 판단은 이 값을 바로 승격 실행으로 읽지 않고, [recommendation-review-gate-policy-promotion-checklist.md](./recommendation-review-gate-policy-promotion-checklist.md) 기준 explicit policy review를 먼저 통과시키는 쪽이 맞습니다.

그래서 현재 `latest-overview` 는 두 층을 같이 보여 줍니다. baseline artifact 기반 `operator_next_step` 은 historical snapshot 포인터로 남지만, current action의 기본값은 `latest-status` 가 계산한 `effective_operator_next_step=USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT` 입니다. readiness 포함 실행에서도 이 값을 먼저 따르고, status effective reading이 아직 old wait-state일 때만 `readiness_override_detected=true`, `readiness_override_reason=LIVE_REAL_USER_GATES_READY_BUT_REVIEW_GATE_PENDING`, `effective_operator_next_step=WAIT_FOR_REAL_USER_LEADER_SIGNAL` 같은 추가 override를 얹습니다. 그리고 latest artifact는 이 조합을 `review_gate_interpretation_class=HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`, `review_gate_operating_mode=PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`, `gate_action_class=READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES` 로도 같이 승격합니다. 즉 지금 남은 blocker는 더 이상 “real-user traffic/cohort 없음”이 아니라 **real-user leader/review signal이 아직 mixed latest batch top1에 안 올라온 상태**로 읽는 편이 맞습니다.

이 blocker는 `run-local-recommendation-review-gate-blocker-audit.sh` 로 더 직접 확인할 수 있습니다. 현재 local mixed latest batch는 `users=548`, `example_users=464`, `real_user_users=80`, `top1_leader=청년월세 지원사업`, `top1_leader_share_pct=50.55`, `top1_leader_real_user_users=0` 인 반면, real-user-only latest batch는 `top1_leader=드림나래(인천청년 면접복장 지원)`, `top1_leader_share_pct=7.50`, `concentration_readiness=NO_PRIORITY_DOMINANT` 입니다. 즉 review gate blocker는 recommendation rank collapse보다 **mixed batch non-real dominance** 로 보는 편이 더 정확합니다.

더 좁히면 mixed latest batch leader 서비스 `2622(청년월세 지원사업)` 는 real-user 쪽 `top1/top3/top5/top10/any-rank` 에 한 번도 나타나지 않습니다. 현재 local 값은 `real_user_mixed_leader_top1_count=0`, `top3_count=0`, `top5_count=0`, `top10_count=0`, `any_rank_count=0` 이고, blocker audit는 이를 `MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH` 로 분류합니다. 더 중요한 건 `housing_leader_path` exact cohort로 example-heavy leader profile과 같은 `인천광역시/중구/income=5/미취업/1인 가구` 를 generic-domain `REAL_USER` 로 10명 더 시드해도 path가 여전히 `0` 이었다는 점입니다. 즉 current sample 기준으로는 단순 wait보다 **profile/account_origin/flow 차이가 same persona path를 어떻게 바꾸는지** 를 더 직접 추적하는 쪽이 맞습니다.

이 next question을 직접 보는 wrapper도 고정했습니다. `run-local-recommendation-same-profile-origin-differential-audit.sh` 기준 exact profile latest batch user 수는 `EXAMPLE_SMOKE=454`, `REAL_USER=14`, `LOCAL_REAL_NON_EXAMPLE_SEED=3`, `BOUNDED_LOCAL=1` 이고, target service `2622` 는 example 쪽 `top1=272`, `top3=434`, `top10=442` 인 반면 real-user 쪽은 `top1/top3/top5/top10/any-rank=0` 입니다. 그리고 `run-local-recommendation-same-profile-path-differential-audit.sh` 로 exact profile 대표 example/real-user를 직접 비교하면, `2622` 는 example 쪽에서 retrieval/filter/window에는 안 보이지만 `latest_saved_rank=1`, `drop_stage=PRESENT_IN_SAVED_BATCH` 로 saved batch에 남아 있고, real-user 쪽에서는 `drop_stage=NOT_IN_SQL_RETRIEVAL` 입니다. 더 나아가 `run-local-recommendation-same-profile-fresh-saved-differential-audit.sh` 로 representative example/real-user에 `personal=true` fresh refresh를 다시 태우면, example 쪽 stale saved path도 즉시 `NOT_IN_SQL_RETRIEVAL` 로 내려가고 real-user는 전후 동일합니다. 그리고 `run-local-recommendation-review-gate-staleness-audit.sh` 기준 mixed leader `2622` top1 example users `272명` 은 최근 `24h=0`, oldest/newest `2026-05-13 13:39:31 / 2026-05-17 11:49:50` 인 반면, real-user latest users `80명` 은 최근 `24h=80` 입니다. 반대로 `run-local-recommendation-review-gate-recent-window-audit.sh` 기준 recent 24h latest batch만 보면 mixed leader는 `3284(인천 청년도약기지(취업아카데미))`, `users=6`, `share=7.23%`, origin mix `EXAMPLE_SMOKE:1,REAL_USER:5` 이고 `2622 top1=0` 입니다. 즉 current blocker는 단순히 “mixed batch에서 example이 많다”가 아니라 **historical example latest batch가 mixed leader 해석을 지배하고 있고, current live signal은 이미 `2622` dominance에서 벗어난 상태** 입니다. 이 상태의 다음 기술적 단계는 표본 수 확대보다 `full latest batch gate` 와 `recent-window supplemental gate` 를 운영 해석에서 어떻게 같이 쓸지 정리하는 쪽입니다.

이 재사용 가능한 local `REAL_USER` persona 세트는 [recommendation-real-user-cohort-library-manifest.md](./recommendation-real-user-cohort-library-manifest.md) 와 `deploy/smoke/run-local-real-user-cohort-library-seed.sh` 로 관리합니다.

같은 날 분산 프로필 `REAL_USER` 를 30명까지 늘려 보니 recommendation 상단도 실제로 분산됩니다. 예를 들어 latest top1은 `3688(드림나래)`, `2884(4차산업칼리지 청년인턴 지원사업)`, `2766(청년 어학·자격시험 응시료 지원사업)`, `2852(광주청년구직활동지원사업)`, `2881(청년13(일+삶)통장)`, `2928(청년창업 지원사업)`, `2960(구직활동비 지원사업)`, `2982(공공근로사업)`, `3021(대학생 생활안정비 지원사업)`, `3132(화성시 청년 전월세 보증금 대출이자 지원사업)`, `3205(제주 청년 이사비 지원)`, `3218(청년사회진입 활동비 지원)`, `3254(인천 중구 청년 자격시험 응시료 지원사업)`, `3291(희망두배 청년통장)` 처럼 갈립니다. real-user-only concentration도 `top1_share_pct=10.00`, `concentration_readiness=NO_PRIORITY_DOMINANT` 로 더 분산됐습니다. 즉 homogeneous 3계정에서 보이던 `zero_ai_rows=0` 는 분산 표본이 좁아서였고, 분산 `REAL_USER` 샘플을 30명 수준으로 늘리면 exclusion bucket이 더 안정적으로 다시 드러난다고 읽는 편이 맞습니다.

또 `latest-status` 와 `latest-gate` 는 이제 `status_json_stale_relative_to_summaries`, `status_json_recommended_action` 도 같이 보여 줍니다. 만약 latest refresh/drift summary가 export JSON보다 새로워졌다면 `RERUN_LATEST_STATUS_EXPORT` 를 먼저 수행해 human/machine latest artifact를 다시 맞추는 편이 맞습니다.

수동 정합보다 편한 경로가 필요하면 `AUTO_REFRESH_STATUS_JSON_IF_STALE=true` 로 `latest-status`, `latest-gate` 를 실행할 수 있습니다. 이 경우 stale JSON이면 `latest-status-export` 를 먼저 다시 태운 뒤 fresh latest JSON 기준으로 값을 읽습니다.

daily operator entrypoint로는 `run-local-recommendation-ai-exclusion-latest-overview.sh` 를 쓰는 편이 맞습니다. 이 wrapper는 latest export를 먼저 갱신한 뒤 `latest-status`, 기본 `latest-gate`, strict `latest-gate` 를 순서대로 보여 주므로, 지금 상태와 다음 행동을 한 번에 다시 읽을 수 있습니다.

이 overview wrapper는 이제 `tmp/recommendation-ai-exclusion-latest-overview/<ts>/latest-overview-summary.txt`, `latest-overview-note.md`, `latest-overview.json` 과 `latest` symlink도 같이 남깁니다. 즉 daily 확인 뒤에는 stdout만 보지 않고 compact summary, 사람용 note, machine-readable JSON 중 필요한 artifact를 바로 handoff 기준으로 써도 됩니다. 같은 artifact에는 `review_gate_context` 도 포함돼, full latest batch primary gate(`MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH`)와 recent-window supplemental reading(`RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`), 그리고 `historical_example_dominance_detected=true` 까지 한 번에 같이 읽을 수 있습니다.

필요하면 같은 overview에서 `REAL_USER` readiness도 같이 확인할 수 있습니다. `INCLUDE_REAL_USER_READINESS=true` 와 `APP_BASE_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` 를 넘기면 overview artifact에 readiness gate와 distribution 실행 여부까지 함께 남깁니다. 기본 `auto` 모드도 로컬 기본 URL `http://127.0.0.1:8082` 와 발견 가능한 admin 자격이 있으면 readiness를 자동 포함하려 시도하고, 자격이 없으면 skip 이유만 남기고 계속 진행합니다.

auto skip일 때는 `real_user_readiness_next_action` 도 같이 남깁니다. 현재 로컬 auto 기준으로는 `SET_ADMIN_PASSWORD_OR_ADMIN_PASSWORD_FILE` 이고, 이는 admin email은 찾았지만 password discovery가 비어 있다는 뜻입니다.

strict 모드(`FAIL_ON_LATEST_OBSERVATION_CHANGE=true`)는 current local에서 `LATEST_OBSERVATION_CHANGED` 로 fail 합니다. 다만 현재 latest status 자체가 `latest_drift_class=VOLATILE_ONLY_DRIFT`, `stable_baseline_changed=false`, `latest_observation_changed=true` 이므로, 이 fail을 stable baseline regression으로 읽는 편은 맞지 않습니다. 지금은 fresh target window 관찰값만 흔들리는 상태로 읽는 편이 정확합니다.

`2026-05-20` KST에 latest status / latest gate / readiness를 다시 태워도 이 판정은 그대로 유지됐습니다. current latest export artifact는 `tmp/recommendation-ai-exclusion-latest-status/20260519T150359Z` 이고, `Z` suffix UTC 기준이라 KST 자정 이후 실행도 날짜상 전날처럼 보일 수 있습니다.

현재 admin `recommendation-diagnostics` 응답도 이 판단을 더 직접 읽게 보강된 상태다. 기존 `latestSavedAiScore`, `latestSavedAiStatus` 에 더해 `latestSavedAiReason` 도 같이 내려가므로, fresh saved batch 기준 target family와 top competitor를 비교할 때 더 이상 DB row를 따로 열지 않고도 persisted exclusion 이유를 바로 확인할 수 있다. `ai stage gap / ai input contrast / ai reason contrast` wrapper도 이제 이 diagnostics truth를 공통으로 사용한다.

`2026-05-19` local 검증 중간에는 `<example local recommendation user>` 계정으로 `run-local-recommendation-ai-reason-contrast-audit.sh`, `run-local-recommendation-ai-reason-coverage-audit.sh` 를 태웠을 때 fresh top scored row의 `savedAiReason` 이 전부 blank로 보이는 구간이 있었습니다. 당시에는 `ai_scored_count=15`, `ai_scored_blank_reason_count=15`, `ai_scored_non_blank_reason_count=0` 이었고, 분포도 `sourceType=BOKJIRO_LOCAL:9,GOV24:6` 으로 넓게 퍼져 있어 persisted reason coverage 부족처럼 읽혔습니다.

다만 같은 날 최신 workspace 코드로 `docker compose up -d --build app` 뒤 `run-local-recommendation-ai-upstream-reason-trace-audit.sh` 를 같은 계정에 다시 태우자 현재 truth는 달랐습니다. fresh top `20` 기준 `ai_scored_count=15`, `ai_scored_blank_reason_count=0`, `ai_scored_non_blank_reason_count=15` 였고, same refresh 구간의 app log도 `[RealtimeAiGateway][reason-coverage] ... blankReasonCount=0 nonBlankReasonCount=15` 로 찍혔습니다. 즉 현재 HEAD/local runtime 기준으로는 OpenAI upstream 응답과 persisted saved row가 모두 reason을 보존하고 있으며, 직전 all-blank 관측은 **stale app runtime drift** 로 읽는 편이 맞습니다.

이 판단에 대해 `2026-05-18` 에 bounded hybrid 완화도 한 번 실험했습니다. `RealtimeAiGateway` system prompt에 “청년을 생애주기/대상군에 명시적으로 포함하면 저소득층·주거취약계층·mixed life stage라는 이유만으로 0점을 주지 말라”는 가이드를 추가해 봤지만, server 재검증 결과 `3257/3209` 는 여전히 `savedAi=0` 이었고, 오히려 직전 완화됐던 `3288` 도 다시 `savedAi=0` 으로 내려갔습니다. 즉 이 prompt 완화는 효과적으로 검증되지 않았고, 현재 기준 mainline에서는 원복했습니다. 따라서 남은 이슈는 기술 수정이 아니라 **이 primary audience exclusion을 제품 정책으로 유지할지, 더 강한 프롬프트/후처리 완화를 승인할지에 대한 제품 판단** 입니다.

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

`2026-05-17` historical pre-real-user local audit 기준:

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

즉 이 시점엔 raw click 수와 weight bucket 개수만 보면 review 문턱은 넘었지만, current gate는 그걸로 reopen 하지 않았습니다.
당시 `REAL_NON_EXAMPLE` 로그도 `run-local-real-non-example-recommendation-seed-smoke.sh` 가 만든 local synthetic account `1명` 뿐이어서, 이 신호는 **실사용 품질 신호**가 아니라 **synthetic-heavy local baseline + cohort drill** 로 읽는 편이 맞았습니다.
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

`2026-05-17` historical pre-real-user local concentration audit 기준:

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

우선순위가 완전히 무시되는 상태는 아니었습니다.
다만 이 시점 latest batch의 `bounded local 1명 + local real-non-example seed 3명` 모두 실사용자가 아니라 로컬 seed 계정이고, `real_user` 는 `0명` 이었습니다. 따라서 이 수치는 제품 실사용 baseline이라기보다 historical local smoke/validation baseline으로 해석해야 합니다.
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
   `2026-05-17` historical pre-real-user local summary smoke 값은 `realUserTrafficGateInWindow=DEFERRED_NO_REAL_USER_TRAFFIC`, `recommendationReviewGate=DEFERRED_NO_REAL_USER_TRAFFIC`, `top1LeaderServiceId=2622`, `top1LeaderSharePct=60.17`, `top1LeaderUserMix.exampleUsers=274`, `top1LeaderUserMix.boundedLocalUsers=1`, `top1LeaderUserMix.localRealNonExampleSeedUsers=3`, `top1LeaderUserMix.realUserUsers=0`, `top1LeaderSignalSummary=LOCAL_SEED_WITHOUT_REAL_USER_LEADER`, `concentrationReadiness=CONCENTRATED_TOP1`, `realUserCohortGate=DEFERRED_NO_REAL_USER_COHORT`, `signalQuality=LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH` 입니다.
   `GET /api/admin/dashboard/recommendation-breakdowns` 도 같은 `realUserTrafficGateInWindow`, `recommendationReviewGate` 를 내려주고, 추가로 `latestBatchConcentration` 에 `latestBatchRows/users/distinctServices`, `top1Leader*`, `top1LeaderUserMix`, `top1LeaderSignalSummary`, `concentrationReadiness`, `realUserCohortGate`, `signalQuality` 를 포함합니다.
   또한 `topRepeatedServices`, `top1Services` 도 같이 내려 current latest batch에서 어떤 서비스가 반복 노출되고 top1을 나눠 갖는지 API에서 바로 읽을 수 있고, 각 row의 `userMix(example/boundedLocal/localRealNonExampleSeed/realUser/realNonExample users)` 로 leader 편중이 어느 cohort에서 왔는지도 바로 확인할 수 있습니다.
   `2026-05-17` historical pre-real-user local breakdown smoke 값은 `recommendationReviewGate=DEFERRED_NO_REAL_USER_TRAFFIC`, `top1LeaderServiceId=2622`, `top1LeaderSharePct=60.17`, `top1LeaderUserMix.exampleUsers=274`, `top1LeaderUserMix.boundedLocalUsers=1`, `top1LeaderUserMix.localRealNonExampleSeedUsers=3`, `top1LeaderUserMix.realUserUsers=0`, `top1LeaderSignalSummary=LOCAL_SEED_WITHOUT_REAL_USER_LEADER`, `concentrationReadiness=CONCENTRATED_TOP1`, `realUserCohortGate=DEFERRED_NO_REAL_USER_COHORT`, `signalQuality=LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH`, `topRepeatedServices[0].serviceId=2622`, `topRepeatedServices[0].userMix.exampleUsers=453`, `topRepeatedServices[0].userMix.localRealNonExampleSeedUsers=3`, `top1Services[0].serviceId=2622`, `top1Services[0].userMix.exampleUsers=274`, `top1Services[0].userMix.localRealNonExampleSeedUsers=3`, `top1Services[0].userMix.realUserUsers=0` 입니다.
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
