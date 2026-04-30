# Admin Forced Logout Package And Dependencies Policy

## 결정

`UserSessionRevocationService` 는 **`backend/src/main/java/com/example/welfare/user/service`** 패키지에 두고, 1차 생성자 dependency는 아래 **최소 집합** 으로 고정한다.

1. `RedisTemplate<String, String>`
2. `JwtUtil`
3. `AccessTokenRevocationService`

즉 1차 구현은 `user.service` 패키지 안에서, 기존 auth/user revoke 경계와 같은 층에 두고 시작한다.

## 왜 `user.service` 인가

### 1. 현재 revoke/auth user 경계가 이미 여기 있다

지금 관련 책임은 모두 `com.example.welfare.user.service` 아래에 있다.

- `AuthService`
- `UserService`
- `AccessTokenRevocationService`

`UserSessionRevocationService` 도:

- refresh key delete
- access cutoff read/write
- userKey 기준 session revoke

를 다루므로 같은 층에 두는 편이 가장 자연스럽다.

### 2. 별도 auth 패키지 재배치는 지금 scope가 아니다

이 기능 하나를 위해:

- `auth.service`
- `security.session`
- `global.auth`

같은 새 패키지로 빼기 시작하면, 이번 작은 task 범위를 넘어선 구조 재배치가 된다.

현재 phase의 목표는:

- forced logout 구현 경계 고정
- 최소 diff로 실제 hardening 준비

이므로 package 재배치보다 현행 `user.service` 정렬이 더 맞다.

## 왜 이 dependency들만 받는가

### 1. `RedisTemplate<String, String>`

필수다.

이 서비스는:

- `refresh:{userKey}` delete
- `access-cutoff:{userKey}` write/read

를 직접 다룬다.

### 2. `JwtUtil`

필수다.

read path에서:

- subject/userKey
- `iatm`
- allow-expired read helper

를 꺼내야 한다.

따라서 token parsing/claim read는 이 서비스가 `JwtUtil` 을 통해 받는 게 맞다.

### 3. `AccessTokenRevocationService`

권장 dependency다.

이 서비스는 exact-token blacklist를 이미 책임지고 있으므로, `UserSessionRevocationService` 가:

- logout/withdraw exact revoke 규칙
- forced logout user-session revoke 규칙

을 한 auth gate에서 함께 볼 때 재사용 지점이 생긴다.

즉 새 서비스가 exact revoke key shape를 다시 복제하지 않도록 composition으로 넣어 둔다.

## 왜 다른 dependency는 지금 넣지 않는가

### 1. `UserRepository`

현재 API contract 자체가 `userKey` 를 받는다.  
1차 구현에서 read/write 핵심에는 DB lookup이 필수가 아니다.

### 2. `AuthUserRepository`

forced logout은 role/source 변경이 아니라 session/token revoke다.  
account state read가 꼭 필요해질 때까지는 빼 둔다.

### 3. `ChatSessionCleanupService`

챗 세션 정리는 logout/withdraw 경계에 붙어 있지만, forced logout 1차 baseline은 access/refresh revoke다.  
세션 cleanup까지 섞으면 scope가 커진다.

### 4. `UserCoreSyncService`

DB state sync를 바꾸는 기능이 아니므로 현재는 불필요하다.

## 권장 초기 형태

```java
@Service
@RequiredArgsConstructor
class UserSessionRevocationService {
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtil jwtUtil;
    private final AccessTokenRevocationService accessTokenRevocationService;
}
```

## next step

다음 작은 task는 이 package/dependency 정책을 기준으로, `UserSessionRevocationService` 구현에 필요한 `JwtUtil` helper 추가를 먼저 할지, 아니면 service skeleton부터 만들지 순서를 정하는 것이다.
