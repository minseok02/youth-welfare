# 인증과 세션 회수 검증

계약은 [auth 현재 상태](auth-session-revocation-current-state.md), API 예시는 [runtime 명령](../core/runtime-api-smoke-commands.md)을 따릅니다. logout·withdraw·allowlist·forced logout을 섞어 판정하지 않습니다.

## 실행 전

- [환경 안내](../core/environment-guide.md)에서 앱·DB·Redis·비밀·테스트 계정과 영향 범위를 정합니다. 실제 사용자 토큰을 회수하거나 탈퇴시키는 검증을 일반 조회로 실행하지 않습니다.
- `run-local-auth-observation-suite.sh`는 **기존 결과만 읽는 wrapper가 아닙니다.** 기본으로 runtime API, 로그인 실패 추적, 계정 잠금, 탈퇴, 관리자 forced logout을 실행합니다.
- `RUN_RUNTIME_API_SMOKE/RUN_LOGIN_FAILURE_TRACKING_SMOKE/RUN_ACCOUNT_LOCKOUT_SMOKE/RUN_WITHDRAW_SMOKE/RUN_ADMIN_FORCED_LOGOUT_SMOKE`의 선택 범위와 실제 하위 script를 확인합니다. 전부 끈 실행은 회귀 검증 통과 증거가 아닙니다.
- Redis 유실·재기동이 있었다면 아래 단일 회수 성공을 내구성 검증으로 사용하지 않습니다. [미해결 위험](auth-session-revocation-current-state.md#redis-상태-유실-미해결-위험)을 먼저 확인합니다.

## 검증 시나리오

| 경계 | Endpoint / wrapper | 기대 관측 |
| --- | --- | --- |
| Logout | `POST /api/auth/logout`; [runtime-api-smoke](../../deploy/smoke/run-local-runtime-api-smoke.sh) | cookie clear·refresh 회수·cutoff, bearer-present exact revoke, presented/older access 401/A006 |
| Cookie-only logout | 같은 endpoint에 식별 가능한 refresh | bearer가 없어도 같은 사용자 old access 차단. 식별 불가 요청의 cookie 삭제와 구분 |
| Withdraw | `DELETE /api/users/me`; [withdraw-smoke](../../deploy/smoke/run-local-withdraw-smoke.sh) | refresh 삭제·presented revoke·withdrawn 정리, old access 401/A006·stale refresh 410/U003 |
| Allowlist 변경 | 승인된 SECURITY_ADMIN_EMAILS 적용 | old admin JWT 즉시 차단 아님. 새 refresh 발급 access에서 ROLE_ADMIN 제거 |
| Forced logout | `POST /api/admin/users/forced-logout`; [admin-forced-logout-smoke](../../deploy/smoke/run-local-admin-forced-logout-smoke.sh) | refresh v2/legacy 정리·cutoff v2 기록, old access 401/A006·old refresh 401/A003, 재로그인 보호 API 200 |
| Legacy admin access | forced logout cutoff 보호 경계 | iatm 없음은 401/A006. INVALID_TOKEN 형식 오류로 오해하지 않음 |

wrapper가 해당 변형을 실제 실행했는지 확인합니다. 일반 runtime smoke 성공만으로 cookie-only·legacy·상태 유실 변형까지 검사됐다고 기록하지 않습니다.

## 증적과 중단 기준

forced logout은 `success=true, data.userKey=대상, data.accepted=true`와 해시 감사 로그를 함께 봅니다. 응답의 cutoffMillis를 기대하거나 로그에 원문 userKey/JWT를 추가하지 않습니다. Redis key/legacy 호환·epoch cutoff·actorHash/targetUserKeyHash는 [현재 계약](auth-session-revocation-current-state.md#redis-key-shape)을 따릅니다.

다음 경우에는 판정·실행을 멈추고 경계를 바로잡습니다.

- allowlist 변경을 즉시 forced logout으로 해석함.
- 식별 가능한 cookie-only logout을 refresh 회수만으로 판정함.
- 식별 가능한 bearer/refresh가 없는데 특정 사용자 회수 성공으로 기록함.
- Redis 유실 후 기존 차단이 그대로라고 가정하거나 legacy A006을 잘못 해석함.
- 운영 계정·다른 사용자의 세션/탈퇴 데이터가 검증 대상에 포함됨.

## 결과 기록

실제 대상·endpoint/wrapper·실행한 변형·old access/refresh·relogin·상태 코드·해시 증적·실패/skip·다음 액션을 기록합니다. token/password/cookie 원문은 보존하지 않습니다.

auth observation의 decision_class·enabled_smoke_steps·operator_reading·next_action과 summary/JSON/note는 **선택해 실행한 경계**의 결과입니다. 기존 artifact를 읽는 것과 하위 smoke를 재실행하는 것을 구분하고, 새 관측 없이 과거 PASS를 현재 세션 보안의 보장으로 사용하지 않습니다.
