# recommendation latest window audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `same-sido fallback` 까지 반영한 뒤에도 local target family가

- latest retrieval 에서는 `present=true`
- 그런데 `20-window` 밖(`24위`, `28위`, `33위` 등)

으로 남을 때,

- top 20 안을 채우는 row가 누구인지
- `21~40위` blocker row가 누구인지
- target family가 현재 어떤 tier에서 밀리는지

를 same-user 기준으로 읽는 bounded audit runbook 입니다.

즉 이 문서는 branch inclusion audit 다음 단계의 **latest ordering/window audit** 입니다.

## 언제 이 문서를 쓰나

아래 상황이면 이 runbook 이 맞습니다.

1. `region-window audit` 에서 target family가 `present=true` 로 branch 안에 들어왔다
2. base retrieval `150-window` 안에는 들어온다
3. latest retrieval `20-window` 에서는 계속 밖이다

즉 현재 질문이

- “branch 밖인가?”
- “searchYouthRelevant 가 false 인가?”

가 아니라

**“왜 latest top 20 안에 못 들어오나?”**

일 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-latest-window-audit.sh
```

기본값:

- `TARGET_SERVICE_IDS_CSV=2736,3257,3281,3575,3714`
- `TOP_COMPETITOR_LIMIT=3`
- `LATEST_FETCH_SIZE=20`
- `BLOCKER_WINDOW_LIMIT=40`

즉 기본 실행만 해도

- target family latest rank
- top latest 20
- blocker 21~40

를 같이 읽습니다.

## 주요 입력

- `TARGET_USER_KEY`
  - 비워 두면 latest recommendation batch 사용자
- `TARGET_SERVICE_IDS_CSV`
  - target family service id 목록
- `TOP_COMPETITOR_LIMIT`
  - latest batch 상위 competitor 개수
- `LATEST_FETCH_SIZE`
  - latest retrieval window 크기. 기본 `20`
- `BLOCKER_WINDOW_LIMIT`
  - blocker rows 최대 rank. 기본 `40`
- `KEEP_ARTIFACTS=true`
  - JSONL artifact 유지

## 출력

### 1. metric

- `target_user_key`
- `target_service_ids_csv`
- `top_competitor_ids_csv`
- `query_context`
  - `branch_mode:region_code:sido:age:income_level:latest_fetch_size:blocker_window_limit`
- `candidate_counts`
  - latest query 전체 후보 수

### 2. target latest

`[TARGET LATEST]` 에서 각 target family 서비스마다 아래를 봅니다.

- `present`
- `rank`
- `in_window`
- `projection`
- `tiers=region/youth/category`
- `searchYouthRelevant`
- `category`

### 3. top latest

`[TOP LATEST]` 는 actual latest query 상위 `20` 건입니다.

### 4. blocker latest

`[BLOCKER LATEST]` 는 `21~40위` 행입니다.

여기서

- target family가 실제로 몇 위에 박혀 있는지
- target 바로 앞 blocker 들이 누구인지
- `target`, `competitor` 플래그

를 봅니다.

## tier 해석

`tiers=region/youth/category` 는 latest query order-by 의 3단 tier를 그대로 드러냅니다.

예:

- `0/0/0`
  - exact-region local
  - youth relevant
  - non-기타
- `1/1/1`
  - same-sido bounded fallback
  - youth relevant
  - non-기타
- `3/3/3`
  - 기타/nationwide/약한 row

즉 target이 `1/1/1` 인데 top 20 대부분도 `1/1/1` 이면
문제는 inclusion 이 아니라 **같은 tier 안 recency 경쟁** 입니다.

## 관련 문서

1. [recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md)
2. [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)
3. [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)

## 요약

1. 이 문서는 latest query `20-window` 바깥 blocker를 읽는 audit 입니다.
2. branch inclusion 다음 병목이 latest ordering/window 인지 좁힐 때 씁니다.
3. 결과는 direct scoring patch 가 아니라 `latest ordering` 또는 `latest fetch size` 과제로 이어집니다.
