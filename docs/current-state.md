# 현재 상태

- 현재는 운영 전환 단계가 아니라 로컬 기능/구조 검증 단계입니다.
- 운영 서버 smoke/재기동/추천 진단까지는 실제로 확인했지만, 현재 active main track은 여전히 신규 운영 인프라 확장보다 로컬 기능/구조 검증과 bounded runtime 기준선 유지입니다.
- 프론트는 기본 연동/빌드/브라우저 smoke까지 확인했고, 현재는 신규 기능보다 회귀 방지와 운영 문서 정리가 우선입니다.
- `2026-05-19` 기준 로컬 full collect도 다시 끝까지 닫혔습니다. `collect/all` + `youth-details` + `gov24` 3축 + `bokjiro-details-refresh` 를 실제 runtime에서 다시 태웠고, 최종 기준선은 `YOUTH=2578`, `GOV24=10948`, `BOKJIRO_LOCAL=1223`, `BOKJIRO_CENTRAL=134`, `YOUTH_DETAILS failed=0`, `BOKJIRO_DETAIL_REFRESH requested=1358 saved=1357 failed=0` 입니다.
- `2026-05-19` 기준 위 로컬 full collect 뒤 `run-local-ops-baseline-suite.sh` 도 다시 통과했습니다. 즉 로컬 runtime 기준으로도 `health -> admin dashboard -> collect failures -> recommendation breakdowns` one-shot 기준선이 현재 수집 데이터 위에서 다시 green 입니다.
- `2026-05-18` 기준 YOUTH official fact는 `admin diagnostics`, 정책 상세 read-only, 정책 목록 compact badge, admin recommendation facet까지 연결됐고, retrieval/filter/scoring은 아직 안 건드렸습니다.
- `2026-05-19` 기준 `Gov24 canonical promotion` 로컬 closeout도 닫혔습니다. `GOV24_SERVICE_FIELD` exact-label term, `GOV24_USER_TYPE_TOKEN / GOV24_BENEFIT_TYPE_TOKEN` allowlist token term은 이제 `diagnostics`, admin facet, recommendation read-model, presentation, AI prompt, integration test까지 term-first 경계로 고정됐고, public filter/scoring/service_facts 승격은 아직 안 건드렸습니다.
- `2026-05-18` 기준 메일 발송은 provider-neutral SMTP 설정으로 일반화됐습니다. 현재 기본 runtime은 Gmail SMTP fallback을 유지하지만, 운영 방향은 건당 과금과 bulk 발송 적합성을 고려해 AWS SES SMTP 전환 검토가 우선입니다.
- `2026-05-18` 기준 collect/runtime governance도 한 단계 더 닫혔습니다. `admin/dashboard/collect-failures` 는 이제 실패/partial/streak/circuit 뿐 아니라 `nightly vs manual collect lane inventory`, lane별 `마지막 실행 요약`, `budget/config summary` 를 같이 내려, 어떤 source가 왜 scheduled/manual 인지와 최근 실행 결과, pacing/budget/retry guard를 운영 화면에서 바로 읽을 수 있습니다.
- `2026-05-18` 기준 운영 read-only 기준선도 one-shot wrapper로 묶였습니다. `deploy/smoke/run-local-ops-baseline-suite.sh` 는 `health -> admin dashboard -> collect failures -> recommendation breakdowns` 를 한 번에 다시 확인합니다.
- `2026-05-18` 기준 위 `ops baseline suite` 는 운영 서버에서도 끝까지 통과했습니다. 즉 health, admin dashboard, collect failures, recommendation breakdowns 세 축을 one-shot read-only smoke로 다시 확인하는 경로가 실제 runtime 기준선으로 닫혔습니다.
- `2026-05-20` 기준 recommendation 의 `savedAi=0` 이슈는 여전히 AI exclusion evidence 정리 단계지만, local generic-domain signup 기반 `REAL_USER` 표본은 이제 distributed baseline `30명` + targeted cohort library `50명` 으로 총 `80명`까지 확보됐습니다. 현재 `run-local-real-user-exclusion-readiness-check.sh` 기준 `dashboard_real_user_gate=READY_REAL_USER_TRAFFIC`, `breakdown_real_user_cohort_gate=READY_REAL_USER_COHORT`, `audit_scope_users=80`, `ctr_clicked_users=80`, `real_user_distribution_executed=true` 입니다. real-user-only concentration은 `top1_leader=드림나래(인천청년 면접복장 지원)`, `top1_share_pct=7.50`, `concentration_readiness=NO_PRIORITY_DOMINANT` 로 더 분산됐고, real-user latest top-N zero-AI 분포는 계속 `zero_ai_rows=53`, `zero_ai_users=17`, `zero_ai_reason_buckets=AUDIENCE_MISMATCH:8,INCOME_MISMATCH:11,OTHER:13,REGION_MISMATCH:1,STUDENT_AUDIENCE_MISMATCH:20` 입니다.
- 같은 `2026-05-20` 기준으로 mixed latest batch review gate blocker도 더 직접 좁혔습니다. `run-local-recommendation-review-gate-blocker-audit.sh` 결과 mixed latest batch는 `users=548`, `example_users=464`, `real_user_users=80`, `top1_leader=청년월세 지원사업`, `top1_leader_share_pct=50.55`, `top1_leader_real_user_users=0` 인 반면, real-user-only latest batch는 `top1_leader=드림나래(인천청년 면접복장 지원)`, `top1_leader_share_pct=7.50`, `concentration_readiness=NO_PRIORITY_DOMINANT` 입니다. 즉 현재 blocker는 recommendation rank bug보다 **mixed batch non-real dominance** 로 읽는 편이 맞습니다.
- 더 좁히면 mixed leader 서비스 `2622(청년월세 지원사업)` 는 real-user 쪽에서 `top1/top3/top5/top10/any-rank` 에 여전히 한 번도 안 나타납니다. 현재 값은 `real_user_mixed_leader_top10_count=0`, `real_user_mixed_leader_any_rank_count=0` 이고 blocker class는 `MIXED_BATCH_NON_REAL_DOMINANCE_WITH_NO_REAL_USER_PATH` 입니다. `housing` targeted cohort에 더해 `housing_leader_path` exact cohort(`인천광역시/중구/income=5/미취업/1인 가구`)를 generic-domain `REAL_USER` 로 10명 더 시드해도 path가 여전히 `0` 이었으므로, 지금 해석은 **추가 표본 수 부족**보다 **same-profile example vs real-user differential을 더 추적해야 하는 상태** 쪽이 더 정확합니다.
- 같은 exact profile differential audit도 별도 wrapper로 고정했습니다. `run-local-recommendation-same-profile-origin-differential-audit.sh` 기준 exact profile latest batch user 수는 `EXAMPLE_SMOKE=454`, `REAL_USER=14`, `LOCAL_REAL_NON_EXAMPLE_SEED=3`, `BOUNDED_LOCAL=1` 이고, example 쪽 `2622` 는 `top1=272`, `top3=434`, `top10=442` 인 반면 real-user 쪽은 `top1/top3/top5/top10/any-rank=0` 입니다. 그리고 대표 example/real-user를 직접 비교하는 `run-local-recommendation-same-profile-path-differential-audit.sh` 기준 target `2622` 는 example 쪽 `drop_stage=PRESENT_IN_SAVED_BATCH`, `latest_saved_rank=1` 인 반면 real-user 쪽은 `drop_stage=NOT_IN_SQL_RETRIEVAL` 입니다. 즉 현재 남은 질문은 “주거형 real-user가 더 필요하냐”가 아니라 **same-profile인데 왜 example은 saved batch에 `2622` 를 갖고 있고 generic-domain real-user는 SQL retrieval에도 못 들어가느냐** 입니다.
- recommendation 관찰 첫 entrypoint는 이제 `latest-overview` 입니다. `run-local-recommendation-ai-exclusion-latest-overview.sh` 는 latest export, latest status, 기본/strict gate를 한 번에 다시 태우고, artifact도 `latest-overview-summary.txt`, `latest-overview-note.md`, `latest-overview.json` 으로 남깁니다. `REAL_USER` readiness까지 같이 보려면 `INCLUDE_REAL_USER_READINESS=true` 와 admin/app 자격을 함께 넘기면 됩니다.
- 개별 wrapper를 직접 읽을 때는 `latest-status -> latest-status-export -> latest-gate -> real-user-exclusion-readiness-check` 순서로 보는 편이 맞습니다. 현재 local에서는 기본 gate는 `PASS`, strict gate(`FAIL_ON_LATEST_OBSERVATION_CHANGE=true`)는 `LATEST_OBSERVATION_CHANGED` 로 `FAIL` 이지만, 이는 stable baseline drift가 아니라 fresh window 관찰값 흔들림으로 해석합니다.
- reviewer가 이번 closeout 범위를 빨리 파악하려면 [recommendation-pr-review-brief.md](recommendation/recommendation-pr-review-brief.md) 를 먼저 보고, `REAL_USER` traffic/cohort가 실제로 생기면 [recommendation-real-user-recheck-checklist.md](recommendation/recommendation-real-user-recheck-checklist.md) 순서대로 다시 여는 편이 맞습니다.
- 현재 draft PR을 왜 유지하는지와 draft/reviewer-ready/merge-ready 경계는 [recommendation-pr-draft-exit-checklist.md](recommendation/recommendation-pr-draft-exit-checklist.md) 에 따로 정리돼 있습니다. 현재 기본 해석은 “코드 미완성”보다 `REAL_USER` evidence 부재에 가까우므로, reviewer-ready와 merge-ready를 분리해서 읽는 편이 맞습니다.
- closeout PR이 merge된 뒤 follow-up과 `REAL_USER` reopen 전 current 해석은 [recommendation-post-merge-followup-checklist.md](recommendation/recommendation-post-merge-followup-checklist.md) 를 기준으로 유지합니다.
- recommendation 문서군 전체에서는 이 흐름을 `review brief -> draft exit -> post-merge follow-up -> real-user recheck` 순서로 읽는 편이 맞고, 이 순서는 [recommendation-docs-index.md](recommendation/recommendation-docs-index.md) 의 `PR lifecycle order` 섹션에 고정돼 있습니다.
- `2026-05-20` KST 재확인에서도 위 판정은 그대로였습니다. 다만 일부 wrapper의 `generated_at` 과 artifact 경로는 UTC `Z` 기준이라 로컬 날짜보다 하루 전처럼 보일 수 있으므로, 최신 실행 여부는 `tmp/.../latest` symlink와 wrapper 재실행 자체로 판단하는 편이 맞습니다.
- `2026-05-20` 기준 repo-wide 예시 자격 inventory도 다시 분류됐습니다. active/current/support/handoff/history 문서와 `.env.example` 쪽 placeholder 정리는 닫혔고, 남은 `admin@example.com`, `password123!`, `Password123!`, `welfare1234!` 류 문자열은 주로 `phase-plan` / `troubleshooting-log` 이력, `deploy/smoke` local 기본값, `backend/src/test/**` 및 `application-integration.yml` fixture 같은 intentional scope에만 남아 있습니다.
- 지금 우선순위는 기능 검증, 구조 검증, 수정, 최적화/보안, 프론트 연동 검증 순서입니다.
- 운영/배포 관련 작업은 마지막 단계에서만 다룹니다.
- 현재는 `YOUTH/Gov24 소비처 추가` 와 `collect/runtime governance` 1차 정리가 모두 닫힌 상태입니다.
- recommendation, `Gov24 canonical promotion`, collect/runtime governance 는 모두 로컬 closeout 뒤 기준선 유지 단계입니다.
- 지금 다음 관찰 track은 recommendation `AI exclusion baseline` 재확인과 `REAL_USER` cohort readiness gate가 열리는지 보는 것입니다.
- 2026-05-10 기준 복지로 운영 계정을 확보했고, `중앙 list`, `중앙 detail`, `지자체 list`, `지자체 detail` 을 각각 일일 `100,000` quota로 다시 운영합니다.
- 코드 안전 상한은 복지로 list source별 `1회 10,000 items`, detail source별 `1회 10,000 calls` 로 둡니다.
- 복지로 detail backlog 는 더 이상 개발 계정 quota 때문에 의도적으로 남겨 두는 상태로 보지 않고, `gap fill` / `refresh` 로 full coverage 를 다시 채우는 대상으로 봅니다.
- 2026-05-13 기준 메인라인은 PostgreSQL 기반입니다. 정책 검색은 PostgreSQL FTS + `pg_trgm`, 챗봇 semantic retrieval은 `pgvector`, 추천/챗봇 탐색은 공통 exploration engine 기준으로 정리돼 있습니다.
- 2026-05-13 기준 retrieval 운영 검증 경로도 붙어 있습니다. `retrieval evaluation`, `compare/export`, `quality gate`, `category audit`, `embeddings rebuild` 를 admin API로 실행할 수 있습니다.
- 2026-05-13 기준 `reference-urls/rebuild` 런타임 검증도 끝냈습니다. 로컬 DB 기준 `welfare_service_details=1356` 에 대해 `scanned=1356`, `updated=1356`, `failed=0` 으로 `reference_urls_json` 을 모두 채웠습니다.
- 2026-05-13 기준 `reference-urls/rebuild` integration 회귀 검증도 추가했습니다. `missingOnly=true` 채움, 기존 값 skip, `missingOnly=false` overwrite 를 실제 PostgreSQL 기준으로 고정했습니다.
- 2026-05-14 기준 챗 세션 재조회도 branch/clarification 메타를 다시 복원합니다. `chat_retrieval_snapshots` 를 이용해 `answerMode`, `needsClarification`, `branchSuggestions` 가 새로고침 뒤에도 유지되도록 맞췄습니다.
- 2026-05-14 기준 정책 상세는 `referenceUrlsJson` 후보 링크를 실제 추가 링크/CTA로 사용합니다. 대표 `detailUrl` 이 비어도 fallback 링크가 있으면 화면에서 바로 열 수 있습니다.
- 2026-05-14 기준 legacy `deploy/mysql/apply-local-policy-sidecar-draft.sh` 는 PostgreSQL main에서 더 이상 MySQL draft SQL을 재적용하지 않습니다. 현재 integrated schema 존재 여부만 검증하고, missing이면 PostgreSQL bootstrap/collect flow를 쓰도록 명시적으로 실패합니다.
- 2026-05-14 기준 local replay smoke의 `RECONCILE_LOCAL_DB_ACCOUNTS` 는 PostgreSQL main에서 legacy MySQL 경로를 다시 타지 않도록 fail-fast 경계를 둡니다. `preflight-runtime-cutover-env.sh` 도 PostgreSQL JDBC + `currentSchema=youth_welfare_pii` 를 현재 기준으로 검증합니다.
- 2026-05-13 기준 로컬 데이터 기준선은 `welfare_services=3925`, `search_youth_relevant=2544`, `welfare_service_details=1356`, `policy_chunks=14210`, embedded chunk `14210` 입니다.
- 2026-05-13 기준 retrieval baseline 은 `top1HitRate=1.0`, `top3HitRate=1.0`, `branchSuggestionHitRate=1.0`, `emptyResultCount=0`, quality gate `passed=true` 입니다.

## 이 문서 읽는 법

- 이 문서의 상단 현재 상태, 아래 `지금 먼저 볼 문서`, `PostgreSQL 리팩토링 기준선` 은 active 기준선입니다.
- 실행 순서, 검증 기준, 현재 계약은 이 문서 상단과 연결된 current-state/testing/validation 문서를 우선합니다.
- 이 문서 아래쪽의 `변경 이력` 섹션들은 배경과 회귀 추적을 위한 기록입니다.
- 상단 active 기준선과 아래 이력 섹션이 충돌하면 상단 active 기준선과 실제 코드를 우선합니다.

## 지금 먼저 볼 문서

- 인증 문서군 진입점: [auth-docs-index.md](auth/auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](collect/collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- 프론트 QA 문서군 진입점: [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- 정책 문서군 진입점: [policy-docs-index.md](policy/policy-docs-index.md)
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 서버 런타임 drift 체크리스트: [server-runtime-drift-checklist.md](core/server-runtime-drift-checklist.md)
- 운영 baseline wrapper: [ops-baseline-runbook.md](core/ops-baseline-runbook.md)
- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)
- 인증/세션: [auth-session-revocation-current-state.md](auth/auth-session-revocation-current-state.md)
- 수집: [collect-current-state.md](collect/collect-current-state.md)
- 추천: [recommendation-current-state.md](recommendation/recommendation-current-state.md)
- 정책 정규화: [policy-normalization-current-state.md](policy/policy-normalization-current-state.md)
- PostgreSQL 전환/챗봇 retrieval: [postgres-chat-refactor-playbook.md](postgres/postgres-chat-refactor-playbook.md)
- 웹 기능 접근표: [feature-access-matrix.md](core/feature-access-matrix.md)
- 다음 active track 우선순위: [policy-next-active-track-priority.md](policy/policy-next-active-track-priority.md)
- 로컬 closeout pending: [policy-local-closeout-pending-inventory.md](policy/policy-local-closeout-pending-inventory.md)
- `Gov24` runtime closeout / blocked-deferred track: [policy-gov24-blocked-track-status.md](policy/policy-gov24-blocked-track-status.md)
- `Gov24` canonical promotion 설계: [policy-gov24-canonical-promotion-plan.md](policy/policy-gov24-canonical-promotion-plan.md)
- 정책 admin runtime 런북: [policy-admin-runtime-runbook.md](policy/policy-admin-runtime-runbook.md)
- 정책 quality summary 런북: [policy-quality-summary-runbook.md](policy/policy-quality-summary-runbook.md)
- 신규 source 구조: [policy-source-onboarding-architecture.md](policy/policy-source-onboarding-architecture.md)
- API 응답 contract: [api-mapping.md](core/api-mapping.md)

## PostgreSQL 리팩토링 기준선 (2026-05-13)

### 현재 끝난 것

- MySQL 런타임/스키마 의존 제거 후 PostgreSQL 기준 앱/테스트 복구
- 정책 검색을 MySQL FULLTEXT 에서 PostgreSQL FTS + `pg_trgm` 으로 전환
- 챗봇을 `branch suggestion -> retrieval -> grounded answer` 흐름으로 확장
- `policy_chunks` + `pgvector` 기반 semantic retrieval 연결
- 추천/챗봇 공통 exploration engine 정리
- retrieval snapshot / evaluation / compare / CSV export / quality gate / category audit 추가
- 임베딩 refresh 운영 경로와 admin rebuild 경로 연결
- category audit 응답에 `searchablePolicyRatio`, top unified category summary, 청년 broad category dominant mapping 요약 추가
- 정책 상세/챗봇 UI가 `selectionCriteria`, `homepageUrl`, `relatedLaw`, `formFiles`, `answerMode`, `needsClarification`, `branchSuggestions` 를 실제로 사용하도록 반영
- 수집 detail 단계에서 대표 URL 외 후보 URL 풀을 `referenceUrlsJson` 으로 보존하고, 본문 링크 추출도 함께 저장
- 기존 raw detail payload 로 `referenceUrlsJson` 을 다시 채우는 admin rebuild 경로 추가

### 현재 검증 기준

- 백엔드: `cd backend && ./gradlew test --no-daemon`
- DB/Redis 포함: `cd backend && ./gradlew integrationTest --no-daemon`
- 프론트: `cd frontend && npm run lint && npm run build`
- 운영 성격 확인:
  - `POST /api/admin/policies/retrieval-evaluations/gate`
  - `GET /api/admin/policies/category-audit`
  - `POST /api/admin/policies/reference-urls/rebuild` (`missingOnly=true` 기본)
  - 필요 시 `POST /api/admin/policies/embeddings/rebuild`

### 지금 남은 우선순위

- 운영 문서/런북 고정
- recommendation `AI exclusion suite` 기준선 유지 및 `REAL_USER` readiness gate 반복 확인
- `Gov24` runtime audit runbook 고정 및 반복 점검 경로 유지
- `Gov24` support unmapped inventory는 기준선으로 유지하되, 사업체/업종/창업 상태 code fact 승격은 현재 단계에서 deferred 유지
- `referenceUrlsJson` rebuild/backfill 운영 절차 문서화
- bounded policy admin runtime 경로(`reference-urls/rebuild`, `embeddings/rebuild`, `retrieval-evaluations/gate`, `category-audit`) one-page runbook 정리
- retrieval/category 상태를 one-shot summary smoke로 재확인하는 경로 고정 (`retrieval-baseline-v2`, gate `passed=true`, top unified `일자리`, searchable ratio `0.2399`)
- 프론트 번들 경고와 retrieval/embedding 운영 모니터링 보강
- `service_taxonomies` / `service_taxonomy_summary_slots` integrated schema를 기준으로 legacy draft 문서와 smoke 설명을 더 정리

### 현재 deferred 요약

| 항목 | 지금 안 하는 이유 | 다시 열 조건 |
|---|---|---|
| recommendation 제품 판단 | 재현 가능한 bugfix는 닫혔고, `2736` 류 local 청년 정책 노출 강화는 제품/모델링 선택 문제다. | 새 rank/cache/diagnostics 재현 버그가 생기거나, local 청년 정책 노출 강화가 명시 목표로 승인될 때 |
| `Gov24` stable code/import-backfill / full-scope fact promotion | `Gov24 canonical promotion` core lane은 로컬 closeout까지 끝났고, 현재 남은 것은 `stable code/import-backfill`, `supportConditions` business/industry/startup full-scope fact 승격, `YOUTH_MID` 연결처럼 외연을 넓히는 후속 과제뿐이다. | 외부 codebook/import source가 추가로 확보되거나, `service_facts`/public filter/scoring까지 확장하는 목표가 승인될 때 |
| 추가 infra/server 확장 | 서버 smoke/drift/runtime 검증은 닫혔지만, secret store/HTTPS/deploy 고도화는 지금 active main track이 아니다. | bounded runtime 기준선 유지보다 배포/운영 절차 확장이 우선 목표로 올라올 때 |

## 아래부터는 이력 / 참고

아래 섹션들은 현재 active 기준선을 덮어쓰지 않습니다.
특정 변경의 배경, 회귀 추적, 예전 판단 경로가 필요할 때만 참고합니다.

## 정책 목록 정렬 변경 이력 (2026-05-04)

### 현재 지원 sort 값 (`?sort=`)

| 값 | 설명 | UI 노출 |
|----|------|---------|
| `LATEST` | 최신순 (기본값) | ✅ |
| `VIEWS` | 조회수순 | ✅ |
| `DEADLINE` | 마감임박순 — apply_end_date 빠른 순, NULL(상시)은 맨 뒤 | ✅ |
| `NAME` | 이름순 | ❌ UI에서 제거됨, API 코드는 유지 |
| `RELEVANCE` | 관련도순 (검색 전용) | — |

### 변경 이유

- **DEADLINE 추가**: 프론트엔드 마감임박순 UI 기능 지원을 위해 백엔드 normalizeSort()와 SQL ORDER BY에 추가
- **NAME 제거 (UI)**: 프론트 정렬 Select에서 이름순 옵션 제거. 백엔드 코드(normalizeSort, SQL)는 API 호환성을 위해 그대로 유지

### 지역 우선 정렬 (region-first, B안)

지역 필터(sido/sgg)가 선택된 경우 sort 값에 따라 다르게 적용 (B안):

| sort | 지역 처리 |
|------|-----------|
| `LATEST` | 지역 일치 정책이 무조건 먼저 (strict region-first 그룹) |
| `VIEWS`, `DEADLINE` | 품질/긴급도 우선, 동점일 때만 지역 일치 정책 앞 (tiebreaker) |

프론트엔드 auto-sort: 지역 Select에서 전체 외 값 선택 시 sort를 `latest`로 자동 전환, 전체 복귀 시 `views`로 복귀.

- 관련 파일: `PolicyListService.java`, `PolicySearchService.java`, `WelfareServiceRepository.java`, `PolicyListReadCondition.java`, `PoliciesPage.jsx`
- 설계 배경: [policy-listing-sort-region-strategy.md](./history/policy/policy-listing-sort-region-strategy.md)

## 정책 카드 source 필드 변경 이력 (2026-05-04)

### 변경 전 → 후

프론트 `mapPolicySummary`의 source 폴백 순서:

| 전 | 후 |
|----|-----|
| `hostOrg → applyMethodName` | `hostOrg → sido → applyMethodName` |

### 변경 이유

- hostOrg가 null인 정책에서 카드 source 자리에 "방문, 인터넷"(applyMethodName)이 표시됨
- 상세 API에는 service_regions JOIN으로 지역명("경기")이 나오지만 목록 API에는 지역 정보가 없었음
- BOKJIRO_LOCAL 1,223개 정책이 hostOrg=null, sido_name=값 있음 → 이 정책들만 "경기" 등으로 표시됨
- YOUTH는 sido_name=null(region_code만 존재), BOKJIRO_CENTRAL은 service_regions 행 자체 없음 → 변화 없음

### 구현 위치

- `ServiceRegionRepository.java` — `findFirstSidoByServiceIds()` 추가
- `PolicySummaryResponse.java` — `sido` 필드 추가, `from()` 4-arg 오버로드 추가
- `PolicyPresentationReadService.java` — `buildSidoMap()` 추가 (목록·검색 공통 적용)
- `PoliciesPage.jsx` — `mapPolicySummary` source 폴백에 `sido` 삽입

## 정책 서비스 구조 변경 이력 (2026-05-05, main merge)

`PolicyService.java`가 역할별로 분리됨 (main branch, 2026-05-05 merge):

| 이전 | 이후 |
|------|------|
| `PolicyService.getList()` | `PolicyListService.getList()` |
| `PolicyService.getDetail()` | `PolicyDetailService.getDetail()` |
| `PolicyService.toggleBookmark()` | `PolicyBookmarkCommandService.toggleBookmark()` |
| (분산) 북마크·projection 조회 | `PolicyPresentationReadService.buildSummaryPage()` |

sido 로직(`buildSidoMap`)은 `PolicyPresentationReadService`에 통합되어 목록·검색 공통 적용.

## 진행/기록

- 진행 상황: [phase-plan.md](./phase-plan.md)
- 문제 기록: [troubleshooting-log.md](core/troubleshooting-log.md)
- 전체 길찾기: [documentation-map.md](./documentation-map.md)
