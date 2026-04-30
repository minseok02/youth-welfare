# OpenAI AI Score Product Policy

same `promptSha256` + same `replaySeed` + same `systemFingerprint`
조건에서도 `ai_score` drift가 남는 상황에서,
제품이 무엇을 보장하고 무엇을 보장하지 않는지 정리한 문서입니다.

관련 문서:

- [openai-replay-validation-policy.md](./openai-replay-validation-policy.md)
- [openai-replay-allowed-drift-metrics.md](./openai-replay-allowed-drift-metrics.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

## 결론

현재 제품 정책은 아래처럼 둡니다.

1. `ai_score` 는 **deterministic truth** 가 아니라 **live rerank signal**
2. 추천 품질 보장의 기준선은 `rule-only` / retrieval / rule scoring / priority bridge
3. `real-openai` 는 추천을 보강하는 계층이지, 동일 입력에 동일 점수를 보장하는 계층이 아님
4. 따라서 제품은 `ai_score exact equality` 를 보장하지 않고,
   `target row visibility improvement + artifact traceability` 를 보장 대상으로 둔다

즉 지금 단계에서 `ai_score` 는
**설명 가능한 가변 보조 신호**
로 취급합니다.

## 제품이 보장하는 것

### 1. rule-only 기준선

아래는 코드/제품이 직접 통제하는 영역으로 봅니다.

- retrieval candidate filtering
- canonical bridge
- rule scoring
- priority weighting
- non-AI rerank 경계

즉 `rule-only-invalid-key` replay가 깨지면
코드 회귀로 봅니다.

### 2. target row visibility

현재 `교육 -> 교육·직업훈련` narrow experiment의 제품 목적은
`compat=기타 + youth_major=교육` row를
`priority=EDUCATION` 사용자에게 더 잘 보이게 하는 것입니다.

따라서 제품이 실제로 보는 성공 기준은:

- sample A target row top-N count improvement
- sample A target row presence 유지/증가
- control sample에서 catastrophic drift 없음

입니다.

### 3. artifact traceability

`real-openai` run에서는 아래 증적이 남아야 합니다.

- `candidateIds`
- `candidateRuleScores`
- `promptSha256`
- `replaySeed`
- `systemFingerprint`
- `responseId`
- off/on score snapshot

즉 drift가 나더라도
“어디에서 흔들렸는지 추적 가능함” 은 제품 운영 기준으로 보장합니다.

## 제품이 보장하지 않는 것

아래는 현재 단계에서 **보장하지 않습니다**.

1. same input에 same `ai_score`
2. same input에 same `final_score`
3. same input에 same `ai_reason`
4. same real-openai replay에서 sample B exact top-10 동일

즉 live OpenAI 계층은
현재 제품의 deterministic contract 밖에 있습니다.

## 왜 이렇게 두나

실측상 이미 아래가 확인됐습니다.

1. same `promptSha256`
2. same `replaySeed`
3. same `systemFingerprint`

조건에서도 `ai_score` drift가 남습니다.

이 상태에서 제품이 `ai_score exact equality` 를 품질 보장으로 들고 가면:

- false positive 회귀 판정이 늘고
- PR/ops 게이트가 과민해지고
- 사용자가 체감하는 추천 목적과 무관한 숫자 일치에 집착하게 됩니다

반면 실제 제품 목적은:

- target 정책이 더 위로 오는가
- control이 크게 망가지지 않는가
- drift가 나면 추적 가능한가

입니다.

## 권장 제품 해석

### 저장 관점

- `ai_score` 는 **run-local score snapshot**
- `final_score` 도 **run-local ranking outcome**

으로 봅니다.

즉 DB에 저장되더라도
“정책 자체의 절대 점수” 로 해석하지 않습니다.

### 응답 관점

- 추천 응답은 현재 run의 best-effort ranking 결과
- `ai_reason` 은 현재 run의 explanation

으로 봅니다.

즉 사용자에게는 “현재 추천 맥락의 결과”를 주는 것이지,
동일 입력 영구 재현성을 약속하는 것은 아닙니다.

### 운영 관점

- PR gate: `rule-only`
- nightly/diagnostic: `real-openai`
- drift triage: artifact review

이 3층 구조를 유지합니다.

## 현재 제품 정책 요약

1. `ai_score` 는 soft signal
2. `final_score` 는 run-local outcome
3. deterministic contract는 `rule-only` 쪽에 둔다
4. `real-openai` 는 target visibility 개선 여부와 trace completeness로 평가한다
5. exact score equality는 현재 제품 보장 범위 밖이다

## 다음 단계

나중에 아래가 생기면 정책을 다시 조일 수 있습니다.

1. replay cache
2. offline judge / deterministic evaluator
3. stable batch scoring lane
4. drift distribution 장기 축적

그 전까지는
`ai_score = variable-but-traceable rerank signal`
정책을 유지합니다.
