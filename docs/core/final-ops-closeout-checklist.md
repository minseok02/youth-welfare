# 운영 점검과 작업 인계

일상 점검·실패 분리·배포 후 확인 순서입니다. 실행 wrapper와 지표는 [운영 baseline](ops-baseline-runbook.md), 실제 배포는 [배포 절차](../deployment.md)를 따릅니다. [환경 안내](environment-guide.md)로 서버/RDS·로컬 앱/DB·배포된 프론트를 구분합니다.

## 작업 전

- 요청 목표·제외 범위·branch·기존 변경을 확인합니다. `git status --short --branch`로 확인하되 기존 변경을 초기화하지 않습니다.
- 실제 앱/DB·Redis·점검 계정·권한을 확인합니다. read-only 점검에 가입/추천 refresh·repair·backfill·schema patch·발송을 섞지 않습니다.
- 기대값은 앱 healthy·health UP이지만 실제 데이터·후처리 완전성까지 보장하지 않습니다.
- 운영 Redis는 revoke 상태를 보관하지만 현재 비영속입니다. [세션 상태 유실 위험](../auth/auth-session-revocation-current-state.md#redis-상태-유실-미해결-위험)을 확인하고 보호 조치 없이 캐시처럼 flush/restart하지 않습니다.

## 일상 점검과 실패 분리

1. 승인된 설정으로 [ops observation/baseline](ops-baseline-runbook.md)을 실행하거나 이미 있는 같은 환경의 artifact를 확인합니다.
2. decision_class·attention_feed_item_keys/warning 수·wrapper_promoted_alert_severity·collect 실패/partial·open circuit·policy triage·safe reconcile/conflict를 봅니다.
3. CI는 backend unit, frontend lint/build, browser smoke 중 실패 job을 구분합니다. 계정·fixture·rate limit·smoke 결함과 제품 회귀를 분리하고 관련 재현을 선택합니다.
4. Nightly는 `/var/log/youth-welfare/*/nightly-summary-YYYY-MM-DD.log`의 ops/current_priority/policy/blocker_class, 새 warning과 실제 queue를 대조합니다. raw 숫자 변동만으로 상태 변경을 실행하지 않습니다.

`BASELINE_HEALTHY`·info·warning 0은 참고 신호입니다. warning이 있다고 모두 장애도 아니고, 0이라고 보안·데이터 보존 문제가 없다는 뜻도 아닙니다.

## Attention과 backlog 판정

attention은 collect-drift → policy-error-report-backlog → support-inquiry-backlog → policy-link-review-backlog → policy-duplicate-backlog → notification-stale-backlog → standard-code-backlog 순으로 원인을 좁힙니다. 실제 정책 queue 처리 순서는 [정책 triage](../policy/policy-data-quality-triage-runbook.md)를 기준으로 오류 제보와 중복·링크를 구분합니다.

| 관측 | 다음 판정 |
| --- | --- |
| 표준코드 safe_reconcile_candidate_rows > 0 | 승인된 bounded reconcile 후보. 자동 실행 아님 |
| conflicting_value_gap_rows > 0 | 수동 conflict 근거 확인 |
| 둘 다 0, 단순 미입력 잔량 | 사용자 입력 유도·관찰. smoke/example와 non-example 잔량 분리 |
| terminal_failed_total > 0 | 영구 실패·채널 상태 원인 확인 |
| retryable_failed_due_now > 0 | retry runner·채널 상태 확인 |
| stale_14d_total > 0 | target/title/deeplink 단위 hide 후보. 승인 전 write 금지 |
| stale_unread_7d > 0, 14일 미만 | cadence·가치 검토. 즉시 hide 대상 아님 |
| duplicate/link OPEN > 0 | 실제 review queue 검토 |
| REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS | queue는 닫혔지만 raw 품질 잔량 존재. 신규 제보·재현 문제는 따로 검토 |

알림 감사는 `run-local-notification-backlog-audit.sh`, `-sample-audit.sh`, `run-local-notification-stale-target-audit.sh`; 정책은 `run-local-policy-data-triage-observation-suite.sh`를 사용합니다. 의미·권한·쓰기 절차는 [알림 backlog](notification-backlog-audit-runbook.md), [정책 triage](../policy/policy-data-quality-triage-runbook.md)를 확인합니다.

추천 `KEEP_OBSERVING/reopen_allowed=false`, `DEFERRED_REAL_USER_SAMPLE_THIN`, `EXAMPLE_SMOKE_ONLY_LEADER`는 표본·품질 튜닝 판단입니다. `DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW`는 승인 없이 promotion run을 실행하지 말라는 뜻이며, 버그·북마크/로그 유실·보안 수정 금지 조건이 아닙니다. [추천 precheck](../../deploy/smoke/run-local-recommendation-reopen-precheck.sh)도 앱·인증·대상을 정한 뒤 사용합니다.

## 변경 검증과 배포 후 확인

- Markdown만 변경: 링크·앵커·코드 문서 포인터와 관련 ContractTest를 실제 재실행하고 `git diff --check`를 확인합니다.
- Backend/frontend 변경: [테스트 기준](testing.md)의 unit·lint/build·필요한 integration/browser 검증을 선택합니다. 실행되지 않은 검사를 통과로 기록하지 않습니다.
- 배포 요청이 있는 경우에만 [deployment](../deployment.md)의 runtime env 생성 → preflight → 기동 절차를 따릅니다. 일반 점검에 Compose 재기동을 포함하지 않습니다.
- 배포 후 내부 health, 승인된 public origin의 HTTP·index 새 bundle·주요 화면을 확인합니다. 단순 조회에 불필요한 `/tmp` 파일 덮어쓰기를 넣지 않습니다.
- 배포 프론트는 `FRONTEND_E2E_MODE=deployed-origin`과 명시 public URL을 사용합니다. 테스트 계정/프로필/북마크/문의 등의 쓰기 영향을 확인하고 dev-only 제외·admin opt-in을 유지합니다.
- 수정 후 CI green, 운영 smoke 성공, merge 여부, local main 동기화는 서로 다른 단계입니다. 사용자 요청 없이 커밋·푸시·PR 변경을 수행하지 않습니다.

## 날짜가 있는 운영 관측

| 시점·환경 | 당시 기록 |
| --- | --- |
| 2026-06-27 서버/RDS | active baseline passed, artifact `tmp/active-baseline-suite/20260627T123051Z`; warning 1=standard-code-backlog |
| 같은 표준코드 관측 | users/profiles house-tenure-none mismatch 0/0, safe reconcile/conflict 0/0, users missing all 626 / non-example 5 |
| 2026-06-24 서버/RDS 알림 | failed 없음, unread 26(RECOMMENDATION_DIGEST), stale 7d 14 / 14d 0, NO_STALE_TARGETS. `/policies/3324` 초과 14일 unread 4건은 bounded hide 완료 |
| 2026-06-27 서버/RDS 정책 | duplicate OPEN 0 / link OPEN 0; raw YOUTH duplicate 85, BOKJIRO_LOCAL 59, active-visible YOUTH link blank 161 |
| 2026-06-27 서버/RDS 추천 | reopen_allowed=false / OBSERVE_REAL_USER_TRAFFIC, 실사용자 표본 얇음 |

이 수치는 현재 서버 조회 결과가 아닙니다. 새 관측 없이 “지금 queue 0/추천 손대지 않음”으로 재사용하지 않습니다.

## 인계와 완료

원인·변경 범위·환경/대상·실제 실행한 명령·성공/실패/skip·artifact·현재 decision과 남은 문제를 [작업 현황](../phase-plan.md)에 남깁니다. 현재 계약이 바뀐 분야 문서와 [현재 상태](../current-state.md)를 갱신하고, 재사용할 원인/해결만 [문제 해결 기록](troubleshooting-log.md)에 정리합니다.
