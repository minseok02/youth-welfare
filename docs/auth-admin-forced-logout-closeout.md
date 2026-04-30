# Admin Forced Logout 1차 Closeout

## 결론

현재 phase의 `admin forced logout` 1차 hardening 범위는 닫습니다.

이번 phase에서 이미 고정된 범위는 아래와 같습니다.

- 운영자 진입점: `POST /api/admin/users/forced-logout`
- revoke source of truth: Redis `refresh:{userKey}` 삭제 + `access-cutoff:{userKey}` 기록
- access token cutoff 비교: access token `iatm` 기준
- legacy admin access token 처리: forced logout 보호 경계에서 `401 / A006`
- auth read gate 위치: `JwtAuthenticationFilter`
- 운영 증적: response는 `userKey + accepted`, triage는 로그/Redis 기준
- 현재 로그 범위: `userKey + cutoffMillis`

즉 현재 phase에서는 다음까지가 완료 상태입니다.

- old admin access token 즉시 차단
- old refresh token 즉시 차단
- relogin fresh access token 회복
- legacy `iatm` 없는 admin access token 차단
- smoke/test 기준선 확보

## 일부러 닫은 것

이번 phase에서는 아래 범위를 다시 열지 않습니다.

- actor identifier 로그
- 별도 DB audit table
- forced logout action history UI/조회 API
- account lock / admin role revoke 와 forced logout 결합
- multi-node audit 확장

이 항목들은 revoke correctness가 아니라 감사/운영성 확장 문제이므로, 별도 reopen 조건이 생길 때 다시 다룹니다.

## 다음 활성 트랙

auth/admin hardening 1차를 닫은 뒤의 다음 활성 pending은 정책 source onboarding 쪽입니다.

현재 우선순위는 아래 순서로 둡니다.

1. `고용24/워크넷 채용정보`, `마이홈포털 공공주택 모집공고/단지/예비입주자 대기현황` 같은 listing형 source 분리 스키마 초안
2. `정부지원일자리정보`, `구직자취업역량 강화프로그램`, `Gov24/보조금24` 의 정책형 source canonical onboarding 우선순위와 live validation 순서
3. 복지로 live detail validation / gap-fill observability 남은 항목

## reopen 조건

forced logout 트랙은 아래 중 하나가 생길 때 다시 엽니다.

- actor-level audit 요구
- persistent audit/history 요구
- cluster/multi-node 운영 증적 요구
- forced logout batch/대량 실행 요구
- account lock 과 session revoke를 함께 다뤄야 하는 운영 요구
