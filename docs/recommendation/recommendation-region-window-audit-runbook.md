# recommendation region window audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `searchYouthRelevant`, `category/theme/keyword` 구조화까지는 살아났는데도
특정 local 정책군이 계속 `NOT_IN_SQL_RETRIEVAL` 로 남을 때,

- 실제 retrieval branch가 `REGION_CODE` 인지 `SIDO` 인지
- target family가 query 안에는 들어오는지
- 들어오면 몇 위인지
- 150건/20건 window 밖으로 밀리는지

를 same-user 기준으로 읽는 bounded audit runbook 입니다.

## 언제 이 문서를 쓰나

아래 상황이면 이 runbook 이 맞습니다.

1. `signal gap audit` 에서 target family가 계속 `NOT_IN_SQL_RETRIEVAL`
2. `searchYouthRelevant=true` 로 바뀌었는데도 movement 가 없음
3. 다음 질문이 “점수를 더 줄까”가 아니라
   **`region/query window` 안에서 왜 계속 밀리나** 인 경우

즉 이 문서는 scoring audit 이 아니라 **retrieval branch/window audit** 입니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-region-window-audit.sh
```

기본값:

- `TARGET_SERVICE_IDS_CSV` 를 비우면 current latest batch user context에서
  region-matched local 청년/생활지원 family를 자동 선택
  (`BOKJIRO_LOCAL` 이 있으면 우선 사용하고, 현재 Gov24 중심 데이터셋처럼 없으면 `GOV24/YOUTH` 지역 청년 후보를 사용)
- `TOP_COMPETITOR_LIMIT=3`
- `BASE_FETCH_SIZE=150`
- `LATEST_FETCH_SIZE=20`
- `TOP_WINDOW_LIMIT=20`

즉 기본 실행만 해도

- target family
- latest batch 상위 competitor
- 실제 retrieval branch top window

를 같이 읽습니다.

## 주요 입력

- `TARGET_USER_KEY`
  - 비워 두면 latest recommendation batch 사용자
- `TARGET_SERVICE_IDS_CSV`
  - target family service id 목록
- `TOP_COMPETITOR_LIMIT`
  - latest batch 상위 competitor 개수
- `BASE_FETCH_SIZE`
  - base retrieval window 크기. 기본 `150`
- `LATEST_FETCH_SIZE`
  - latest retrieval window 크기. 기본 `20`
- `TOP_WINDOW_LIMIT`
  - 출력 시 보여 줄 상위 row 수. 기본 `20`
- `KEEP_ARTIFACTS=true`
  - JSONL artifact 유지

예:

```bash
TARGET_USER_KEY='<user-key>' \
TARGET_SERVICE_IDS_CSV='<service-id csv>' \
TOP_COMPETITOR_LIMIT=3 \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-region-window-audit.sh
```

## 출력

### 1. metric

- `target_user_key`
- `target_service_ids_csv`
- `top_competitor_ids_csv`
- `query_context`
  - `branch_mode:region_code:sido:age:income_level:base_fetch_size:latest_fetch_size`
- `candidate_counts`
  - branch filter 이후 base/latest 전체 후보 수

### 2. target rows

`[TARGET BASE]`, `[TARGET LATEST]` 에서 각 target family 서비스마다 아래를 봅니다.

- `present`
  - branch query 안에 실제로 들어오는지
- `rank`
  - query 전체 순위
- `in_window`
  - `150/20` window 안에 들어오는지
- `projection`
  - `EXACT_REGION`
  - `EXACT_SIDO`
  - `NATIONWIDE`
  - `REGION_MISMATCH`
- `searchYouthRelevant`
- `category`
- `lifeStage`

### 3. top rows

`[TOP BASE]`, `[TOP LATEST]` 는 actual retrieval branch 상위 `20` 건입니다.

여기서

- 어떤 source/category 가 앞을 차지하는지
- `EXACT_REGION` local row가 얼마나 많은지
- target family가 왜 뒤로 밀리는지

를 바로 봅니다.

## 읽는 법

### 1. `present=false` 인지 먼저 본다

`present=false` 면 branch filter 바깥입니다.

이 경우 질문은

- region projection 이 약한가
- exact region 이 아니라 `REGION_MISMATCH` 인가
- 애초에 전국 정책보다도 branch 안에 못 들어오는가

입니다.

### 2. `present=true` 인데 `in_window=false` 면 query window 문제다

이 경우는

- branch filter 에는 들어왔지만
- 앞에 더 많은 local/nationwide 후보가 있어서
- `150` 또는 `20` 바깥으로 밀린 상태

입니다.

즉 이 경우 다음 과제는 scoring 이 아니라

- ordering branch
- projection strength
- candidate window 크기

중 어디가 더 직접적인지 좁히는 것입니다.

### 3. `projection` 을 꼭 같이 본다

- `EXACT_REGION`
  - 같은 region code 직접 매칭
- `EXACT_SIDO`
  - region code는 안 맞지만 같은 시도
- `NATIONWIDE`
  - `service_regions` 가 없는 전국 정책
- `REGION_MISMATCH`
  - 지역이 있지만 현재 user region/sido 와 안 맞음

`searchYouthRelevant=true` 인데도 `REGION_MISMATCH` 면
문제는 youth gate가 아니라 region projection 입니다.

## 현재 기본 해석

현재 bounded local lane 에서 이 runbook 은 아래 상황을 겨냥합니다.

1. `BOKJIRO_LOCAL` signal structuring 성공
2. `searchYouthRelevant` bridge 성공
3. 그래도 target family가 `NOT_IN_SQL_RETRIEVAL`

즉 현재 다음 질문은
“청년성 신호가 약한가”보다
“같은 user의 actual region branch 안에서 이 family가 왜 150건 밖에 남나” 입니다.

## 관련 문서

1. [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)
2. [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
3. [recommendation-current-state.md](./recommendation-current-state.md)

## 요약

1. 이 문서는 same-user actual retrieval branch/window 를 읽는 audit 입니다.
2. `searchYouthRelevant` 다음 병목이 region/query window 인지 좁힐 때 씁니다.
3. 결과는 scoring patch 가 아니라 region projection / ordering / candidate window 과제로 이어집니다.
