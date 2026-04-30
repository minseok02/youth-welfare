# OpenAI Replay Cron Runbook

`real-openai` diagnostic replay를 `ops cron host` 에 붙일 때의
최소 적용 절차입니다.

관련 문서:

- [openai-replay-diagnostic-lane-plan.md](./openai-replay-diagnostic-lane-plan.md)
- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)
- [openai-replay-cron-security-boundary.md](./openai-replay-cron-security-boundary.md)
- [deployment.md](./deployment.md)

## 목적

현재 lane의 목적은 PR hard gate가 아니라
nightly diagnostic artifact를 안정적으로 남기는 것입니다.

즉 이 runbook은 아래 두 작업만 다룹니다.

1. nightly real-openai replay 등록
2. daily retention cleanup 등록

## 사전 조건

아래가 먼저 만족돼야 합니다.

1. repo가 host에 배치돼 있다
   - 예: `/home/minseok/youth-welfare`
2. `.env` 가 최신 운영 값으로 채워져 있다
3. cron user가 아래 접근 권한을 가진다
   - repo read/execute
   - `.env` read
   - Docker / local DB / Redis
   - `OPENAI_API_KEY`
   - 자세한 경계는 [openai-replay-cron-security-boundary.md](./openai-replay-cron-security-boundary.md)를 따른다
4. replay log root를 만들 수 있다
   - 기본값: `/var/log/youth-welfare/openai-replay`

## 1. 사전 수동 검증

cron 등록 전에 아래 두 명령을 수동으로 먼저 실행합니다.

nightly replay wrapper:

```bash
REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay \
  /home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh
```

cleanup dry-run:

```bash
DRY_RUN=true \
REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay \
  /home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh
```

둘 다 통과한 뒤에만 cron을 등록합니다.

## 2. crontab 등록

대상 운영 계정으로 아래를 실행합니다.

```bash
crontab -e
```

권장 crontab block:

```cron
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# nightly real-openai diagnostic replay
10 1 * * * REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay /home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh >> /var/log/youth-welfare/openai-replay/nightly-cron.log 2>&1

# daily retention cleanup
40 1 * * * REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 /home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh >> /var/log/youth-welfare/openai-replay/cleanup-cron.log 2>&1
```

권장 이유:

1. replay와 cleanup이 서로 다른 runtime log를 남긴다
2. cleanup이 replay 직후 실패로 섞이지 않는다
3. wrapper/cleanup 스크립트의 env contract를 그대로 재사용한다

## 3. 등록 직후 확인

등록 직후 아래를 확인합니다.

1. `crontab -l`
2. log root 생성 여부
   - `/var/log/youth-welfare/openai-replay/`
3. wrapper/cleanup 스크립트 실행 권한
4. 다음 새벽 실행 전 수동 재실행 1회

권장 확인 명령:

```bash
crontab -l
ls -ld /var/log/youth-welfare/openai-replay
ls -l /home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh
ls -l /home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh
```

## 4. 다음날 확인 포인트

nightly replay 후에는 아래만 먼저 봅니다.

1. summary file 존재
   - `/var/log/youth-welfare/openai-replay/nightly-summary-YYYY-MM-DD.log`
2. artifact dir 생성
   - `/var/log/youth-welfare/openai-replay/artifacts/<timestamp>/`
3. runtime log 존재
   - `/var/log/youth-welfare/openai-replay/nightly-cron.log`
   - `/var/log/youth-welfare/openai-replay/cleanup-cron.log`

요약 확인 예시:

```bash
tail -n 5 /var/log/youth-welfare/openai-replay/nightly-summary-$(date +%F).log
tail -n 50 /var/log/youth-welfare/openai-replay/nightly-cron.log
tail -n 50 /var/log/youth-welfare/openai-replay/cleanup-cron.log
```

## 5. 롤백

문제가 있으면 cron line만 먼저 제거합니다.

```bash
crontab -e
```

제거 대상:

1. nightly replay line
2. cleanup line

artifact / summary file은 일단 남겨 두고,
원인 분석 후 수동으로 정리합니다.

## 운영 메모

1. summary file은 metric one-line append 용도입니다.
2. `nightly-cron.log`, `cleanup-cron.log` 는 shell/runtime failure triage 용도입니다.
3. `real-openai` nightly는 diagnostic lane이므로,
   summary warning만으로 바로 코드 회귀로 단정하지 않습니다.
