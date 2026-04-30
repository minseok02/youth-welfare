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
   - 1순위: ops cron host
   - 2순위: self-hosted scheduled workflow

즉 “repo 기본 CI에 바로 넣는다”가 아니라,
“별도 secret-bearing diagnostic runner에 붙인다”가 현재 권장안입니다.

## 왜 `ops cron host` 를 먼저 보나

현재 상태에서는 `ops cron host` 를 먼저 여는 쪽이 더 현실적입니다.

이유:

1. repo 안에 `.github/workflows` 가 아직 없습니다.
2. 이미 `deploy/smoke/run-local-education-priority-replay.sh` 가 host 기준 절차를 갖고 있습니다.
3. `deployment.md`, `runtime-cutover-checklist.md` 같은 운영 문서도 host/compose 중심으로 정리돼 있습니다.
4. 지금 필요한 건 merge blocker가 아니라 periodic diagnostic artifact 수집이므로,
   GitHub Actions onboarding보다 host cron이 더 짧은 경로입니다.

즉 현재 순서는:

1. local/manual diagnostic
2. ops cron host scheduled replay
3. 필요 시 self-hosted runner로 승격

입니다.

## 현재 보류하는 것

- GitHub Actions self-hosted runner를 먼저 여는 작업
- repo 안에 workflow 파일부터 만드는 작업
- PR comment/status check 와 `real-openai` replay를 직접 연결하는 작업

## 지금 당장 하지 않는 것

- PR workflow에 `USE_REAL_OPENAI_FOR_REPLAY=true` 추가
- GitHub-hosted 기본 runner에 OpenAI replay 강제
- `real-openai` strict equality를 merge blocker로 사용

## 권장 스케줄

현재 단계에서는 아래 스케줄이 맞습니다.

1. 기본 자동 실행:
   - `ops cron host`
   - **매일 밤 1회**
2. 추가 실행:
   - 추천/AI 관련 큰 변경 후 수동 on-demand replay
3. 하지 않는 것:
   - PR마다 실행
   - 짧은 간격 반복 실행

권장 이유:

- 지금 목적은 merge blocker가 아니라 drift 분포 관찰입니다.
- `real-openai` replay는 비용과 변동성이 있어 자주 돌릴수록 신호보다 노이즈가 늘 수 있습니다.
- 매일 1회면 same fingerprint / different fingerprint 분포와 sample A/B count 변화를 보기엔 충분하고,
  운영 부담도 가장 낮습니다.

즉 기본값은:

- `nightly once`
- `manual on demand after notable changes`

입니다.

## 다음 결정 포인트

1. ops cron host에서 artifact를 어디에 보관/공유할 것인가
2. 이후 self-hosted runner로 옮길 필요가 생기는 조건은 무엇인가

## 권장 요약 채널

현재 단계에서는 nightly 결과 요약 채널을
**외부 chat/email integration** 으로 넓히지 않고,
`ops cron host` 의 **append-only summary file** 로 시작하는 편이 맞습니다.

권장 형태:

1. cron job stdout/stderr는 run별 artifact dir에 남김
2. `SUMMARY_METRIC ...` 한 줄은
   host의 append-only daily summary file에도 함께 append
3. 운영자는 먼저 summary file을 보고,
   이상 시 해당 artifact dir을 열어 triage

즉 1차 채널은:

- `host-local summary file`

이고, 2차 상세 증적은:

- `artifact dir`

입니다.

## 왜 chat/email push를 바로 하지 않나

현재는 아래가 아직 없습니다.

- 팀 공용 Slack/ChatOps 경로
- replay 전용 메일 alias
- external notification secret/runbook

이 상태에서 chat/email push를 먼저 열면:

- 운영 경로가 repo 밖 의존성에 묶이고
- false positive drift가 바로 알림 피로로 번질 수 있고
- app Gmail/notification 경로와 diagnostic lane이 섞일 위험이 있습니다

따라서 지금은:

1. host-local summary file
2. artifact dir
3. 필요 시 사람이 수동 공유

순서가 맞습니다.

## 권장 경로 / rotate

현재 단계에서는 아래 경로를 기본값으로 둡니다.

- summary root:
  - `/var/log/youth-welfare/openai-replay/`
- daily summary file:
  - `/var/log/youth-welfare/openai-replay/nightly-summary-YYYY-MM-DD.log`
- run artifact root:
  - `/var/log/youth-welfare/openai-replay/artifacts/`
- run artifact dir:
  - `/var/log/youth-welfare/openai-replay/artifacts/YYYY-MM-DDTHHMMSSZ/`

rotate 기본값:

1. daily summary file
   - 일 단위 분리
   - 최근 `30일` 보관
2. run artifact dir
   - 최근 `14일` 보관
3. 보관 만료 정리
   - cron job 후단 또는 별도 daily cleanup step에서 삭제

## 왜 이 경로/보존기간인가

- `/var/log/youth-welfare/` 는 앱/운영 로그와 같은 host-local 관리 맥락에 놓기 쉽습니다.
- summary는 append-only이므로 30일 정도는 남겨야 drift 추세를 보기 쉽습니다.
- artifact는 크기가 더 크고 재현 확인이 끝나면 가치가 빨리 떨어지므로 14일 보관이면 충분합니다.
- summary와 artifact를 같은 root 아래 두되, 파일과 디렉터리를 분리하면 수동 triage가 단순해집니다.

즉:

- 장기 비교는 `nightly-summary-YYYY-MM-DD.log`
- 상세 triage는 `artifacts/<timestamp>/`

역할로 나눕니다.
