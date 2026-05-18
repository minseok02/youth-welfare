# recommendation next lane brief

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 현재 recommendation 트랙을
다시 열기로 결정한다면,
**무엇부터 여는 편이 맞는지** 를 한 장으로 고정하는 brief 입니다.

현재 기준 권장안은:

- `lane 1. local 신호 구조화`

입니다.

즉 다음 recommendation reopen 은
`diversity/balancing` 이나 `direct tuning` 보다 먼저
**local 청년 정책군의 direct signal 구조화** 로 시작하는 편이 맞습니다.

## 현재 권장 결론

`2026-05-18` 기준으로 recommendation 을 다시 열면,
첫 reopen lane 은 아래로 둡니다.

1. `lane 1. local 신호 구조화`
2. 그다음 필요하면 `lane 2. diversity/balancing`
3. `lane 3. direct ranking/weight/prompt tuning` 은 마지막

즉 current recommendation next lane 은
**global tuning 이 아니라 local signal structuring** 입니다.

## 왜 lane 1 이 먼저인가

현재 evidence 는 대체로 아래를 말합니다.

1. 새 재현 가능한 recommendation bugfix는 이미 닫혔다.
2. 인천 local 후보는 retrieval 자체에서 빠지던 경계가 이미 줄었다.
3. `2736` 류 후보는 이제 retrieval 안에는 들어오지만,
   경쟁 후보보다 direct `interest/theme/benefit` 신호가 약하게 읽힌다.
4. 따라서 남은 문제는 “추천이 고장났다”보다
   “local 청년 정책군 신호를 더 구조화하고 싶은가”에 가깝다.

즉 현재 병목은

- priority 미반영 bug
- retrieval miss bug

보다

- local 정책군의 설명 신호 부족

으로 읽는 편이 맞습니다.

## 지금 바로 lane 2 로 가지 않는 이유

`diversity/balancing` 은 여전히 유효한 다음 후보지만,
현재 local 사례는 source 전체 집중 문제보다
**개별 정책군 direct signal 부족** 으로 설명되는 비중이 더 큽니다.

즉 아래 질문부터 먼저 푸는 편이 맞습니다.

- `2736` 류 local 청년 정책군에
  `interest/theme`, 지역 적합성, direct benefit signal 을 더 줄 것인가

이걸 풀기 전에 바로

- source/category balancing
- diversity penalty
- fallback 분산 규칙

으로 가면 문제를 너무 크게 일반화할 수 있습니다.

## 지금 바로 lane 3 로 가지 않는 이유

현재 단계에서 direct tuning 을 미루는 이유는 단순합니다.

1. `REAL_USER` gate 와 recommendation review gate는 reopen 전제일 뿐,
   곧바로 global tuning 근거는 아니다.
2. local 정책군 문제를 더 좁게 설명할 수 있는데,
   바로 weight patch 로 가면 과하다.
3. admin facet, detail read-only, card badge까지는 이미 붙어 있어
   먼저 신호 구조화를 시도할 수 있다.

즉 direct tuning 은
**더 좁은 lane 으로 안 풀린다는 증거가 남을 때만** 갑니다.

## 현재 대상으로 보는 정책군

현재 권장 해석은 source 전체가 아니라 **정책군 단위** 입니다.

### 1. 인천 지역 청년 일자리/생활지원 계열

대표 사례:

- `2736`
- 인천 local `BOKJIRO_LOCAL / 기타` 근접 후보군

이 정책군은

- 지역/청년 맥락은 있으나
- 경쟁 후보보다 direct `interest/theme/benefit` 신호가 약한지

를 먼저 봅니다.

### 2. 필요 시 bounded family 단위 확장

다음 가족군은 source 전체 일반론이 아니라
bounded family 단위로만 확장합니다.

- 주거 / 월세보증
- 지역 장학금
- 창업 / 소상공인

이 분류는 [gov24-recommendation-audit-runbook.md](./gov24-recommendation-audit-runbook.md)
와 같은 “정책군 단위 읽기” 원칙과 맞춥니다.

## lane 1 에서 먼저 열 일

1. local 청년 정책군의 `interest/theme` 구조화 강화
2. 지역 적합성 신호 재정리
3. direct benefit / program type 신호를 더 구조화할지 검토
4. 먼저 [recommendation-signal-gap-audit-runbook.md](./recommendation-signal-gap-audit-runbook.md)
   와 `run-local-recommendation-signal-gap-audit.sh` 로
   target family와 latest batch competitor 의 signal gap 을 고정
5. 필요하면 admin/read-only 관찰 경계부터 먼저 추가

현재 첫 concrete scope는 아래로 좁힙니다.

1. `BOKJIRO_LOCAL` summary/provision text에서 `주거`, `생활지원`, `보호·돌봄`, `교육`
   같은 derived `INTEREST_THEME` 를 additive 하게 생성
2. 같은 입력에서 `주거지원`, `월세보증금`, `생활안정자금`, `융자`, `바우처`, `돌봄서비스`
   같은 program `KEYWORD` 를 additive 하게 생성
3. exact-region `BOKJIRO_LOCAL` retrieval 안에서는
   `searchYouthRelevant=true`, `unifiedCategory!=기타` 후보를 pure recency보다 먼저 보게 좁게 정렬

즉 lane 1 의 첫 구현은 global tuning 이 아니라
**`BOKJIRO_LOCAL` structured signal + bounded region-ordering** 입니다.

이 첫 구현을 서버 bounded audit로 다시 확인하면 `3257/3281` 의 `category/theme/keyword` 구조화는 실제로 살아났지만, same-user latest batch 기준으로는 여전히 target family가 전부 `NOT_IN_SQL_RETRIEVAL` 로 남았습니다. 현재 남은 공통 병목은 broad mixed life stage 때문에 `searchYouthRelevant=false` 인 local 후보들이 exact-region ordering 안에서도 뒤로 밀리는 점입니다.

그래서 lane 1 의 다음 concrete scope는 아래 한 줄로 더 좁힙니다.

4. `BOKJIRO_LOCAL + 청년 포함 life stage + structured local support signal` 조합에만 bounded `searchYouthRelevant` bridge를 열어 `3257` 같은 exact-region 후보가 retrieval 안으로 먼저 들어오게 보기

즉 여전히 broad local 전체를 youth로 푸는 것이 아니라, **청년 life stage를 이미 갖고 있고 local structured signal이 생긴 후보만 retrieval gate를 넘기는 bounded bridge** 로 읽습니다.

이 bridge를 서버에 다시 반영하면 `3257/3281/3575/3714` 의 `searchYouthRelevant=true` 자체는 실제 DB에 기록됩니다. 다만 같은 bounded audit family는 여전히 전부 `NOT_IN_SQL_RETRIEVAL` 이고 retrieval/saved batch 안으로 새로 들어온 target도 없었습니다. 따라서 lane 1 의 다음 concrete scope는 score patch가 아니라 아래로 다시 좁혀집니다.

5. same-user exact-region candidate query 안에서 이 family가 150-row window 바깥에 남는 이유를 region projection / ordering branch / candidate window 관점에서 직접 audit 하기

이 단계의 기본 wrapper는
[recommendation-region-window-audit-runbook.md](./recommendation-region-window-audit-runbook.md)
와 `run-local-recommendation-region-window-audit.sh` 로 고정합니다.

첫 bounded fix는 `REGION_CODE` branch 필터 자체에 `BOKJIRO_LOCAL + same-sido + searchYouthRelevant=true + unifiedCategory!=기타` fallback tier를 넣는 것입니다. 목적은 exact-region code가 비어 `EXACT_SIDO` 로만 잡히는 local youth-support 후보를 branch 안으로 편입시키는 것이지, broad same-sido 정책 전반을 여는 것이 아닙니다.

이 fix를 서버에 다시 태운 뒤 결과는 이렇게 읽습니다.

1. `3257/3281` 은 branch 바깥에서 branch 안으로 이동했다.
2. base retrieval 에서는 `3257=8위`, `3281=18위` 로 이미 `150-window` 안이다.
3. latest retrieval 에서는 `3257=28위`, `3281=24위` 로 아직 `20-window` 밖이다.

즉 다음 bounded step은 다시 youth relevance 나 region inclusion이 아니라, **latest 20 window ordering / latest fetch size 경계 안에서 same-sido local 후보가 왜 밀리는지** 를 좁히는 것이다.

이 단계의 기본 wrapper는 [recommendation-latest-window-audit-runbook.md](./recommendation-latest-window-audit-runbook.md) 와 `run-local-recommendation-latest-window-audit.sh` 로 고정합니다.

그리고 latest evidence만으로 바로 ordering patch로 가지 않고, 같은 family를 `run-local-recommendation-pipeline-lane-audit.sh` 에 넣어 실제 `merged -> saved` 경계까지 같이 읽는 편이 맞습니다. 현재 이 wrapper는 `pass_base/pass_latest` 를 actual predicate pass 로, `retain_base/retain_latest` 를 base/latest top window 잔존 여부로 따로 보여 줍니다. 즉 다음 질문은 더 이상 “youth/age가 맞나”가 아니라, **`3257/3281` 이 predicate는 통과했는데 `base 50 / latest 5` window에서 밀리는지** 입니다.

## lane 1 에서 아직 안 할 일

1. global source bonus
2. direct weight patch
3. AI prompt 대규모 변경
4. public filter/scoring 즉시 승격

## 최소 실험 범위

lane 1 reopen 을 실제로 승인하면
최소 범위는 아래로 둡니다.

1. 정책군 범위 명시
   - 예: `인천 지역 청년 일자리/생활지원`
2. 어떤 신호를 더 구조화하는지 명시
   - `interest/theme`
   - 지역 적합성
   - direct benefit / program type
3. 비교 baseline 명시
   - latest batch
   - fresh batch
   - top competitor 비교
4. 이번 단계에서 안 건드리는 것 명시
   - global weight
   - source balancing
   - scoring/ranking direct patch

## 다음 문서 순서

1. [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)
2. [recommendation-reopen-decision-runbook.md](./recommendation-reopen-decision-runbook.md)
3. 이 brief
4. 필요하면 개별 audit runbook

## 요약

1. 현재 recommendation next lane 은 `lane 1. local 신호 구조화` 입니다.
2. `2736` 류 사례는 source 전체가 아니라 정책군 단위로 읽습니다.
3. `diversity/balancing` 은 다음 후보이고, `direct tuning` 은 마지막입니다.
