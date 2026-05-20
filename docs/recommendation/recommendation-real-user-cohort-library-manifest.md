# recommendation real-user cohort library manifest

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-review-gate-blocker-audit-runbook.md](./recommendation-review-gate-blocker-audit-runbook.md)

## 목적

이 문서는 local generic-domain signup 기반 `REAL_USER` 표본을 **한 번 만들고 여러 검증에 재사용** 하기 위한 cohort library 설계를 고정합니다.

핵심은 두 가지입니다.

1. 매번 랜덤 계정을 새로 만들지 않기
2. 검증 목적별로 의도된 persona 묶음을 재사용하기

## 기본 원칙

- 이메일 도메인은 `realuser.app`
  - `account_origin=REAL_USER` 로 분류되게 유지합니다.
- 계정은 고정 이메일과 고정 `name` 을 사용합니다.
  - rerun 때는 새 계정을 만들지 않고 같은 계정을 재사용합니다.
- cohort는 4개로 나눕니다.
  - `housing`
  - `education`
  - `job`
  - `finance`

## 현재 라이브러리

- 기존 distributed baseline sample: `30명`
  - broad gate/readiness/evidence 확인용
- targeted cohort library: `40명`
  - 각 cohort `10명`

즉 총량은 `70명` 내외로 유지하는 편을 기본값으로 봅니다.

## cohort 역할

### 1. `housing`

- 월세/주거/이사비/보증금 성격 신호를 더 많이 보려는 cohort
- mixed leader가 `청년월세 지원사업` 같은 housing service일 때 우선 확인합니다.

### 2. `education`

- 대학생/대학원생/교육비/학자금/장학금 성격 신호 확인용
- `STUDENT_AUDIENCE_MISMATCH` 나 교육 계열 경쟁 서비스 분포를 볼 때 씁니다.

### 3. `job`

- 구직/취업/인턴/정장대여/활동비 성격 신호 확인용
- `BOKJIRO_LOCAL` 일자리/취업 계열 top1 분산을 볼 때 씁니다.

### 4. `finance`

- 저소득/취약/생활지원/통장/융자/장학금 성격 신호 확인용
- `INCOME_MISMATCH` 와 금융·생활지원 계열 반응을 볼 때 씁니다.

## seed entrypoint

전체 library seed:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-real-user-cohort-library-seed.sh
```

특정 cohort만:

```bash
COHORT_FILTER='housing' \
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-real-user-cohort-library-seed.sh
```

복수 cohort만:

```bash
COHORT_FILTER='housing,job' \
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-real-user-cohort-library-seed.sh
```

artifact:

- `tmp/recommendation-real-user-cohort-library-seed/latest-cohort-library-summary.txt`
- `tmp/recommendation-real-user-cohort-library-seed/latest-seeded-users.tsv`
- `tmp/recommendation-real-user-cohort-library-seed/latest-cohort-profiles.tsv`

## 현재 targeted profiles

`run-local-real-user-cohort-library-seed.sh` 안의 고정 manifest를 source of truth로 봅니다.

- `housing`: `realuser.housing01@realuser.app` ... `realuser.housing10@realuser.app`
- `education`: `realuser.edu01@realuser.app` ... `realuser.edu10@realuser.app`
- `job`: `realuser.job01@realuser.app` ... `realuser.job10@realuser.app`
- `finance`: `realuser.finance01@realuser.app` ... `realuser.finance10@realuser.app`

## 해석 규칙

- cohort library는 랜덤 표본이 아니라 **설계된 재사용 표본** 입니다.
- 새로운 검증이 필요할 때는 먼저 기존 cohort를 재사용합니다.
- 새 계정을 만들기 전에 아래를 먼저 확인합니다.
  1. 현재 질문이 `housing / education / job / finance` 중 어디에 가까운가
  2. 기존 cohort로 충분히 signal이 안 보이는가
  3. 그래도 부족할 때만 해당 축의 targeted profile을 추가한다

## 현재 local 해석

- distributed `REAL_USER` 30명만으로는 mixed leader `2622(청년월세 지원사업)` 가 real-user `top10` 안에도 없었습니다.
- 그래서 다음 실질 step은 무작정 random 100명을 늘리는 것보다
  - `housing` targeted cohort를 포함한 cohort library를 미리 확보하고
  - 그 위에서 mixed leader transition path를 다시 보는 쪽이 맞습니다.
