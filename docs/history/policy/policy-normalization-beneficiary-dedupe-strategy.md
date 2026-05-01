# 복지로 Beneficiary Soft Taxonomy Dedupe 전략

## 목적

복지로 detail 본문에서 추출한 beneficiary soft taxonomy(`기초생활수급자`, `차상위계층`)는 저장 단계에서는 multi-term으로 보존한다.  
하지만 추천/read-model 단계에서는 같은 의미 축에서 중복 가중치만 만들지 않도록 dedupe가 필요하다.

## 현재 상태

- sidecar 저장:
  - `service_taxonomy_terms`
  - `term_group='TARGET_GROUP'`
  - `source_field='targetDetail/selectionCriteria'`
- 저장 정책:
  - `기초생활수급자`
  - `차상위계층`
  - 둘 다 source에 있으면 둘 다 저장

local snapshot 기준:

- whitelist term: `59 rows / 42 services`
- overlap service: `17`

즉 일부 서비스는 beneficiary label 두 개를 동시에 가진다.

## 결정

### 1. 저장 단계

- 그대로 multi-term 유지
- collapse 하지 않음
- raw 의미 보존 우선

### 2. read-model 단계

`TARGET_GROUP` raw label은 그대로 노출하되, 추천용 계산에는 별도 dedupe bucket을 만든다.

예시:

- raw terms:
  - `기초생활수급자`
  - `차상위계층`
- derived bucket:
  - `BENEFICIARY_SUPPORT`

즉 한 서비스에 두 raw term이 있어도, 추천 점수 계산에서는 `BENEFICIARY_SUPPORT` 하나로만 본다.

### 3. recommendation scoring 단계

- beneficiary soft taxonomy bonus는 서비스당 최대 1회만 허용
- `기초생활수급자` + `차상위계층` 동시 보유여도 stack 금지
- 설명/배지/UI에는 raw term 복수 노출 가능

## 적용 원칙

1. persistence는 원문 의미를 잃지 않는다.
2. read-model은 scoring-friendly bucket을 따로 만든다.
3. scoring은 bucket 기준으로 max-one bonus를 준다.
4. explanation/UI는 raw term을 그대로 쓸 수 있다.

## 현재 추천 코드와의 정합성

현재 [RuleScoringService.java](../../../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)는 `targetGroupMatches(...)`가 boolean 기반이라 같은 source에서 raw tag가 여러 개 있어도 즉시 2배 가산되지는 않는다.
다만 future canonical read-model이 `service_taxonomy_terms`를 읽기 시작하면 raw multi-term과 derived bucket을 동시에 점수에 반영해 중복 가중치가 생길 수 있으므로, 그 전에 dedupe 전략을 먼저 고정한다.

## 후속 구현 방향

1. taxonomy read-model에 raw `targetGroups` 와 deduped `targetGroupBuckets` 를 같이 만든다.
2. recommendation scoring은 `targetGroupBuckets` 만 사용한다.
3. beneficiary bucket 매칭은 현재 user snapshot 정밀도가 낮으므로 초기에는:
   - `incomeLevel <= 3` 이면 soft match 후보
   - 그 외는 no bonus
   로 단순 시작하고, 이후 user snapshot이 richer해지면 refine한다.

## 비목표

- 저장 단계에서 `기초생활수급자` 를 `차상위계층` 으로 흡수하지 않음
- raw term 하나만 남기도록 destructive collapse 하지 않음
- 현재 단계에서 new hard fact를 만들지 않음
