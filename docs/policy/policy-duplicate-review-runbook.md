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

## 운영 review에서 확인된 기준

실제 운영 sample review 기준으로는 아래처럼 읽는 게 맞습니다.

### `BOKJIRO_LOCAL`

- title 기준 false positive가 많습니다.
- 같은 title이어도 `구리/예산/사천/합천`, `하남/강남/고양/성북`, `청송/고창/음성/고령/진천/함양`처럼
  **기관/지역이 다르면 지역별 개별 사업으로 유지**하는 쪽이 기본값입니다.
- 즉 title만 같다고 바로 dedupe 후보로 올리면 안 됩니다.

### `YOUTH`

- 같은 기관 + 같은 기간 + 같은 URL 반복이면 **진짜 수집 중복 후보**일 가능성이 높습니다.
- URL만 다르면 mirror/channel variant인지 추가 확인이 필요합니다.
- 같은 title이어도 기관/지역/기간이 다르면 바로 dedupe 하지 않고 유지 쪽으로 먼저 봅니다.

즉 운영 기준상:

1. `BOKJIRO_LOCAL`
   - 기본값은 `지역별 개별 사업으로 유지`
2. `YOUTH`
   - `동일 기관 + 동일 기간 + 동일 URL` 반복을 먼저 진짜 중복 후보로 봄

`YOUTH` true duplicate candidate는 별도 audit로 먼저 좁혀서 보는 편이 맞습니다.

- [policy-youth-duplicate-candidate-audit-runbook.md](./policy-youth-duplicate-candidate-audit-runbook.md)

## 메모

- 이 queue는 실제 중복 row가 사라지면 대시보드에서도 자연스럽게 빠집니다.
- review record는 “이 묶음을 운영자가 한 번 검토했다”는 메타데이터로만 남습니다.
