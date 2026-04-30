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

- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
  Gov24 필드를 현재 추천/응답 호환 분류로 브릿지하는 규칙과 복지로 text/detail의 facts fallback 허용 범위를 확인합니다.

- [policy-normalization-fact-merge-rules.md](./policy-normalization-fact-merge-rules.md)
  복지로 list aggregate 와 detail aggregate 가 같은 `service_facts` 슬롯에 들어올 때의 merge/upsert 우선순위와 `fact_merge_key` 규칙을 확인합니다.

- [policy-normalization-beneficiary-dedupe-strategy.md](./policy-normalization-beneficiary-dedupe-strategy.md)
  복지로 detail beneficiary soft taxonomy(`기초생활수급자`, `차상위계층`)를 multi-term으로 저장한 뒤 추천/read-model에서 어떻게 dedupe할지 확인합니다.

- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
  canonical sidecar를 추천 파이프라인에 붙일 때 raw term, dedupe bucket, retrieval/scoring 경계를 어디서 나눌지 확인합니다.

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

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
  실제 DB 적재 스냅샷을 기준으로 일자리/주거/장학/공공서비스 source를 `정책형 / listing형 / reference형` 으로 어떻게 나눠 붙일지 확인합니다.

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
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-fact-merge-rules.md](./policy-normalization-fact-merge-rules.md)
- [policy-normalization-beneficiary-dedupe-strategy.md](./policy-normalization-beneficiary-dedupe-strategy.md)
- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
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
