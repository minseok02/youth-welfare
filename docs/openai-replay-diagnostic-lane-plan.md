# OpenAI Replay Diagnostic Lane Plan

`real-openai` replay를 PR hard gate에서 분리한 뒤,
실제로 어느 실행 경로에 붙일지 정리한 문서입니다.

관련 문서:

- [openai-replay-validation-policy.md](./openai-replay-validation-policy.md)
- [openai-replay-allowed-drift-metrics.md](./openai-replay-allowed-drift-metrics.md)
- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)

## 결론

현재 기준에서 `real-openai` replay의 기본 실행 위치는 아래처럼 나눕니다.

1. PR lane
   - 붙이지 않음
2. local/manual diagnostic lane
   - `deploy/smoke/run-local-education-priority-replay.sh`
   - `USE_REAL_OPENAI_FOR_REPLAY=true`
   - 운영자/개발자가 필요할 때 수동 실행
3. scheduled diagnostic lane
   - future target
   - GitHub-hosted runner가 아니라
     **secret 이 있는 self-hosted runner 또는 ops cron host**
     에 붙임

즉, 지금 당장 repo 안의 기본 CI에 넣는 것이 아니라
`manual diagnostic first, scheduled diagnostic later`
순서로 갑니다.

## 왜 PR lane이 아닌가

이미 local replay evidence로 아래가 확인됐습니다.

- same `promptSha256`
- same `replaySeed`
- same `systemFingerprint`

조건에서도 `ai_score` / `final_score` drift가 남습니다.

따라서 `real-openai` replay를 PR lane에 넣으면:

- 코드 회귀가 없어도 flaky failure가 날 수 있고
- OpenAI secret 노출 범위를 PR 검증선까지 넓히게 되고
- 실행 시간/비용도 PR마다 반복됩니다

현재 제품이 통제할 수 있는 deterministic 경계는
`rule-only-invalid-key` replay 쪽이므로,
PR lane은 그쪽에만 남기는 것이 맞습니다.

## 왜 GitHub-hosted runner보다 self-hosted / ops host인가

현재 repo에는 `.github/workflows` 자체가 없고,
`real-openai` replay는 아래 전제를 요구합니다.

- OpenAI secret 주입
- local runtime bootRun
- local DB/Redis
- replay artifact 보관
- 필요 시 반복 실행

이 경로는 일반적인 public GitHub-hosted PR runner보다
아래 둘 중 하나가 더 자연스럽습니다.

### 1. self-hosted GitHub Actions runner

장점:

- schedule trigger를 GitHub에서 바로 관리 가능
- artifact 업로드/링크 연결이 쉬움
- PR과 별도 workflow로 lane 분리가 명확함

단점:

- runner 운영 부담
- secret / network / Docker 환경 준비 필요

### 2. ops cron host

장점:

- 지금 있는 `deploy/smoke` 스크립트를 거의 그대로 재사용 가능
- 로컬/운영 비슷한 실행 환경 확보가 쉬움
- GitHub Actions onboarding 없이 시작 가능

단점:

- artifact 수집/공유 체계를 따로 정해야 함
- 실행 기록 가시성이 약해질 수 있음

## 현재 권장안

지금 단계에서는 아래 순서가 맞습니다.

1. 기본 PR 검증은 계속 `rule-only-invalid-key`
2. `real-openai` replay는 local/manual diagnostic로 유지
3. 이후 자동화를 붙일 때는
   - 1순위: self-hosted scheduled workflow
   - 2순위: ops cron host

즉 “repo 기본 CI에 바로 넣는다”가 아니라,
“별도 secret-bearing diagnostic runner에 붙인다”가 현재 권장안입니다.

## 지금 당장 하지 않는 것

- PR workflow에 `USE_REAL_OPENAI_FOR_REPLAY=true` 추가
- GitHub-hosted 기본 runner에 OpenAI replay 강제
- `real-openai` strict equality를 merge blocker로 사용

## 다음 결정 포인트

1. self-hosted runner를 먼저 열 수 있는가
2. 아니면 ops cron host로 먼저 시작할 것인가
3. artifact를 어디에 보관할 것인가
4. nightly frequency를 매일로 둘지, 수동/on-demand 중심으로 둘지
