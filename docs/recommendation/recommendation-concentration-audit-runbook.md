# recommendation concentration audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 CTR readiness와 별도로, **최신 저장 추천 배치 자체가 특정 서비스/출처/카테고리로 과하게 몰리는지** 확인하기 위한 audit runbook 입니다.

즉 이 wrapper는 아래를 봅니다.

- 최신 `user_recommendations` 배치가 몇 명/몇 서비스에 걸쳐 있는지
- top1 추천이 특정 서비스에 과하게 몰리는지
- `HAS_PRIORITY` / `NO_PRIORITY` 사용자가 어떻게 다른지
- 우선순위가 아예 안 먹는지, 아니면 먹고도 여전히 공통 정책으로 수렴하는지

## 실행

```bash
bash deploy/smoke/run-local-recommendation-concentration-audit.sh
```

실사용 cohort 기준으로 별도 해석하려면 아래도 같이 봅니다.

```bash
USER_COHORT=real_non_example bash deploy/smoke/run-local-recommendation-concentration-audit.sh
```

이 스크립트는 현재 로컬 DB의

- `user_recommendations`
- `user_priorities`
- `priority_options`
- `welfare_services`

를 직접 읽습니다.

CTR readiness wrapper와 달리, 이 스크립트는 **클릭 로그가 아니라 저장 추천 결과 자체의 집중도**를 봅니다.
기본값 `USER_COHORT=all` 은 전체 latest batch를 읽고, `example`, `bounded_local`, `real_non_example` 으로 cohort를 좁혀 같은 batch를 다시 해석할 수 있습니다. legacy 호환용 `non_example` 은 `bounded_local + real_non_example` 합산입니다.

## 출력 항목

- `latest_batch_rows`
- `audit_user_cohort`
- `latest_batch_users`
- `latest_batch_example_users`
- `latest_batch_bounded_local_users`
- `latest_batch_real_non_example_users`
- `latest_batch_non_example_users`
- `latest_batch_distinct_services`
- `latest_batch_priority_users`
- `latest_batch_no_priority_users`
- `top1_leader_service_id`
- `top1_leader_title`
- `top1_leader_source`
- `top1_leader_category`
- `top1_leader_users`
- `top1_leader_share_pct`
- `avg_recommendations_per_user`
- `avg_distinct_services_per_user`
- `avg_distinct_categories_per_user`
- `avg_distinct_sources_per_user`
- `[latest_source_distribution]`
- `[latest_category_distribution]`
- `[top_repeated_services]`
- `[top1_services]`
- `[top1_by_priority_state]`
- `[priority_profile_counts]`
- `[priority_profile_top1]`
- `[concentration_readiness]`
- `[signal_quality]`

포맷:

- `[latest_source_distribution]`

```text
source_type|row_count|distinct_users
```

- `[latest_category_distribution]`

```text
category|row_count|distinct_users
```

- `[top_repeated_services]`

```text
service_id|title|source_type|category|row_count|distinct_users
```

- `[top1_services]`

```text
service_id|title|source_type|category|users_as_top1
```

- `[top1_by_priority_state]`

```text
HAS_PRIORITY|title|source_type|category|users
NO_PRIORITY|title|source_type|category|users
```

- `[priority_profile_counts]`

```text
profile|users
```

- `[priority_profile_top1]`

```text
profile|title|source_type|category|users
```

## 현재 기준선 (2026-05-17, signal quality 출력 추가 후 local audit 기준)

```text
latest_batch_rows=4059
latest_batch_users=452
latest_batch_example_users=451
latest_batch_bounded_local_users=1
latest_batch_real_non_example_users=0
latest_batch_non_example_users=1
latest_batch_distinct_services=113
latest_batch_priority_users=38
latest_batch_no_priority_users=414
top1_leader_service_id=2622
top1_leader_title=청년월세 지원사업
top1_leader_source=BOKJIRO_CENTRAL
top1_leader_category=주거
top1_leader_users=269
top1_leader_share_pct=59.51
avg_recommendations_per_user=8.98
avg_distinct_services_per_user=8.98
avg_distinct_categories_per_user=3.39
avg_distinct_sources_per_user=3.17
```

latest source distribution:

```text
YOUTH|1446|117
GOV24|944|418
BOKJIRO_CENTRAL|894|447
BOKJIRO_LOCAL|775|449
```

latest category distribution:

```text
금융·생활지원|1343|450
주거|1073|452
교육·직업훈련|690|452
일자리|666|62
참여·기회|188|36
기타|59|59
문화·여가|36|18
건강·의료|4|4
```

top repeated services:

```text
2622|청년월세 지원사업|BOKJIRO_CENTRAL|주거|447|447
5728|주택금융공사 월세자금보증|GOV24|주거|418|418
6790|지역인재육성을 위한 장학금 지원|GOV24|교육·직업훈련|418|418
2571|청년내일저축계좌|BOKJIRO_CENTRAL|금융·생활지원|390|390
3689|인천 재직청년 복지포인트|BOKJIRO_LOCAL|금융·생활지원|354|354
3605|나만의 결혼식 지원|BOKJIRO_LOCAL|금융·생활지원|140|140
3611|청년 웰컴페이(이사비) 지원사업|BOKJIRO_LOCAL|금융·생활지원|140|140
891|(국토부) 26년 청년월세 지원사업|YOUTH|주거|73|73
969|에너지차세대리더육성|YOUTH|교육·직업훈련|60|60
978|중남미 지역기구 인턴 파견|YOUTH|일자리|60|60
979|지방청년인재 재외공관 파견|YOUTH|일자리|60|60
985|산림산업 창업지원_청년 산림창업 마중물 지원|YOUTH|일자리|60|60
2589|우수학생 국가장학금 지원|BOKJIRO_CENTRAL|금융·생활지원|55|55
3688|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|54|54
1416|기초청년 주거급여(임차급여)|YOUTH|주거|60|60
```

top1 by priority state:

```text
NO_PRIORITY|청년월세 지원사업|BOKJIRO_CENTRAL|주거|259
NO_PRIORITY|청년내일저축계좌|BOKJIRO_CENTRAL|금융·생활지원|107
NO_PRIORITY|청년 웰컴페이(이사비) 지원사업|BOKJIRO_LOCAL|금융·생활지원|39
HAS_PRIORITY|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|12
HAS_PRIORITY|지역인재육성을 위한 장학금 지원|GOV24|교육·직업훈련|11
HAS_PRIORITY|청년월세 지원사업|BOKJIRO_CENTRAL|주거|10
HAS_PRIORITY|청년월세 지원사업|YOUTH|주거|5
NO_PRIORITY|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|5
```

priority profile counts:

```text
EDUCATION>JOB|19
HOUSING|8
HOUSING>EDUCATION>JOB|5
EDUCATION|4
HOUSING>JOB|1
HOUSING>JOB>EDUCATION>FINANCE>DEADLINE|1
```

priority profile top1:

```text
EDUCATION>JOB|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|12
HOUSING|청년월세 지원사업|BOKJIRO_CENTRAL|주거|8
EDUCATION>JOB|지역인재육성을 위한 장학금 지원|GOV24|교육·직업훈련|7
HOUSING>EDUCATION>JOB|청년월세 지원사업|YOUTH|주거|5
EDUCATION|지역인재육성을 위한 장학금 지원|GOV24|교육·직업훈련|4
```

concentration readiness:

```text
CONCENTRATED_TOP1
```

signal quality:

```text
LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH
```

같은 시점 `USER_COHORT=bounded_local` rerun:

```text
latest_batch_rows=6
latest_batch_users=1
CONCENTRATED_TOP1
BOUNDED_LOCAL_ONLY_COHORT
```

같은 시점 `USER_COHORT=local_real_non_example_seed` rerun:

```text
latest_batch_rows=12
latest_batch_users=2
CONCENTRATED_TOP1
LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT
```

같은 시점 `USER_COHORT=real_user` rerun:

```text
latest_batch_rows=0
latest_batch_users=0
DEFERRED_EMPTY_COHORT
EMPTY_REAL_USER_COHORT
```

해석:

- `SYNTHETIC_ONLY_LATEST_BATCH`: latest batch 사용자가 전부 `@example.com` smoke/validation 계정이다.
- `BOUNDED_LOCAL_WITH_SYNTHETIC_BATCH`: `REAL_NON_EXAMPLE` 도 `REAL_USER` 도 없이 `EXAMPLE + BOUNDED_LOCAL` 만 섞여 있다.
- `LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH`: `LOCAL_REAL_NON_EXAMPLE_SEED` 와 `EXAMPLE/BOUNDED_LOCAL` 이 섞여 있다.
- `MIXED_WITH_NON_REAL_BATCH`: `REAL_USER` 와 `EXAMPLE/BOUNDED_LOCAL/LOCAL_REAL_NON_EXAMPLE_SEED` 가 섞여 있다.
- `LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_BATCH`: latest batch가 local seed non-example 계정만으로 구성된다.
- `REAL_USER_ONLY_BATCH`: latest batch가 `REAL_USER` 만으로 구성된다.
- `EMPTY_REAL_NON_EXAMPLE_COHORT`: `USER_COHORT=real_non_example` 로 다시 봤을 때 latest batch에 실사용 계정이 없다.
- `EMPTY_REAL_USER_COHORT`: `USER_COHORT=real_user` 로 다시 봤을 때 latest batch에 실사용 계정이 없다.
- `EXAMPLE_ONLY_COHORT`: `USER_COHORT=example` 진단 모드 결과다.
- `BOUNDED_LOCAL_ONLY_COHORT`: `USER_COHORT=bounded_local` 진단 모드 결과다.
- `LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT`: `USER_COHORT=local_real_non_example_seed` 진단 모드 결과다.

## 해석 기준

### 1. 우선순위가 아예 안 먹는 경우

아래 신호가 함께 보이면 우선순위 미반영 가능성을 의심합니다.

- `HAS_PRIORITY` / `NO_PRIORITY` 의 top1 leader가 사실상 동일
- `priority_profile_top1` 도 profile 차이 없이 같은 서비스만 반복
- latest category/source 분포가 profile 구분 없이 완전히 동일

### 2. 우선순위는 먹지만 여전히 결과가 쏠리는 경우

현재 local snapshot은 이 케이스에 가깝습니다.

- `HAS_PRIORITY` 와 `NO_PRIORITY` 의 top1 대표 서비스가 다름
- 일부 profile별 top1 차이도 보임
- 하지만 latest batch 전체로 보면 특정 서비스가 거의 모든 사용자에게 반복 노출
- top1 leader share가 `50%+` 이면 집중도가 높다고 봅니다

### 3. `NO_PRIORITY` 사용자 공통 정책 수렴

`NO_PRIORITY` 사용자가 `HAS_PRIORITY` 보다 훨씬 많고, top1이 공통 정책 몇 개에 몰리면

- priority 로직 자체보다
- no-priority fallback
- diversity penalty
- source/category balancing

쪽을 먼저 의심하는 것이 맞습니다.

## 현재 판단

`2026-05-17` local snapshot 기준 판단은:

1. 우선순위는 코드와 데이터에서 **일부 반영된다**
2. default `USER_COHORT=all` 기준 latest batch는 `latest_batch_bounded_local_users=1`, `latest_batch_local_real_non_example_seed_users=2`, `latest_batch_real_user_users=0`, `signal_quality=LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH` 이다
3. 같은 wrapper를 `USER_COHORT=bounded_local` 로 다시 태우면 현재 값은 `latest_batch_rows=6`, `concentration_readiness=CONCENTRATED_TOP1`, `signal_quality=BOUNDED_LOCAL_ONLY_COHORT` 이다
4. `USER_COHORT=local_real_non_example_seed` 로 다시 태우면 현재 값은 `latest_batch_rows=12`, `signal_quality=LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT` 이다
5. `USER_COHORT=real_user` 로 다시 태우면 현재 값은 `latest_batch_rows=0`, `signal_quality=EMPTY_REAL_USER_COHORT` 이다
6. 따라서 지금 병목은 “priority 미반영”보다 **synthetic-heavy baseline + local seed non-example만 존재 + real-user cohort 부재 + diversity / fallback / balancing 약함** 쪽이며, 상태는 계속 `CONCENTRATED_TOP1` 으로 본다

즉 지금 practical next step은

- 최신 완화 로직이 신규 사용자에선 실제로 먹는지 계속 확인하고
- 같은 wrapper로 전/후 비교를 유지하면서
- top1 leader share를 더 낮출 추가 조정이 필요한지 판단

입니다.

## 실행 후 남길 최소 기록

- wrapper 실행 시각
- `latest_batch_rows`, `latest_batch_users`, `latest_batch_distinct_services`
- `top1_leader_title`, `top1_leader_share_pct`
- `[latest_source_distribution]` 상위 4줄
- `[latest_category_distribution]` 상위 5줄
- `[top_repeated_services]` 상위 5줄
- `[top1_by_priority_state]` 상위 3~5줄
- `[priority_profile_top1]` 상위 3~5줄
- `concentration_readiness`
- baseline과 달라진 점 / 편중 완화 여부
