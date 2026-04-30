# OpenAI Replay Cron Security Boundary

`ops cron host` 에서 `real-openai` diagnostic replay를 돌릴 때
`cron user` 가 가져야 하는 권한과 가져서는 안 되는 권한 경계를 정리한 메모입니다.

관련 문서:

- [openai-replay-cron-runbook.md](./openai-replay-cron-runbook.md)
- [openai-replay-diagnostic-lane-plan.md](./openai-replay-diagnostic-lane-plan.md)
- [deployment.md](./deployment.md)

## 목적

nightly replay는 `OPENAI_API_KEY`, `.env`, local DB/Redis, host-local artifact 경로를 같이 다룹니다.
따라서 단순 cron line 예시만으로는 충분하지 않고,
`cron user` 의 권한 범위를 명시적으로 좁혀 두는 편이 안전합니다.

## 결론

현재 단계의 권장 모델은 아래입니다.

1. `cron user` 는 앱 운영 계정과 **같은 계정 또는 동등한 권한 계정**
2. 다만 replay 전용으로 필요한 권한만 가진다
3. `root` crontab 에 직접 올리는 것은 기본값으로 권장하지 않는다
4. OpenAI secret 은 repo 파일이 아니라 운영 `.env` / host secret 주입 경로에서만 읽는다

즉:

- `host-local operational user`
- `minimum sufficient secret access`
- `no extra privilege beyond app runtime needs`

를 기본값으로 둡니다.

## `cron user` 가 가져야 하는 것

1. repo read/execute
   - `deploy/smoke/run-nightly-openai-replay.sh`
   - `deploy/smoke/run-local-education-priority-replay.sh`
   - `deploy/smoke/cleanup-openai-replay-artifacts.sh`
2. 운영 `.env` read
   - replay 스크립트가 same-process env 로 읽을 수 있어야 함
3. local Docker / DB / Redis 접근
   - replay 경로는 local runtime boot와 backend smoke를 수반함
4. replay log root write
   - `/var/log/youth-welfare/openai-replay/`
5. OpenAI secret 접근
   - `OPENAI_API_KEY`

## `cron user` 가 기본적으로 가지지 않아야 하는 것

1. broad sudo
   - `sudo bash`, unrestricted root shell 등
2. unrelated deployment write 권한
   - system-wide config 전체 수정
   - 무관한 app/service 배포 파일 수정
3. repo write 권한의 과잉 사용
   - nightly replay는 repo 변경이 아니라 script 실행이 목적
4. 외부 notification secret 접근
   - Slack/email/chat webhook 같은 별도 비밀값
   - 현재 replay lane은 host-local summary/artifact 중심이라 기본 필요 없음

## 왜 `root` crontab 을 기본값으로 두지 않나

`root` crontab 은 편하지만, 현재 lane 목적에는 과합니다.

문제:

1. `.env` / OpenAI secret / host log path 접근이 모두 root 권한과 섞입니다.
2. replay script 오작동 시 영향 범위가 unnecessarily 커집니다.
3. cleanup script 의 `rm -rf` 계열도 root 범위로 넓어집니다.

따라서 현재 권장안은:

- replay를 실제로 수동 실행하는 운영 계정
- 또는 그와 동등한 제한 계정

에 cron을 두는 것입니다.

## `.env` / OpenAI secret 경계

현재 replay 경로는 host `.env` 를 읽습니다.

권장 기준:

1. `.env` 는 repo 안에 있더라도 권한을 운영 계정 수준으로 제한
2. cron user는 `.env` read만 필요하고 write는 기본 요구사항이 아님
3. `OPENAI_API_KEY` 는 `.env` 또는 host export 경로 중 **하나**로만 일관되게 관리
4. PR/CI lane에는 같은 secret 을 재사용하지 않음

즉 `real-openai` nightly replay secret 은
`ops cron host` 의 local diagnostic lane 범위 안에만 남겨 둡니다.

## artifact / summary 경계

artifact root와 summary file은 host-local로 남기되,
기본 권한은 cron user와 운영 계정만 읽을 수 있는 수준으로 두는 편이 맞습니다.

권장 경로:

- `/var/log/youth-welfare/openai-replay/nightly-summary-YYYY-MM-DD.log`
- `/var/log/youth-welfare/openai-replay/artifacts/<timestamp>/`

권장 이유:

1. replay artifact에는 recommendation snapshot, trace, fingerprint 정보가 남습니다.
2. 이것을 broad world-readable 로 둘 이유는 없습니다.
3. summary file도 drift 관찰용 운영 증적이므로 app public asset 처럼 다루면 안 됩니다.

## 운영 메모

1. 이 lane은 `diagnostic lane` 이므로,
   권한 모델도 `deploy automation` 보다 `local ops diagnostic` 에 가깝게 둡니다.
2. 더 엄격한 분리가 필요해지면 그때는
   - 별도 service account
   - dedicated secret store read policy
   - self-hosted runner isolation
   순서로 승격합니다.
3. 현재 단계에서는 그 전 단계로,
   `non-root ops cron user + host-local secret/artifact boundary`
   를 기본값으로 둡니다.
4. host 적용 전에는 문장 판단보다 실제 권한 체크 명령을 먼저 본다.
   - `id`
   - `crontab -l`
   - `test -r /home/minseok/youth-welfare/.env`
   - `test -w /var/log/youth-welfare/openai-replay`
   - `stat -c '%A %U:%G %n' ...`
