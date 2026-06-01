# recommendation signal gap audit runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `2736` 류 local 청년 정책군처럼

- retrieval 안에는 들어오는데
- 경쟁 후보보다 final rank 가 약하고
- 어떤 structured signal 이 부족한지 먼저 좁혀야 할 때

쓰는 bounded audit runbook 입니다.

현재 기본 용도는
`인천 지역 청년 일자리/생활지원` 정책군 signal gap 확인입니다.

## 언제 이 문서를 쓰나

아래 상황이면 이 runbook 이 맞습니다.

1. recommendation reopen lane 이 `local 신호 구조화` 로 좁혀졌을 때
2. 특정 local 정책군이 latest saved batch 에는 보이지만 rank 가 약할 때
3. `diversity/balancing` 이나 `direct tuning` 으로 가기 전에
   **무슨 신호가 빠졌는지** 를 먼저 확인하고 싶을 때

이 runbook 은 source 전체 일반론이 아니라
**정책군 단위** 로 읽습니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-signal-gap-audit.sh
```

기본값:

- `TARGET_SERVICE_IDS_CSV` 를 비우면 current latest batch user context에서
  region-matched local 청년/생활지원 family를 자동 선택
  (`BOKJIRO_LOCAL` 이 있으면 우선 사용하고, 현재 Gov24 중심 데이터셋처럼 없으면 `GOV24/YOUTH` 지역 청년 후보를 사용)
- `TOP_COMPETITOR_LIMIT=3`

즉 기본 실행만 해도

- current local context에 맞는 target family
- 같은 user latest batch 상위 경쟁 후보

를 같이 읽습니다.

## 주요 입력

- `TARGET_USER_KEY`
  - 비워 두면 latest recommendation batch 사용자
- `TARGET_SERVICE_IDS_CSV`
  - target family service id 목록
- `TOP_COMPETITOR_LIMIT`
  - latest batch 에서 같이 비교할 상위 경쟁 후보 수
- `SKIP_DIAGNOSTICS=true`
  - admin diagnostics 없이 DB evidence 만 볼 때
- `KEEP_ARTIFACTS=true`
  - 산출물 파일 유지

예:

```bash
TARGET_USER_KEY='<user-key>' \
TARGET_SERVICE_IDS_CSV='<service-id csv>' \
TOP_COMPETITOR_LIMIT=3 \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-signal-gap-audit.sh
```

## 출력

이 스크립트는 크게 두 종류 evidence를 같이 냅니다.

### 1. DB evidence

- `user_context`
- `latest_batch_focus_rows`

`latest_batch_focus_rows` 는 각 서비스마다 아래를 같이 담습니다.

- target family 인지 competitor 인지
- `final_rank`, `rule_rank`
- `source_type`, `unified_category`
- `rule_weighted_score`, `ai_score`, `final_score`, `ai_status`
- `search_youth_relevant`
- `min/max age`, `min/max income`
- `youth_major`, `youth_mid`, `provision_method`
- `gov24 summary labels`
- `interest themes`, `keyword tags`, `target groups`, `life stages`
- `fact_summary`
- `raw_tags`
- `summary_excerpt`

### 2. diagnostics evidence

admin diagnostics 가 가능하면 아래도 같이 냅니다.

- `dropStage`
- `latestSavedRank`
- `latestSavedFinalScore`
- `latestSavedAiScore`
- `latestSavedAiStatus`
- `ruleWeightedScore`
- `rerankCurrentRank`
- `rerankCurrentFinalScore`
- Gov24 token
- YOUTH official labels

`DIAG ...` 한 줄이 서비스 하나입니다.

## 읽는 법

### 1. target family 가 latest batch 에 실제로 있는가

먼저 `latest_batch_focus_rows` 와 `DIAG` 에서
target family 서비스가 실제로 있는지 봅니다.

- saved batch 에 없으면 retrieval/후속 filter 문제일 수 있습니다
- saved batch 에 있으면 다음은 signal 비교입니다

### 2. competitor 와 신호 차이를 본다

같은 user latest batch 안에서

- competitor 는 어떤 `interest_theme`, `target_group`, `fact_summary`
  를 갖는지
- target family 는 어떤 direct signal 이 비어 있는지

를 나란히 봅니다.

현재 권장 질문은 이 세 가지입니다.

1. `interest/theme` 구조가 약한가
2. 지역 적합성 설명 신호가 약한가
3. direct benefit / program type 신호가 약한가

### 3. 먼저 결론을 크게 일반화하지 않는다

이 runbook 결과로 바로

- source bonus
- global weight patch
- balancing

으로 가지 않습니다.

먼저 정책군 단위로
“무슨 structured signal 이 부족한가”만 적습니다.

## 현재 기본 해석

`2736` 류 local 정책군은 현재 기준으로

- retrieval miss bug 보다는
- direct `interest/theme/benefit` 신호 부족

을 먼저 의심하는 편이 맞습니다.

즉 이 runbook 의 목적은
“추천이 망가졌는가”가 아니라
“local 신호 구조화 과제가 무엇인가”를 좁히는 것입니다.

## 다음 액션으로 남길 최소 기록

1. user key
2. target family service ids
3. 같이 비교한 competitor ids
4. target family 공통 부족 신호
5. competitor 에는 있고 target 에는 약한 신호
6. 이번 단계에서 안 건드리는 것

## 관련 문서

1. [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
2. [recommendation-next-lane-brief.md](./recommendation-next-lane-brief.md)
3. [recommendation-current-state.md](./recommendation-current-state.md)

## 요약

1. 이 runbook 은 `2736` 류 local 정책군의 signal gap 을 좁히는 용도입니다.
2. 기본 스크립트는 target family와 latest batch competitor 를 같이 읽습니다.
3. 결과는 source 전체 일반론이 아니라 정책군 단위 local signal 구조화 과제로 연결합니다.
