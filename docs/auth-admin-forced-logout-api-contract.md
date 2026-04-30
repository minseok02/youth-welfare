# Admin Forced Logout API Contract

## 결정

future `admin forced logout` 의 1차 API 계약은 아래처럼 둔다.

- method
  - `POST`
- path
  - `/api/admin/users/forced-logout`
- auth
  - `ROLE_ADMIN`
- target identifier
  - request body의 `userKey`
- success meaning
  - **해당 시점 이전에 발급된 access/refresh token revoke 요청이 수락되었다**

즉 이 API는 role revoke나 account lock API가 아니라, **특정 user의 existing session/token revoke API** 다.

## request

### body

```json
{
  "userKey": "usr_..."
}
```

### `userKey` 를 기본 식별자로 쓰는 이유

현재 auth/PII 분리 이후 운영 경계는 `userId` 보다 `userKey` 가 더 안정적이다.

- refresh Redis key도 `refresh:{userKey}` 기준
- admin replay/sync 계열 운영 경로도 `userKey` 중심
- PII/metadata/core split 이후 runtime identity도 `userKey` 기준으로 수렴

따라서 forced logout도 `userId` query param보다 `userKey` body를 기준 식별자로 둔다.

## response

### success

기본은 `200 OK` 로 두고, body는 작은 ack만 준다.

```json
{
  "success": true,
  "data": {
    "userKey": "usr_...",
    "accepted": true
  }
}
```

중요한 점은 이 API의 success가 “모든 노드/모든 요청이 이미 완전히 정리됐다”가 아니라, **revoke intent가 current runtime source of truth에 기록되었다** 는 의미라는 점이다.

### failure

- admin 아님
  - `403`
- 대상 user 없음
  - 현재 phase에서는 `404` 또는 domain not found error
- 잘못된 body
  - `400`

## idempotency

이 API는 **idempotent by effect** 로 본다.

즉 같은 `userKey` 로 짧은 시간 안에 여러 번 호출돼도:

- 이미 revoke 상태가 있으면 그대로 유지
- 추가 부작용 없이 success ack를 다시 줄 수 있음

이 기능은 incident response/offboarding 경로라, 운영자가 재시도했을 때 “이미 처리됨” 때문에 실패시키는 것보다 같은 결과를 유지하는 편이 낫다.

## scope

### 이 API가 해야 하는 것

- old access token 차단 경로 열기
- old refresh token 재발급 차단 경로 열기

### 이 API가 하지 않는 것

- `SECURITY_ADMIN_EMAILS` 수정
- user 활성/비활성 전환
- password reset
- `ROLE_ADMIN` 영구 제거
- account lock

즉 이 API는 **session/token revoke only** 다.

## current admin API 구조와의 정렬

기존 운영 경로도 사용자 운영 액션은 `/api/admin/users/...` 아래에 둔다.

예:

- `/api/admin/users/pii-backfill`
- `/api/admin/users/metadata-user-key-backfill`
- `/api/admin/users/pii-sync-replay`
- `/api/admin/users/pii-sync-status`

따라서 forced logout도 `/api/admin/users/forced-logout` 으로 두는 편이 현재 controller 분리와 가장 잘 맞는다.

## future smoke에서 바로 볼 것

이 계약이 구현되면 future integration smoke는 아래 순서로 본다.

1. admin user login
2. old access token 확보
3. old refresh token 확보
4. `POST /api/admin/users/forced-logout`
5. old access token으로 admin API 호출 -> `401 / A006`
6. old refresh token으로 `/api/auth/refresh` 호출 -> 실패

## next step

다음 작은 task는 이 API가 기록할 **Redis key shape / TTL / clear 조건** 을 고정하는 것이다.

즉 이제 path와 request/response는 고정했고, 그 다음은 revoke state를 Redis에 어떤 두 층으로 남길지를 정리하면 된다.
