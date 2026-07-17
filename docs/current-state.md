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
- 2026-07-17 추천 AI latency 재검토 기준: recommendation gate는 여전히 `KEEP_OBSERVING/reopen_allowed=false` 로 읽는다. 따라서 candidate 축소, AI top-N 변경, weight/prompt/source balancing은 열지 않고, 먼저 저장 추천을 즉시 보여 주면서 동일 refresh 파이프라인을 백그라운드로 실행하는 품질 보존형 async refresh UX/API 개선만 [recommendation-ai-latency-quality-first-plan-2026-07-17.md](recommendation/recommendation-ai-latency-quality-first-plan-2026-07-17.md) 기준으로 검토한다.
- 2026-07-16 최종 운영 handoff 기준: public path는 Route53 public hosted zone의 `youthmoa.kr` / `www.youthmoa.kr` A alias -> ALB -> EC2 2대 -> nginx -> Spring Boot 이고, ALB target은 최종 점검에서 `2` healthy 다. direct EC2 public `80/443` 은 차단되어 ALB를 우회하지 않는다. 최종 기능 회귀는 `18/18` 단계 통과, 오류/429 `0` 이며, 최종 운영 확인 절차는 [core/final-production-operations-runbook-2026-07-16.md](core/final-production-operations-runbook-2026-07-16.md) 를 기준으로 본다.
- 2026-06-20 지역 데이터 운영 기준: 정규화 지역 컬럼과 지역 JSON/fact에는 좌표값 유입이 `0`건이다. 사용자 `sido/sgg` 가 명확한데 `region_code` 가 비어 있던 row는 `users` 591건, `user_profiles` 592건을 bounded 보정했고, 두 테이블 모두 `missing_with_sido_sgg=0` 으로 닫았다. `service_regions.region_code` NULL 1,228건은 전부 `BOKJIRO_LOCAL` 이름 기반 매칭 경로라 정상 잔량으로 둔다. 정책 검색 지역 audit, Gov24 region coverage audit, 추천 region mismatch audit은 모두 통과했다.
- 2026-06-20 최신 운영 배포 기준: 운영 compose app/redis를 `main` `08589523` 기준으로 rebuild/recreate 했고 health는 `UP` 다. `run-prod-cutover-verification.sh` 는 env/RDS privilege/nginx edge/public guard를 통과했고, runtime API smoke는 signup/login/recommend/bookmark/logout 및 presented/older token after logout `401/A006` 경계를 통과했다.
- 2026-06-20 chat memory/ranking 운영 기준: 후속 질문 smoke에서 `chat_sessions.context_state_json.memory` 에 질문 2개와 추천 정책 1개가 저장되고, 최신 `chat_retrieval_snapshots.search_keyword` 에 `저장된 관심 맥락` 이 포함됐다. 두 번째 답변은 `POLICY_GROUNDED`, reference count `1` 로 확인했다.
- 2026-06-21 챗봇 운영 재점검/배포 기준: 코드 레벨 점검에서 `chat_retrieval_snapshots` 가 세션 삭제 뒤 고아 row로 남을 수 있는 문제와 신청 코칭 action link의 URL user-info 우회 가능성을 닫았다. 운영 RDS에 `fk_crs_session ON DELETE CASCADE` 와 `idx_crs_session_id` 를 적용했고 기존 orphan snapshot 28건을 정리했다. 실제 운영 시나리오 audit에서 서울/마포 사용자의 `주거 지원 -> 월세 쪽으로`, `취업 지원 -> 주거 지원 -> 월세 쪽으로` 가 모두 `서울시 청년 월세 지원`, `서울 월세 지원 알려줘 -> 그럼 전세는?` 가 `전세보증금반환보증 보증료 지원` 중심으로 응답하는 것을 확인했다. 추가 운영 API/DB 점검 `tmp/chat-continuity-coaching-manual/20260621T114215Z` 에서는 `서울 월세 지원 알려줘 -> 그럼 전세는? -> 다시 월세 쪽으로 돌아가면?` 3턴이 모두 `POLICY_GROUNDED` 로 이어졌고 `chat_sessions.context_state_json` 에 3개 질문과 누적 추천 정책이 남았으며, `chat_retrieval_snapshots` 3건도 후속 검색 키워드/branch/preferred terms를 저장했다. 같은 점검에서 `coachPolicyId=14886` 신청 코칭은 `APPLICATION_COACHING`, 고정 참조 정책, `RELATED_SITE/OFFICIAL_APPLY/NOTICE` action link, 자격·신청방법·제출서류·공식 링크 안내를 반환했다. 이 수동 기준은 [run-local-chat-continuity-coaching-smoke.sh](../deploy/smoke/run-local-chat-continuity-coaching-smoke.sh) 로 영구화했고, 운영형 로컬/RDS 기준 최신 artifact `tmp/chat-continuity-coaching-smoke/20260621T123649Z` 에서 `continuity_message_count=6`, `continuity_snapshot_count=3`, `APPLICATION_COACHING`, `post_delete_snapshot_count=0` 으로 통과했다. 최종 `run-local-chat-followup-scenario-audit.sh` 는 `POLICY_GROUNDED 4 / CLARIFICATION 0 / HOLD_LONG_TERM_MEMORY`, `run-local-chat-followup-smoke.sh`, `run-local-public-profile-chat-smoke.sh`, `run-prod-cutover-verification.sh` 모두 통과했고 app/redis는 healthy 다.
- 2026-06-21 챗봇 프론트 UX 기준: 정책 상세의 `AI와 신청 준비하기` Playwright smoke를 강화해 자동 세션 생성/`coachPolicyId` 전송이 각각 1회만 일어나는지, URL에서 `coachPolicyId` 가 제거되고 `session` 으로 고정되는지, action link 버튼이 보이는지, 새로고침·뒤로가기·앞으로가기 후 중복 전송이 없는지 확인한다. 로컬 mock과 deployed-origin `https://youthmoa.kr` 기준 targeted Playwright 모두 통과했고 `frontend npm run lint` 도 통과했다.
- 2026-06-21 챗봇 품질 회귀셋 기준: [run-local-chat-followup-scenario-audit.sh](../deploy/smoke/run-local-chat-followup-scenario-audit.sh) 는 10개 시나리오에서 대화 이어짐, branch 후속, 면접비, 자격증 응시료, 청년근로자 교통비, 청년창업센터 사업화자금, 서울 청년수당, 청년 동아리 활동비를 검증한다. 최신 운영 RDS artifact `tmp/chat-followup-scenario-audit/20260621T161949Z` 는 `scenario_count=10`, `policy_grounded_last_turn_scenarios=9`, `profile_check_last_turn_scenarios=1`, `HOLD_LONG_TERM_MEMORY` 로 통과했고, 각 시나리오 reference title fragment 검증도 모두 통과했다. 이 라운드에서 `창업 지원 정책 알려줘 -> 청년창업센터 사업화자금 쪽으로 보여줘` 가 `지원` label 토큰 때문에 `housing-cash` 로 오인되어 월세 정책을 참조할 수 있던 문제를 고쳤다. `지원` 은 branch match에서 제외하고, 창업/사업화자금은 `일자리` category hint로 고정했으며, 구체 토큰이 2개 이상인 후속 질문은 현재 질문 자체로 retrieval 한다. `transport-expense` 처럼 근거 정책은 찾았지만 서울/마포 smoke 사용자와 지역 자격이 맞지 않을 수 있는 경우는 `profile_check` 성공 축으로 분리하고, 근거 없는 clarification이나 기대 title 불일치는 실패 처리한다.
- 2026-06-21 챗봇 운영 관측 기준: [run-local-chat-observability-audit.sh](../deploy/smoke/run-local-chat-observability-audit.sh) 는 1/7/30일 `chat_messages` assistant reference/action link 비율과 `chat_retrieval_snapshots` branch suggestion, clarification, zero-result, avg result_count, fallback strategy를 집계한다. 최신 단독 artifact `tmp/chat-observability-audit/20260621T162145Z` 기준 7일 창은 assistant `11`, reference rate `81.82%`, action link rate `27.27%`, clarification `0.00%`, 전체 zero-result `20.00%`, non-branch zero-result `0.00%`, `CHAT_BASELINE_HEALTHY` 다. branch suggestion snapshot의 설계상 0-result는 별도 `zero_result_branch_suggestion_snapshots` 로 분리해 실제 검색 실패 경보를 부풀리지 않는다.
- 2026-06-21 챗봇 실사용자 샘플 기준: [run-local-chat-real-user-quality-sample-audit.sh](../deploy/smoke/run-local-chat-real-user-quality-sample-audit.sh) 를 추가해 `EXAMPLE_SMOKE/BOUNDED_LOCAL/LOCAL_REAL_NON_EXAMPLE_SEED` 와 테스트 도메인을 제외한 실제 사용자 채팅만 읽는다. 원문 질문은 artifact에 남기지 않고 사용자 hash, session, 추정 answer mode, reference/action link 수, reference title만 남긴다. 최신 운영 RDS artifact `tmp/chat-real-user-quality-sample-audit/20260621T162136Z` 는 real user `2`, sessions `2`, assistant messages `5`, reference rate `100.00%`, clarification rate `40.00%`, decision `REAL_USER_CHAT_SAMPLE_THIN` 이다. 표본이 `MIN_ASSISTANT_MESSAGES_FOR_ATTENTION=20` 미만이라 제품 판단은 smoke 회귀셋 기준으로 유지하고 관찰만 계속한다.
- 2026-06-21 챗봇 신청 코칭 매트릭스 기준: [run-local-chat-application-coaching-matrix-audit.sh](../deploy/smoke/run-local-chat-application-coaching-matrix-audit.sh) 를 추가해 `reference_urls_json` 이 많은 정책, 링크가 없는 정책, 종료된 참고 링크 정책, 종료된 신청 링크 정책을 각각 새 세션에서 확인한다. 최신 운영 RDS artifact `tmp/chat-application-coaching-matrix-audit/20260621T162114Z` 는 4개 scenario 모두 `APPLICATION_COACHING`, 지정 정책 reference, 기대 action link type/개수, 신청·자격·서류 안내 조건을 통과했다.
- 2026-06-21 정책 데이터 품질 기준: 운영 RDS 기준 [run-local-policy-data-triage-observation-suite.sh](../deploy/smoke/run-local-policy-data-triage-observation-suite.sh) 는 `tmp/policy-data-triage-observation/20260621T141645Z` 에서 `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 로 통과했다. raw audit에는 YOUTH duplicate group `85`, BOKJIRO_LOCAL duplicate group `59`, 링크 공백 active visible YOUTH `161` 이 남지만, 운영 review queue는 duplicate/link 모두 `0` 이다. [run-local-policy-quality-observation-suite.sh](../deploy/smoke/run-local-policy-quality-observation-suite.sh) 는 `tmp/policy-quality-observation/20260621T125317Z` 에서 retrieval `top1/top3/branch=1.0`, empty result `0`, `BASELINE_HEALTHY` 로 통과했다.
- 2026-06-20 웹푸시 운영 기준: `/sw.js` 는 HTTPS에서 `application/javascript` 200, 인증된 `push-public-key` 는 87자 public key를 반환한다. 운영 DB는 `web_push_subscriptions total=1/enabled=1`, enabled endpoint host는 `fcm.googleapis.com` 이며, `push-test-send` 는 `attempted=1`, `sent=1`, `disabled=0`, `failed=0` 으로 성공했다.
- 2026-06-21 프론트 deployed-origin 기준: `FRONTEND_PUBLIC_BASE_URL=https://youthmoa.kr` 관측 스위트에서 사용자 Playwright smoke 31개가 통과했고, admin 포함 재실행에서는 `RUN_FRONTEND_ADMIN_E2E=true`, deployed-origin 기준 Playwright 52개가 통과했다. 최신 admin 포함 artifact는 `tmp/frontend-observation/20260621T143547Z`, `decision_class=BASELINE_HEALTHY` 다. 이 실행은 allowlist의 `admin-smoke@smoke.local` BOUNDED_LOCAL 계정을 명시 credential로 재검증한 결과다. 관측 wrapper와 Playwright bootstrap은 admin E2E가 켜졌는데 admin email/password가 없으면 기본 `admin@example.com/password123!` 로 조용히 떨어지지 않고 실패한다. `E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD` 도 preflight에서 정상 인식하며, 기본 admin credential은 `ALLOW_DEFAULT_ADMIN_CREDENTIALS=true` 를 명시한 로컬 smoke에서만 허용한다.
- 2026-06-21 운영 관측 후속 기준: `run-local-ops-observation-suite.sh` 최신 artifact `tmp/ops-observation/20260621T162356Z` 는 `BASELINE_HEALTHY`, collect 실패/partial/circuit `0`, policy duplicate/link open queue `0`, chat observability `CHAT_BASELINE_HEALTHY`, attention warning `0` 으로 통과했다. 알림은 `tmp/notification-stale-target-audit/20260621T162159Z` 기준 `stale_14d_total=0`, `NO_STALE_TARGETS` 이고, `tmp/notification-backlog-sample-audit/20260621T162159Z` 기준 unread `26`건은 모두 `RECOMMENDATION_DIGEST` 다. 이 중 7일 초과 `14`건과 recent `12`건이 같은 제목 `맞춤 정책 추천이 도착했어요` 로 2명에게 남아 있으며, 14일 초과 target cluster가 생기기 전까지는 hide보다 digest tail 관찰이 우선이다.
- 2026-06-24 알림 운영 재점검 기준: `/policies/3324` 의 `RECOMMENDATION_DIGEST` 14일 초과 unread 4건을 admin bounded `hide-stale` 경로로 숨겼다. 재점검 artifact는 `tmp/notification-backlog-audit/20260624T162302Z`, `tmp/notification-backlog-sample-audit/20260624T162302Z`, `tmp/notification-stale-target-audit/20260624T162302Z` 이며, 현재 `unread_total=26`, `stale_unread_7d=14`, `stale_unread_14d=0`, `failed notification=0`, `stale_14d_total=0`, `NO_STALE_TARGETS` 다. 웹푸시는 인증 상태에서 `push-public-key=200` 과 87자 public key, `push-subscriptions/me=1` 을 확인했고, `push-public-key` 는 현재 보안 계약상 로그인 사용자에게만 허용한다. `ALERT_WEBHOOK_URL` 은 `.env.production`, `.env.runtime.production`, `/home/ubuntu/.config/youth-welfare/ops.env` 에 없고, watchdog용 `HEALTHCHECKS_PING_URL` 만 설정되어 있다. `send-log-alert` 최신 재평가는 `LOG_ALERT_STATUS=ok` 다.
- 2026-06-24 개인정보/동의 재점검 기준: 운영 RDS 집계에서 활성 사용자 기준 `PRIVACY_NOTICE` 누락, 선택정보 저장값 대비 `OPTIONAL_PROFILE` 동의 누락, 민감정보 저장값 대비 `SENSITIVE_INFO` 동의 누락, `user_profiles` projection 동의 drift 가 모두 `0` 이다. `disabilityGradeCode=NONE` 은 추천 매칭에서는 미보유로 취급하지만 장애 관련 응답값을 저장하는 것이므로 현재 계약상 `SENSITIVE_INFO` 동의가 필요하다. 알림 수신 해지는 `notification_yn=false` 와 함께 `notification_consent_at` 도 비우는 기준으로 고정한다.
- 2026-06-26 로컬 프론트 관측 기준: `tmp/frontend-observation/20260626T104138Z` 는 local-dev 기본 경계에서 lint/build/Playwright `33 passed`, `RUN_FRONTEND_ADMIN_E2E=false`, `playwright_grep_invert=@admin-required`, `decision_class=BASELINE_HEALTHY` 다. 별도 admin opt-in `tmp/frontend-observation/20260626T103616Z` 는 `RUN_FRONTEND_ADMIN_E2E=true ALLOW_DEFAULT_ADMIN_CREDENTIALS=true PLAYWRIGHT_GREP='admin dashboard'` 로 admin dashboard `24 passed`, `decision_class=BASELINE_HEALTHY` 다. 따라서 기본 smoke는 admin credential 없이 안정적으로 유지하고, 관리자 operator flow는 명시 opt-in으로 분리해서 본다.
- 2026-06-26 로컬 active/current wrapper 기준: `tmp/active-baseline-suite/20260626T112015Z` 는 backend test, frontend lint/build/E2E `33 passed`, ops baseline/observation, collect legacy repair까지 `active_baseline_suite=passed` 이고 `effective_playwright_grep_invert=@admin-required`, attention warning `0` 이다. 이어 `tmp/current-priority-suite/20260626T112406Z` 는 이 active baseline을 `active_baseline_reused=true` 로 재사용했고 recommendation observation은 `passed`, `decision_class=OBSERVE_REAL_USER_TRAFFIC`, `reopen_allowed=false` 로 닫혔다.
- 2026-06-27 server/RDS active baseline 기준: compose app이 `.env.runtime.production` runtime/RDS DB를 쓰는 상태라 smoke도 `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' ALLOW_ADMIN_JWT_MINT=true` 로 맞춰 실행한다. 최신 artifact `tmp/active-baseline-suite/20260627T130715Z` 는 backend test, frontend lint/build/E2E `33 passed`, ops baseline/observation `BASELINE_HEALTHY`, collect legacy repair까지 `active_baseline_suite=passed`, `suite_duration_seconds=388.936` 이다. PR CI green 이후 배포 전 server/RDS baseline으로 재확인한 결과다.
- 2026-06-27 표준코드 bounded 보정 기준: `house_tenure_code=NONE` 인 실제 사용자 1건에서 `housing_type_code=4` 가 남아 있어 `users` 와 `user_profiles` 를 각각 `housing_type_code=NONE` 으로 bounded 보정했다. 최신 user profile standard-code coverage audit은 `users_house_tenure_none_housing_type_mismatch=0`, `profiles_house_tenure_none_housing_type_mismatch=0`, `safe_reconcile_candidate_rows=0`, `conflicting_value_gap_rows=0`, `users_with_any_standard_code=467`, `users_missing_all_standard_codes=628`, `non_example_users_missing_all_standard_codes=5`, `example_smoke_users_missing_all_standard_codes=623` 로 닫혔다. attention feed에는 `standard-code-backlog`, `notification-backlog`, `notification-stale-backlog` 3개가 남고 warning은 `standard-code-backlog` 1개지만, 현재 자동 보정 후보나 conflict gap은 없다.
- 2026-06-27 운영 queue 기준: policy triage는 `policy_duplicate_open_groups=0`, `policy_link_open_reviews=0`, `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 로 운영 review queue가 닫힌 상태다. recommendation observation은 계속 `OBSERVE_REAL_USER_TRAFFIC`, `reopen_allowed=false` 이므로 추천 score/weight/prompt는 다시 열지 않는다. 관리자 smoke는 실제 admin password 대신 bounded local smoke용 JWT mint 경로로 검증하며, `smoke-common.sh` 의 admin bootstrap 조회는 `auth_users.email_lookup_hash` 와 user email shadow 값을 기준으로 맞췄다.
- 2026-06-27 운영 배포/재검증 기준: PR #348을 merge commit `c9d93afd` 로 운영 배포했고, 운영 nginx는 repo의 `deploy/nginx/youth-welfare.conf` 로 다시 맞춰 `/actuator` 와 `/actuator/health` 가 모두 `403` 을 반환한다. `run-prod-cutover-verification.sh` 는 배포 전/후 모두 통과했다. 배포 후 deployed-origin active baseline은 `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' ALLOW_ADMIN_JWT_MINT=true` 로 실행한다. `.env.runtime.production` 은 runtime 전용이라 migration admin password가 없고, E2E DB bootstrap이 필요한 active baseline에는 `.env.production` 을 쓴다. 최신 post-deploy artifact `tmp/active-baseline-suite/20260627T133719Z` 는 backend test, frontend lint/build, deployed-origin Playwright, ops baseline/observation `BASELINE_HEALTHY`, collect legacy repair까지 `active_baseline_suite=passed`, `suite_duration_seconds=305.151` 이다.
- 2026-06-27 배포 후 운영 관찰 기준: `/policies/3324` 의 `RECOMMENDATION_DIGEST` 14일 초과 unread 4건을 admin bounded `hide-stale olderThanDays=14` 경로로 숨겼다. 재검증 artifact는 `tmp/notification-backlog-audit/20260627T134357Z`, `tmp/notification-backlog-sample-audit/20260627T134357Z`, `tmp/notification-stale-target-audit/20260627T134357Z`, `tmp/ops-observation/20260627T134357Z` 이다. 현재 `unread_total=24`, `stale_unread_7d=12`, `stale_unread_14d=0`, failed notification `0`, `stale_14d_total=0`, `NO_STALE_TARGETS` 이고 attention warning은 `0` 으로 내려갔다. 남은 attention은 정보성 `standard-code-backlog`, `notification-backlog` 이다. recommendation reopen precheck는 `KEEP_OBSERVING`, `reopen_allowed=false`, `DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW` 를 유지하므로 score/weight/prompt는 계속 닫아 둔다.
- 2026-06-27 digest 중복 억제 배포 후 기준: PR #350을 merge commit `5c9c18cb` 로 운영 배포했다. 동일 사용자/제목/딥링크의 `UNREAD RECOMMENDATION_DIGEST` 가 이미 있으면 새 digest alert 저장을 건너뛰는 guard가 들어갔고, 배포 후 `run-prod-cutover-verification.sh` 는 통과했다. 최신 알림 재점검 artifact `tmp/notification-backlog-audit/20260627T151348Z`, `tmp/notification-backlog-sample-audit/20260627T151348Z`, `tmp/notification-stale-target-audit/20260627T151348Z` 기준 `unread_total=24`, `digest_unread=24`, `stale_unread_7d=12`, `stale_unread_14d=0`, failed notification `0`, `stale_14d_total=0`, `NO_STALE_TARGETS` 로 기존 tail은 그대로 유지된다. 새 guard 효과는 다음 예약 digest 발송 뒤 동일 `/policies/3324` unread row가 늘지 않는지로 확인한다. 같은 시각 recommendation reopen precheck는 `KEEP_BASELINE_MONITORING`, `DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW` 를 유지했고, 최신 ops observation은 `tmp/ops-observation/latest-ops-observation-summary.txt` 의 `generated_at_utc=20260627T151404Z` 기준 `BASELINE_HEALTHY`, collect 실패/partial/circuit `0`, attention item `standard-code-backlog,notification-backlog`, attention warning `0`, `recommendation_standard_code_precheck_status=KEEP_OBSERVING` 으로 통과했다.
- 2026-06-27 표준코드 입력 유도 배포 기준: PR #352를 merge commit `13af8968` 로 반영해 마이페이지의 표준코드 보완 카드, 저장 리마인더, 알림 설정 안내에 실제 누락 항목 chip을 표시하고 `선택 안 함` 과 `해당 없음` 의 차이를 설명하게 했다. 프론트 정적 파일은 `frontend/dist` 를 `/var/www/youth-welfare/frontend` 로 동기화해 edge에서 `MyPage-Co4o9i5k.js` 와 새 문구가 내려오는 것을 확인했다. 배포 후 `run-prod-cutover-verification.sh` 는 통과했고, deployed-origin 프론트 관측은 PR #353의 `@dev-only` smoke 분류 보정 뒤 `tmp/frontend-observation/20260627T160638Z` 에서 Playwright `31 passed`, `playwright_grep_invert=@dev-only|@admin-required`, `decision_class=BASELINE_HEALTHY` 로 통과했다. 이 변경은 표준코드 backlog를 DB 보정이 아니라 사용자 입력 유도 UX로 줄이는 현재 방향이다.
- 2026-06-27 표준코드 UX 배포 후 관측 기준: PR #352/#353 이후 server/RDS 재관측에서 알림 backlog는 `tmp/notification-backlog-audit/20260627T165749Z`, `tmp/notification-backlog-sample-audit/20260627T165757Z`, `tmp/notification-stale-target-audit/20260627T165805Z` 기준 `unread_total=24`, `digest_unread=24`, `stale_unread_7d=12`, `stale_unread_14d=0`, failed notification `0`, `stale_14d_total=0`, `NO_STALE_TARGETS` 로 중복 증가 없이 유지됐다. 표준코드 coverage는 `users_with_any_standard_code=517`, `users_missing_all_standard_codes=637`, `real_user_users_missing_all_standard_codes=0`, `safe_reconcile_candidate_rows=0`, `conflicting_value_gap_rows=0` 이고, smoke 계정 증가로 총량만 변했을 뿐 실제 사용자 추가 보정 후보는 없다. recommendation standard-code adoption은 최신 ops 관측 `tmp/ops-observation/20260627T165813Z` 기준 `latest_batch_users_with_any_standard_code_share_pct=82.05`, `latest_batch_users_missing_all_standard_codes=114` 이며 precheck는 계속 `KEEP_OBSERVING`, `DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW` 다. policy triage는 같은 ops 관측 기준 OPEN review queue `0`, `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 이고, ops observation은 `BASELINE_HEALTHY`, collect 실패/partial/circuit `0`, chat observability `CHAT_BASELINE_HEALTHY`, attention item `standard-code-backlog,notification-backlog` 로 통과했다. 따라서 다음 조치는 코드 재개방이 아니라 다음 예약 digest와 실제 사용자 표준코드 입력률 관찰이다.
- 2026-06-27 nightly ops cron drift 기준: 운영 crontab의 nightly handoff가 `ALLOW_ADMIN_JWT_MINT=true` 없이 설치되어 `ADMIN_PASSWORD is empty` 로 active/auth 계열 단계가 실패할 수 있던 상태를 확인했다. `install-nightly-ops-handoff-cron.sh` 의 기본 cron block과 runbook 예시에 `ALLOW_ADMIN_JWT_MINT=true` 를 포함하도록 맞췄고, 실제 crontab도 새 block으로 재설치했다. 모든 하위 관측을 끈 최소 `run-nightly-ops-handoff.sh` 실행은 admin password 없이 통과해 mint 경계를 확인했다. cleanup cron은 현재 `bash .../cleanup-nightly-ops-handoff-artifacts.sh` 형태라 execute bit에 의존하지 않는다.
- 2026-06-27 DB 운영 감사 기준: PR #360/#361/#362로 운영 DB audit를 nightly handoff와 alert evaluator에 연결했다. 운영 RDS에는 `schema_migration_history` 와 사용자 projection FK(`fk_auth_users_user_key`, `fk_user_profiles_user_key`, `fk_user_pii_user_key`)가 적용/검증됐고, projection drift는 `auth_without_users=0`, `profiles_without_users=0`, `pii_without_users=0` 이다. `users_without_pii` 는 탈퇴/비활성 계정 PII 삭제 잔량과 active 누락을 분리해 보며, 현재 기준 `active_users_without_pii=0` 이어야 한다. `chat_snapshots_nonnull_orphan_session=0`, `user_pii_sync_pending_or_failed=0`, `waiting_locks=0`, `active_queries_over_5m=0` 이면 `DB_AUDIT_INTEGRITY` 는 `ok` 로 읽는다.
- 2026-06-27 nightly trusted origin 기준: PR #363 merge commit `cb5edb07` 로 nightly wrapper가 `FRONTEND_PUBLIC_BASE_URL` 을 하위 auth smoke의 `SMOKE_TRUSTED_ORIGIN` 기본값으로 export한다. prod CORS는 `https://youthmoa.kr,https://www.youthmoa.kr` 만 허용하므로 server-side smoke가 `APP_BASE_URL=http://127.0.0.1:8082` 로 API를 호출하더라도 `Origin` 은 public origin이어야 한다. 축소 nightly 검증 `tmp/nightly-ops-handoff-auth-db-check/artifacts/manual-auth-db-20260627T181727Z` 는 `auth=passed`, `db_audit=ok`, `smoke_trusted_origin=https://youthmoa.kr` 로 통과했고, 해당 `operational-db-audit.out` 로 alert evaluator를 돌리면 `OP_ALERT_STATUS=ok` 다.
- 2026-07-11 AWS/RDS/ElastiCache 운영 확인 기준: `youth-welfare-ops-monitor-v2-role` 에 `deploy/ops/aws-ops-monitor-role-policy.json` 기준 inline policy가 반영되어 `cloudwatch:DescribeAlarms`, `elasticache:DescribeCacheClusters`, `rds:DescribeDBInstances`, Route53 health check 조회가 통과한다. ElastiCache Valkey `youth-welfare-prod-redis-valkey-001` 은 `available`, 알람 6개(`engine-cpu`, `memory-usage`, `freeable-memory`, `evictions`, `current-connections`, `new-connections`)는 모두 `OK` 이고 `youth-welfare-ops-alerts` SNS topic에 연결돼 있다. RDS `youth-welfare-prod-db` 는 `available`, backup retention `7`, latest restorable time `2026-07-11T14:50:26Z`, deletion protection `true`, encrypted `true`, Multi-AZ `false`, public access `false` 로 확인됐다. 실제 restore rehearsal은 별도 RDS instance를 생성하는 비용 발생 작업이라 현재 보류하고, 명시 승인 시 [db-backup-restore-rehearsal-runbook.md](core/db-backup-restore-rehearsal-runbook.md) 기준으로 진행한다.
- 2026-07-11 ElastiCache 전환 closeout 기준: `.env.production` 과 `.env.runtime.production` 모두 ElastiCache `REDIS_HOST`, `REDIS_PORT=6379` 를 갖고 있고 EC2에서 DNS/TCP 6379 연결이 통과했다. `docker-compose.prod.elasticache.yml` 기준 app은 `healthy`, 내부 `/actuator/health` 는 `UP` 다. `run-prod-runtime-smoke-suite.sh` 는 cutover env render/preflight, RDS runtime privilege verify, nginx edge baseline, runtime API smoke, auth observation을 모두 통과했고 artifact는 `tmp/prod-runtime-smoke/20260711T143716Z` 다. 알림 stale audit에서 `/policies/3324` 의 14일 초과 `RECOMMENDATION_DIGEST` 20건을 admin `hide-stale olderThanDays=14` 로 bounded 정리했고, 재검증은 notification backlog `BASELINE_HEALTHY`, stale target `NO_STALE_TARGETS` 다. 이어 정책 중복 review queue 6개 그룹/12개 row를 `exact -> mirror -> drift -> title-only` 순서로 review 처리했고 review record는 `146`-`151` 이다. post-review ops observation `tmp/ops-observation/20260711T151452Z` 는 `BASELINE_HEALTHY`, collect 실패/partial/circuit `0`, notification unread/failed `0`, attention item `standard-code-backlog`, warning `0` 이다. policy triage는 `policy_duplicate_open_groups=0`, `policy_duplicate_open_rows=0`, `policy_link_open_reviews=0`, `policy_error_open_reports=0`, `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 다.
- 2026-07-11 nightly follow-up 기준: 정기 nightly handoff는 `2026-07-11T01:10Z` 실행에서 standard-code observation의 `current-priority -> active baseline -> backend test` 단계가 실패해 summary가 start line만 남았다. 원인은 정책 presentation/detail 테스트 fixture가 `applyEndDate=2026-06-30` 으로 고정되어 2026-07-11 기준 `ACTIVE` 정책도 `종료` 라벨을 반환하는 시간 의존 drift였다. 테스트 fixture를 `LocalDate.now()` 기준 진행 중 기간으로 바꿨고, targeted Gradle 테스트와 current-priority 재실행이 통과했다. 수동 auth/DB 보완 artifact `manual-auth-db-20260711T172809Z` 는 `auth=passed`, `db_audit=ok`, `smoke_trusted_origin=https://youthmoa.kr`, `OP_ALERT_STATUS=ok` 이다. 수동 standard-code wrapper artifact `manual-standard-code-20260711T173642Z` 는 `ops=passed`, `current_priority=passed`, `observation=passed`, attention warning `0`, attention item `standard-code-backlog` 다.
- 2026-06-21 current-priority wrapper 영향 확인 기준: admin credential tightening 뒤 `RUN_FRONTEND_ADMIN_E2E=false` 기본 경계로 [run-local-current-priority-suite.sh](../deploy/smoke/run-local-current-priority-suite.sh) 를 deployed-origin에서 재실행했다. 중간 실행 `tmp/current-priority-suite/20260621T170155Z` 는 backend test, frontend lint/build, deployed-origin Playwright `31 passed`, ops observation `BASELINE_HEALTHY` 까지는 통과했지만, 같은 시간 예약 GOV24 수집이 `collect-global` lock을 heartbeat 중이라 `collect legacy repair` 첫 backfill이 `409/COL002` 로 막혔다. lock을 강제로 지우지 않고 실제 수집 완료를 기다렸고, 단독 [run-local-collect-legacy-repair-suite.sh](../deploy/smoke/run-local-collect-legacy-repair-suite.sh) 재실행에서 YOUTH inverted age 잔량 `9`건을 모두 보정해 `remaining_inverted_rows=(none)` 으로 닫았다. 최신 전체 artifact `tmp/current-priority-suite/20260621T171607Z` 는 `current_priority_suite=passed`, active baseline `passed`, collect legacy repair `passed`, ops observation `BASELINE_HEALTHY`, attention warning `0`, deployed-origin Playwright `31 passed` 다. recommendation observation은 `KEEP_OBSERVING`, `reopen_allowed=false`, `decision_class=OBSERVE_REAL_USER_TRAFFIC` 이다.
- 2026-06-17 운영 재확인 기준: `main` 은 `origin/main` 과 동기화됐고, 운영 서버는 `app + redis` healthy 상태다. `run-prod-cutover-verification.sh` 는 public smoke `30 passed`, 관리자 Playwright `@admin-required` 는 `21 passed`, `npm audit` 은 `0 vulnerabilities`, `npm run lint`, `npm run build` 도 통과했다.
- 2026-06-17 후속 운영 review 기준: 정책 오류 제보 `OPEN=5` 와 정책 중복 그룹 `OPEN=2` 를 review 처리했다. 최종 ops observation은 `BASELINE_HEALTHY`, attention warning `0`, policy duplicate/link/error/support queue `OPEN=0` 이고, 남은 attention 항목은 정보성 `standard-code-backlog`, `notification-backlog` 뿐이다.
- 2026-06-17 웹푸시 점검 기준: 운영 VAPID env와 인증된 `push-public-key` API는 정상이고 `/sw.js` 는 HTTPS에서 200으로 제공된다. 다만 운영 DB의 `web_push_subscriptions` 는 `0`건이라 실제 수신자는 아직 없고, 서버 `push-test-send` 도 `attempted=0` 이다. `run-local-notification-channel-smoke.sh` 는 auth PII 분리 후 깨져 있던 admin user_key 조회를 `auth_users.email_lookup_hash` 기준으로 보정해 다시 통과했다.
- 2026-06-17 관리자 브라우저 웹푸시 허용 후 재점검 기준: 운영 DB에 관리자 계정 enabled 구독 `1`건이 생성됐고 endpoint host는 `fcm.googleapis.com` 이다. 인증된 `/api/notifications/push-subscriptions/me` 는 구독 `1`건을 반환했으며, `/api/notifications/push-test-send` 는 `attempted=1`, `sent=1`, `disabled=0`, `failed=0` 으로 성공했다.
- 2026-06-10 server/RDS 안정화 sweep 기준: `ops observation=BASELINE_HEALTHY`, attention warning `0`, collect 실패/partial/circuit `0`, policy review `OPEN` queue `0`, recommendation `KEEP_OBSERVING`, frontend deployed-origin smoke `30 passed`
- 2026-06-10 DB closeout 기준: RDS runtime privilege verification 통과, `db/migration` active version 중복 제거 및 계약 테스트 추가

## 지금 먼저 할 일

1. 다음 실제 nightly cron 뒤 `/var/log/youth-welfare/nightly-ops-handoff/nightly-summary-YYYY-MM-DD.log` 와 `artifacts/<UTC timestamp>/operational-db-audit.out` 를 확인합니다. 2026-07-11 수동 보완은 통과했지만, 다음 정기 cron에서 summary가 끝까지 append 되는지 한 번 더 봅니다. 기대값은 `auth=passed`, `db_audit=ok`, auth smoke의 `smoke_trusted_origin=https://youthmoa.kr`, DB alert evaluator의 `OP_ALERT_STATUS=ok` 입니다.
2. 다음 작업 시작 시 server/RDS와 app runtime env를 먼저 맞춥니다. runtime read-only smoke는 `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082'` 로 맞추고, migration/admin DB bootstrap이 필요한 active baseline은 `ENV_FILE=.env.production` 을 씁니다. 관리자 password가 없으면 `ALLOW_ADMIN_JWT_MINT=true` 를 명시합니다.
3. `run-local-ops-observation-suite.sh` 를 먼저 돌려 attention warning이 다시 생겼는지 확인합니다. 최신 전체 ops 기준선은 `tmp/ops-observation/20260711T175745Z` 의 `BASELINE_HEALTHY`, attention warning `0`, attention item `standard-code-backlog` 입니다. 표준코드 bounded 보정 후보와 conflict gap은 모두 `0` 입니다.
4. attention에 남는 `standard-code-backlog` 는 관찰 항목입니다. 표준코드는 `safe_reconcile_candidate_rows` 또는 `conflicting_value_gap_rows` 가 생길 때만 직접 보정합니다. 알림은 2026-07-11 bounded hide 이후 `unread_total=0`, failed notification `0`, `NO_STALE_TARGETS` 로 닫혀 있으므로 새 `stale_14d_total > 0` 또는 failed queue가 생길 때만 다시 hide/retry를 검토합니다.
5. RDS restore rehearsal은 비용 발생 작업이라 현재 보류합니다. 다시 진행하기로 하면 [db-backup-restore-rehearsal-runbook.md](core/db-backup-restore-rehearsal-runbook.md) 의 restore DB identifier/subnet/security group을 먼저 정합니다.
6. 챗봇은 코드 재개방보다 관측 유지가 먼저입니다. `run-local-chat-followup-scenario-audit.sh`, `run-local-chat-continuity-coaching-smoke.sh`, `run-local-chat-application-coaching-matrix-audit.sh`, `run-local-chat-real-user-quality-sample-audit.sh`, `run-local-chat-observability-audit.sh` 순서로 대화 이어짐, 신청 코칭, 실사용 샘플, 운영 지표를 같이 봅니다. 단, follow-up / coaching 계열은 대표 정책 corpus가 필요한 데이터 의존 smoke이므로 fresh local DB에서 `INSUFFICIENT_POLICY_CORPUS` 로 skip되면 제품 회귀가 아니라 데이터 전제 미충족으로 읽습니다.
7. 추천은 아직 reopen 대상이 아닙니다. 최신 읽기성 gate 기준은 `KEEP_BASELINE_MONITORING`, `WAIT_FOR_REAL_USER_TRAFFIC` 이며, promotion/review run은 실행하지 않습니다. 표준코드 미입력 backlog의 1차 UX/입력 유도는 PR #352로 배포했으므로, 다음에는 실제 입력률 변화가 생기는지 `users_missing_all_standard_codes=659`, 최신 배치 adoption share `83.59%`, 지역 mismatch `0` 을 관찰합니다.
8. 정책 데이터는 운영 review queue가 다시 열릴 때만 처리합니다. raw duplicate/link 잔량은 관찰값으로 두고, `policy_duplicate_open_groups` 또는 `policy_link_open_reviews` 가 1 이상이면 `policy-data-quality-triage-runbook.md` 순서로 review합니다.
9. 지역/좌표 이슈는 현재 닫힌 상태입니다. 새 smoke나 seed가 `sido/sgg` 있음 + `region_code` 공백을 다시 만들면 해당 smoke payload부터 고치고, 운영 row는 `RegionCodeUtil` 매핑으로 bounded 보정합니다.

## 지금 먼저 볼 문서

- 안정화 체크리스트: [stabilization-checklist.md](core/stabilization-checklist.md)
- 최종 운영 closeout 체크리스트: [final-ops-closeout-checklist.md](core/final-ops-closeout-checklist.md)
- 최종 운영 handoff runbook: [final-production-operations-runbook-2026-07-16.md](core/final-production-operations-runbook-2026-07-16.md)
- 결과보고서 제출 체크리스트: [final-report-submission-checklist-2026-07-16.md](./final-report-submission-checklist-2026-07-16.md)
- 인수인계: [stabilization-handoff.md](./stabilization-handoff.md)
- 인증 문서군 진입점: [auth-docs-index.md](auth/auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](collect/collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](recommendation/recommendation-docs-index.md)
- 추천 AI latency 품질 우선 설계: [recommendation-ai-latency-quality-first-plan-2026-07-17.md](recommendation/recommendation-ai-latency-quality-first-plan-2026-07-17.md)
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
- admin attention feed는 열린 backlog를 아래 key로 승격하고, 각 item에 사람이 바로 취할 `nextAction`을 함께 내려줍니다.
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
  - 현재 latest 기준은 `stale_14d_total=0`, `decision_class=NO_STALE_TARGETS` 이고, 남은 unread backlog는 `unread_total=20`, `stale_unread_7d=12`, `stale_unread_14d=0` 수준의 recommendation digest tail 입니다.
  - server/RDS 최신 backlog 기준 unread는 `digest=20`, `deadline=0`, `system=0`, failed notification은 `0` 입니다.
  - 즉 현재 알림 운영 우선순위는 14일 초과 stale 정리가 아니라 digest unread 총량과 7일 초과 tail 관찰입니다.

## 작업 전 기본 검증 기준

- one-shot local active baseline: `bash deploy/smoke/run-local-active-baseline-suite.sh`
- one-shot server active baseline: `APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-active-baseline-suite.sh`
  - 참고: `deployed-origin` 모드는 배포 번들에서 성립하지 않는 `@dev-only` admin forced-failure Playwright 2개를 자동 제외합니다.
  - latest artifact: `tmp/active-baseline-suite/latest-active-baseline-summary.txt`, `tmp/active-baseline-suite/latest-active-baseline-summary.json`
  - latest summary/json 에 `ops_attention_feed_*`, `ops_user_profile_standard_code_*`, `ops_recommendation_standard_code_*` 가 같이 포함됩니다.
  - frontend targeted smoke를 섞을 때는 `playwright_grep*` 는 reuse 비교용 입력값, `effective_playwright_grep*` 는 wrapper가 실제 Playwright에 적용한 값으로 읽습니다.
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/active-baseline-suite/latest/` snapshot은 남습니다.
- one-shot current priority suite: `bash deploy/smoke/run-local-current-priority-suite.sh`
- one-shot server current priority suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-current-priority-suite.sh`
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
- server/RDS recommendation reopen precheck: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-reopen-precheck.sh`
  - 현재 active 판정은 latest observation/precheck artifact를 우선한다.
  - 2026-06-29 최신 반복 점검은 smoke 계정 생성을 피하기 위해 읽기성 `run-local-recommendation-ai-exclusion-latest-overview.sh`, `run-local-recommendation-standard-code-adoption-audit.sh`, `run-local-recommendation-region-mismatch-audit.sh` 조합으로 봤다.
  - 최신 기준은 `KEEP_BASELINE_MONITORING`, `WAIT_FOR_REAL_USER_TRAFFIC`, 최신 배치 `707`명 중 표준코드 있음 `591`, 없음 `116`, 지역 mismatch `0` 이다.
  - 즉 지금은 recommendation score/weight/prompt를 다시 열지 않고 real-user sample과 leader signal을 관찰한다.
- recommendation observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
- server/RDS recommendation observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-observation-suite.sh`
  - latest artifact: `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`, `tmp/recommendation-observation/latest-recommendation-observation-note.md`, `tmp/recommendation-observation/latest-recommendation-observation.json`
  - latest housing effect stdout: `tmp/recommendation-observation/latest/housing-standard-code-effect.out`
  - latest welfare matrix stdout: `tmp/recommendation-observation/latest/welfare-standard-code-matrix.out`
  - latest adoption audit stdout: `tmp/recommendation-observation/latest/recommendation-standard-code-adoption.out`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/recommendation-observation/latest/` snapshot은 남습니다.
- recommendation region mismatch audit: `bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh`
- server/RDS recommendation region mismatch audit: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh`
- bounded recommendation region mismatch repair: `USER_LIMIT=25 DRY_RUN=false bash deploy/smoke/run-local-recommendation-region-mismatch-repair.sh`
  - current query는 이미 맞는데 old saved batch가 남아 있을 때 쓰는 repair 경로입니다.
- housing standard code matrix audit: `bash deploy/smoke/run-local-housing-standard-code-matrix-audit.sh`
- welfare standard code matrix audit: `bash deploy/smoke/run-local-welfare-standard-code-matrix-audit.sh`
- user profile standard code coverage audit: `bash deploy/smoke/run-local-user-profile-standard-code-coverage-audit.sh`
  - latest artifact의 `safe_reconcile_candidate_rows` 또는 `conflicting_value_gap_rows` 가 1 이상일 때만 bounded 보정 후보로 봅니다.
  - 둘 다 0이면 현재는 DB 자동 보정 대상이 아니라 사용자 입력/관찰 backlog 입니다.
- collect governance observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
- server/RDS collect governance observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-governance-observation-suite.sh`
  - latest artifact: `tmp/collect-governance-observation/latest-collect-governance-observation-summary.txt`, `tmp/collect-governance-observation/latest-collect-governance-observation-note.md`, `tmp/collect-governance-observation/latest-collect-governance-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/collect-governance-observation/latest/` snapshot은 남습니다.
- collect source resilience audit: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-source-resilience-audit.sh`
- server/RDS collect source resilience audit: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-collect-source-resilience-audit.sh`
  - latest artifact: `tmp/collect-source-resilience-audit/latest-collect-source-resilience-summary.txt`, `tmp/collect-source-resilience-audit/latest-collect-source-resilience-note.md`, `tmp/collect-source-resilience-audit/latest-collect-source-resilience.json`
- auth observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
- server/RDS auth observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-auth-observation-suite.sh`
  - latest artifact: `tmp/auth-observation/latest-auth-observation-summary.txt`, `tmp/auth-observation/latest-auth-observation-note.md`, `tmp/auth-observation/latest-auth-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/auth-observation/latest/` snapshot은 남습니다.
- ops observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
  - local admin password가 없으면 `ALLOW_ADMIN_JWT_MINT=true` 를 같이 명시합니다.
- server/RDS ops observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-ops-observation-suite.sh`
  - latest artifact: `tmp/ops-observation/latest-ops-observation-summary.txt`, `tmp/ops-observation/latest-ops-observation-note.md`, `tmp/ops-observation/latest-ops-observation.json`
  - attention feed is included in the same summary/json (`attention_feed_*`, `attention_feed.items`)
  - admin dashboard summary notification section now includes `notification_unread_alerts`, `notification_retryable_failed_notifications`, `notification_terminal_failed_notifications`
  - standard code coverage is included in the same summary/json (`user_profile_standard_code_*`), including `non_example` and `EXAMPLE_SMOKE` origin breakdown
  - recommendation standard code effect/matrix is included in the same summary/json (`recommendation_standard_code_*`)
  - adoption audit is included in the same summary/json (`recommendation_standard_code_adoption_*`)
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/ops-observation/latest/` snapshot은 남습니다.
- nightly standard-code observation wrapper: `bash deploy/smoke/run-nightly-standard-code-observation.sh`
- server/RDS nightly standard-code observation wrapper: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-nightly-standard-code-observation.sh`
  - default log root: `/var/log/youth-welfare/standard-code-observation`
- nightly ops handoff wrapper: `ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' ALLOW_ADMIN_JWT_MINT=true bash deploy/smoke/run-nightly-ops-handoff.sh`
  - default log root: `/var/log/youth-welfare/nightly-ops-handoff`
  - cron/install procedure: [nightly-ops-handoff-cron-runbook.md](./core/nightly-ops-handoff-cron-runbook.md)
  - idempotent crontab install: `bash deploy/smoke/install-nightly-ops-handoff-cron.sh`
  - appends compact lines to `nightly-summary-YYYY-MM-DD.log`
- admin attention feed: `GET /api/admin/dashboard/attention-feed`
  - collect drift, 표준코드 backlog, wrapper warning을 재사용 가능한 운영 알림 목록으로 반환합니다.
  - 각 item은 `targetId`, `source`, `nextAction`을 포함하며 화면은 이를 “다음 조치”로 표시합니다.
  - wrapper current-priority 비교는 현재/이전 summary 양쪽에 값이 있는 metric만 비교합니다.
    - 이전 missing-count가 비어 있으면 `0명` 이 아니라 `이전값 없음` 으로 읽습니다.
- frontend observation suite: `bash deploy/smoke/run-local-frontend-observation-suite.sh`
- server frontend observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' FRONTEND_E2E_MODE=deployed-origin FRONTEND_PUBLIC_BASE_URL='https://youthmoa.kr' bash deploy/smoke/run-local-frontend-observation-suite.sh`
  - 기본 deployed-origin 경계는 fresh e2e user/bootstrap을 먼저 준비하고 `@dev-only`, `@admin-required` 케이스를 제외합니다.
  - admin dashboard smoke까지 포함하려면 `RUN_FRONTEND_ADMIN_E2E=true` 를 명시합니다.
  - latest artifact: `tmp/frontend-observation/latest-frontend-observation-summary.txt`, `tmp/frontend-observation/latest-frontend-observation-note.md`, `tmp/frontend-observation/latest-frontend-observation.json`
  - `KEEP_ARTIFACTS=false` 기본값에서도 latest summary/json 과 `tmp/frontend-observation/latest/` snapshot은 남습니다.
- policy quality observation suite: `APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
  - fresh local DB처럼 대표 정책 corpus가 부족하면 `INSUFFICIENT_POLICY_CORPUS` 로 skip되며, retrieval drift로 보지 않습니다.
- server/RDS policy quality observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-local-policy-quality-observation-suite.sh`
- policy data triage observation suite: `bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh`
- server/RDS policy data triage observation suite: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh`
- nightly server/RDS policy quality wrapper: `ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' bash deploy/smoke/run-nightly-policy-quality-observation.sh`
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
  - fresh local DB처럼 `active_visible_youth_total=0` 이면 `NO_ACTIVE_VISIBLE_LINK_REVIEW_CANDIDATES` 로 읽고, bucket 우선순위 없이 review queue를 새로 열지 않는다.
- policy link review queue runbook: `docs/policy/policy-link-review-queue-runbook.md`
  - 운영자는 `지원금/급부형 -> 공고/모집형 -> 프로그램형 -> 행사/문화형 -> 기타` 순서로 보는 편이 맞다.
  - `REVIEWED` 는 “고쳤다”가 아니라 “운영자가 한 번 판단과 note를 남겼다”는 뜻으로 읽는다.
- policy data triage observation suite: `bash deploy/smoke/run-local-policy-data-triage-observation-suite.sh`
  - `policy-data-quality`, `policy-link-review-sample`, `youth-duplicate-candidate` 를 한 번에 다시 읽는 compact handoff wrapper다.
  - 현재는 `정책 오류 제보 -> duplicate -> link review -> drift tail` 순서로 backlog를 보는 편이 맞는지 빠르게 판정한다.
  - wrapper summary는 정책 오류 제보, raw duplicate/link 후보와 실제 운영 `OPEN` queue를 분리해서 남긴다. 운영 queue가 닫혀 있으면 raw 후보가 남아도 `REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 로 읽고, 새 `OPEN` queue가 생길 때만 review를 재개한다.
  - 현재 server/RDS 최신 기준은 `policy_error_open_reports=0`, `policy_duplicate_open_groups=0`, `policy_duplicate_open_rows=0`, `policy_link_open_reviews=0`, `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS` 이다.
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
