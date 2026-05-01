# OpenAI Replay Diagnostic Lane Plan

`real-openai` replay를 PR hard gate에서 분리한 뒤,
실제로 어느 실행 경로에 붙일지 정리한 문서입니다.

관련 문서:

- [openai-replay-validation-policy.md](./openai-replay-validation-policy.md)
- [openai-replay-allowed-drift-metrics.md](./openai-replay-allowed-drift-metrics.md)
- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)
- [openai-replay-cron-security-boundary.md](./openai-replay-cron-security-boundary.md)

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

## 권장 summary line 필드

nightly summary file에는 run당 **한 줄**만 append 하는 것을 기본값으로 둡니다.

권장 필드:

1. `ts`
   - run 종료 시각
2. `mode`
   - `rule-only-invalid-key` 또는 `real-openai`
3. `A_top10_target`
   - sample A `off->on`
4. `B_top10_target`
   - sample B `off->on`
5. `A_target_total`
   - sample A 전체 결과 내 target row 수 `off->on`
6. `B_target_total`
   - sample B 전체 결과 내 target row 수 `off->on`
7. `A_fp`
   - `same|different|missing`
8. `B_fp`
   - `same|different|missing`
9. `artifact_dir`
   - 상세 triage용 경로

예시:

```text
ts=2026-04-30T23:10:00+09:00 mode=real-openai A_top10_target=1->7 B_top10_target=0->1 A_target_total=12->12 B_target_total=3->3 A_fp=same B_fp=different artifact_dir=/var/log/youth-welfare/openai-replay/artifacts/2026-04-30T141000Z
```

## 왜 이 필드만 남기나

- `A_top10_target`, `B_top10_target` 는 현재 gate/warning 1순위 지표입니다.
- `A_target_total`, `B_target_total` 은 top-10 밖에 있던 target row 풀이 같이 줄었는지 보는 보조 지표입니다.
- `A_fp`, `B_fp` 는 warning 이후 triage 분기를 바로 돕습니다.
- `artifact_dir` 가 있어야 운영자가 summary file에서 바로 상세 증적으로 점프할 수 있습니다.

반대로 지금은 summary line에 아래를 넣지 않습니다.

- 개별 row `ai_score`
- `responseId`
- `systemFingerprint` 원문 값
- top-10 row title dump

이 값들은 한 줄 요약보다 artifact dir 안의 상세 파일에서 보는 편이 맞습니다.

## script/env contract

현재 기준에서 nightly summary line을 실제로 append 할 때는
[run-local-education-priority-replay.sh](/home/minseok/youth-welfare/deploy/smoke/run-local-education-priority-replay.sh)
에 아래 env contract를 씁니다.

- `REPLAY_SUMMARY_APPEND_FILE`
  - 비어 있지 않으면 summary line을 해당 파일에 append
  - 비어 있으면 stdout에만 출력
- `REPLAY_SUMMARY_TS`
  - 비어 있으면 스크립트가 local timezone 기준 current timestamp를 사용
  - cron wrapper가 명시 timestamp를 주고 싶으면 override 가능

즉 nightly cron wrapper 기본형은 아래처럼 잡습니다.

```bash
REPLAY_SUMMARY_APPEND_FILE=/var/log/youth-welfare/openai-replay/nightly-summary-$(date +%F).log \
REPLAY_SUMMARY_TS="$(date --iso-8601=seconds)" \
USE_REAL_OPENAI_FOR_REPLAY=true \
KEEP_ARTIFACTS=true \
deploy/smoke/run-local-education-priority-replay.sh
```

## nightly replay wrapper command/env contract

ops cron host에서는 아래 wrapper를 기본 진입점으로 둡니다.

- [run-nightly-openai-replay.sh](/home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh)

이 wrapper는 아래를 자동으로 정합니다.

- `USE_REAL_OPENAI_FOR_REPLAY=true`
- `KEEP_ARTIFACTS=true`
- `ARTIFACT_DIR=/var/log/youth-welfare/openai-replay/artifacts/<UTC timestamp>`
- `REPLAY_SUMMARY_APPEND_FILE=/var/log/youth-welfare/openai-replay/nightly-summary-YYYY-MM-DD.log`
- `REPLAY_SUMMARY_TS=<local timestamp>`

기본 명령 예시:

```bash
REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay \
deploy/smoke/run-nightly-openai-replay.sh
```

필요하면 아래 값만 override 합니다.

- `RUN_TS_UTC`
- `SUMMARY_DATE`
- `REPLAY_SUMMARY_TS`
- `ARTIFACT_DIR`
- `REPLAY_SUMMARY_APPEND_FILE`

## 권장 cleanup 실행 경계

cleanup 은 replay cron 후단에 섞지 않고,
**별도 daily cleanup cron** 으로 분리하는 편이 맞습니다.

권장 이유:

1. replay 실패와 retention 정리를 분리할 수 있습니다.
2. cleanup 실패가 replay 결과 성공/실패를 가리지 않습니다.
3. summary file/artifact 디렉터리 정리 로직을 독립적으로 재실행하기 쉽습니다.

즉 권장 구조는:

1. replay cron
   - replay 실행
   - artifact 생성
   - summary line append
2. cleanup cron
   - `30일` 초과 summary file 정리
   - `14일` 초과 artifact dir 정리

현재 단계에서는 이 둘을 같은 스크립트 후단에 묶지 않는 것이 맞습니다.

## cleanup cron command/env contract

cleanup cron은 아래 스크립트를 기준으로 둡니다.

- [cleanup-openai-replay-artifacts.sh](/home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh)

기본 env:

- `REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay`
- `SUMMARY_RETENTION_DAYS=30`
- `ARTIFACT_RETENTION_DAYS=14`
- `DRY_RUN=false`

권장 명령 예시:

```bash
REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay \
SUMMARY_RETENTION_DAYS=30 \
ARTIFACT_RETENTION_DAYS=14 \
deploy/smoke/cleanup-openai-replay-artifacts.sh
```

수동 검증 예시:

```bash
DRY_RUN=true \
REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay \
deploy/smoke/cleanup-openai-replay-artifacts.sh
```

## scheduler choice

현재 단계에서는 `nightly replay wrapper` 와 `cleanup cron` 둘 다
**systemd timer** 보다 **system cron** 을 먼저 쓰는 편이 맞습니다.

이유:

1. 현재 운영 문서가 host shell/compose/cron 수준 절차에 더 가깝습니다.
2. replay와 cleanup 모두 wrapper 스크립트가 이미 있어 cron line만 붙이면 됩니다.
3. 지금 필요한 건 observability보다 “가장 작은 운영 진입 경로” 입니다.

즉 권장 순서는:

1. `system cron` 으로 먼저 운영
2. 필요하면 이후 `systemd timer` 로 승격

현재 단계에서 보류하는 것:

- 전용 `.service` / `.timer` unit 파일 추가
- journal 기반 관찰 체계 먼저 설계

## system cron entry examples

현재 단계의 권장 형태는 host의 해당 계정 crontab에서
wrapper/cleanup 스크립트를 **절대경로로 직접 호출**하는 것입니다.

권장 전제:

1. repo root:
   - `/home/minseok/youth-welfare`
2. replay log root:
   - `/var/log/youth-welfare/openai-replay`
3. cron user:
   - 앱 `bootRun`, Docker, `.env`, OpenAI secret 접근 권한이 있는 동일 운영 계정

권장 crontab 예시:

```cron
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# nightly real-openai diagnostic replay
10 1 * * * REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay /home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh >> /var/log/youth-welfare/openai-replay/nightly-cron.log 2>&1

# daily retention cleanup
40 1 * * * REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 /home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh >> /var/log/youth-welfare/openai-replay/cleanup-cron.log 2>&1
```

## 왜 이 형태를 권장하나

1. replay cron은 `run-nightly-openai-replay.sh` 가 `USE_REAL_OPENAI_FOR_REPLAY`,
   `KEEP_ARTIFACTS`, `ARTIFACT_DIR`, `REPLAY_SUMMARY_APPEND_FILE` 를 직접 계산하므로
   cron line은 `REPLAY_LOG_ROOT` 정도만 알면 됩니다.
2. cleanup cron도 retention 숫자만 env로 주면 되므로
   host별 `find`/`rm` 명령 drift를 피할 수 있습니다.
3. `nightly-summary-YYYY-MM-DD.log` 는 metric one-line append 용도이고,
   `nightly-cron.log` / `cleanup-cron.log` 는 wrapper/runtime stderr/stdout 용도라
   역할이 섞이지 않습니다.

## 운영 메모

1. replay cron은 cleanup cron보다 먼저 두는 편이 낫습니다.
   - 권장 간격은 `20~30분`
2. cron 등록 전 수동 검증을 먼저 합니다.
   - replay:
     - `REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay /home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh`
   - cleanup dry-run:
     - `DRY_RUN=true REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay /home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh`
3. summary file과 runtime log file은 둘 다 host-local이지만 읽는 목적이 다릅니다.
   - summary file:
     - drift metric scan
   - runtime log:
     - shell/app failure triage
