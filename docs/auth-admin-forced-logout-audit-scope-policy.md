# Admin Forced Logout Audit Scope Policy

## 결정

현재 phase의 `admin forced logout` 운영 증적은 아래처럼 좁게 둔다.

1. API response
   - `userKey`
   - `accepted=true`
2. server log
   - `userKey`
   - `cutoffMillis`
3. 제외
   - response body의 `cutoffMillis`
   - 별도 DB audit table
   - actor/admin identifier persistent 저장

즉 지금은 **운영자에게는 최소 ack만 돌려주고**, 실제 revoke ordering 증적은 서버 로그와 Redis state에서 본다.

## 왜 response에 `cutoffMillis` 를 싣지 않는가

### 1. API success 의미는 intent accepted지, ordering detail 전달이 아니다

현재 [auth-admin-forced-logout-api-contract.md](./auth-admin-forced-logout-api-contract.md) 에서 success 의미는:

- existing access/refresh revoke intent accepted

이다.

여기에 `cutoffMillis` 까지 response 대표값처럼 올리면, 클라이언트/운영자가 이 값을 별도 제품 계약처럼 소비하기 시작할 수 있다.

현재 단계에서 필요한 것은:

- 어떤 userKey에 대해
- forced logout intent가 들어갔는지

이지, response body에서 ordering 기준 숫자를 API 계약으로 노출하는 것은 아니다.

### 2. `cutoffMillis` 는 auth gate 내부 구현 세부에 더 가깝다

`cutoffMillis` 는:

- `access-cutoff:{userKey}`
- token `iatm`
- old/new token ordering

비교를 위한 내부 기준값이다.

이 값은 운영 증적으론 유용하지만, 외부 응답 계약에 싣기 시작하면:

- future key shape 변경
- millis source 변경
- multi-node clock handling

같은 내부 구현 변경이 response contract까지 끌려 올라온다.

## 왜 로그에는 남기는가

### 1. same-second/off-by-one triage에 실제로 필요하다

forced logout의 핵심 문제는:

- old token은 왜 막혔는가
- relogin token은 왜 통과했는가

를 ordering 기준으로 설명할 수 있어야 한다는 점이다.

이때 최소 증적은:

- `userKey`
- `cutoffMillis`

이다.

그래서 current implementation처럼 server log에는 남겨 두는 편이 맞다.

### 2. Redis key만으로는 실행 시점 문맥이 바로 보이지 않는다

Redis `access-cutoff:{userKey}` 를 직접 보면 현재 cutoff 값은 확인할 수 있다.

하지만:

- 누가 언제 API를 쳤는지
- 어떤 요청에서 새 cutoff가 기록됐는지

는 log가 더 빠르다.

따라서 지금 단계의 운영 증적은:

- response: minimal ack
- log: cutoff detail
- Redis: current source of truth

로 삼분하는 편이 가장 단순하다.

## 왜 actor/admin identifier persistent audit를 지금 같이 열지 않는가

### 1. 현재 auth/session revoke 범위를 넘는다

actor까지 같이 저장하려면 곧바로 아래가 따라온다.

- audit storage location
- retention
- PII/identifier masking 기준
- replay/runbook 문서

이건 지금 forced logout 1차 hardening 범위를 넘는다.

### 2. 현재 baseline은 “차단이 되느냐”가 우선이다

이미 구현/테스트로 닫아야 했던 핵심은:

- old access fail
- old refresh fail
- relogin access pass
- legacy token A006

였다.

actor audit는 중요하지만, 지금 단계에선 revoke correctness보다 우선순위가 낮다.

## 현재 권장 범위

### response

```json
{
  "success": true,
  "data": {
    "userKey": "usr_...",
    "accepted": true
  }
}
```

### log

예:

```text
[Admin] forced logout 트리거 userKey=usr_123 cutoffMillis=1777588800000
```

## future reopen 조건

아래 요구가 생기면 audit 범위를 다시 연다.

1. 운영자별 forced logout 추적이 실제 incident review requirement가 됨
2. host log만으로는 증적 보존이 부족함
3. admin action history UI/API가 필요해짐

그 전까지는:

- response minimal
- log + Redis sufficient

로 유지한다.

## next step

다음 작은 task는 이 audit scope를 기준으로, `forced logout` log line에 `actor` 까지 당장 얹을지 말지를 결정하기보다, 먼저 **current log line format을 smoke/runbook 문서에 명시할지** 정하는 것이다.
