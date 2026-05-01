# Blocked SQL Reopen 우선순위

관련 문서:

- [policy-normalization-youth-mid-stable-code-source-plan.md](./policy-normalization-youth-mid-stable-code-source-plan.md)
- [policy-normalization-gov24-label-source-plan.md](./policy-normalization-gov24-label-source-plan.md)
- [policy-normalization-gov24-support-condition-source-plan.md](./policy-normalization-gov24-support-condition-source-plan.md)
- [policy-source-canonical-onboarding-priority.md](./policy-source-canonical-onboarding-priority.md)
- [phase-plan.md](./phase-plan.md)

## 목적

현재 아직 source-of-truth 부족으로 막혀 있는 SQL 초안들 중

- `YOUTH_MID stable code mapping SQL`
- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE import/backfill SQL`
- `GOV24_SUPPORT_CONDITION full inventory/backfill`

을 어떤 순서로 다시 열지 고정합니다.

## 결론

현재 reopen 우선순위는 아래와 같습니다.

1. `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
2. `GOV24_SUPPORT_CONDITION` full inventory/backfill
3. `YOUTH_MID stable code mapping SQL`

즉 blocked SQL pending 중 다음 기본 축은 `YOUTH_MID` 가 아니라 `GOV24_*` 입니다.

## 이유

## 1. `Gov24` 는 canonical onboarding 기준선과 더 직접 연결된다

이미 고정된 정책형 source canonical onboarding 우선순위는:

1. `Gov24/보조금24`
2. `정부지원일자리정보`
3. `구직자취업역량 강화프로그램`

입니다.

즉 `Gov24` 쪽 metadata/code inventory가 먼저 열려야,

- `core/detail/facts`
- `official taxonomy`
- `compat bridge`

를 한꺼번에 더 전진시킬 수 있습니다.

반면 `YOUTH_MID stable code` 는 중요하지만,
현재 canonical 기준선 자체를 막는 축은 아닙니다.

## 2. `GOV24_*` 는 current API source가 명확하다

`Gov24` 는 적어도 current source-of-truth가 명확합니다.

- `serviceList`
- `serviceDetail`
- `supportConditions`

반면 `YOUTH_MID` 는:

- authenticated metadata inventory 부재
- live payload에 `srchPolyBizSecd` 직접 부재
- operator-provided codebook 필요

상태라, reopen 조건 충족까지 더 멉니다.

즉 “막혀 있더라도 먼저 다시 열 가능성이 높은 쪽”은 `Gov24_*` 입니다.

## 3. `YOUTH_MID` 는 label-only fallback이 이미 비교적 안정적이다

현재 `YOUTH_MID` 는

- label-only taxonomy
- `YOUTH_MID_RAW_ALIAS`

정책으로 일단 버틸 수 있습니다.

완전하지는 않지만,
현재 canonical sidecar와 read-model에서 치명적인 공백을 바로 만들지는 않습니다.

반대로 `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` 은
official taxonomy inventory가 열리면
현재 metadata-only placeholder 상태를 실제 import/backfill로 바꿀 수 있습니다.

## 실무 우선순위

### 1. 먼저 다시 열 것

- `GOV24_SERVICE_FIELD`
- `GOV24_USER_TYPE`
- `GOV24_BENEFIT_TYPE`

조건:

- current Swagger/schema export 또는 provider codebook 확보

### 2. 그 다음

- `GOV24_SUPPORT_CONDITION` full inventory/backfill

이유:

- representative subset seed는 이미 있음
- label inventory 확보가 되면 `GOV24_*` 본체 import와 같이 묶어 확장 가능

### 3. 마지막

- `YOUTH_MID stable code mapping SQL`

이유:

- practical next step이 여전히 operator-provided export/codebook
- current canonical 진행축을 당장 더 밀어 주는 정도는 `Gov24_*` 보다 낮음

## 요약

1. blocked SQL reopen 기본 우선순위는 `Gov24_* -> Gov24 supportConditions full inventory -> YOUTH_MID` 입니다.
2. 이유는 `Gov24` 가 current canonical onboarding 기준선과 더 직접 연결되고, current API source도 더 명확하기 때문입니다.
3. `YOUTH_MID` 는 중요하지만, 지금은 label-only fallback으로 유지한 채 나중에 다시 여는 편이 맞습니다.
