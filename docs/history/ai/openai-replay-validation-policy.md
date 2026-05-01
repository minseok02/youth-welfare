# OpenAI Replay Validation Policy

`교육 -> 교육·직업훈련` priority experiment를 포함한 추천 replay smoke에서,
`rule-only` 와 `real-openai` 결과를 어떤 기준으로 판정할지 정리한 문서입니다.

## 결론

현재 기본 검증선은 아래처럼 분리합니다.

1. `rule-only-invalid-key` replay
2. `real-openai` replay

이 둘은 같은 “pass/fail” 기준을 쓰지 않습니다.

## 1. `rule-only-invalid-key` 는 hard gate

`USE_REAL_OPENAI_FOR_REPLAY=false` 기본 모드에서는 아래를 **엄격한 통과 기준**으로 둡니다.

- sample A target improvement 존재
- sample B(control) target row count 비정상 증가 없음
- request trace 동일
- score snapshot drift 없음 또는 설명 가능한 최소 변화

이 모드의 목적은:

- retrieval
- rule scoring
- priority matcher
- canonical bridge
- re-ranking의 non-AI 부분

을 안정적으로 검증하는 것입니다.

즉 이 모드가 깨지면 코드 문제로 봅니다.

## 2. `real-openai` 는 exploratory gate

`USE_REAL_OPENAI_FOR_REPLAY=true` 모드에서는
OpenAI live response variability가 남아 있으므로,
현재 단계에서 `strict equality` 를 pass/fail 기준으로 두지 않습니다.

이 모드의 목적은:

- OpenAI 연동이 살아 있는지
- trace/artifact가 충분히 남는지
- drift가 어떤 층에서 생기는지
- same prompt / same seed / same fingerprint 조건에서도 variability가 남는지

를 관찰하는 것입니다.

즉 이 모드는 지금 당장은 **관측/분석 smoke** 입니다.

그리고 운영 기준으로는 이 replay를
**PR 기본 gate가 아니라 nightly/diagnostic lane** 으로 분리하는 것이 맞습니다.

## 허용 기준

### 허용

- sample A 개선 유지
- sample B top-10 target row count가 소폭 흔들리지 않거나, 흔들려도 artifact로 설명 가능
- `candidateIds`, `promptSha256`, `replaySeed`, `systemFingerprint` 증적 확보
- `ai_score` / `final_score` drift를 artifact로 재현 가능

### 비허용

- replay artifact가 불완전해 drift 층위를 분리할 수 없음
- request trace가 비어서 input drift 여부를 판단할 수 없음
- response trace가 없어 backend fingerprint 변화를 분리할 수 없음
- sample A가 아예 개선되지 않음

## 현재 운영 판단

현재는 아래 정책이 맞습니다.

1. PR 기본 검증선은 `rule-only` 결과
2. `real-openai` replay는 nightly/diagnostic lane의 supplementary evidence
3. `real-openai` strict equality 실패만으로 PR을 막지 않음
4. sample B `unexpected target count increase` 는 모든 `real-openai` replay에서 warning 으로 본다
5. 대신 trace/artifact가 남지 않는 실패는 막음

실제 lane 배치와 runner 후보는
[openai-replay-diagnostic-lane-plan.md](./openai-replay-diagnostic-lane-plan.md)
를 기준으로 봅니다.

즉:

- `rule-only` 안정성은 release blocker
- `real-openai` variability는 nightly/diagnostic 관측 대상

입니다.

## lane 분리 이유

`real-openai` replay를 PR hard gate에 두지 않는 이유는 세 가지입니다.

1. same `promptSha256` + same `replaySeed` + same `systemFingerprint`
   조건에서도 `ai_score` drift가 남았습니다.
2. 이 drift는 현재 코드 회귀와 live response variability를 완전히 분리하지 못합니다.
3. 그래서 PR 게이트에 두면 실제 코드 문제보다 false positive를 더 자주 만들 수 있습니다.

따라서 현재 권장 실행 위치는:

- PR / local development:
  - `rule-only-invalid-key` replay
- nightly / scheduled diagnostic:
  - `real-openai` replay
- 필요 시 수동 triage:
  - `USE_REAL_OPENAI_FOR_REPLAY=true` local replay

## future tightening 조건

아래가 충족되기 전까지는 `real-openai` 를 hard gate로 올리지 않습니다.

1. same `promptSha256` + same `replaySeed` + same `systemFingerprint` 에서 drift 분포를 더 수집
2. drift 허용 범위를 수치로 정의
3. 필요하면 replay cache / offline stub / deterministic evaluator 대안을 확정

그 뒤에만:

- top-N exact match
- score delta threshold
- explanation drift threshold

같은 stricter gate를 논의합니다.

## 권장 다음 순서

1. `rule-only` 를 계속 기본 smoke gate로 유지
2. `real-openai` replay artifact를 몇 번 더 쌓아 drift 분포를 본다
3. `allowed drift` 를 top-N count / score delta / explanation delta 중 무엇으로 볼지 결정
4. nightly artifact retention / summary 노출 / triage 절차를 운영 경로에 연결
