# policy duplicate review runbook

## 목적

이 문서는 `YOUTH` / `BOKJIRO_LOCAL` source에서 `title + host_org` 기준으로 반복되는 정책 묶음을 운영자가 review queue로 확인하는 절차입니다.

지금 이 queue는 broad parser regression을 찾기 위한 것이 아니라, 현재 남아 있는 **실제 중복 검토 backlog**를 빠르게 triage하기 위한 용도입니다.

## 어디서 보나

- admin API
  - `GET /api/admin/dashboard/policy-duplicate-groups?status=OPEN|REVIEWED|ALL`
  - `POST /api/admin/dashboard/policy-duplicate-groups/review`
- admin dashboard
  - `정책 중복 review queue`

## 실행

중복 묶음 baseline은 먼저 아래 audit로 확인합니다.

```bash
bash deploy/smoke/run-local-policy-data-quality-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-data-quality-audit.sh
```

## 현재 해석

- `missing_apply_period_*`, `missing_host_org_bokjiro_local`, `missing_detail_url_youth` 같은 값은 source contract 성격이 강합니다.
- 현재 운영자가 먼저 봐야 할 큐는 `duplicate title/host groups` 입니다.
- 대시보드 queue는 현재 duplicate group을 실시간 집계하고, review 상태만 별도 record로 덧씌우는 방식입니다.

## review 기준

- `OPEN`
  - 아직 운영 확인이 필요
- `REVIEWED`
  - 운영자가 중복 여부를 확인했고, note를 남긴 상태

현재는 `sourceType + title + hostOrgKey` 를 review key로 사용합니다.

## 메모

- 이 queue는 실제 중복 row가 사라지면 대시보드에서도 자연스럽게 빠집니다.
- review record는 “이 묶음을 운영자가 한 번 검토했다”는 메타데이터로만 남습니다.
