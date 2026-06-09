# 현재 상태

이 문서는 긴 이력 저장소가 아니라, 지금 무엇을 먼저 읽고 무엇을 먼저 검증해야 하는지 빠르게 찾는 active 진입 문서입니다.

## 한 줄 요약

- 현재 active main track은 기능 추가가 아니라 안정화와 회귀 방지입니다.
- 안정화 단계의 작업 기준은 [stabilization-checklist.md](core/stabilization-checklist.md) 를 먼저 봅니다.
- 최근 보안/운영 follow-up은 [core/security-hardening-current-state.md](./core/security-hardening-current-state.md) 를 먼저 봅니다.
- 실제 운영 전환 절차가 필요할 때만 [deployment.md](./deployment.md) 를 같이 봅니다.

## 지금 유지하는 active 기준선

- 보안: `Tomcat 10.1.55`, `pgjdbc 42.7.11`, `Bouncy Castle 1.84`, logout 후 older token까지 `401/A006`
- collect/runtime: 로컬 full collect, ops baseline, broad quality 재검사까지 다시 green
- recommendation/policy: 각 current-state 문서와 runbook을 기준으로 baseline 유지 단계
- 프론트: 기본 연동, lint, build, browser smoke까지 확인 완료

## 지금 먼저 할 일

1. 지금 작업 주제의 `*-docs-index.md` 와 `*-current-state.md` 를 먼저 확인합니다.
2. 필요한 smoke/test만 먼저 돌려 현재 기준선을 확인합니다.
3. 코드 수정 후 문서, 검증 결과, Git 정리를 같은 작업 단위로 마무리합니다.

## 지금 먼저 볼 문서

- 안정화 체크리스트: [stabilization-checklist.md](core/stabilization-checklist.md)
- 최종 운영 closeout 체크리스트: [final-ops-closeout-checklist.md](core/final-ops-closeout-checklist.md)
- 인수인계: [stabilization-handoff.md](./stabilization-handoff.md)
- 인증 문서군 진입점: [auth-docs-index.md](auth/auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](collect/collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- 프론트 QA 문서군 진입점: [frontend-qa-docs-index.md](frontend/frontend-qa-docs-index.md)
- 정책 문서군 진입점: [policy-docs-index.md](policy/policy-docs-index.md)
- `Gov24` bounded lane closeout: [policy/policy-gov24-lane-closeout.md](policy/policy-gov24-lane-closeout.md)
- `Gov24` taxonomy validation smoke: `bash deploy/smoke/run-local-gov24-taxonomy-validation.sh`
- `Gov24` async collect smoke: `bash deploy/smoke/run-local-gov24-async-collect-smoke.sh`
- 성능 문서군 진입점: [performance-docs-index.md](performance/performance-docs-index.md)
- 성능 최적화 변경 로그: [performance-optimization-log.md](performance/performance-optimization-log.md)
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 서버 런타임 drift 체크리스트: [server-runtime-drift-checklist.md](core/server-runtime-drift-checklist.md)
- 보안/운영 hardening 현재 상태: [security-hardening-current-state.md](core/security-hardening-current-state.md)
- 운영 baseline wrapper: [ops-baseline-runbook.md](core/ops-baseline-runbook.md)
- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- OpenAI runtime 계약: [openai-runtime-contract.md](core/openai-runtime-contract.md)
- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)

## 문의/제보 운영 기준

- 정책 데이터 오류는 정책 상세의 `정책 오류 제보`로 받습니다.
- 서비스 사용 문의는 공개 `/support` 페이지에서 받습니다.
- 관리자 대시보드는 제보/문의 recent queue를 모두 노출합니다.
  - `정책 오류 제보 recent queue`
  - `서비스 문의 recent queue`
- 정책 데이터 품질 review는 별도 `정책 중복 review queue` 로 봅니다.
  - `YOUTH / BOKJIRO_LOCAL` duplicate title/host 묶음
- `BOKJIRO_LOCAL` duplicate는 title-only false positive가 많아서 기본값을 `지역별 개별 사업 유지`로 둡니다.
- admin duplicate queue는 현재 `reviewClass` 를 노출하고 `exact -> mirror -> drift -> title-only 주의` 순서로 정렬해, `YOUTH` 진짜 중복 후보를 먼저 보게 합니다.
- `YOUTH` duplicate는 `같은 기관 + 같은 기간 + 같은 URL` 반복이면 진짜 수집 중복 후보로 먼저 봅니다.
- 이 `YOUTH` true duplicate candidate는 `bash deploy/smoke/run-local-youth-duplicate-candidate-audit.sh` 로 먼저 좁혀서 봅니다.
- queue를 실제로 줄일 때는 [policy/policy-data-quality-triage-runbook.md](policy/policy-data-quality-triage-runbook.md) 기준으로 `오류 제보 -> 링크 review -> 중복 review` 순서와 1회 처리량을 그대로 따릅니다.
- 두 queue 모두 `OPEN -> REVIEWED` 처리와 운영 메모를 지원합니다.
- `POST /api/admin/dashboard/policy-error-reports/{reportId}/review`
- `POST /api/admin/dashboard/support-inquiries/{inquiryId}/review`
- `POST /api/admin/dashboard/policy-duplicate-groups/review`
- recent queue는 `status=OPEN|REVIEWED|ALL` query로 운영 필터를 바꿔 볼 수 있습니다.
- admin attention feed는 열린 backlog를 아래 key로 승격합니다.
  - `standard-code-backlog`
    - 자동 보정 후보나 충돌 gap이 있으면 `warning`
    - 단순 미입력 잔량이면 `info`
  - `policy-error-report-backlog`
  - `support-inquiry-backlog`
  - `policy-duplicate-backlog`
  - `notification-backlog`
    - 안 읽은 알림 수
    - 재시도 대기 failed notification 수
    - 종결 failed notification 수
- notification backlog 세부 triage는 `bash deploy/smoke/run-local-notification-backlog-audit.sh` 로 다시 읽습니다.
  - unread를 `digest`, `deadline`, `system`, 장기 미열람으로 나눠 봅니다.
  - failed는 `retry due`, `retry scheduled later`, `terminal` 로 나눠 봅니다.
  - 운영 기준은 [core/notification-backlog-audit-runbook.md](./core/notification-backlog-audit-runbook.md) 를 봅니다.
- sample title triage는 `bash deploy/smoke/run-local-notification-backlog-sample-audit.sh` 로 다시 읽습니다.
  - stale unread가 실제로 어떤 제목/종류에 몰리는지 `digest/deadline/system` 기준으로 봅니다.
  - 운영 기준은 [core/notification-backlog-sample-audit-runbook.md](./core/notification-backlog-sample-audit-runbook.md) 를 봅니다.
- target cluster triage는 `bash deploy/smoke/run-local-notification-stale-target-audit.sh` 로 다시 읽습니다.
  - `2주 이상 unread` 가 특정 정책/링크 target에 몰리는지 확인합니다.
  - 운영 기준은 [core/notification-stale-target-audit-runbook.md](./core/notification-stale-target-audit-runbook.md) 를 봅니다.
  - 첫 local triage target이었던 `/policies/2622` stale deadline reminder cluster (`5 users / 5 rows`) 는 `hide-stale` 경로로 정리됐습니다.
  - 현재 latest 기준은 `stale_14d_total=0`, `decision_class=NO_STALE_TARGETS` 이고, 남은 unread backlog는 `unread_total=24`, `stale_unread_7d=12` 수준의 recommendation digest tail 입니다.
  - server/RDS 최신 sample 기준 unread는 `digest=24`, `deadline=0`, `system=0`, failed notification은 `0` 입니다.
  - 즉 현재 알림 운영 우선순위는 `14일 초과 stale cluster hide` 보다 `7일 초과 recommendation digest tail의 cadence/가치` 를 관찰하는 단계입니다.

## 작업 전 기본 검증 기준

- one-shot local active baseline: `bash deploy/smoke/run-local-active-baseline-suite.sh`
- one-shot server active baseline: `APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-active-baseline-suite.sh`
  - 참고: `deployed-origin` 모드는 배포 번들에서 성립하지 않는 `@dev-only` admin forced-failure Playwright 2개를 자동 제외합니다.
  - latest artifact: `tmp/active-baseline-suite/latest-active-baseline-summary.txt`, `tmp/active-baseline-suite/latest-active-baseline-summary.json`
  - latest summary/json 에 `ops_attention_feed_*`, `ops_user_profile_standard_code_*`, `ops_recommendation_standard_code_*` 가 같이 포함됩니다.
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/active-baseline-suite/latest/` snapshot은 남습니다.
- one-shot current priority suite: `bash deploy/smoke/run-local-current-priority-suite.sh`
- one-shot server current priority suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-current-priority-suite.sh`
  - latest artifact: `tmp/current-priority-suite/latest-current-priority-summary.txt`, `tmp/current-priority-suite/latest-current-priority-summary.json`
  - latest summary/json 에 `active_baseline_attention_feed_*`, `active_baseline_user_profile_standard_code_*`, `recommendation_standard_code_*` 가 같이 포함됩니다.
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/current-priority-suite/latest/` snapshot은 남습니다.
  - same-config `active_baseline` latest가 TTL 안에 있으면 재사용할 수 있고, summary/json 에 `active_baseline_reused=true` 로 남습니다.
- backend only: `cd backend && ./gradlew test --no-daemon`
- frontend only: `cd frontend && npm run lint && npm run build && npm run test:e2e`
- runtime read-only baseline only: `bash deploy/smoke/run-local-ops-baseline-suite.sh`
- collect governance observation only: `bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- collect legacy repair only: `bash deploy/smoke/run-local-collect-legacy-repair-suite.sh`
- `Gov24` async collect/status only: `bash deploy/smoke/run-local-gov24-async-collect-smoke.sh`

추천을 다시 열지 말지 빠르게 다시 보고 싶으면 아래 wrapper를 먼저 씁니다.

- recommendation reopen precheck: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
- server/RDS recommendation reopen precheck: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
  - 현재 server/RDS 최신 기준은 `reopen_precheck_status=KEEP_OBSERVING`, `real_user_dashboard_gate=DEFERRED_REAL_USER_SAMPLE_THIN`, `real_user_breakdown_cohort_gate=DEFERRED_REAL_USER_SAMPLE_THIN`, `real_user_top1_leader_signal_summary=EXAMPLE_SMOKE_ONLY_LEADER`, `review_gate_policy_promotion_execution_status=DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW` 이다.
  - 즉 지금은 recommendation score/weight/prompt를 다시 열지 않고 real-user sample과 leader signal을 관찰한다.
- recommendation observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
- server/RDS recommendation observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
  - latest artifact: `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`, `tmp/recommendation-observation/latest-recommendation-observation-note.md`, `tmp/recommendation-observation/latest-recommendation-observation.json`
  - latest housing effect stdout: `tmp/recommendation-observation/latest/housing-standard-code-effect.out`
  - latest welfare matrix stdout: `tmp/recommendation-observation/latest/welfare-standard-code-matrix.out`
  - latest adoption audit stdout: `tmp/recommendation-observation/latest/recommendation-standard-code-adoption.out`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/recommendation-observation/latest/` snapshot은 남습니다.
- recommendation region mismatch audit: `bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh`
- server/RDS recommendation region mismatch audit: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh`
- bounded recommendation region mismatch repair: `USER_LIMIT=25 DRY_RUN=false bash deploy/smoke/run-local-recommendation-region-mismatch-repair.sh`
  - current query는 이미 맞는데 old saved batch가 남아 있을 때 쓰는 repair 경로입니다.
- housing standard code matrix audit: `bash deploy/smoke/run-local-housing-standard-code-matrix-audit.sh`
- welfare standard code matrix audit: `bash deploy/smoke/run-local-welfare-standard-code-matrix-audit.sh`
- user profile standard code coverage audit: `bash deploy/smoke/run-local-user-profile-standard-code-coverage-audit.sh`
- collect governance observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- server/RDS collect governance observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
  - latest artifact: `tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt`, `tmp/collect-governance-observation/latest-collect-governance-observation-note.md`, `tmp/collect-governance-observation/latest-collect-governance-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/collect-governance-observation/latest/` snapshot은 남습니다.
- collect source resilience audit: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-source-resilience-audit.sh`
- server/RDS collect source resilience audit: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-source-resilience-audit.sh`
  - latest artifact: `tmp/collect-source-resilience-audit/latest-collect-source-resilience-summary.txt`, `tmp/collect-source-resilience-audit/latest-collect-source-resilience-note.md`, `tmp/collect-source-resilience-audit/latest-collect-source-resilience.json`
- auth observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
- server/RDS auth observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
  - latest artifact: `tmp/auth-observation/latest-auth-observation-summary.txt`, `tmp/auth-observation/latest-auth-observation-note.md`, `tmp/auth-observation/latest-auth-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/auth-observation/latest/` snapshot은 남습니다.
- ops observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
- server/RDS ops observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
  - latest artifact: `tmp/ops-observation/latest-ops-observation-summary.txt`, `tmp/ops-observation/latest-ops-observation-note.md`, `tmp/ops-observation/latest-ops-observation.json`
  - attention feed is included in the same summary/json (`attention_feed_*`, `attention_feed.items`)
  - admin dashboard summary notification section now includes `notification_unread_alerts`, `notification_retryable_failed_notifications`, `notification_terminal_failed_notifications`
  - standard code coverage is included in the same summary/json (`user_profile_standard_code_*`)
  - recommendation standard code effect/matrix is included in the same summary/json (`recommendation_standard_code_*`)
  - adoption audit is included in the same summary/json (`recommendation_standard_code_adoption_*`)
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/ops-observation/latest/` snapshot은 남습니다.
- nightly standard-code observation wrapper: `bash deploy/smoke/run-nightly-standard-code-observation.sh`
- server/RDS nightly standard-code observation wrapper: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-nightly-standard-code-observation.sh`
  - default log root: `/var/log/youth-welfare/standard-code-observation`
- nightly ops handoff wrapper: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-nightly-ops-handoff.sh`
  - default log root: `/var/log/youth-welfare/nightly-ops-handoff`
  - cron/install procedure: [nightly-ops-handoff-cron-runbook.md](./core/nightly-ops-handoff-cron-runbook.md)
  - idempotent crontab install: `bash deploy/smoke/install-nightly-ops-handoff-cron.sh`
  - appends compact lines to `nightly-summary-YYYY-MM-DD.log`
- admin attention feed: `GET /api/admin/dashboard/attention-feed`
  - collect drift, 표준코드 backlog, wrapper warning을 재사용 가능한 운영 알림 목록으로 반환합니다.
  - wrapper current-priority 비교는 현재/이전 summary 양쪽에 값이 있는 metric만 비교합니다.
    - 이전 missing-count가 비어 있으면 `0명` 이 아니라 `이전값 없음` 으로 읽습니다.
- frontend observation suite: `bash deploy/smoke/run-local-frontend-observation-suite.sh`
- server frontend observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-frontend-observation-suite.sh`
  - 기본 deployed-origin 경계는 fresh e2e user/bootstrap을 먼저 준비하고 `@dev-only`, `@admin-required` 케이스를 제외합니다.
  - admin dashboard smoke까지 포함하려면 `RUN_FRONTEND_ADMIN_E2E=true` 를 명시합니다.
  - latest artifact: `tmp/frontend-observation/latest-frontend-observation-summary.txt`, `tmp/frontend-observation/latest-frontend-observation-note.md`, `tmp/frontend-observation/latest-frontend-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/frontend-observation/latest/` snapshot은 남습니다.
- policy quality observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- server/RDS policy quality observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- policy data triage observation suite: `bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh`
- server/RDS policy data triage observation suite: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh`
- nightly server/RDS policy quality wrapper: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-nightly-policy-quality-observation.sh`
  - retrieval/category summary뿐 아니라 `policy-search-scenario-audit` 도 같이 실행해 검색어/지역 필터 품질을 compact nightly line에 남깁니다.
  - latest artifact: `tmp/policy-quality-observation/latest-policy-quality-observation-summary.txt`, `tmp/policy-quality-observation/latest-policy-quality-observation-note.md`, `tmp/policy-quality-observation/latest-policy-quality-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/policy-quality-observation/latest/` snapshot은 남습니다.
  - search/detail convenience field baseline도 같이 본다. 현재 API read model은 `providerName`, `regionLabel`, `applicationPeriod`, `statusLabel` 을 summary/detail 응답에 직접 내려서, 프론트가 raw field를 다시 조합하지 않아도 핵심 품질 필드를 바로 읽을 수 있다.
- keyword/region scenario audit: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-search-scenario-audit.sh`
  - `월세/청약/면접비/자격증` 실검색어와 `서울특별시` region filter 샘플을 다시 읽는다.
  - 현재 최신 기준은 `missing_provider_count=0`, `missing_status_count=0`, `missing_region_for_local_count=0`, `region_filter_mismatch_count=0`, `decision_class=BASELINE_HEALTHY` 이다.
- policy link quality audit: `bash deploy/smoke/run-local-policy-link-quality-audit.sh`
  - 현재 최신 기준은 `missing_any_link_youth=558`, `missing_any_link_active_visible_youth=164`, `missing_any_link_active_past_end_tail_youth=15`, 나머지 source `0`, `decision_class=ACTIVE_LINK_REVIEW_PRIORITY` 이다.
  - 즉 broad source tail 전체보다, 실제로 노출될 수 있는 `YOUTH active visible` 164건이 더 actionable 하다.
- policy link review sample audit: `bash deploy/smoke/run-local-policy-link-review-sample-audit.sh`
  - 현재 최신 기준은 `active_visible_youth_total=163`, `benefit_support=33`, `announcement_recruitment=11`, `program_event=9`, `event_culture=5`, `other=105`, `decision_class=MIXED_LINK_REVIEW_PRIORITY` 이다.
  - 즉 `정책 링크 review queue`는 단일 기준으로 닫기보다 `급부형`, `공고/프로그램형`, 나머지 `other` tail을 나눠 review 하는 편이 맞다.
- policy link review queue runbook: `docs/policy/policy-link-review-queue-runbook.md`
  - 운영자는 `지원금/급부형 -> 공고/모집형 -> 프로그램형 -> 행사/문화형 -> 기타` 순서로 보는 편이 맞다.
  - `REVIEWED` 는 “고쳤다”가 아니라 “운영자가 한 번 판단과 note를 남겼다”는 뜻으로 읽는다.
- policy data triage observation suite: `bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh`
  - `policy-data-quality`, `policy-link-review-sample`, `youth-duplicate-candidate` 를 한 번에 다시 읽는 compact handoff wrapper다.
  - 현재는 `duplicate -> link review -> drift tail` 순서로 backlog를 보는 편이 맞는지 빠르게 판정한다.
  - wrapper summary는 raw duplicate/link 후보와 실제 운영 `OPEN` queue를 분리해서 남긴다. 운영 queue가 닫혀 있으면 raw 후보가 남아도 `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 로 읽고, 새 `OPEN` queue가 생길 때만 review를 재개한다.
  - 현재 server/RDS 최신 기준은 `policy_duplicate_open_groups=0`, `policy_duplicate_open_rows=0`, `policy_link_open_reviews=0`, `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 이다.
  - 같은 triage 결정은 admin dashboard summary의 `policy triage` 카드에도 노출된다. 운영자는 `exact duplicate`, `mirror variant`, `급부형 링크 review` 수치를 한 화면에서 보고 현재 backlog 우선순위를 바로 읽을 수 있다.
- policy application period quality audit: `bash deploy/smoke/run-local-policy-application-period-quality-audit.sh`
  - 현재 최신 기준은 `active_past_end_youth=208`, `active_past_end_gov24=0`, `active_past_end_youth_future_end_tail=208`, `active_past_end_youth_true_review=0`, `closed_future_end_total=2` 이다.
  - 즉 남은 `YOUTH` 잔량은 대부분 `end_date` 가 아직 미래인 source tail 이고, 사용자-facing `ACTIVE_ONLY` 경계에서는 이미 숨겨진다.
- policy host/org quality audit: `bash deploy/smoke/run-local-policy-host-org-quality-audit.sh`
  - 현재 최신 기준은 `missing_host_bokjiro_local=1224`, `missing_operating_youth=1507`, `placeholder_host_total=0`, `decision_class=SOURCE_CONTRACT_DOMINANT` 이다.
- policy status sync smoke: `bash deploy/smoke/run-local-policy-status-sync-smoke.sh`
  - `POST /api/admin/policies/status-sync` 를 수동 실행해 stale status/date mismatch를 실제로 줄이는 운영 경로다.
  - 현재는 `reopened_count=2` 까지 확인됐고, 남은 `closed_future_end` 는 `end_date` 가 이미 지난 `YOUTH` 2건 수준이다.

## 작업 전/후 읽는 법

- 상단 요약과 연결된 current-state 문서가 active source of truth입니다.
- 수치 기준선, 긴 판단 기록, 과거 closeout 맥락은 각 문서군의 current-state 또는 `history/` 문서에서 확인합니다.
- active 문서와 오래된 기록이 충돌하면 active 문서와 실제 코드를 우선합니다.

## 관련 기록

- 진행 기록: [phase-plan.md](./phase-plan.md)
- 문제/해결 로그: [core/troubleshooting-log.md](core/troubleshooting-log.md)
- 전체 길찾기: [documentation-map.md](./documentation-map.md)
