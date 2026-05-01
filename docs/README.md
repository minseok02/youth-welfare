# 문서 목차

이 파일은 `docs/` 문서의 시작점입니다.  
작업할 때 아래 상황에 맞는 문서를 먼저 열어보면 됩니다.

## 작업별로 보기

### 전체 구조를 파악할 때

- [architecture.md](./architecture.md)  
  백엔드, 프론트엔드, DB, 외부 API가 어떻게 연결되는지 확인합니다.

- [srs-v2.10.md](./srs-v2.10.md)  
  프로젝트 요구사항과 기능 범위를 확인합니다.

### API를 연결하거나 화면을 붙일 때

- [api-mapping.md](./api-mapping.md)  
  프론트엔드 화면과 백엔드 API 매핑을 확인합니다.

- [recommendation-pipeline.md](./recommendation-pipeline.md)  
  추천 API, 추천 저장, 점수 계산 흐름을 확인합니다.

### 챗봇을 구현하거나 설계할 때

- [chatbot-plan.md](./chatbot-plan.md)  
  챗봇 모듈 경계, 세션/메시지 API 초안, 작은 task 단위 구현 순서를 확인합니다.

### 공공 API 수집을 수정하거나 운영할 때

- [collect-ops.md](./collect-ops.md)  
  수집 배치, 429 대응, 중복 실행 방지, 부분 성공 기준을 확인합니다.

- [policy-normalization-research.md](./policy-normalization-research.md)
  신규 데이터 API 확장에 맞춰 공식 정규화 기준(온통청년 운영 코드북, 정부24/보조금24 지원조건 코드)과 AI enrichment 분리 방향을 확인합니다.

- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
  온통청년/복지로/Gov24 대표 샘플이 새 canonical 구조(`core/detail/taxonomy/facts`)에 실제로 어떻게 들어가는지와 source별 공백을 확인합니다.

- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
  새 canonical 구조의 sidecar 스키마(`service_taxonomies`, `service_taxonomy_terms`, `service_facts`)와 코드테이블 저장 방식을 확인합니다.

- [policy-normalization-youth-mid-alias-rules.md](./policy-normalization-youth-mid-alias-rules.md)
  온통청년 `YOUTH_MID` 의 comma-delimited 조합과 non-official variant(`온·오프라인교육`, `문화활동 및 생활지원`)를 canonical taxonomy에서 어떻게 split/skip할지 확인합니다.

- [policy-normalization-youth-mid-live-inventory.md](./policy-normalization-youth-mid-live-inventory.md)
  authenticated 온통청년 live 목록 응답 기준 `mclsfNm` 전체 inventory와 code-like field 분포, 왜 `srchPolyBizSecd` stable mapping이 아직 보류인지 확인합니다.

- [policy-normalization-youth-mid-stable-code-source-plan.md](./policy-normalization-youth-mid-stable-code-source-plan.md)
  `YOUTH_MID` stable code mapping SQL을 다시 열기 위한 최소 source 조건과, 어떤 근거는 충분하고 어떤 근거는 아직 불충분한지 확인합니다.

- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
  Gov24 필드를 현재 추천/응답 호환 분류로 브릿지하는 규칙과 복지로 text/detail의 facts fallback 허용 범위를 확인합니다.

- [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
  `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` import SQL을 다시 열기 위한 official source 조건과, deprecated endpoint를 왜 source로 쓰지 않는지 확인합니다.

- [policy-normalization-gov24-schema-acquisition-path.md](./policy-normalization-gov24-schema-acquisition-path.md)
  `GOV24_*` import/backfill SQL을 다시 열기 전에 current Swagger/schema export를 어디서 확보할지 확인합니다.

- [policy-normalization-gov24-swagger-visibility-check.md](./policy-normalization-gov24-swagger-visibility-check.md)
  current `data.go.kr` dataset page와 `schema.org` metadata만으로 `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` finite inventory가 실제로 보이는지 확인합니다.

- [policy-normalization-gov24-codebook-request-template.md](./policy-normalization-gov24-codebook-request-template.md)
  `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` codebook을 provider/operator 에 실제로 요청할 때의 제목, 본문, sufficient/insufficient 판정 기준을 정리합니다.

- [policy-normalization-gov24-support-condition-source-plan.md](./policy-normalization-gov24-support-condition-source-plan.md)
  `GOV24_SUPPORT_CONDITION` 을 representative subset seed에서 full inventory/backfill로 넓힐 때 어떤 official source가 더 필요한지 확인합니다.

- [policy-normalization-gov24-support-condition-request-template.md](./policy-normalization-gov24-support-condition-request-template.md)
  `GOV24_SUPPORT_CONDITION` full inventory/codebook을 provider/operator 에 실제로 요청할 때의 제목, 본문, sufficient/insufficient 판정 기준을 정리합니다.

- [policy-normalization-gov24-request-package-checklist.md](./policy-normalization-gov24-request-package-checklist.md)
  `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE`, `GOV24_SUPPORT_CONDITION` 요청을 한 번에 보낼 때의 발송 순서와 트랙별 판정 체크리스트를 정리합니다.

- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
  `Gov24` blocked SQL/doc 트랙, 로컬 검증 트랙, 운영/deploy 트랙 중 무엇을 다음 active main track으로 둘지 정리합니다.

- [policy-normalization-fact-merge-rules.md](./policy-normalization-fact-merge-rules.md)
  복지로 list aggregate 와 detail aggregate 가 같은 `service_facts` 슬롯에 들어올 때의 merge/upsert 우선순위와 `fact_merge_key` 규칙을 확인합니다.

- [policy-normalization-beneficiary-dedupe-strategy.md](./policy-normalization-beneficiary-dedupe-strategy.md)
  복지로 detail beneficiary soft taxonomy(`기초생활수급자`, `차상위계층`)를 multi-term으로 저장한 뒤 추천/read-model에서 어떻게 dedupe할지 확인합니다.

- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
  canonical sidecar를 추천 파이프라인에 붙일 때 raw term, dedupe bucket, retrieval/scoring 경계를 어디서 나눌지 확인합니다.

- [policy-normalization-compat-storage-policy.md](./policy-normalization-compat-storage-policy.md)
  `compat_unified_category` 를 현재 phase에서 저장 필드로 유지할지, read-model 계산값으로 미루지 않을지와 그 이유를 확인합니다.

- [policy-normalization-unified-category-response-bridge.md](./policy-normalization-unified-category-response-bridge.md)
  검색/상세/랭킹/추천 응답의 `unifiedCategory` 필드를 canonical taxonomy 전환 중에도 어떤 의미로 유지할지 확인합니다.

- [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
  `findCandidates* -> RetrievalService -> RuleScoringService -> DefaultPriorityMatcher` 순서로 추천 본체를 점진 이행해야 하는 이유와 각 단계의 허용/금지 범위를 확인합니다.

- [policy-normalization-text-constraint-output-model.md](./policy-normalization-text-constraint-output-model.md)
  `TextConstraintExtractor` 를 legacy `COND_*` 토큰 생성기에서 `service_facts` 저장 규격 친화 fact candidate extractor로 바꿀 목표 출력 모델을 확인합니다.

- [policy-normalization-raw-ai-enrichment-pipeline.md](./policy-normalization-raw-ai-enrichment-pipeline.md)
  신규 source-specific 필드를 `raw 보존 -> official/rule-derived canonical -> AI batch enrichment` 순서로 흡수하는 경계를 확인합니다.

- [auth-logout-revocation-scope-policy.md](./auth-logout-revocation-scope-policy.md)
  `logout` 의 즉시 무효화 범위를 `bearer-present exact token revoke` 와 `cookie-only refresh-only` 로 어떻게 나눌지 확인합니다.

- [auth-revocation-reopen-order.md](./auth-revocation-reopen-order.md)
  future user-level revoke를 다시 열어야 할 때 `withdraw`, `관리자 강제 로그아웃`, generic `cookie-only logout` 중 무엇을 먼저 다룰지 확인합니다.

- [auth-withdraw-revocation-next-step.md](./auth-withdraw-revocation-next-step.md)
  `withdraw` 를 다음 revoke 후보로 보더라도, 구현보다 먼저 baseline smoke/inventory를 고정해야 하는 이유와 다음 액션을 확인합니다.

- [auth-admin-revoke-boundary-policy.md](./auth-admin-revoke-boundary-policy.md)
  현재 admin 권한 회수가 `SECURITY_ADMIN_EMAILS + 앱 재기동` 과 token/session revoke 중 어디까지를 뜻하는지 확인합니다.

- [auth-admin-refresh-revoke-policy.md](./auth-admin-refresh-revoke-policy.md)
  allowlist 제거 후 기존 admin refresh token을 즉시 끊을지, 아니면 새 token부터 role만 제거할지 현재 계약을 확인합니다.

- [auth-admin-forced-logout-baseline-policy.md](./auth-admin-forced-logout-baseline-policy.md)
  future `admin forced logout` 가 `allowlist revoke` 와 달리 무엇을 즉시 차단해야 하는지, baseline/success criteria를 확인합니다.

- [auth-admin-forced-logout-entrypoint-policy.md](./auth-admin-forced-logout-entrypoint-policy.md)
  future `admin forced logout` 의 운영자 진입점을 admin API로 둘지, revoke state의 즉시 source를 Redis로 둘지 확인합니다.

- [auth-admin-forced-logout-api-contract.md](./auth-admin-forced-logout-api-contract.md)
  future `admin forced logout` 의 최소 API 계약(path, body, idempotency, success 의미)을 확인합니다.

- [auth-admin-forced-logout-redis-shape.md](./auth-admin-forced-logout-redis-shape.md)
  future `admin forced logout` 가 Redis에 `refresh:{userKey}` 삭제와 `access-cutoff:{userKey}` 를 어떤 의미로 남길지 확인합니다.

- [auth-admin-forced-logout-issued-at-policy.md](./auth-admin-forced-logout-issued-at-policy.md)
  future `admin forced logout` cutoff 비교에서 표준 `iat` 만으로 충분한지, 별도 millis precision claim이 필요한지 확인합니다.

- [auth-admin-forced-logout-jwt-helper-policy.md](./auth-admin-forced-logout-jwt-helper-policy.md)
  `JwtUtil` 에 `iatm` claim write/read helper를 어떻게 추가하고, legacy token fallback을 어디까지 허용할지 확인합니다.

- [auth-admin-forced-logout-legacy-token-rollout-policy.md](./auth-admin-forced-logout-legacy-token-rollout-policy.md)
  forced logout 기능 rollout 이후 `iatm` 없는 legacy admin access token을 재로그인 요구 대상으로 볼지 확인합니다.

- [auth-admin-forced-logout-legacy-error-policy.md](./auth-admin-forced-logout-legacy-error-policy.md)
  forced logout 보호 경계에서 `iatm` 없는 legacy admin access token을 어떤 에러 계약으로 노출할지 확인합니다.

- [auth-admin-forced-logout-implementation-location.md](./auth-admin-forced-logout-implementation-location.md)
  forced logout 차단을 `JwtAuthenticationFilter` 에서 처리할지, 별도 controller/service guard로 둘지 구현 위치를 확인합니다.

- [auth-admin-forced-logout-helper-interface.md](./auth-admin-forced-logout-helper-interface.md)
  forced logout helper/service가 filter에 어떤 read 메서드와 admin API에 어떤 write 메서드를 노출할지 확인합니다.

- [auth-admin-forced-logout-helper-name-policy.md](./auth-admin-forced-logout-helper-name-policy.md)
  forced logout helper/service 이름을 `AccessTokenRevocationService` 와 어떻게 분리할지 확인합니다.

- [auth-admin-forced-logout-helper-method-name-policy.md](./auth-admin-forced-logout-helper-method-name-policy.md)
  `UserSessionRevocationService` 의 read/write 메서드명을 generic하게 유지할지 확인합니다.

- [auth-admin-forced-logout-service-structure-policy.md](./auth-admin-forced-logout-service-structure-policy.md)
  `UserSessionRevocationService` 를 새 클래스로 둘지, 기존 `AccessTokenRevocationService` 와 어떤 관계로 둘지 확인합니다.

- [auth-admin-forced-logout-package-dependencies-policy.md](./auth-admin-forced-logout-package-dependencies-policy.md)
  `UserSessionRevocationService` 를 어느 package에 두고 어떤 최소 dependency만 주입할지 확인합니다.

- [auth-admin-forced-logout-implementation-order.md](./auth-admin-forced-logout-implementation-order.md)
  forced logout 구현을 `JwtUtil` helper부터 시작할지, service skeleton부터 시작할지 순서를 확인합니다.

- [auth-admin-forced-logout-audit-scope-policy.md](./auth-admin-forced-logout-audit-scope-policy.md)
  forced logout 운영 증적에서 `cutoffMillis` 를 response/log/Redis 중 어디까지 노출할지 최소 범위를 확인합니다.

- [auth-admin-forced-logout-actor-log-policy.md](./auth-admin-forced-logout-actor-log-policy.md)
  forced logout 로그 라인에 `actor` 를 지금 바로 추가할지, 아니면 future audit reopen으로 미룰지 확인합니다.

- [auth-admin-forced-logout-closeout.md](./auth-admin-forced-logout-closeout.md)
  현재 phase의 `admin forced logout` 1차 hardening 범위를 어디까지 완료로 보고, 다음 활성 pending을 무엇으로 넘길지 확인합니다.

- [policy-normalization-compat-category-drift-inventory.md](./policy-normalization-compat-category-drift-inventory.md)
  local `service_taxonomies` 스냅샷 기준으로 `compat_unified_category` 와 canonical summary(`youth_major_label`, `gov24_*`) 사이 drift/공백/legacy raw 복사 흔적을 확인합니다.

- [policy-normalization-youth-major-summary-rules.md](./policy-normalization-youth-major-summary-rules.md)
  온통청년 raw `category_main` 을 `service_taxonomies.youth_major_*` single canonical summary로 언제 collapse 하고 언제 `NULL` 로 둘지 확인합니다.

- [policy-normalization-compat-other-youth-major-policy.md](./policy-normalization-compat-other-youth-major-policy.md)
  `compat=기타` 인데 canonical `youth_major` 는 채워지는 `YOUTH` 서비스들을 priority/read-model에서 그대로 둘지, 별도 힌트로만 볼지 해석 정책을 확인합니다.

- [policy-normalization-compat-other-youth-major-inventory.md](./policy-normalization-compat-other-youth-major-inventory.md)
  `compat=기타 + canonical youth_major 채움` `421`건이 `복지문화 / 참여권리 / 교육 / 일자리 / 주거` 로 실제 어떻게 분포하는지와 bridge 검토 우선순위를 확인합니다.

- [policy-normalization-compat-other-youth-major-bridge-review.md](./policy-normalization-compat-other-youth-major-bridge-review.md)
  `참여권리 / 교육 / 복지문화` 3개 bridge candidate를 row-level sample 기준으로 지금 승격할지, 조건부 후보로 둘지, 보류할지 판정을 확인합니다.

- [policy-normalization-priority-bridge-table-policy.md](./policy-normalization-priority-bridge-table-policy.md)
  canonical `youth_major -> legacy priority bucket` explicit bridge table을 지금 도입할지, 아니면 실험/전환 착수 시점까지 보류할지 정책을 확인합니다.

- [policy-normalization-education-priority-experiment.md](./policy-normalization-education-priority-experiment.md)
  `교육 -> 교육·직업훈련` 을 기본 동작 변경 없이 첫 priority 실험 후보로 승인할지, 승인한다면 `DefaultPriorityMatcher` 가 아니라 `RuleScoringService` bonus 경계에서 어디까지 좁게 실험할지 확인합니다.

- [policy-normalization-education-priority-flag-scope.md](./policy-normalization-education-priority-flag-scope.md)
  `교육 -> 교육·직업훈련` narrow experiment를 실제로 켤 때 사용할 flag key와 on/off 범위를 전역 boolean 기준으로 확인합니다.

- [policy-normalization-education-priority-config-boundary.md](./policy-normalization-education-priority-config-boundary.md)
  `교육 -> 교육·직업훈련` narrow experiment flag를 실제 코드에 넣을 때 `RuleScoringService` 내부 어느 helper/config 경계에서 읽을지 확인합니다.

- [policy-normalization-education-priority-validation-criteria.md](./policy-normalization-education-priority-validation-criteria.md)
  `교육 -> 교육·직업훈련` narrow experiment를 켰을 때 sample top-N 과 explanation 을 어떤 기준으로 통과/보류 판정할지 확인합니다.

- [policy-normalization-education-priority-implementation-slot.md](./policy-normalization-education-priority-implementation-slot.md)
  `교육 -> 교육·직업훈련` narrow experiment를 실제 코드에 넣을 때 `RuleScoringService.applyPriorityWeight(...)` 어느 filter/helper 경계에 최소 diff로 끼울지 확인합니다.

- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)
  `교육 -> 교육·직업훈련` narrow experiment 구현 후 local에서 `flag off/on` 추천 refresh를 어떤 명령 순서로 비교할지 확인합니다.

- [policy-normalization-education-control-drift-analysis.md](./policy-normalization-education-control-drift-analysis.md)
  latest replay artifact 기준으로 sample B(control)의 `finalScore` / top-10 drift가 왜 strict fail 기본값이 아닌지 확인합니다.

- [policy-normalization-education-control-ruleweighted-snapshot.md](./policy-normalization-education-control-ruleweighted-snapshot.md)
  sample B(control)를 direct `off/on` replay 했을 때 `rule_weighted_score` / `final_score` 가 실제로 달라지는지 DB snapshot 기준으로 확인합니다.

- [openai-replay-stability-options.md](./openai-replay-stability-options.md)
  `real-openai` replay에서 같은 `promptSha256` 에도 `ai_score` 가 달라질 때 `seed`, `system_fingerprint`, prompt caching 중 무엇을 먼저 검토할지 공식 OpenAI 문서 기준으로 확인합니다.

- [openai-replay-validation-policy.md](./openai-replay-validation-policy.md)
  `rule-only` replay와 `real-openai` replay를 같은 pass/fail 기준으로 볼지, 현재 어떤 쪽을 hard gate로 둘지 확인합니다.

- [openai-replay-allowed-drift-metrics.md](./openai-replay-allowed-drift-metrics.md)
  `real-openai` replay에서 strict equality 대신 `top-N target count`, `score delta`, `explanation drift` 중 무엇을 gate metric으로 볼지 확인합니다.

- [openai-ai-score-product-policy.md](./openai-ai-score-product-policy.md)
  same prompt/seed/fingerprint 조건에서도 `ai_score` drift가 남을 때, 제품이 무엇을 보장하고 무엇을 보장하지 않는지 정리한 정책입니다.

- [openai-replay-diagnostic-lane-plan.md](./openai-replay-diagnostic-lane-plan.md)
  `real-openai` replay를 PR gate가 아닌 diagnostic lane으로 분리한 뒤, 실제로 local/manual, self-hosted runner, ops cron 중 어디에 붙일지 확인합니다.

- [openai-replay-cron-runbook.md](./openai-replay-cron-runbook.md)
  `ops cron host` 에 nightly replay / cleanup cron 을 실제로 적용할 때의 사전 체크, `crontab -e` 예시, 등록 후 확인 절차를 확인합니다.

- [openai-replay-cron-security-boundary.md](./openai-replay-cron-security-boundary.md)
  `ops cron host` 의 replay `cron user` 가 어떤 권한과 secret 접근 범위를 가져야 하는지, 왜 `root` crontab 을 기본값으로 두지 않는지 확인합니다.

- [policy-normalization-education-target-sample-inventory.md](./policy-normalization-education-target-sample-inventory.md)
  `compat=기타 + youth_major=교육` target row가 실제 추천 결과 집합에 들어오는 local replay sample이 현재 있는지와, region/age scan 결과를 확인합니다.

- [policy-normalization-participation-subset-bridge-policy.md](./policy-normalization-participation-subset-bridge-policy.md)
  `참여권리` 전체는 승격하지 않되, `청년참여` subset만 future bridge 후보로 남길지와 왜 지금 바로 실험하지 않는지 정리한 정책입니다.

- [policy-normalization-income-threshold-soft-signal-policy.md](./policy-normalization-income-threshold-soft-signal-policy.md)
  복지로 `threshold_like` income signal 을 `INCOME_*` hard fact 로 올리지 않고 optional soft signal 로만 유지하는 이유와 소비 범위를 정리한 정책입니다.

- [policy-normalization-youth-income-zero-policy.md](./policy-normalization-youth-income-zero-policy.md)
  온통청년 `min_income/max_income = 0/0` 을 retrieval에서 미지정으로 볼지 실제 소득 gate 로 볼지 결정한 정책을 확인합니다.

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
  실제 DB 적재 스냅샷을 기준으로 일자리/주거/장학/공공서비스 source를 `정책형 / listing형 / reference형` 으로 어떻게 나눠 붙일지 확인합니다.

- [policy-listing-source-schema-draft.md](./policy-listing-source-schema-draft.md)
  `고용24/워크넷 채용정보`, `마이홈포털 공공주택 모집공고/단지/대기현황` 같은 listing형 source를 `welfare_services` 대신 어떤 분리 스키마로 받을지 확인합니다.

- [policy-source-canonical-onboarding-priority.md](./policy-source-canonical-onboarding-priority.md)
  `정부지원일자리정보`, `구직자취업역량 강화프로그램`, `Gov24/보조금24` 같은 정책형 source를 어떤 순서로 canonical onboarding / live validation 할지 확인합니다.

- [policy-normalization-blocked-sql-reopen-priority.md](./policy-normalization-blocked-sql-reopen-priority.md)
  아직 source-of-truth 부족으로 막혀 있는 `GOV24_*` / `YOUTH_MID` 관련 SQL 초안 중 무엇을 먼저 다시 열지 확인합니다.

- [policy-scholarship-reference-matrix-draft.md](./policy-scholarship-reference-matrix-draft.md)
  한국장학재단/국가장학금 계열에서 `제도 row` 와 `지원가능대학/학기/지원구간` reference matrix를 어떻게 분리할지 확인합니다.

- [policy-bokjiro-detail-validation-rehearsal.md](./policy-bokjiro-detail-validation-rehearsal.md)
  복지로 live detail 적재 기준에서 `welfare_service_details` / `service_facts` 를 어떤 순서로 검증할지 확인합니다.

- [policy-bokjiro-detail-budget-observability-policy.md](./policy-bokjiro-detail-budget-observability-policy.md)
  복지로 detail refresh/gap-fill 의 `centralBudget` / `localBudget` 을 admin observability에 어디까지 노출할지 확인합니다.

- [policy-bokjiro-gap-fill-budget-strategy.md](./policy-bokjiro-gap-fill-budget-strategy.md)
  `bokjiro-details-gap-fill` 를 몇 라운드, 몇 호출부터 시작하고 어디서 멈출지 운영 기준을 확인합니다.

- [policy-bokjiro-gap-fill-execution-policy.md](./policy-bokjiro-gap-fill-execution-policy.md)
  `bokjiro-details-gap-fill` 추가 실행을 현재 phase에서 기본 작업으로 둘지, 수동 catch-up/on-demand 로만 둘지 확인합니다.

- [troubleshooting-log.md](./troubleshooting-log.md)  
  이전에 발생한 장애와 해결 과정을 확인합니다.

### DB 스키마나 마이그레이션을 건드릴 때

- [db-migration.md](./db-migration.md)  
  DB 변경 사항, 마이그레이션 기준, 스키마 반영 방법을 확인합니다.

- [db-search-recommend-ops-guide.md](./db-search-recommend-ops-guide.md)
  검색/추천 쿼리의 EXPLAIN 체크 포인트, 인덱스 후보, DB 운영 시작안을 확인합니다.

### 사용자 데이터 분리나 보안 경계를 정리할 때

- [user-data-separation-design.md](./user-data-separation-design.md)
  인증/프로필/PII 분리 방향과 서비스 계정 권한 경계를 확인합니다.
  현재 cut-over 진행 상태와 남은 구현 작업은 `phase-plan.md`와 같이 봅니다.

- [admin-account-runbook.md](./admin-account-runbook.md)
  운영 admin 계정 생성, `SECURITY_ADMIN_EMAILS` 반영, 검증/회수 절차를 확인합니다.

- [db-account-cutover-runbook.md](./db-account-cutover-runbook.md)
  기존 운영 DB에서 `app_core_rw` / `app_pii_rw` / `notification_pii_ro` / `migration_admin` 계정 생성과 앱 datasource 전환 절차를 확인합니다.

### 테스트를 실행하거나 실패 원인을 볼 때

- [testing.md](./testing.md)  
  기본 테스트와 MySQL/Redis 통합 테스트 실행 방법을 확인합니다.

### GitHub로 협업할 때

- [github-workflow.md](./github-workflow.md)  
  2명 기준 프론트/백 담당 분리, 브랜치, 커밋, 푸시, PR 규칙을 확인합니다.

### 배포하거나 서버 설정을 볼 때

- [deployment.md](./deployment.md)  
  Docker, nginx, 운영 환경 변수, 배포 절차를 확인합니다.

- [runtime-cutover-checklist.md](./runtime-cutover-checklist.md)
  운영 전환 시 `계정 전환 -> migration -> preflight -> app 재기동 -> smoke` 순서를 한 페이지로 빠르게 확인합니다.

- [runtime-cutover-log-template.md](./runtime-cutover-log-template.md)
  운영 전환 직후 남길 실행 로그 템플릿과 최소 증적 항목을 확인합니다.

- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
  로그인, refresh, 추천, 북마크, admin status용 최소 curl smoke 명령을 바로 복사해 실행할 수 있습니다.

### 일정, 단계, 데모 흐름을 볼 때

- [phase-plan.md](./phase-plan.md)  
  현재 구현 상태와 남은 1차 작업을 확인합니다.

- [demo-scenario.md](./demo-scenario.md)  
  시연 흐름과 데모용 사용자 시나리오를 확인합니다.

## 전체 문서 목록

- [api-mapping.md](./api-mapping.md)
- [admin-account-runbook.md](./admin-account-runbook.md)
- [architecture.md](./architecture.md)
- [chatbot-plan.md](./chatbot-plan.md)
- [collect-ops.md](./collect-ops.md)
- [db-search-recommend-ops-guide.md](./db-search-recommend-ops-guide.md)
- [db-migration.md](./db-migration.md)
- [db-account-cutover-runbook.md](./db-account-cutover-runbook.md)
- [demo-scenario.md](./demo-scenario.md)
- [deployment.md](./deployment.md)
- [runtime-cutover-checklist.md](./runtime-cutover-checklist.md)
- [runtime-cutover-log-template.md](./runtime-cutover-log-template.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [github-workflow.md](./github-workflow.md)
- [phase-plan.md](./phase-plan.md)
- [auth-logout-revocation-scope-policy.md](./auth-logout-revocation-scope-policy.md)
- [auth-revocation-reopen-order.md](./auth-revocation-reopen-order.md)
- [auth-withdraw-revocation-next-step.md](./auth-withdraw-revocation-next-step.md)
- [auth-admin-revoke-boundary-policy.md](./auth-admin-revoke-boundary-policy.md)
- [auth-admin-refresh-revoke-policy.md](./auth-admin-refresh-revoke-policy.md)
- [policy-normalization-youth-income-zero-policy.md](./policy-normalization-youth-income-zero-policy.md)
- [policy-normalization-youth-mid-live-inventory.md](./policy-normalization-youth-mid-live-inventory.md)
- [policy-normalization-youth-mid-stable-code-source-plan.md](./policy-normalization-youth-mid-stable-code-source-plan.md)
- [policy-normalization-education-control-drift-analysis.md](./policy-normalization-education-control-drift-analysis.md)
- [policy-normalization-education-control-ruleweighted-snapshot.md](./policy-normalization-education-control-ruleweighted-snapshot.md)
- [openai-replay-stability-options.md](./openai-replay-stability-options.md)
- [openai-replay-validation-policy.md](./openai-replay-validation-policy.md)
- [openai-replay-allowed-drift-metrics.md](./openai-replay-allowed-drift-metrics.md)
- [openai-replay-diagnostic-lane-plan.md](./openai-replay-diagnostic-lane-plan.md)
- [policy-normalization-education-target-sample-inventory.md](./policy-normalization-education-target-sample-inventory.md)
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-fact-merge-rules.md](./policy-normalization-fact-merge-rules.md)
- [policy-normalization-beneficiary-dedupe-strategy.md](./policy-normalization-beneficiary-dedupe-strategy.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-compat-storage-policy.md](./policy-normalization-compat-storage-policy.md)
- [policy-normalization-unified-category-response-bridge.md](./policy-normalization-unified-category-response-bridge.md)
- [policy-normalization-recommendation-migration-order.md](./policy-normalization-recommendation-migration-order.md)
- [policy-normalization-text-constraint-output-model.md](./policy-normalization-text-constraint-output-model.md)
- [policy-normalization-raw-ai-enrichment-pipeline.md](./policy-normalization-raw-ai-enrichment-pipeline.md)
- [policy-normalization-youth-mid-alias-rules.md](./policy-normalization-youth-mid-alias-rules.md)
- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-research.md](./policy-normalization-research.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [srs-v2.10.md](./srs-v2.10.md)
- [testing.md](./testing.md)
- [troubleshooting-log.md](./troubleshooting-log.md)
- [user-data-separation-design.md](./user-data-separation-design.md)

## 보관 문서

- [archive/project-plan-v11.md](./archive/project-plan-v11.md)  
  과거 프로젝트 플랜 원본입니다. 현재 작업 기준은 `phase-plan.md`를 봅니다.

- [archive/runtime-cutover-log-sample.md](./archive/runtime-cutover-log-sample.md)
  운영 cutover 실행 로그 템플릿이 실제로 어떻게 채워지는지 보여주는 redacted 예시입니다.
