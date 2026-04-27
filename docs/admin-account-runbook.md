# 운영 admin 계정 생성 런북

이 문서는 운영에서 `/api/admin/**`를 호출할 관리자 계정을 처음 만들거나 회수할 때 사용하는 절차입니다.
현재 구조에서는 관리자 권한이 `SECURITY_ADMIN_EMAILS` allowlist와 DB 사용자 row를 동시에 만족할 때만 부여됩니다.

관련 코드와 배경:

- [`backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java`](../backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java)
- [`backend/src/main/java/com/example/welfare/user/service/AuthService.java`](../backend/src/main/java/com/example/welfare/user/service/AuthService.java)
- [`docs/deployment.md`](./deployment.md)

## 1. 전제 조건

- 운영에서 사용할 관리자 이메일을 정확히 1개 이상 확정합니다.
- 관리자 이메일은 모두 소문자로 관리합니다.
- `.env`의 `SECURITY_ADMIN_EMAILS`에 관리자 이메일을 넣습니다.
- `SECURITY_ADMIN_EMAILS`를 바꾼 뒤에는 앱을 재기동합니다.
  - 현재 allowlist는 앱 시작 시 1회 로딩되므로, 환경변수만 바꾸고 재시작하지 않으면 바로 반영되지 않습니다.

예시:

```env
SECURITY_ADMIN_EMAILS=admin@youth-welfare.kr,ops@youth-welfare.kr
```

## 2. 왜 공개 signup으로 만들지 않는가

관리자 예약 이메일은 공개 `POST /api/auth/signup`에서 차단됩니다.
이 제약은 일반 사용자가 관리자 이메일을 먼저 점유하는 것을 막기 위한 보안 장치입니다.

정리하면:

- 공개 signup: 관리자 이메일 사용 불가
- 운영자 수동 생성: 가능
- 로그인/refresh: allowlist에 있는 이메일만 `ROLE_ADMIN` 부여

## 3. 비밀번호 해시 준비

가장 안전한 방법은 현재 애플리케이션이 실제로 사용하는 BCrypt 해시를 그대로 재사용하는 것입니다.

권장 절차:

1. 로컬 또는 테스트 환경에서 일반 이메일로 임시 계정을 하나 가입합니다.
2. 운영 admin 계정에 사용할 최종 비밀번호를 그 임시 계정에도 동일하게 설정합니다.
3. DB에서 임시 계정의 `password_hash` 값을 조회합니다.
4. 그 해시를 운영 admin row 생성 SQL에 그대로 사용합니다.
5. 임시 계정은 조회 후 삭제합니다.

예시 조회:

```sql
SELECT id, email, password_hash
FROM users
WHERE email = 'temp-admin-hash-source@example.com';
```

이미 Spring Security BCrypt와 동일한 규칙을 쓰는 사내 해시 생성 수단이 있으면 그 값을 직접 써도 됩니다.
다만 해시 prefix와 cost가 다른 임의 도구는 섞지 않는 편이 안전합니다.

## 4. 운영 DB에 admin row 생성

`SECURITY_ADMIN_EMAILS`에 넣은 이메일과 DB row의 이메일이 정확히 같아야 합니다.

예시 SQL:

```sql
INSERT INTO users (
    email,
    password_hash,
    name,
    birth_date
) VALUES (
    'admin@youth-welfare.kr',
    '$2a$10$replace_with_bcrypt_hash',
    '운영 관리자',
    '1990-01-01'
);
```

확인 쿼리:

```sql
SELECT id, email, name, is_active, created_at
FROM users
WHERE email = 'admin@youth-welfare.kr';
```

주의:

- 같은 이메일로 기존 일반 사용자 row가 있으면 먼저 정리하고 하나만 남깁니다.
- 관리자 이메일은 반드시 `SECURITY_ADMIN_EMAILS`와 동일한 소문자 값으로 넣습니다.
- `users` 기본값(`is_active`, `notification_yn` 등)은 schema 기본값을 그대로 사용합니다.

## 5. 반영 순서

권장 순서:

1. `.env`에 `SECURITY_ADMIN_EMAILS` 반영
2. 앱 재기동
3. 운영 DB에 관리자 row insert
4. `/api/auth/login`으로 관리자 로그인
5. `/api/admin/**` 엔드포인트로 권한 확인
6. `refresh` 이후에도 관리자 권한이 유지되는지 확인

앱 재기동 예시:

```bash
docker compose -f docker-compose.yml up -d --build app
```

## 6. 검증 방법

최소 검증:

1. 관리자 이메일/비밀번호로 로그인합니다.
2. access token으로 관리자 API를 호출합니다.
3. refresh 후 새 access token으로 같은 관리자 API를 다시 호출합니다.

예시:

```bash
curl -i -X POST http://localhost:8082/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@youth-welfare.kr","password":"YOUR_PASSWORD"}'
```

로그인 후 access token으로 관리자 API 확인:

```bash
curl -i -X POST http://localhost:8082/api/admin/collect/youth \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

기대 결과:

- 비인증: `401`
- 일반 사용자: `403`
- 관리자 사용자: `200` 또는 실제 수집 실행 결과에 따른 도메인 응답

운영에서 실제 수집을 바로 때리기 부담되면, 별도 무해한 admin 조회 API가 추가되기 전까지는 테스트/스테이징에서 먼저 검증하는 편이 안전합니다.

## 7. 비밀번호 변경과 회수

비밀번호 변경:

- 가장 단순한 방법은 새 BCrypt 해시를 만든 뒤 `users.password_hash`를 갱신하는 것입니다.
- 로그인 가능한 상태라면 일반 비밀번호 변경 또는 비밀번호 재설정 플로우를 사용할 수 있습니다.

관리자 권한 회수:

1. `.env`의 `SECURITY_ADMIN_EMAILS`에서 해당 이메일 제거
2. 앱 재기동
3. 필요하면 DB 사용자 비활성화 또는 삭제
4. 필요하면 Redis refresh token 정리

예시:

```sql
UPDATE users
SET is_active = false
WHERE email = 'admin@youth-welfare.kr';
```

## 8. 운영 체크리스트

- [ ] `SECURITY_ADMIN_EMAILS`에 대상 이메일 반영
- [ ] 앱 재기동 완료
- [ ] 운영 DB에 관리자 row 1건만 존재
- [ ] 관리자 로그인 성공
- [ ] 관리자 API 호출 성공
- [ ] refresh 후 관리자 API 재호출 성공
- [ ] 관리자 비밀번호 저장 위치와 전달 경로 분리

## 9. 금지 사항

- 공개 signup으로 관리자 계정을 만들려고 시도하지 않기
- `SECURITY_ADMIN_EMAILS`만 바꾸고 앱 재기동을 생략하지 않기
- 일반 사용자와 관리자 이메일을 혼용하지 않기
- 검증 없이 운영 수집 API를 반복 호출하지 않기
