# OpenAI Replay Stability Options

`real-openai` replay에서 같은 `candidateIds` / `promptSha256` 인데도 `ai_score` 가 달라지는 현상을 줄이기 위해, 공식 OpenAI 문서 기준으로 선택지를 정리한 문서입니다.

## 현재 관찰

- local replay artifact `/tmp/tmp.WoIyHuKtMd` 기준 sample B는 `candidateIds`, `candidateRuleScores`, `promptSha256` 가 `off/on` 동일했습니다.
- 그런데도 `ai_score` / `final_score` drift가 재현됐습니다.
- 따라서 현재 경계는 retrieval/prompt drift가 아니라 `live AI 응답 변동성` 입니다.

## 현재 gateway 상태

- [RealtimeAiGateway.java](../../../backend/src/main/java/com/example/welfare/recommend/gateway/RealtimeAiGateway.java)는 `chat.completions` 를 사용합니다.
- 현재 request body에는 `model`, `messages`, `temperature=0.3`, `response_format` 만 들어가고 `seed` 는 없습니다.
- replay trace는 `candidateIds`, `candidateRuleScores`, `promptSha256` 만 남기고, OpenAI 응답의 `system_fingerprint` 나 request identifier 는 저장하지 않습니다.

## 공식 문서 기준

### 1. `seed` 는 determinism 보조 수단이다

OpenAI Chat Completions reference는 `seed` 를 주면 같은 파라미터 요청에 대해 **best effort** determinism 을 시도한다고 설명합니다.  
다만 determinism 은 보장되지 않고, backend 변화 여부는 `system_fingerprint` 로 같이 봐야 합니다.

실무 해석:

- 지금처럼 replay 실험을 할 때는 `seed` 가 가장 먼저 검토할 옵션입니다.
- 단, `seed` 만 넣고 `system_fingerprint` 를 안 남기면 drift 해석이 다시 모호해집니다.

### 2. `system_fingerprint` 는 같이 남겨야 한다

OpenAI reference는 `system_fingerprint` 를 backend 변화 추적에 쓰라고 안내합니다.

실무 해석:

- replay artifact에는 최소한 아래를 같이 남기는 게 맞습니다.
- `seed`
- `system_fingerprint`
- 가능하면 OpenAI response/request id

그래야 “같은 seed + 같은 prompt인데 backend fingerprint 가 달라졌는지”를 분리할 수 있습니다.

### 3. Prompt Caching 은 drift 해결책이 아니다

OpenAI Prompt Caching guide는 caching 을 latency/cost 최적화 기능으로 설명하고, cached prompt 자체가 output generation 을 바꾸지 않는다고 안내합니다.

실무 해석:

- prompt caching 은 replay drift 완화 1순위가 아닙니다.
- 지금 문제는 속도/비용이 아니라 같은 입력 대비 output variability 이므로, caching 을 먼저 넣어도 원인 분리는 거의 안 됩니다.

### 4. Prompt versioning 은 관리 도구이지 request determinism 도구는 아니다

Prompting guide의 long-lived prompt/versioning 은 prompt 관리와 실험 공유에 유용합니다.

실무 해석:

- prompt versioning 은 추후 prompt 관리에는 도움 되지만
- 현재 replay drift를 직접 줄이는 1차 수단은 아닙니다.

## 이번 판단

### 채택

1. `seed` optional support 추가
2. `system_fingerprint` / response id trace export 추가
3. same prompt + same seed + same fingerprint 에서도 drift가 남는지 재측정

### 보류

1. prompt caching 도입을 drift 대응으로 먼저 추진하지 않음
2. product default `temperature=0.3` 변경을 지금 바로 하지 않음
3. replay cache/mock layer 도입은 `seed + fingerprint` 증적 확보 뒤 재검토

## 권장 다음 순서

1. `RealtimeAiGateway` request body에 optional replay seed를 넣습니다.
2. OpenAI raw response에서 `system_fingerprint` 와 response id를 추출해 trace/artifact에 남깁니다.
3. `USE_REAL_OPENAI_FOR_REPLAY=true` 상태에서 same prompt/same seed replay를 다시 실행합니다.
4. 결과를 아래 셋으로 분리합니다.
   - fingerprint 도 다름
   - fingerprint 는 같고 ai_score 가 다름
   - ai_score 는 같고 final_score 만 다름
5. 그 뒤에만 temperature 조정 또는 replay cache 전략을 검토합니다.

## 참고 링크

- Chat Completions reference: https://platform.openai.com/docs/api-reference/chat/create-chat-completion
- Chat object / `seed` / `system_fingerprint`: https://platform.openai.com/docs/api-reference/chat/object
- Reproducible outputs / advanced usage: https://platform.openai.com/docs/advanced-usage/reproducible-outputs%3B.midi
- Prompt Caching guide: https://platform.openai.com/docs/guides/prompt-caching
- Prompting guide: https://platform.openai.com/docs/guides/prompting
