# Admin Forced Logout Legacy Token Rollout Policy

> Status note (2026-05-01)
>
> 이 문서는 현재 구현의 배경을 남긴 **design history** 입니다.
> 현재 제품/코드 계약은 [auth-session-revocation-current-state.md](../../auth-session-revocation-current-state.md) 와
> [auth-admin-forced-logout-closeout.md](../../auth-admin-forced-logout-closeout.md) 를 우선 기준으로 봅니다.


## 결정

future `admin forced logout` 가 실제로 켜지는 시점부터는, **`iatm` 없는 legacy admin access token은 forced logout 보호 경계에서 재로그인을 요구하는 대상** 으로 본다.

즉 현재 phase의 rollout 계약은 아래와 같다.

- new access token
  - `iatm` 포함
  - forced logout cutoff 비교 지원
- legacy access token (`iatm` 없음)
  - forced logout 비교 대상에서 정상 fallback 불가
  - 운영적으로는 재로그인 요구

다시 말해, forced logout rollout은 legacy admin access token을 끝까지 호환하는 기능이 아니라, **admin hardening 경계에서 새 token contract로 정렬하는 기능** 이다.

## 이유

### 1. fallback을 열면 same-second ambiguity가 다시 돌아온다

이미 [auth-admin-forced-logout-issued-at-policy.md](./auth-admin-forced-logout-issued-at-policy.md), [auth-admin-forced-logout-jwt-helper-policy.md](./auth-admin-forced-logout-jwt-helper-policy.md) 에서 정한 것처럼:

- forced logout은 `access-cutoff:{userKey}` 와 발급시각 ordering이 핵심
- 표준 `iat` 초 단위 fallback은 경계를 흐린다

따라서 rollout 시점에 legacy token도 “대충 비슷하게” 지원하려고 하면, 기능의 핵심 보장 자체가 약해진다.

### 2. admin forced logout은 일반 사용자 UX보다 운영 보안 이벤트다

이 기능은:

- admin offboarding
- incident response
- suspected compromise

를 위한 것이다.

이 경우 priority는 “기존 admin token을 최대한 매끄럽게 오래 살린다”가 아니라:

- cutoff ordering correctness
- 즉시 차단 의미
- 운영 절차 단순성

이다.

따라서 legacy admin access token은 graceful fallback보다 **재로그인 요구** 가 더 맞다.

### 3. refresh token은 이미 새 contract로 정리 가능하다

forced logout 설계에서 refresh는:

- `refresh:{userKey}` delete

로 즉시 정리된다.

즉 rollout 시점에 stale session을 정리하는 기본 운영 동작은:

1. old refresh unusable
2. old legacy access token은 forced logout 보호 경계에서 신뢰하지 않음
3. 필요하면 재로그인으로 새 `iatm` token 발급

으로 단순하게 가져가는 편이 낫다.

## 운영 계약

### feature on 이후 기대값

- fresh login 후 발급된 access token
  - `iatm` 있음
  - forced logout 비교 가능

- rollout 이전 또는 transition 중 발급된 legacy admin access token
  - forced logout 경계에 들어오면 재로그인 요구 대상

### 운영자 해석

운영자는 아래처럼 이해하면 된다.

- allowlist revoke
  - old access는 계속 살 수 있음
  - refresh로 새 token부터 role 제거

- forced logout rollout 이후
  - 새 admin token contract는 `iatm` 기준
  - old legacy admin access token은 재로그인 요구로 정리 가능

## integration baseline에서의 취급

future integration baseline은 legacy admin access token을 아래처럼 다룬다.

- success baseline
  - 새 login으로 발급된 `iatm` 포함 token 기준
- legacy token baseline
  - optional smoke/inventory
  - “호환 지원”이 아니라 “재로그인 요구/명시적 fail” 확인용

즉 mainline success test는 legacy token compatibility를 목표로 두지 않는다.

## current phase에서 제외하는 것

이번 rollout 정책은 아래까지 같이 열지 않는다.

- legacy admin access token auto-upgrade
- `iat` fallback을 통한 부분 호환
- rollout 동안 dual-claim acceptance window
- user-facing migration banner/UI

## next step

다음 작은 task는 이 정책을 기준으로, future 구현 시 **legacy admin access token이 forced logout protected path에서 어떤 error contract를 낼지** 를 문서로 고정하는 것이다.

예:

- `401 / A006`
- `401 / INVALID_TOKEN`
- 별도 forced-logout-specific code를 둘지

를 먼저 정리하면 된다.
