# 인증과 세션 회수 계약

현재 코드의 logout·withdraw·allowlist 변경·forced logout을 구분합니다. 실제 검증은 [실행 체크리스트](auth-operation-checklist.md), auth 분야 진입점은 [목차](auth-docs-index.md)입니다. 아래 즉시 차단 계약은 **Redis revoke 상태가 유지된다는 전제**이며 내구성은 미해결입니다.

## 1. logout

`POST /api/auth/logout`은 refresh 회수·user-wide access cutoff·bearer-present exact revoke입니다. cookie-only라도 refresh token에서 userKey를 식별할 수 있으면 기존 access를 cutoff로 차단합니다. bearer와 식별 가능한 refresh가 모두 없는 요청의 쿠키 삭제만으로 사용자 세션 회수 성공을 보장하지 않습니다.

old refresh는 401 invalidation 계열, presented access와 refresh 이전 older login access는 `401/A006`이어야 합니다. 호출 맥락상 refresh cookie가 삭제된 뒤의 missing-cookie 응답과 이미 회수한 refresh 원문을 재사용한 응답 코드는 다를 수 있습니다.

구현은 [AuthSessionService](../../backend/src/main/java/com/example/welfare/user/service/AuthSessionService.java) → [AuthTokenService](../../backend/src/main/java/com/example/welfare/user/service/AuthTokenService.java)의 logoutByUserKey/logoutByRefreshToken, [UserSessionRevocationService](../../backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java), [AccessTokenRevocationService](../../backend/src/main/java/com/example/welfare/user/service/AccessTokenRevocationService.java)입니다.

## 2. withdraw

`DELETE /api/users/me`은 refresh 회수·presented bearer exact revoke·withdrawn 상태와 채팅/속성/우선순위 정리입니다. 탈퇴에 사용한 old access는 `401/A006`, stale refresh는 `410/U003` 경계를 확인합니다. 구현은 [UserAccountCommandService](../../backend/src/main/java/com/example/welfare/user/service/UserAccountCommandService.java)입니다.

## 3. admin allowlist revoke

`SECURITY_ADMIN_EMAILS` 변경을 앱에 적용하면 future token issuance의 ROLE_ADMIN이 바뀝니다. 이미 발급된 admin access와 기존 refresh를 즉시 회수하는 forced logout이 아닙니다. 기존 refresh로 새 token을 받을 때는 현재 allowlist로 role을 다시 계산합니다. 구현은 [AuthAdminRoleService](../../backend/src/main/java/com/example/welfare/user/service/AuthAdminRoleService.java)의 initAdminEmails/resolveRoles입니다. 적용을 위한 앱 변경·재기동은 별도 승인 범위입니다.

## 4. admin forced logout

`POST /api/admin/users/forced-logout`에 JSON `{"userKey":"..."}`를 전달합니다. 대상 refresh를 회수하고 cutoff를 기록해 old access/refresh를 차단하며, 재로그인한 fresh access는 허용합니다. 응답은 `userKey, accepted=true`이고 cutoffMillis는 응답 필드가 아닙니다.

write는 [UserAdminController](../../backend/src/main/java/com/example/welfare/user/controller/UserAdminController.java), 상태 write/read는 UserSessionRevocationService, 인증 gate는 [JwtAuthenticationFilter](../../backend/src/main/java/com/example/welfare/global/config/JwtAuthenticationFilter.java)입니다.

## Redis key shape

| 용도 | 현재 key·값 |
| --- | --- |
| bearer 1개 exact revoke | `access-revoked:{sha256(accessToken)}`; token 남은 만료 시간 동안 차단 |
| refresh session | `refresh:v2:{sha256(userKey)}`; 값도 refresh token SHA-256 해시 |
| user-wide cutoff | `access-cutoff:v2:{sha256(userKey)}`; epoch millis, cutoff 이하 발급 token 거부 |

legacy `refresh:{userKey}/access-cutoff:{userKey}`는 읽기·정리 호환 경계이지 신규 write 대상이 아닙니다. 원문 token/userKey key를 진단 목적으로 다시 쓰지 않습니다.

## Redis 상태 유실: 미해결 위험

`docker-compose.prod.yml`은 `--save "" --appendonly no`와 /data tmpfs로 비영속입니다. 재시작·재생성 뒤 revoke/cutoff를 복구하지 못하며, `isAccessAllowed(...)`는 exact revoke와 cutoff가 모두 없으면 access를 허용합니다. 따라서 **아직 만료되지 않은 회수 대상 JWT가 다시 허용될 수 있습니다.**

refresh 소실로 재로그인이 필요한 현상과 old access 재허용은 별개입니다. 재로그인·health UP·단일 logout smoke 통과는 차단 복구 증거가 아닙니다. 운영 Redis를 일반 캐시처럼 flush/restart하지 말고 보호 조치와 영향 범위를 먼저 정합니다.

후속 설계는 영속 세션 버전/회수 시각, Redis 내구성·복구, 장애 시 gate 정책을 비교하고 상태 유실 후 old access 거부를 검증해야 합니다. 문서 감축으로 인증 동작·Redis 설정·실제 세션을 고친 것은 아닙니다.

## JWT 현재 계약

access token은 `iatm` claim을 가지며 refresh/notification token에는 없습니다. [JwtUtil](../../backend/src/main/java/com/example/welfare/global/util/JwtUtil.java)의 getIssuedAtMillis/getIssuedAtMillisAllowExpired를 사용합니다. forced-logout cutoff 경계의 `iatm` 없는 legacy admin access는 invalid-format이 아닌 `401/A006` 회수 경계로 처리합니다.

A006의 대표 사례는 logout 후 presented/older access, withdraw 후 presented access, forced logout 후 old access와 legacy token입니다.

## 운영 증적 현재 계약

```text
[AdminAudit] event=forced_logout outcome=accepted actorHash=<sha256> targetUserKeyHash=<sha256> cutoffMillis=<epochMillis>
```

actor/target 해시와 cutoff를 확인하고 userKey·이메일·JWT 원문을 로그에 추가하지 않습니다. 해시 로그가 존재하는 것과 별도 영속 audit/history table·조회 UI가 있다는 것은 다릅니다.

## Closeout과 후속 범위

기존 1차 closeout은 API·Redis cutoff·iatm·filter gate·old token 거부/relogin 회귀의 구현 범위입니다. 영속 audit/history·multi-node 증적·대량 강제 회수·account lock/role revoke 결합은 별도 요구이며, Redis 상태 유실은 별도 보안 결함입니다.

당시 source onboarding/listing 스키마·복지로 detail 후속 우선순위는 현재 전체 개발 순서를 대신하지 않습니다. 현재 결함은 [전체 과제](../current-state.md), source 검토는 [온보딩 가이드](../policy/policy-source-onboarding-checklist.md)를 봅니다.

개별 forced-logout API/entrypoint/Redis/iatm/helper 설계는 history입니다. [logout 범위](../history/auth/auth-logout-revocation-scope-policy.md), [allowlist 경계](../history/auth/auth-admin-revoke-boundary-policy.md), [refresh role 재계산](../history/auth/auth-admin-refresh-revoke-policy.md), [서비스 책임](../history/auth/auth-admin-forced-logout-service-structure-policy.md)은 배경 근거로 읽고 현재 구현을 대신하지 않습니다.
