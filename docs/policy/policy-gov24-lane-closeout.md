# `Gov24` lane closeout

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-reopen-checklist.md](./policy-gov24-reopen-checklist.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)

## 목적

이 문서는 `Gov24` lane에서

- 이미 운영 반영까지 닫힌 범위
- 계속 deferred 로 남기는 범위
- 다시 열 때의 최소 조건

만 빠르게 읽기 위한 closeout 요약입니다.

## 현재 결론

현재 `Gov24` lane은 **bounded 제품 확장까지 닫혔고, 더 큰 구조화 작업은 deferred** 입니다.

이미 active/closed 인 범위:

1. `serviceField` public filter
2. `userType` public filter
3. `benefitType` public filter
4. recommendation soft additive scoring
5. matcher hard condition 비활성 경계
6. `Gov24 -> YOUTH_MID` bridge 비활성 경계

계속 deferred 인 범위:

1. matcher hard condition
2. `service_facts` 승격
3. `Gov24 -> YOUTH_MID` bridge
4. stable code/import-backfill
5. `supportConditions` full-scope business/industry/startup 승격

## 지금 운영 기준으로 열린 것

### 1. public discovery filter

현재 `/api/policies`, `/api/policies/search`, `/policies` 에서 실제로 열린 축은 아래 셋입니다.

1. `gov24ServiceField`
2. `gov24UserType`
3. `gov24BenefitType`

이 셋은 모두 canonical term 우선, legacy raw summary fallback 계약을 유지합니다.

### 2. recommendation scoring

recommendation 은 이제 `Gov24` taxonomy를 **soft additive bonus** 로만 소비합니다.

- `serviceField`: small category-aligned bonus
- `benefitType`: 일부 managed token만 small bonus
- `userType`: `개인`, `가구` 정도의 tiny audience bonus

즉 recommendation 에서도 `Gov24` 3축을 전혀 안 쓰는 상태는 끝났지만,
이 값이 hard matcher, hard eligibility, hard gate로 승격된 것은 아닙니다.

## 지금 일부러 안 연 것

아래는 현재 명시적으로 안 연 상태입니다.

### 1. matcher hard condition

`gov24ServiceFieldLabel`, `gov24UserTypeTokens`, `gov24BenefitTypeTokens` 만으로
`HOUSING`, `FINANCE` 같은 hard matcher를 열지 않습니다.

### 2. `Gov24 -> YOUTH_MID` bridge

`Gov24` canonical term이 recommendation projection에 들어와도
`youthMajorLabel`, `youthMidLabel` 은 자동으로 채우지 않습니다.

즉 `주거·자립`, `개인`, `현금(융자)` 같은 조합이 있어도
별도 source-of-truth 없이 `YOUTH_MID` 쪽으로 bridge 하지 않습니다.

### 3. `service_facts` 승격

현재 `Gov24` 3축은 canonical term/projection/scoring 수준까지만 열려 있고,
`service_facts` 기반 hard structured fact로는 아직 승격하지 않습니다.

## 왜 여기서 멈췄는가

이 lane의 목적은 `Gov24` 값을 조금씩 제품 surface에 여는 것이었지,
한 번에 hard codebook, matcher, bridge, fact promotion까지 같이 여는 것이 아니었습니다.

여기서 더 가면 다음부터는 작은 bounded step이 아니라,
의도적으로 더 큰 제품/데이터 모델 결정을 여는 작업이 됩니다.

즉 지금 남은 것은 “구현 누락”보다 **명시 승인 전제의 deferred scope** 입니다.

## 다시 열 조건

아래 중 하나가 명시적으로 승인될 때만 reopen 하는 편이 맞습니다.

1. matcher hard condition
2. `service_facts` 승격
3. `Gov24 -> YOUTH_MID` bridge
4. stable code/import-backfill
5. `supportConditions` full-scope 승격

그 전까지는 현재 label-first canonical + public filter + soft scoring 경계를 유지합니다.

## 지금 먼저 볼 문서

1. 현재 구현 계약: [policy-normalization-current-state.md](./policy-normalization-current-state.md)
2. blocked/deferred 경계: [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
3. reopen 판단 절차: [policy-gov24-reopen-checklist.md](./policy-gov24-reopen-checklist.md)

## 요약

1. `Gov24` public filter 3축은 이미 운영 반영까지 닫혔습니다.
2. recommendation scoring도 bounded soft additive bonus까지는 열렸습니다.
3. hard matcher, `service_facts`, `YOUTH_MID bridge`, stable import/backfill 은 계속 deferred 입니다.
4. 따라서 현재 `Gov24` lane의 기본 해석은 **closeout 유지, reopen 없음** 입니다.
