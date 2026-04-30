# `교육` replay control sample `ruleWeightedScore` snapshot 확인

2026-04-30 기준
sample B(control) drift가 정말 `ruleWeightedScore` 단계부터 생기는지 보기 위해
`flag off` / `flag on` 을 각각 host `bootRun` 으로 띄우고,
같은 sample B 사용자의 `user_recommendations` snapshot을 직접 비교한 결과입니다.

관련 문서:

- [policy-normalization-education-control-drift-analysis.md](./policy-normalization-education-control-drift-analysis.md)
- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)

## 결론

direct snapshot 기준으로는
sample B의 `rule_weighted_score`, `ai_score`, `final_score` 가
`off/on` 사이에 **완전히 동일**했습니다.

즉 이번 direct capture만 놓고 보면:

- drift가 `ruleWeightedScore` 단계에서 시작된다고 볼 근거는 없음
- direct replay만으로는 `ReRankingService` request-local normalization 가설을 확정할 수 없음
- 다만 이후 full replay script artifact(`/tmp/tmp.aoUkRUpDdB`, 이후 확인된 `real-openai` mode 시점)에서
  `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 비교 결과
  `rule_weighted_score` 는 그대로인데 `ai_score` 와 `final_score` 가 함께 달라지는 snapshot이 추가로 확보됐다

정확히는:

- 이전 script artifact(`/tmp/tmp.pKVwRY4dlt`)에서 보였던
  `sample B finalScore drift`
- 이번 isolated `sample B only` direct replay snapshot

이 둘이 서로 다르다는 점이 더 중요합니다.

그리고 이후 기본 `rule-only-invalid-key` 모드로 full replay script를 다시 돌렸을 때도
sample B snapshot은 다시 동일하게 수렴했습니다.

## direct snapshot 조건

- app: host `bootRun`
- `flag off` / `flag on` 각각 별도 기동
- same sample B:
  - `education.replay.afterincome.b@example.com`
  - `regionCode=28110`
  - age `25`
  - `incomeLevel=5`
  - `interestFields=["교육"]`
  - `priorityCodes=["HOUSING","JOB"]`
- `OPENAI_API_KEY=invalid-for-rule-only-replay`

capture 대상:

- response JSON:
  - `/tmp/edu-control-off.json`
  - `/tmp/edu-control-on.json`
- persisted snapshot:
  - `/tmp/edu-control-off.tsv`
  - `/tmp/edu-control-on.tsv`

## 결과

response top-15:

- `off` 와 `on` 동일
- 상위 6건은 모두 `finalScore=1.0`
- 다음 9건은 모두 `finalScore=0.8333333333333334`

DB snapshot top-15:

- `off` 와 `on` 동일
- 상위 6건:
  - `rule_weighted_score=30.00`
  - `final_score=1.00000`
- 다음 9건:
  - `rule_weighted_score=25.00`
  - `final_score=0.83333`

`diff -u /tmp/edu-control-off.tsv /tmp/edu-control-on.tsv`
결과도 빈 출력이었다.

## 해석

이 direct snapshot은 적어도 다음 둘을 말해준다.

1. `sample B` 단독 direct replay에서는
   `education` canonical bonus가 control sample persisted score를 흔들지 않았다
2. 이전 full script artifact에서 보인 drift는
   `sample B` 단독 고정 조건이 아니라
   **full replay 문맥**
   에서 생긴 것일 가능성이 높다

가능한 후보:

- sample A refresh가 먼저 돌면서 생기는 full replay context 차이
- full script의 sample seed / refresh 순서 차이
- direct capture와 full replay script 사이의 AI score 계산/저장 경계 차이
- 그리고 당시 `rule-only` 스크립트가 `.env` 의 real `OPENAI_API_KEY` 를 상속해 실제 AI 호출이 섞였을 가능성

## 현재 판단

이제 가장 타당한 다음 질문은
“sample B drift가 normalization 때문인가” 자체보다는:

`sample B only direct replay에서는 drift가 없는데,
왜 full replay script artifact에서는 `ai_score` drift가 생겼는가`

입니다.

그래서 다음 작업은:

- `sample A -> sample B` 순서가 있는 full replay context에서
  `RealtimeAiGateway` 호출/실패/저장 경계를 추적하거나
- persisted snapshot에 이미 추가된 `ai_score` 기준으로
  drift source를 더 좁히는 것입니다.
