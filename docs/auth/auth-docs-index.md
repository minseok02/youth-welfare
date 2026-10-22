# 인증 문서 목차

현재 동작은 코드·재현으로, 기대 동작은 요구사항·승인된 결정으로 확인합니다. 구현과 다르다고 사용자 보호 계약을 낮추지 않습니다. 실행 전 [환경 안내](../core/environment-guide.md)를 확인합니다.

## 현재 계약과 검증

| 목적 | 문서 |
| --- | --- |
| Logout·withdraw·allowlist·forced logout, Redis/JWT·감사·closeout | [세션 회수 계약](auth-session-revocation-current-state.md) |
| 테스트 계정·실행 범위·기대 코드·기록 | [검증 체크리스트](auth-operation-checklist.md) |
| Auth/session wrapper의 summary·artifact·로그 해석 | [observation runbook](auth-observation-runbook.md) |
| PII·권한·공통 보안과 미해결 문제 | [보안 현재 상태](../core/security-hardening-current-state.md) |
| 수동 API와 로컬 unit/integration 구분 | [runtime 명령](../core/runtime-api-smoke-commands.md), [테스트](../core/testing.md) |
| 장애·재현 기록 | [incident 템플릿](auth-incident-template.md) |

`run-local-auth-observation-suite.sh/run-local-auth-session-smoke.sh`는 기본으로 로그인 실패·잠금·탈퇴·세션 회수를 포함하는 **실행 검증**입니다. 기존 artifact 조회와 구분하고 운영 사용자에 일반 점검처럼 실행하지 않습니다. 개별 runtime/withdraw/admin-forced-logout smoke도 체크리스트에서 대상·부작용을 정한 뒤 선택합니다.

Redis 비영속 상태 유실 후 회수 JWT 재허용 위험은 [미해결 보안 과제](auth-session-revocation-current-state.md#redis-상태-유실-미해결-위험)입니다. 기존 closeout이나 smoke PASS로 해결 완료 처리하지 않습니다.

## 설계 이력

`history/auth/auth-admin-forced-logout-*`는 entrypoint·Redis shape·iatm·legacy token·helper·API의 결정 배경입니다. 현재 계약 대신 매번 전체를 읽지는 않습니다.

- [Logout 범위](../history/auth/auth-logout-revocation-scope-policy.md), [withdraw 후속 설계](../history/auth/auth-withdraw-revocation-next-step.md).
- [Refresh role 재계산](../history/auth/auth-admin-refresh-revoke-policy.md), [allowlist 경계](../history/auth/auth-admin-revoke-boundary-policy.md).
- [기존 reopen 순서](../history/auth/auth-revocation-reopen-order.md).

과거 제외 범위·다음 트랙은 당시 계획으로 읽고 현재 승인된 보안 수정의 금지 조건으로 사용하지 않습니다. 결과 기록에는 실제 실행한 경계·old access/refresh·relogin·해시 증적·실패/skip만 남기며 비밀 원문을 보존하지 않습니다.
