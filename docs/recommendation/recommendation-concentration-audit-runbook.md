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

이 스크립트는 현재 로컬 DB의

- `user_recommendations`
- `user_priorities`
- `priority_options`
- `welfare_services`

를 직접 읽습니다.

CTR readiness wrapper와 달리, 이 스크립트는 **클릭 로그가 아니라 저장 추천 결과 자체의 집중도**를 봅니다.

## 출력 항목

- `latest_batch_rows`
- `latest_batch_users`
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

## 현재 기준선 (2026-05-15, no-priority retrieval/rerank diversification 이후 local snapshot)

```text
latest_batch_rows=2394
latest_batch_users=132
latest_batch_distinct_services=113
latest_batch_priority_users=20
latest_batch_no_priority_users=112
top1_leader_service_id=2622
top1_leader_title=청년월세 지원사업
top1_leader_source=BOKJIRO_CENTRAL
top1_leader_category=주거
top1_leader_users=75
top1_leader_share_pct=56.82
avg_recommendations_per_user=18.14
avg_distinct_services_per_user=18.14
avg_distinct_categories_per_user=4.33
avg_distinct_sources_per_user=3.15
```

latest source distribution:

```text
YOUTH|1352|62
BOKJIRO_LOCAL|427|129
BOKJIRO_CENTRAL|313|127
GOV24|302|98
```

latest category distribution:

```text
금융·생활지원|736|132
일자리|676|62
교육·직업훈련|374|132
주거|323|132
참여·기회|190|36
기타|59|59
문화·여가|36|18
```

top repeated services:

```text
2571|청년내일저축계좌|BOKJIRO_CENTRAL|금융·생활지원|127|127
2622|청년월세 지원사업|BOKJIRO_CENTRAL|주거|127|127
3605|나만의 결혼식 지원|BOKJIRO_LOCAL|금융·생활지원|124|124
3611|청년 웰컴페이(이사비) 지원사업|BOKJIRO_LOCAL|금융·생활지원|124|124
5728|주택금융공사 월세자금보증|GOV24|주거|98|98
6790|지역인재육성을 위한 장학금 지원|GOV24|교육·직업훈련|98|98
969|에너지차세대리더육성|YOUTH|교육·직업훈련|62|62
978|중남미 지역기구 인턴 파견|YOUTH|일자리|62|62
979|지방청년인재 재외공관 파견|YOUTH|일자리|62|62
985|산림산업 창업지원_청년 산림창업 마중물 지원|YOUTH|일자리|62|62
```

top1 by priority state:

```text
NO_PRIORITY|청년월세 지원사업|BOKJIRO_CENTRAL|주거|73
NO_PRIORITY|청년 웰컴페이(이사비) 지원사업|BOKJIRO_LOCAL|금융·생활지원|32
HAS_PRIORITY|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|12
HAS_PRIORITY|청년월세 지원사업|YOUTH|주거|5
NO_PRIORITY|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|5
```

priority profile counts:

```text
EDUCATION>JOB|25
HOUSING>EDUCATION>JOB|5
HOUSING>JOB|1
HOUSING>JOB>EDUCATION>FINANCE>DEADLINE|1
```

priority profile top1:

```text
EDUCATION>JOB|드림나래(인천청년 면접복장 지원)|BOKJIRO_LOCAL|기타|12
HOUSING>EDUCATION>JOB|청년월세 지원사업|YOUTH|주거|5
```

concentration readiness:

```text
CONCENTRATED_TOP1
```

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

`2026-05-15` local snapshot 기준 판단은:

1. 우선순위는 코드와 데이터에서 **일부 반영된다**
2. 최근 no-priority retrieval/rerank 보강 뒤, 신규 no-priority 사용자 표본에서는 `2622` 일변도에서 벗어나 `3611` 이 함께 top1로 올라온다
3. 하지만 저장 추천 결과 전체로 보면 여전히 **강하게 집중**되어 있고, top1 leader share도 아직 `56.82%` 수준이다
4. 따라서 지금 병목은 “priority 미반영”보다 **diversity / fallback / balancing 약함** 쪽이며, 상태는 계속 `CONCENTRATED_TOP1` 으로 본다

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
